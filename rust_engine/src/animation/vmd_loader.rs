//! VMD 文件加载器 - 复刻 mdanceio 实现
//!
//! 解析 VMD 动画文件并转换为 Motion 数据

use std::io::{BufReader, Read, Seek};
use std::fs::File;
use std::path::Path;

use glam::{Vec3, Quat};
use byteorder::{LittleEndian, ReadBytesExt};

use crate::{MmdError, Result};
use crate::skeleton::BoneManager;
use crate::morph::MorphManager;

use super::motion::Motion;
use super::keyframe::{BoneKeyframe, MorphKeyframe, IkKeyframe, CameraKeyframe, CameraInterpolation};
use super::motion_track::{BoneFrameTransform, CameraFrameTransform};

/// VMD 文件头
const VMD_HEADER_V1: &[u8] = b"Vocaloid Motion Data file";
const VMD_HEADER_V2: &[u8] = b"Vocaloid Motion Data 0002";

/// VMD 文件数据
#[derive(Debug, Clone)]
pub struct VmdFile {
    /// 模型名称
    pub model_name: String,
    /// Motion 数据
    pub motion: Motion,
}

impl VmdFile {
    /// 从文件路径加载 VMD
    pub fn load<P: AsRef<Path>>(path: P) -> Result<Self> {
        let file = File::open(path.as_ref())
            .map_err(|e| MmdError::Io(e))?;
        let mut reader = BufReader::new(file);
        Self::load_from_reader(&mut reader)
    }

    /// 从字节切片加载 VMD
    pub fn load_from_bytes(bytes: &[u8]) -> Result<Self> {
        let mut reader = std::io::Cursor::new(bytes);
        Self::load_from_reader(&mut reader)
    }

    /// 从 Reader 加载 VMD
    pub fn load_from_reader<R: Read + Seek>(reader: &mut R) -> Result<Self> {
        // 读取头部
        let mut header = [0u8; 30];
        reader.read_exact(&mut header)
            .map_err(|e| MmdError::VmdParse(format!("Failed to read header: {}", e)))?;

        // 验证头部 (两个头部都是 25 字节)
        let is_v1 = header[..25] == VMD_HEADER_V1[..];
        let is_v2 = header[..25] == VMD_HEADER_V2[..];
        
        if !is_v1 && !is_v2 {
            return Err(MmdError::VmdParse("Invalid VMD header".to_string()));
        }

        // 读取模型名称 (20 字节)
        let mut model_name_bytes = [0u8; 20];
        reader.read_exact(&mut model_name_bytes)
            .map_err(|e| MmdError::VmdParse(format!("Failed to read model name: {}", e)))?;
        let model_name = decode_shift_jis(&model_name_bytes);

        let mut motion = Motion::new();

        // 读取骨骼关键帧
        let bone_keyframe_count = reader.read_u32::<LittleEndian>()
            .map_err(|e| MmdError::VmdParse(format!("Failed to read bone keyframe count: {}", e)))?;

        for _ in 0..bone_keyframe_count {
            let (name, keyframe) = read_bone_keyframe(reader)?;
            motion.insert_bone_keyframe(&name, keyframe);
        }

        // 读取 Morph 关键帧
        let morph_keyframe_count = reader.read_u32::<LittleEndian>()
            .map_err(|e| MmdError::VmdParse(format!("Failed to read morph keyframe count: {}", e)))?;

        for _ in 0..morph_keyframe_count {
            let (name, keyframe) = read_morph_keyframe(reader)?;
            motion.insert_morph_keyframe(&name, keyframe);
        }

        // 尝试读取相机、光照、阴影和 IK 数据
        // 这些数据可能不存在（较老的 VMD 文件）
        if let Ok(camera_count) = reader.read_u32::<LittleEndian>() {
            // 解析相机关键帧 (每个 61 字节)
            for _ in 0..camera_count {
                if let Ok(keyframe) = read_camera_keyframe(reader) {
                    motion.insert_camera_keyframe(keyframe);
                }
            }
            
            // 尝试读取光照数据
            if let Ok(light_count) = reader.read_u32::<LittleEndian>() {
                // 跳过光照数据 (每个 28 字节)
                for _ in 0..light_count {
                    let mut buf = [0u8; 28];
                    let _ = reader.read_exact(&mut buf);
                }
                
                // 尝试读取阴影数据（可能不存在）
                if let Ok(shadow_count) = reader.read_u32::<LittleEndian>() {
                    // 跳过阴影数据 (每个 9 字节)
                    for _ in 0..shadow_count {
                        let mut buf = [0u8; 9];
                        let _ = reader.read_exact(&mut buf);
                    }
                    
                    // 尝试读取 IK 数据
                    if let Ok(ik_count) = reader.read_u32::<LittleEndian>() {
                        for _ in 0..ik_count {
                            if let Ok(ik_keyframes) = read_ik_keyframe(reader) {
                                for keyframe in ik_keyframes {
                                    let ik_name = keyframe.ik_name.clone();
                                    motion.insert_ik_keyframe(&ik_name, keyframe);
                                }
                            }
                        }
                    }
                }
            }
        }

        Ok(Self {
            model_name,
            motion,
        })
    }

