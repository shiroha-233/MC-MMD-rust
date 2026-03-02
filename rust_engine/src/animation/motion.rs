//! Motion 核心数据结构 - 复刻 mdanceio 实现

use std::collections::HashMap;

use super::bezier_curve::BezierCurveCache;
use super::motion_track::{BoneMotionTrack, MorphMotionTrack, IkMotionTrack, CameraMotionTrack, MotionTrack, BoneFrameTransform, CameraFrameTransform};
use super::keyframe::{BoneKeyframe, MorphKeyframe, IkKeyframe, CameraKeyframe};

/// 动画数据
#[derive(Debug, Clone)]
pub struct Motion {
    /// 骨骼动画轨道（骨骼名称 -> 轨道）
    pub bone_tracks: HashMap<String, BoneMotionTrack>,
    /// Morph 动画轨道（Morph 名称 -> 轨道）
    pub morph_tracks: HashMap<String, MorphMotionTrack>,
    /// IK 动画轨道（IK 名称 -> 轨道）
    pub ik_tracks: HashMap<String, IkMotionTrack>,
    /// 相机动画轨道（单一轨道）
    pub camera_track: CameraMotionTrack,
    /// 贝塞尔曲线缓存
    bezier_cache: BezierCurveCache,
    /// 是否脏（已修改）
    pub dirty: bool,
}

impl Motion {
    /// 最大帧索引
    pub const MAX_KEYFRAME_INDEX: u32 = u32::MAX;

    /// 创建空的 Motion
    pub fn new() -> Self {
        Self {
            bone_tracks: HashMap::new(),
            morph_tracks: HashMap::new(),
            ik_tracks: HashMap::new(),
            camera_track: CameraMotionTrack::new(),
            bezier_cache: BezierCurveCache::new(),
            dirty: false,
        }
    }

    /// 获取动画持续时间（最大帧索引）
    pub fn duration(&self) -> u32 {
        let bone_max = self.bone_tracks
            .values()
            .map(|t| t.max_frame_index())
            .max()
            .unwrap_or(0);
        
        let morph_max = self.morph_tracks
            .values()
            .map(|t| t.max_frame_index())
            .max()
            .unwrap_or(0);
        
        let camera_max = if self.camera_track.is_empty() {
            0
        } else {
            self.camera_track.max_frame_index()
        };
        
        bone_max.max(morph_max).max(camera_max)
    }

    /// 插入骨骼关键帧
    pub fn insert_bone_keyframe(&mut self, name: &str, keyframe: BoneKeyframe) {
        self.bone_tracks
            .entry(name.to_string())
            .or_insert_with(BoneMotionTrack::new)
            .insert_keyframe(keyframe);
        self.dirty = true;
    }

    /// 插入 Morph 关键帧
    pub fn insert_morph_keyframe(&mut self, name: &str, keyframe: MorphKeyframe) {
        self.morph_tracks
            .entry(name.to_string())
            .or_insert_with(MorphMotionTrack::new)
            .insert_keyframe(keyframe);
        self.dirty = true;
    }

    /// 插入相机关键帧
    pub fn insert_camera_keyframe(&mut self, keyframe: CameraKeyframe) {
        self.camera_track.insert_keyframe(keyframe);
        self.dirty = true;
    }

    /// 是否包含相机数据
    pub fn has_camera_data(&self) -> bool {
        !self.camera_track.is_empty()
    }

    /// 获取相机关键帧数量
    pub fn camera_keyframe_count(&self) -> u32 {
        self.camera_track.len() as u32
    }

    /// 获取相机帧变换
    pub fn find_camera_transform(
        &self,
        frame_index: u32,
        amount: f32,
    ) -> CameraFrameTransform {
        self.camera_track.seek_precisely(frame_index, amount, &self.bezier_cache)
    }

    /// 插入 IK 关键帧
    pub fn insert_ik_keyframe(&mut self, name: &str, keyframe: IkKeyframe) {
        self.ik_tracks
            .entry(name.to_string())
            .or_insert_with(IkMotionTrack::new)
            .insert_keyframe(keyframe);
        self.dirty = true;
    }

    /// 获取 IK 在指定帧的启用状态
    pub fn is_ik_enabled(&self, name: &str, frame_index: u32) -> bool {
        if let Some(track) = self.ik_tracks.get(name) {
            track.is_enabled_at(frame_index)
        } else {
            true // 默认启用
        }
    }

