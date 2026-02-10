//! 动画轨道 - 复刻 mdanceio 实现
//!
//! 存储单个骨骼或 Morph 的所有关键帧，并提供查找和插值功能

use std::collections::BTreeMap;
use glam::{Vec3, Quat, Mat4};

use super::bezier_curve::BezierCurveFactory;
use super::interpolation::{
    coefficient, lerp_element_wise, lerp_f32, 
    BoneKeyframeInterpolation, KeyframeInterpolationPoint,
};
use super::keyframe::{BoneKeyframe, MorphKeyframe, IkKeyframe};

/// 骨骼帧变换结果
#[derive(Debug, Clone, Copy)]
pub struct BoneFrameTransform {
    /// 平移
    pub translation: Vec3,
    /// 旋转
    pub orientation: Quat,
    /// 插值参数
    pub interpolation: BoneKeyframeInterpolation,
    /// 本地变换混合系数（用于物理过渡）
    pub local_transform_mix: Option<f32>,
    /// 是否启用物理
    pub enable_physics: bool,
    /// 是否禁用物理
    pub disable_physics: bool,
}

impl Default for BoneFrameTransform {
    fn default() -> Self {
        Self {
            translation: Vec3::ZERO,
            orientation: Quat::IDENTITY,
            interpolation: BoneKeyframeInterpolation::default(),
            local_transform_mix: None,
            enable_physics: false,
            disable_physics: false,
        }
    }
}

impl BoneFrameTransform {
    /// 混合平移（考虑 local_transform_mix）
    pub fn mixed_translation(&self, local_user_translation: Vec3) -> Vec3 {
        if let Some(coef) = self.local_transform_mix {
            local_user_translation.lerp(self.translation, coef)
        } else {
            self.translation
        }
    }

    /// 混合旋转（考虑 local_transform_mix）
    pub fn mixed_orientation(&self, local_user_orientation: Quat) -> Quat {
        if let Some(coef) = self.local_transform_mix {
            local_user_orientation.slerp(self.orientation, coef)
        } else {
            self.orientation
        }
    }
}

/// 动画轨道 trait
pub trait MotionTrack {
    type Frame;
    
    /// 查找精确帧
    fn find(&self, frame_index: u32) -> Option<Self::Frame>;
    
    /// 查找最近的前后帧索引
    fn search_closest(&self, frame_index: u32) -> (Option<u32>, Option<u32>);
    
    /// 求值指定帧
    fn seek(&self, frame_index: u32, bezier_factory: &dyn BezierCurveFactory) -> Self::Frame;
    
    /// 精确求值（支持帧间插值）
    fn seek_precisely(
        &self,
        frame_index: u32,
        amount: f32,
        bezier_factory: &dyn BezierCurveFactory,
    ) -> Self::Frame;
    
    /// 获取轨道长度
    fn len(&self) -> usize;
    
    /// 是否为空
    fn is_empty(&self) -> bool {
        self.len() == 0
    }
    
    /// 获取最大帧索引
    fn max_frame_index(&self) -> u32;
}

/// 骨骼动画轨道
#[derive(Debug, Clone)]
pub struct BoneMotionTrack {
    /// 关键帧映射（帧索引 -> 关键帧）
    pub keyframes: BTreeMap<u32, BoneKeyframe>,
}

impl BoneMotionTrack {
    pub fn new() -> Self {
        Self {
            keyframes: BTreeMap::new(),
        }
    }

    /// 插入关键帧
    pub fn insert_keyframe(&mut self, keyframe: BoneKeyframe) -> Option<BoneKeyframe> {
        self.keyframes.insert(keyframe.frame_index, keyframe)
    }

    /// 移除关键帧
    pub fn remove_keyframe(&mut self, frame_index: u32) -> Option<BoneKeyframe> {
        self.keyframes.remove(&frame_index)
    }

    /// 查找最近的前后关键帧
    fn search_closest_keyframes(&self, frame_index: u32) -> (Option<&BoneKeyframe>, Option<&BoneKeyframe>) {
        let mut prev = None;
        let mut next = None;
        
        for (idx, kf) in &self.keyframes {
            if *idx <= frame_index {
                prev = Some(kf);
            } else {
                next = Some(kf);
                break;
            }
        }
        
        (prev, next)
    }
}

impl Default for BoneMotionTrack {
    fn default() -> Self {
        Self::new()
    }
}