    /// 获取最大帧数
    pub fn max_frame(&self) -> u32 {
        self.motion.duration()
    }
}

/// 读取骨骼关键帧
fn read_bone_keyframe<R: Read>(reader: &mut R) -> Result<(String, BoneKeyframe)> {
    // 骨骼名称 (15 字节)
    let mut name_bytes = [0u8; 15];
    reader.read_exact(&mut name_bytes)
        .map_err(|e| MmdError::VmdParse(format!("Failed to read bone name: {}", e)))?;
    let name = decode_shift_jis(&name_bytes);

    // 帧索引
    let frame_index = reader.read_u32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read frame index: {}", e)))?;

    // 平移 (x, y, z)
    let tx = reader.read_f32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read translation: {}", e)))?;
    let ty = reader.read_f32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read translation: {}", e)))?;
    let tz = reader.read_f32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read translation: {}", e)))?;

    // 旋转 (四元数 x, y, z, w)
    let rx = reader.read_f32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read rotation: {}", e)))?;
    let ry = reader.read_f32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read rotation: {}", e)))?;
    let rz = reader.read_f32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read rotation: {}", e)))?;
    let rw = reader.read_f32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read rotation: {}", e)))?;

    // 插值参数 (64 字节)
    let mut interpolation = [0u8; 64];
    reader.read_exact(&mut interpolation)
        .map_err(|e| MmdError::VmdParse(format!("Failed to read interpolation: {}", e)))?;

    // 提取插值参数
    // VMD 插值数据布局：每行 16 字节，共 4 行
    // 每行格式：X_x1, Y_x1, Z_x1, R_x1, X_y1, Y_y1, Z_y1, R_y1, ...
    let interpolation_x = [interpolation[0], interpolation[4], interpolation[8], interpolation[12]];
    let interpolation_y = [interpolation[1], interpolation[5], interpolation[9], interpolation[13]];
    let interpolation_z = [interpolation[2], interpolation[6], interpolation[10], interpolation[14]];
    let interpolation_r = [interpolation[3], interpolation[7], interpolation[11], interpolation[15]];

    // 坐标系转换：Z 轴和 W 分量反转
    let translation = Vec3::new(tx, ty, -tz);
    let orientation = Quat::from_xyzw(rx, ry, -rz, -rw).normalize();

    let keyframe = BoneKeyframe {
        frame_index,
        translation,
        orientation,
        interpolation_x,
        interpolation_y,
        interpolation_z,
        interpolation_r,
        is_physics_simulation_enabled: true,
    };

    Ok((name, keyframe))
}

/// 读取 Morph 关键帧
fn read_morph_keyframe<R: Read>(reader: &mut R) -> Result<(String, MorphKeyframe)> {
    // Morph 名称 (15 字节)
    let mut name_bytes = [0u8; 15];
    reader.read_exact(&mut name_bytes)
        .map_err(|e| MmdError::VmdParse(format!("Failed to read morph name: {}", e)))?;
    let name = decode_shift_jis(&name_bytes);

    // 帧索引
    let frame_index = reader.read_u32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read frame index: {}", e)))?;

    // 权重
    let weight = reader.read_f32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read weight: {}", e)))?;

    let keyframe = MorphKeyframe {
        frame_index,
        weight,
    };

    Ok((name, keyframe))
}

/// 读取 IK 关键帧
/// 每个 IK 帧包含帧索引、显示标志和多个 IK 信息
fn read_ik_keyframe<R: Read>(reader: &mut R) -> Result<Vec<IkKeyframe>> {
    // 帧索引
    let frame_index = reader.read_u32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read IK frame index: {}", e)))?;
    
    // 显示标志 (1 字节)
    let _show = reader.read_u8()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read IK show flag: {}", e)))?;
    
    // IK 信息数量
    let ik_info_count = reader.read_u32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read IK info count: {}", e)))?;
    
    let mut keyframes = Vec::with_capacity(ik_info_count as usize);
    
    for _ in 0..ik_info_count {
        // IK 名称 (20 字节)
        let mut name_bytes = [0u8; 20];
        reader.read_exact(&mut name_bytes)
            .map_err(|e| MmdError::VmdParse(format!("Failed to read IK name: {}", e)))?;
        let ik_name = decode_shift_jis(&name_bytes);
        
        // 启用标志 (1 字节)
        let enabled = reader.read_u8()
            .map_err(|e| MmdError::VmdParse(format!("Failed to read IK enable flag: {}", e)))? != 0;
        
        keyframes.push(IkKeyframe::new(frame_index, ik_name, enabled));
    }
    
    Ok(keyframes)
}