    /// 获取 IK 轨道名称列表
    pub fn ik_track_names(&self) -> impl Iterator<Item = &String> {
        self.ik_tracks.keys()
    }

    /// 查找骨骼关键帧
    pub fn find_bone_keyframe(&self, name: &str, frame_index: u32) -> Option<&BoneKeyframe> {
        self.bone_tracks
            .get(name)
            .and_then(|track| track.keyframes.get(&frame_index))
    }

    /// 查找 Morph 关键帧
    pub fn find_morph_keyframe(&self, name: &str, frame_index: u32) -> Option<&MorphKeyframe> {
        self.morph_tracks
            .get(name)
            .and_then(|track| track.keyframes.get(&frame_index))
    }

    /// 获取骨骼帧变换
    /// 
    /// # 参数
    /// - `name`: 骨骼名称
    /// - `frame_index`: 帧索引
    /// - `amount`: 帧间插值系数 [0, 1)
    pub fn find_bone_transform(
        &self,
        name: &str,
        frame_index: u32,
        amount: f32,
    ) -> BoneFrameTransform {
        if let Some(track) = self.bone_tracks.get(name) {
            track.seek_precisely(frame_index, amount, &self.bezier_cache)
        } else {
            BoneFrameTransform::default()
        }
    }

    /// 获取 Morph 权重
    /// 
    /// # 参数
    /// - `name`: Morph 名称
    /// - `frame_index`: 帧索引
    /// - `amount`: 帧间插值系数 [0, 1)
    pub fn find_morph_weight(
        &self,
        name: &str,
        frame_index: u32,
        amount: f32,
    ) -> f32 {
        if let Some(track) = self.morph_tracks.get(name) {
            track.seek_precisely(frame_index, amount, &self.bezier_cache)
        } else {
            0.0
        }
    }

    /// 获取骨骼轨道名称列表
    pub fn bone_track_names(&self) -> impl Iterator<Item = &String> {
        self.bone_tracks.keys()
    }

    /// 获取 Morph 轨道名称列表
    pub fn morph_track_names(&self) -> impl Iterator<Item = &String> {
        self.morph_tracks.keys()
    }

    /// 检查是否包含骨骼轨道
    pub fn contains_bone_track(&self, name: &str) -> bool {
        self.bone_tracks.contains_key(name)
    }

    /// 检查是否包含 Morph 轨道
    pub fn contains_morph_track(&self, name: &str) -> bool {
        self.morph_tracks.contains_key(name)
    }

    /// 获取骨骼轨道
    pub fn get_bone_track(&self, name: &str) -> Option<&BoneMotionTrack> {
        self.bone_tracks.get(name)
    }

    /// 获取 Morph 轨道
    pub fn get_morph_track(&self, name: &str) -> Option<&MorphMotionTrack> {
        self.morph_tracks.get(name)
    }

    /// 清除所有数据
    pub fn clear(&mut self) {
        self.bone_tracks.clear();
        self.morph_tracks.clear();
        self.camera_track = CameraMotionTrack::new();
        self.dirty = true;
    }

    /// 标记为脏
    pub fn set_dirty(&mut self, dirty: bool) {
        self.dirty = dirty;
    }

    /// 检查是否脏
    pub fn is_dirty(&self) -> bool {
        self.dirty
    }

    /// 合并另一个 Motion
    pub fn merge(&mut self, other: &Motion) {
        // 合并骨骼轨道
        for (name, track) in &other.bone_tracks {
            let entry = self.bone_tracks
                .entry(name.clone())
                .or_insert_with(BoneMotionTrack::new);
            
            for (_, keyframe) in &track.keyframes {
                entry.insert_keyframe(keyframe.clone());
            }
        }
        
        // 合并 Morph 轨道
        for (name, track) in &other.morph_tracks {
            let entry = self.morph_tracks
                .entry(name.clone())
                .or_insert_with(MorphMotionTrack::new);
            
            for (_, keyframe) in &track.keyframes {
                entry.insert_keyframe(keyframe.clone());
            }
        }
        
        self.dirty = true;
    }
}

impl Default for Motion {
    fn default() -> Self {
        Self::new()
    }
}