impl MotionTrack for BoneMotionTrack {
    type Frame = BoneFrameTransform;

    fn find(&self, frame_index: u32) -> Option<Self::Frame> {
        self.keyframes.get(&frame_index).map(|kf| BoneFrameTransform {
            translation: kf.translation,
            orientation: kf.orientation,
            interpolation: BoneKeyframeInterpolation::build(
                &kf.interpolation_x,
                &kf.interpolation_y,
                &kf.interpolation_z,
                &kf.interpolation_r,
            ),
            local_transform_mix: None,
            enable_physics: kf.is_physics_simulation_enabled,
            disable_physics: false,
        })
    }

    fn search_closest(&self, frame_index: u32) -> (Option<u32>, Option<u32>) {
        let mut prev = None;
        let mut next = None;
        
        for idx in self.keyframes.keys() {
            if *idx <= frame_index {
                prev = Some(*idx);
            } else {
                next = Some(*idx);
                break;
            }
        }
        
        (prev, next)
    }

    fn seek(&self, frame_index: u32, bezier_factory: &dyn BezierCurveFactory) -> Self::Frame {
        // 精确匹配
        if let Some(frame) = self.find(frame_index) {
            return frame;
        }
        
        // 查找前后关键帧
        let (prev_kf, next_kf) = self.search_closest_keyframes(frame_index);
        
        match (prev_kf, next_kf) {
            (Some(prev), Some(next)) => {
                let interval = next.frame_index - prev.frame_index;
                let coef = coefficient(prev.frame_index, next.frame_index, frame_index);
                
                let prev_enabled = prev.is_physics_simulation_enabled;
                let next_enabled = next.is_physics_simulation_enabled;
                
                // 物理状态变化处理
                if prev_enabled && !next_enabled {
                    BoneFrameTransform {
                        translation: next.translation,
                        orientation: next.orientation,
                        interpolation: BoneKeyframeInterpolation::build(
                            &next.interpolation_x,
                            &next.interpolation_y,
                            &next.interpolation_z,
                            &next.interpolation_r,
                        ),
                        local_transform_mix: Some(coef),
                        enable_physics: false,
                        disable_physics: true,
                    }
                } else {
                    // 正常插值
                    let translation_interpolation = [
                        KeyframeInterpolationPoint::new(&next.interpolation_x),
                        KeyframeInterpolationPoint::new(&next.interpolation_y),
                        KeyframeInterpolationPoint::new(&next.interpolation_z),
                    ];
                    
                    let amounts = Vec3::new(
                        translation_interpolation[0].curve_value(interval, coef, bezier_factory),
                        translation_interpolation[1].curve_value(interval, coef, bezier_factory),
                        translation_interpolation[2].curve_value(interval, coef, bezier_factory),
                    );
                    
                    let translation = lerp_element_wise(prev.translation, next.translation, amounts);
                    
                    let orientation_interpolation = KeyframeInterpolationPoint::new(&next.interpolation_r);
                    let amount = orientation_interpolation.curve_value(interval, coef, bezier_factory);
                    let orientation = prev.orientation.slerp(next.orientation, amount);
                    
                    BoneFrameTransform {
                        translation,
                        orientation,
                        interpolation: BoneKeyframeInterpolation::build(
                            &next.interpolation_x,
                            &next.interpolation_y,
                            &next.interpolation_z,
                            &next.interpolation_r,
                        ),
                        local_transform_mix: None,
                        enable_physics: prev_enabled && next_enabled,
                        disable_physics: false,
                    }
                }
            }
            (Some(prev), None) => {
                // 只有前帧，使用前帧数据
                BoneFrameTransform {
                    translation: prev.translation,
                    orientation: prev.orientation,
                    interpolation: BoneKeyframeInterpolation::build(
                        &prev.interpolation_x,
                        &prev.interpolation_y,
                        &prev.interpolation_z,
                        &prev.interpolation_r,
                    ),
                    local_transform_mix: None,
                    enable_physics: prev.is_physics_simulation_enabled,
                    disable_physics: false,
                }
            }
            (None, Some(next)) => {
                // 只有后帧，使用后帧数据
                BoneFrameTransform {
                    translation: next.translation,
                    orientation: next.orientation,
                    interpolation: BoneKeyframeInterpolation::build(
                        &next.interpolation_x,
                        &next.interpolation_y,
                        &next.interpolation_z,
                        &next.interpolation_r,
                    ),
                    local_transform_mix: None,
                    enable_physics: next.is_physics_simulation_enabled,
                    disable_physics: false,
                }
            }
            (None, None) => {
                // 无关键帧
                BoneFrameTransform::default()
            }
        }
    }