/// 读取相机关键帧
/// VMD 相机数据: 61 字节/帧
///   frame_index (u32, 4B)
///   distance (f32, 4B)
///   look_at (Vec3, 12B)
///   angle (Vec3, 12B) — 欧拉角，弧度
///   interpolation (24B) — 6组4字节贝塞尔参数
///   fov (u32, 4B)
///   is_perspective (u8, 1B)
fn read_camera_keyframe<R: Read>(reader: &mut R) -> Result<CameraKeyframe> {
    // 帧索引
    let frame_index = reader.read_u32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read camera frame index: {}", e)))?;

    // 距离
    let distance = reader.read_f32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read camera distance: {}", e)))?;

    // 目标点 (look_at)
    let lx = reader.read_f32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read camera look_at: {}", e)))?;
    let ly = reader.read_f32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read camera look_at: {}", e)))?;
    let lz = reader.read_f32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read camera look_at: {}", e)))?;

    // 角度（欧拉角，弧度）
    let ax = reader.read_f32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read camera angle: {}", e)))?;
    let ay = reader.read_f32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read camera angle: {}", e)))?;
    let az = reader.read_f32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read camera angle: {}", e)))?;

    // 插值参数 (24 字节 = 6组 * 4字节)
    // 顺序: X, Y, Z, Rotation, Distance, FOV
    let mut interp_raw = [0u8; 24];
    reader.read_exact(&mut interp_raw)
        .map_err(|e| MmdError::VmdParse(format!("Failed to read camera interpolation: {}", e)))?;

    let interpolation = CameraInterpolation {
        lookat_x: [interp_raw[0], interp_raw[1], interp_raw[2], interp_raw[3]],
        lookat_y: [interp_raw[4], interp_raw[5], interp_raw[6], interp_raw[7]],
        lookat_z: [interp_raw[8], interp_raw[9], interp_raw[10], interp_raw[11]],
        angle:    [interp_raw[12], interp_raw[13], interp_raw[14], interp_raw[15]],
        distance: [interp_raw[16], interp_raw[17], interp_raw[18], interp_raw[19]],
        fov:      [interp_raw[20], interp_raw[21], interp_raw[22], interp_raw[23]],
    };

    // FOV (u32)
    let fov = reader.read_u32::<LittleEndian>()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read camera fov: {}", e)))? as f32;

    // 是否透视 (u8)
    let is_perspective = reader.read_u8()
        .map_err(|e| MmdError::VmdParse(format!("Failed to read camera perspective flag: {}", e)))? == 0;

    // 保留 VMD 原始值（不做坐标系转换，由 compute_camera_transform 统一处理）
    let look_at = Vec3::new(lx, ly, lz);
    let angle = Vec3::new(ax, ay, az);

    Ok(CameraKeyframe {
        frame_index,
        look_at,
        angle,
        distance,
        fov,
        is_perspective,
        interpolation,
    })
}

/// 解码 Shift-JIS 字符串
fn decode_shift_jis(bytes: &[u8]) -> String {
    // 找到第一个 null 字节
    let end = bytes.iter().position(|&b| b == 0).unwrap_or(bytes.len());
    let bytes = &bytes[..end];
    
    // 使用 encoding_rs 解码
    let (decoded, _, _) = encoding_rs::SHIFT_JIS.decode(bytes);
    decoded.into_owned()
}

/// VMD 动画（运行时使用）
#[derive(Debug, Clone)]
pub struct VmdAnimation {
    /// Motion 数据
    motion: Motion,
}

impl VmdAnimation {
    /// 从 VmdFile 创建
    pub fn from_vmd_file(vmd: VmdFile) -> Self {
        Self {
            motion: vmd.motion,
        }
    }

    /// 是否包含相机数据
    pub fn has_camera(&self) -> bool {
        self.motion.has_camera_data()
    }

    /// 是否包含骨骼数据
    pub fn has_bones(&self) -> bool {
        !self.motion.bone_tracks.is_empty()
    }

    /// 是否包含表情数据
    pub fn has_morphs(&self) -> bool {
        !self.motion.morph_tracks.is_empty()
    }

    /// 合并另一个动画的骨骼和 Morph 数据
    pub fn merge(&mut self, other: &VmdAnimation) {
        self.motion.merge(&other.motion);
    }

    /// 获取相机帧变换
    pub fn get_camera_transform(&self, frame: f32) -> CameraFrameTransform {
        let frame = frame.max(0.0);
        let frame_index = frame.floor() as u32;
        let amount = frame.fract();
        self.motion.find_camera_transform(frame_index, amount)
    }