    fn seek_precisely(
        &self,
        frame_index: u32,
        amount: f32,
        bezier_factory: &dyn BezierCurveFactory,
    ) -> Self::Frame {
        let f0 = self.seek(frame_index, bezier_factory);
        
        if amount > 0.0 {
            let f1 = self.seek(frame_index.saturating_add(1), bezier_factory);
            
            let local_transform_mix = match (f0.local_transform_mix, f1.local_transform_mix) {
                (Some(a0), Some(a1)) => Some(lerp_f32(a0, a1, amount)),
                (None, Some(a1)) => Some(amount * a1),
                (Some(a0), None) => Some((1.0 - amount) * a0),
                _ => None,
            };
            
            BoneFrameTransform {
                translation: f0.translation.lerp(f1.translation, amount),
                orientation: f0.orientation.slerp(f1.orientation, amount),
                interpolation: f0.interpolation.lerp(f1.interpolation, amount),
                local_transform_mix,
                enable_physics: f0.enable_physics && f1.enable_physics,
                disable_physics: f0.disable_physics || f1.disable_physics,
            }
        } else {
            f0
        }
    }

    fn len(&self) -> usize {
        self.keyframes.len()
    }

    fn max_frame_index(&self) -> u32 {
        self.keyframes.keys().last().copied().unwrap_or(0)
    }
}

/// Morph 动画轨道
#[derive(Debug, Clone)]
pub struct MorphMotionTrack {
    /// 关键帧映射（帧索引 -> 关键帧）
    pub keyframes: BTreeMap<u32, MorphKeyframe>,
}

impl MorphMotionTrack {
    pub fn new() -> Self {
        Self {
            keyframes: BTreeMap::new(),
        }
    }

    /// 插入关键帧
    pub fn insert_keyframe(&mut self, keyframe: MorphKeyframe) -> Option<MorphKeyframe> {
        self.keyframes.insert(keyframe.frame_index, keyframe)
    }

    /// 移除关键帧
    pub fn remove_keyframe(&mut self, frame_index: u32) -> Option<MorphKeyframe> {
        self.keyframes.remove(&frame_index)
    }

    /// 查找最近的前后关键帧
    fn search_closest_keyframes(&self, frame_index: u32) -> (Option<&MorphKeyframe>, Option<&MorphKeyframe>) {
        let mut prev = None;
        let mut next = None;
        
        for (idx, kf) in &self.keyframes {
            if *idx <= frame_index {
                prev = Some(kf);
            } else {
                next = Some(kf);
                break;
            }
        }
        
        (prev, next)
    }
}

impl Default for MorphMotionTrack {
    fn default() -> Self {
        Self::new()
    }
}

impl MotionTrack for MorphMotionTrack {
    type Frame = f32;

    fn find(&self, frame_index: u32) -> Option<Self::Frame> {
        self.keyframes.get(&frame_index).map(|kf| kf.weight)
    }

    fn search_closest(&self, frame_index: u32) -> (Option<u32>, Option<u32>) {
        let mut prev = None;
        let mut next = None;
        
        for idx in self.keyframes.keys() {
            if *idx <= frame_index {
                prev = Some(*idx);
            } else {
                next = Some(*idx);
                break;
            }
        }
        
        (prev, next)
    }

    fn seek(&self, frame_index: u32, _bezier_factory: &dyn BezierCurveFactory) -> Self::Frame {
        // 精确匹配
        if let Some(weight) = self.find(frame_index) {
            return weight;
        }
        
        // 查找前后关键帧
        let (prev_kf, next_kf) = self.search_closest_keyframes(frame_index);
        
        match (prev_kf, next_kf) {
            (Some(prev), Some(next)) => {
                // Morph 使用线性插值
                let coef = coefficient(prev.frame_index, next.frame_index, frame_index);
                lerp_f32(prev.weight, next.weight, coef)
            }
            (Some(prev), None) => prev.weight,
            (None, Some(next)) => next.weight,
            (None, None) => 0.0,
        }
    }

    fn seek_precisely(
        &self,
        frame_index: u32,
        amount: f32,
        bezier_factory: &dyn BezierCurveFactory,
    ) -> Self::Frame {
        let w0 = self.seek(frame_index, bezier_factory);
        
        if amount > 0.0 {
            let w1 = self.seek(frame_index.saturating_add(1), bezier_factory);
            lerp_f32(w0, w1, amount)
        } else {
            w0
        }
    }

    fn len(&self) -> usize {
        self.keyframes.len()
    }

    fn max_frame_index(&self) -> u32 {
        self.keyframes.keys().last().copied().unwrap_or(0)
    }
}

/// IK 动画轨道
#[derive(Debug, Clone)]
pub struct IkMotionTrack {
    /// 关键帧映射（帧索引 -> 关键帧）
    pub keyframes: BTreeMap<u32, IkKeyframe>,
}

impl IkMotionTrack {
    pub fn new() -> Self {
        Self {
            keyframes: BTreeMap::new(),
        }
    }

    /// 插入关键帧
    pub fn insert_keyframe(&mut self, keyframe: IkKeyframe) -> Option<IkKeyframe> {
        self.keyframes.insert(keyframe.frame_index, keyframe)
    }

    /// 查找指定帧的 IK 启用状态
    pub fn is_enabled_at(&self, frame_index: u32) -> bool {
        let mut enabled = true; // 默认启用
        for (idx, kf) in &self.keyframes {
            if *idx <= frame_index {
                enabled = kf.enabled;
            } else {
                break;
            }
        }
        enabled
    }

    /// 获取最大帧索引
    pub fn max_frame_index(&self) -> u32 {
        self.keyframes.keys().last().copied().unwrap_or(0)
    }
}

impl Default for IkMotionTrack {
    fn default() -> Self {
        Self::new()
    }
}

/// 相机帧变换结果
#[derive(Debug, Clone, Copy)]
pub struct CameraFrameTransform {
    /// 相机位置（基于 look_at + distance + angle 计算）
    pub position: Vec3,
    /// 相机旋转（欧拉角，弧度：pitch, yaw, roll）
    pub rotation: Vec3,
    /// 视场角
    pub fov: f32,
    /// 是否透视
    pub is_perspective: bool,
}

impl Default for CameraFrameTransform {
    fn default() -> Self {
        Self {
            position: Vec3::ZERO,
            rotation: Vec3::ZERO,
            fov: 30.0,
            is_perspective: true,
        }
    }
}

/// 相机原始插值参数（compute_camera_transform 前的中间表示）
/// 用于在原始参数层面做帧间插值，避免对 atan2/asin 结果插值导致的不连续跳变
#[derive(Debug, Clone, Copy)]
struct CameraRawFrame {
    look_at: Vec3,
    angle: Vec3,
    distance: f32,
    fov: f32,
    is_perspective: bool,
}

impl Default for CameraRawFrame {
    fn default() -> Self {
        Self {
            look_at: Vec3::ZERO,
            angle: Vec3::ZERO,
            distance: 0.0,
            fov: 30.0,
            is_perspective: true,
        }
    }
}

use super::keyframe::CameraKeyframe;

/// 相机动画轨道（单一轨道，不按名称分）
#[derive(Debug, Clone)]
pub struct CameraMotionTrack {
    /// 关键帧映射（帧索引 -> 关键帧）
    pub keyframes: BTreeMap<u32, CameraKeyframe>,
}

impl CameraMotionTrack {
    pub fn new() -> Self {
        Self {
            keyframes: BTreeMap::new(),
        }
    }

    /// 插入关键帧
    pub fn insert_keyframe(&mut self, keyframe: CameraKeyframe) -> Option<CameraKeyframe> {
        self.keyframes.insert(keyframe.frame_index, keyframe)
    }

    /// 是否为空
    pub fn is_empty(&self) -> bool {
        self.keyframes.is_empty()
    }

    /// 关键帧数量
    pub fn len(&self) -> usize {
        self.keyframes.len()
    }

    /// 获取最大帧索引
    pub fn max_frame_index(&self) -> u32 {
        self.keyframes.keys().last().copied().unwrap_or(0)
    }