    /// 获取相机关键帧数量
    pub fn camera_keyframe_count(&self) -> u32 {
        self.motion.camera_keyframe_count()
    }

    /// 从文件路径加载
    pub fn load<P: AsRef<Path>>(path: P) -> Result<Self> {
        let vmd = VmdFile::load(path)?;
        Ok(Self::from_vmd_file(vmd))
    }

    /// 从字节加载
    pub fn load_from_bytes(bytes: &[u8]) -> Result<Self> {
        let vmd = VmdFile::load_from_bytes(bytes)?;
        Ok(Self::from_vmd_file(vmd))
    }

    /// 获取最大帧数
    pub fn max_frame(&self) -> u32 {
        self.motion.duration()
    }

    /// 获取骨骼帧变换
    pub fn get_bone_transform(&self, name: &str, frame_index: u32, amount: f32) -> BoneFrameTransform {
        self.motion.find_bone_transform(name, frame_index, amount)
    }

    /// 获取 Morph 权重
    pub fn get_morph_weight(&self, name: &str, frame_index: u32, amount: f32) -> f32 {
        self.motion.find_morph_weight(name, frame_index, amount)
    }

    /// 评估动画并应用到骨骼和 Morph
    /// 
    /// # 参数
    /// - `frame`: 浮点帧数（支持帧间插值）
    /// - `bone_manager`: 骨骼管理器
    /// - `morph_manager`: Morph 管理器
    pub fn evaluate(
        &self,
        frame: f32,
        bone_manager: &mut BoneManager,
        morph_manager: &mut MorphManager,
    ) {
        self.evaluate_with_weight(frame, 1.0, bone_manager, morph_manager);
    }

    /// 带权重评估动画
    /// 
    /// # 参数
    /// - `frame`: 浮点帧数
    /// - `weight`: 混合权重 [0, 1]
    /// - `bone_manager`: 骨骼管理器
    /// - `morph_manager`: Morph 管理器
    pub fn evaluate_with_weight(
        &self,
        frame: f32,
        weight: f32,
        bone_manager: &mut BoneManager,
        morph_manager: &mut MorphManager,
    ) {
        let frame = frame.max(0.0);
        let frame_index = frame.floor() as u32;
        let amount = frame.fract();

        // 应用骨骼动画
        for bone_name in self.motion.bone_track_names() {
            if let Some(bone_idx) = bone_manager.find_bone_by_name(bone_name) {
                let transform = self.motion.find_bone_transform(bone_name, frame_index, amount);
                
                if weight >= 1.0 {
                    bone_manager.set_bone_translation(bone_idx, transform.translation);
                    bone_manager.set_bone_rotation(bone_idx, transform.orientation);
                } else if weight > 0.0 {
                    if let Some(bone) = bone_manager.get_bone(bone_idx) {
                        let blended_translation = bone.animation_translate.lerp(transform.translation, weight);
                        let blended_rotation = bone.animation_rotate.slerp(transform.orientation, weight);
                        bone_manager.set_bone_translation(bone_idx, blended_translation);
                        bone_manager.set_bone_rotation(bone_idx, blended_rotation);
                    }
                }
            }
        }

        // 应用 Morph 动画
        for morph_name in self.motion.morph_track_names() {
            if let Some(morph_idx) = morph_manager.find_morph_by_name(morph_name) {
                let morph_weight = self.motion.find_morph_weight(morph_name, frame_index, amount);
                
                if weight >= 1.0 {
                    morph_manager.set_morph_weight(morph_idx, morph_weight);
                } else if weight > 0.0 {
                    let current = morph_manager.get_morph_weight(morph_idx);
                    let blended = current + (morph_weight - current) * weight;
                    morph_manager.set_morph_weight(morph_idx, blended);
                }
            }
        }
        
        // 应用 IK 启用/禁用状态
        for ik_name in self.motion.ik_track_names() {
            let enabled = self.motion.is_ik_enabled(ik_name, frame_index);
            if weight >= 1.0 {
                bone_manager.set_ik_enabled_by_name(ik_name, enabled);
            }
        }
    }

    /// 检查是否包含骨骼轨道
    pub fn contains_bone_track(&self, name: &str) -> bool {
        self.motion.contains_bone_track(name)
    }

    /// 检查是否包含 Morph 轨道
    pub fn contains_morph_track(&self, name: &str) -> bool {
        self.motion.contains_morph_track(name)
    }

    /// 获取骨骼轨道名称列表
    pub fn bone_track_names(&self) -> Vec<String> {
        self.motion.bone_track_names().cloned().collect()
    }

    /// 获取 Morph 轨道名称列表
    pub fn morph_track_names(&self) -> Vec<String> {
        self.motion.morph_track_names().cloned().collect()
    }
}