    /// 查找最近的前后关键帧
    fn search_closest_keyframes(&self, frame_index: u32) -> (Option<&CameraKeyframe>, Option<&CameraKeyframe>) {
        let mut prev = None;
        let mut next = None;
        
        for (idx, kf) in &self.keyframes {
            if *idx <= frame_index {
                prev = Some(kf);
            } else {
                next = Some(kf);
                break;
            }
        }
        
        (prev, next)
    }

    /// 从 CameraKeyframe 计算相机位置
    /// 复刻 mdanceio PerspectiveCamera.update() 的矩阵法：
    ///   1. angle 直接使用（mdanceio 的 CAMERA_DIRECTION 和 ANGLE_SCALE_FACTOR 双重取反抵消）
    ///   2. 四元数旋转顺序 z * x * y
    ///   3. 视图矩阵 = rotation * translation(-look_at)，然后 Z 列加 -distance（DISTANCE_FACTOR=-1）
    ///   4. 求逆得到世界位置
    ///   5. 转换到 MC 坐标系（Z 取反）并提取 pitch/yaw
    fn compute_camera_transform(look_at: Vec3, angle: Vec3, distance: f32, fov: f32, is_perspective: bool) -> CameraFrameTransform {
        // mdanceio 在 synchronize_camera 中对 angle 乘 CAMERA_DIRECTION(-1,1,1)，
        // 然后在 camera.update 中再乘 ANGLE_SCALE_FACTOR(-1,1,1)，双重取反抵消。
        // 因此直接使用 VMD 原始 angle，不做任何缩放。

        // 四元数旋转顺序: z * x * y (mdanceio camera.rs:115-118)
        let qx = Quat::from_rotation_x(angle.x);
        let qy = Quat::from_rotation_y(angle.y);
        let qz = Quat::from_rotation_z(angle.z);
        let view_orientation = qz * qx * qy;

        // 视图矩阵 = rotation * translation(-look_at)
        let rot_mat = Mat4::from_quat(view_orientation);
        let trans_mat = Mat4::from_translation(-look_at);
        let mut view_matrix = rot_mat * trans_mat;

        // mdanceio project.rs:1135 DISTANCE_FACTOR = -1.0，需要对 distance 取反
        view_matrix.w_axis.z += -distance;

        // 从视图矩阵求逆提取相机世界位置
        let inv = view_matrix.inverse();
        let position_mmd = Vec3::new(inv.w_axis.x, inv.w_axis.y, inv.w_axis.z);

        // MMD → MC 坐标转换（Z 取反）
        let position_mc = Vec3::new(position_mmd.x, position_mmd.y, -position_mmd.z);
        let look_at_mc = Vec3::new(look_at.x, look_at.y, -look_at.z);

        // 从相机指向 look_at 的方向提取 MC pitch/yaw
        let dir = (look_at_mc - position_mc).normalize_or_zero();
        // MC pitch: 正值 = 朝下看，yaw: 0 = 南(+Z)
        let mc_pitch = (-dir.y).asin();
        let mc_yaw = (-dir.x).atan2(dir.z);

        // roll 置零：MC Camera.setRotation() 不支持 roll，VMD angle.z 无法应用
        CameraFrameTransform {
            position: position_mc,
            rotation: Vec3::new(mc_pitch, mc_yaw, 0.0),
            fov,
            is_perspective,
        }
    }

    /// 求值指定帧的原始参数（整数帧，不经过 compute_camera_transform）
    fn seek_raw(&self, frame_index: u32, bezier_factory: &dyn BezierCurveFactory) -> CameraRawFrame {
        if self.keyframes.is_empty() {
            return CameraRawFrame::default();
        }

        // 精确匹配
        if let Some(kf) = self.keyframes.get(&frame_index) {
            return CameraRawFrame {
                look_at: kf.look_at,
                angle: kf.angle,
                distance: kf.distance,
                fov: kf.fov,
                is_perspective: kf.is_perspective,
            };
        }

        // 查找前后关键帧
        let (prev_kf, next_kf) = self.search_closest_keyframes(frame_index);

        match (prev_kf, next_kf) {
            (Some(prev), Some(next)) => {
                let interval = next.frame_index - prev.frame_index;
                let coef = coefficient(prev.frame_index, next.frame_index, frame_index);
                Self::interpolate_keyframes_raw(prev, next, interval, coef, bezier_factory)
            }
            (Some(kf), None) | (None, Some(kf)) => {
                CameraRawFrame {
                    look_at: kf.look_at,
                    angle: kf.angle,
                    distance: kf.distance,
                    fov: kf.fov,
                    is_perspective: kf.is_perspective,
                }
            }
            (None, None) => CameraRawFrame::default(),
        }
    }

    /// 求值指定帧（整数帧）
    pub fn seek(&self, frame_index: u32, bezier_factory: &dyn BezierCurveFactory) -> CameraFrameTransform {
        let raw = self.seek_raw(frame_index, bezier_factory);
        Self::compute_camera_transform(raw.look_at, raw.angle, raw.distance, raw.fov, raw.is_perspective)
    }

    /// 精确求值（支持帧间插值）
    /// 在原始参数（look_at, angle, distance, fov）层面做帧间线性插值，
    /// 最后只调用一次 compute_camera_transform，避免对 atan2/asin 结果插值导致的不连续跳变。
    pub fn seek_precisely(
        &self,
        frame_index: u32,
        amount: f32,
        bezier_factory: &dyn BezierCurveFactory,
    ) -> CameraFrameTransform {
        let f0 = self.seek_raw(frame_index, bezier_factory);

        if amount > 0.0 {
            let f1 = self.seek_raw(frame_index.saturating_add(1), bezier_factory);

            let raw = CameraRawFrame {
                look_at: f0.look_at.lerp(f1.look_at, amount),
                angle: Vec3::new(
                    lerp_f32(f0.angle.x, f1.angle.x, amount),
                    lerp_f32(f0.angle.y, f1.angle.y, amount),
                    lerp_f32(f0.angle.z, f1.angle.z, amount),
                ),
                distance: lerp_f32(f0.distance, f1.distance, amount),
                fov: lerp_f32(f0.fov, f1.fov, amount),
                is_perspective: f0.is_perspective,
            };
            Self::compute_camera_transform(raw.look_at, raw.angle, raw.distance, raw.fov, raw.is_perspective)
        } else {
            Self::compute_camera_transform(f0.look_at, f0.angle, f0.distance, f0.fov, f0.is_perspective)
        }
    }

    /// 在两个关键帧之间插值原始参数（使用贝塞尔曲线）
    fn interpolate_keyframes_raw(
        prev: &CameraKeyframe,
        next: &CameraKeyframe,
        interval: u32,
        coef: f32,
        bezier_factory: &dyn BezierCurveFactory,
    ) -> CameraRawFrame {
        let interp = &next.interpolation;

        // look_at XYZ 各自贝塞尔插值
        let ip_x = KeyframeInterpolationPoint::new(&interp.lookat_x);
        let ip_y = KeyframeInterpolationPoint::new(&interp.lookat_y);
        let ip_z = KeyframeInterpolationPoint::new(&interp.lookat_z);
        let ip_angle = KeyframeInterpolationPoint::new(&interp.angle);
        let ip_distance = KeyframeInterpolationPoint::new(&interp.distance);
        let ip_fov = KeyframeInterpolationPoint::new(&interp.fov);

        let ax = ip_x.curve_value(interval, coef, bezier_factory);
        let ay = ip_y.curve_value(interval, coef, bezier_factory);
        let az = ip_z.curve_value(interval, coef, bezier_factory);
        let a_angle = ip_angle.curve_value(interval, coef, bezier_factory);
        let a_distance = ip_distance.curve_value(interval, coef, bezier_factory);
        let a_fov = ip_fov.curve_value(interval, coef, bezier_factory);

        let look_at = Vec3::new(
            lerp_f32(prev.look_at.x, next.look_at.x, ax),
            lerp_f32(prev.look_at.y, next.look_at.y, ay),
            lerp_f32(prev.look_at.z, next.look_at.z, az),
        );

        let angle = Vec3::new(
            lerp_f32(prev.angle.x, next.angle.x, a_angle),
            lerp_f32(prev.angle.y, next.angle.y, a_angle),
            lerp_f32(prev.angle.z, next.angle.z, a_angle),
        );

        let distance = lerp_f32(prev.distance, next.distance, a_distance);
        let fov = lerp_f32(prev.fov, next.fov, a_fov);

        CameraRawFrame {
            look_at,
            angle,
            distance,
            fov,
            is_perspective: prev.is_perspective,
        }
    }
}

impl Default for CameraMotionTrack {
    fn default() -> Self {
        Self::new()
    }
}
