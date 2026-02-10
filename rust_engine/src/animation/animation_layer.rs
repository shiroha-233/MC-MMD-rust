//! 动画层系统 - 复刻 mdanceio 实现
//!
//! 支持多轨并行动画：
//! - 每层有独立的时间轴、权重和播放状态
//! - 支持淡入淡出过渡
//! - 层间动画通过权重混合
//! - 支持姿态缓存过渡（Pose Snapshot Blend）

use std::collections::HashMap;
use std::sync::Arc;

use glam::{Vec3, Quat};

use crate::skeleton::BoneManager;
use crate::morph::MorphManager;

use super::VmdAnimation;

// ============================================================================
// 姿态快照
// ============================================================================

/// 单个骨骼的姿态数据
#[derive(Clone, Debug)]
pub struct BonePose {
    pub translation: Vec3,
    pub rotation: Quat,
}

impl Default for BonePose {
    fn default() -> Self {
        Self {
            translation: Vec3::ZERO,
            rotation: Quat::IDENTITY,
        }
    }
}

/// 姿态快照 - 存储切换动画时的骨骼和 Morph 状态
#[derive(Clone, Debug, Default)]
pub struct PoseSnapshot {
    /// 骨骼姿态（索引 -> 姿态）
    pub bone_poses: HashMap<usize, BonePose>,
    /// Morph 权重（索引 -> 权重）
    pub morph_weights: HashMap<usize, f32>,
}

impl PoseSnapshot {
    /// 从 BoneManager 和 MorphManager 捕获当前姿态
    pub fn capture(bone_manager: &BoneManager, morph_manager: &MorphManager) -> Self {
        let mut bone_poses = HashMap::new();
        let mut morph_weights = HashMap::new();
        
        // 捕获所有骨骼姿态
        for i in 0..bone_manager.bone_count() {
            if let Some(bone) = bone_manager.get_bone(i) {
                bone_poses.insert(i, BonePose {
                    translation: bone.animation_translate,
                    rotation: bone.animation_rotate,
                });
            }
        }
        
        // 捕获所有 Morph 权重
        for i in 0..morph_manager.morph_count() {
            let weight = morph_manager.get_morph_weight(i);
            if weight.abs() > 0.001 {
                morph_weights.insert(i, weight);
            }
        }
        
        Self { bone_poses, morph_weights }
    }
    
    /// 检查快照是否为空
    pub fn is_empty(&self) -> bool {
        self.bone_poses.is_empty() && self.morph_weights.is_empty()
    }
}

/// 动画层状态
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum AnimationLayerState {
    /// 停止状态
    Stopped,
    /// 播放中
    Playing,
    /// 暂停
    Paused,
    /// 淡入中
    FadingIn,
    /// 淡出中
    FadingOut,
    /// 过渡中（从缓存姿态过渡到新动画）
    Transitioning,
}

/// 动画层配置
#[derive(Clone, Debug)]
pub struct AnimationLayerConfig {
    /// 层权重（0.0 - 1.0）
    pub weight: f32,
    /// 播放速度倍率
    pub speed: f32,
    /// 是否循环播放
    pub loop_playback: bool,
    /// 淡入时间（秒）
    pub fade_in_time: f32,
    /// 淡出时间（秒）
    pub fade_out_time: f32,
}

impl Default for AnimationLayerConfig {
    fn default() -> Self {
        Self {
            weight: 1.0,
            speed: 1.0,
            loop_playback: true,
            fade_in_time: 0.0,
            fade_out_time: 0.0,
        }
    }
}

/// 单个动画层
pub struct AnimationLayer {
    /// 层ID
    pub id: usize,
    /// 层名称
    pub name: String,
    /// 当前动画
    animation: Option<Arc<VmdAnimation>>,
    /// 当前播放帧
    current_frame: f32,
    /// 当前状态
    state: AnimationLayerState,
    /// 配置
    config: AnimationLayerConfig,
    /// 实际权重（考虑淡入淡出）
    effective_weight: f32,
    /// 淡入淡出进度（0.0 - 1.0）
    fade_progress: f32,
    /// 是否启用
    enabled: bool,
    
    // ======== 姿态缓存过渡相关 ========
    
    /// 过渡开始时的姿态快照
    transition_snapshot: Option<PoseSnapshot>,
    /// 过渡时间（秒）
    transition_duration: f32,
    /// 过渡进度（0.0 - 1.0）
    transition_progress: f32,
}

impl AnimationLayer {
    /// 创建新层
    pub fn new(id: usize, name: impl Into<String>) -> Self {
        Self {
            id,
            name: name.into(),
            animation: None,
            current_frame: 0.0,
            state: AnimationLayerState::Stopped,
            config: AnimationLayerConfig::default(),
            effective_weight: 0.0,
            fade_progress: 0.0,
            enabled: true,
            transition_snapshot: None,
            transition_duration: 0.0,
            transition_progress: 0.0,
        }
    }

    /// 设置动画（无过渡，直接替换）
    pub fn set_animation(&mut self, animation: Option<Arc<VmdAnimation>>) {
        self.animation = animation;
        self.current_frame = 0.0;
        self.state = AnimationLayerState::Stopped;
        self.effective_weight = 0.0;
        self.fade_progress = 0.0;
        self.transition_snapshot = None;
        self.transition_progress = 0.0;
    }
    
    /// 带过渡地切换动画（姿态缓存过渡）
    /// 
    /// # 参数
    /// - `animation`: 新动画
    /// - `transition_time`: 过渡时间（秒）
    /// - `bone_manager`: 当前骨骼管理器（用于捕获姿态）
    /// - `morph_manager`: 当前 Morph 管理器
    pub fn transition_to(
        &mut self,
        animation: Option<Arc<VmdAnimation>>,
        transition_time: f32,
        bone_manager: &BoneManager,
        morph_manager: &MorphManager,
    ) {
        // 捕获当前姿态
        let snapshot = PoseSnapshot::capture(bone_manager, morph_manager);
        
        // 设置新动画
        self.animation = animation;
        self.current_frame = 0.0;
        
        if transition_time > 0.0 && !snapshot.is_empty() {
            // 开始过渡
            self.transition_snapshot = Some(snapshot);
            self.transition_duration = transition_time;
            self.transition_progress = 0.0;
            self.state = AnimationLayerState::Transitioning;
            self.effective_weight = self.config.weight;
        } else {
            // 无过渡，直接播放
            self.transition_snapshot = None;
            self.state = AnimationLayerState::Playing;
            self.effective_weight = self.config.weight;
        }
        
    }

    /// 播放动画
    pub fn play(&mut self) {
        if self.animation.is_some() {
            if self.config.fade_in_time > 0.0 {
                self.state = AnimationLayerState::FadingIn;
                self.fade_progress = 0.0;
            } else {
                self.state = AnimationLayerState::Playing;
                self.effective_weight = self.config.weight;
            }
        }
    }

    /// 暂停动画
    pub fn pause(&mut self) {
        if self.state == AnimationLayerState::Playing || self.state == AnimationLayerState::FadingIn {
            self.state = AnimationLayerState::Paused;
        }
    }

    /// 恢复播放
    pub fn resume(&mut self) {
        if self.state == AnimationLayerState::Paused {
            self.state = AnimationLayerState::Playing;
        }
    }

    /// 停止动画
    pub fn stop(&mut self) {
        if self.config.fade_out_time > 0.0 && self.effective_weight > 0.0 {
            self.state = AnimationLayerState::FadingOut;
            self.fade_progress = 1.0;
        } else {
            self.state = AnimationLayerState::Stopped;
            self.effective_weight = 0.0;
            self.current_frame = 0.0;
        }
    }

    /// 立即重置
    pub fn reset(&mut self) {
        self.current_frame = 0.0;
        self.state = AnimationLayerState::Stopped;
        self.effective_weight = 0.0;
        self.fade_progress = 0.0;
        self.transition_snapshot = None;
        self.transition_progress = 0.0;
    }

    /// 跳转到指定帧
    pub fn seek_to(&mut self, frame: f32) {
        self.current_frame = frame.max(0.0);
    }

    /// 设置权重
    pub fn set_weight(&mut self, weight: f32) {
        self.config.weight = weight.clamp(0.0, 1.0);
        if self.state != AnimationLayerState::FadingIn && self.state != AnimationLayerState::FadingOut {
            self.effective_weight = self.config.weight;
        }
    }

    /// 设置播放速度
    pub fn set_speed(&mut self, speed: f32) {
        self.config.speed = speed.max(0.0);
    }

    /// 更新层状态
    pub fn update(&mut self, delta_time: f32) -> bool {
        if !self.enabled || self.animation.is_none() {
            return false;
        }

        let dt = delta_time * self.config.speed;

        match self.state {
            AnimationLayerState::Playing => {
                self.update_frame(dt);
                true
            }
            AnimationLayerState::FadingIn => {
                self.update_fade_in(dt);
                self.update_frame(dt);
                true
            }
            AnimationLayerState::FadingOut => {
                self.update_fade_out(dt);
                self.update_frame(dt);
                true
            }
            AnimationLayerState::Transitioning => {
                self.update_transition(delta_time); // 使用原始 delta_time，不受速度影响
                self.update_frame(dt);
                true
            }
            AnimationLayerState::Paused => true,
            AnimationLayerState::Stopped => false,
        }
    }

    /// 更新帧位置
    fn update_frame(&mut self, dt: f32) {
        if let Some(ref anim) = self.animation {
            let max_frame = anim.max_frame() as f32;
            
            if max_frame > 0.0 {
                self.current_frame += dt * 30.0; // 30 FPS
                
                if self.current_frame > max_frame {
                    if self.config.loop_playback {
                        self.current_frame = self.current_frame % max_frame;
                    } else {
                        self.current_frame = max_frame;
                        self.state = AnimationLayerState::Stopped;
                    }
                }
            }
        }
    }

    /// 更新淡入
    fn update_fade_in(&mut self, dt: f32) {
        if self.config.fade_in_time > 0.0 {
            self.fade_progress += dt / self.config.fade_in_time;
            if self.fade_progress >= 1.0 {
                self.fade_progress = 1.0;
                self.state = AnimationLayerState::Playing;
            }
        } else {
            self.fade_progress = 1.0;
            self.state = AnimationLayerState::Playing;
        }
        self.effective_weight = self.config.weight * self.fade_progress;
    }

    /// 更新淡出
    fn update_fade_out(&mut self, dt: f32) {
        if self.config.fade_out_time > 0.0 {
            self.fade_progress -= dt / self.config.fade_out_time;
            if self.fade_progress <= 0.0 {
                self.fade_progress = 0.0;
                self.state = AnimationLayerState::Stopped;
                self.current_frame = 0.0;
            }
        } else {
            self.fade_progress = 0.0;
            self.state = AnimationLayerState::Stopped;
            self.current_frame = 0.0;
        }
        self.effective_weight = self.config.weight * self.fade_progress;
    }
    
    /// 更新过渡状态
    fn update_transition(&mut self, dt: f32) {
        if self.transition_duration > 0.0 {
            self.transition_progress += dt / self.transition_duration;
            if self.transition_progress >= 1.0 {
                self.transition_progress = 1.0;
                self.state = AnimationLayerState::Playing;
                self.transition_snapshot = None; // 过渡完成，清除快照
            }
        } else {
            self.transition_progress = 1.0;
            self.state = AnimationLayerState::Playing;
            self.transition_snapshot = None;
        }
    }

    /// 评估动画并应用到骨骼管理器
    pub fn evaluate(&self, bone_manager: &mut BoneManager, morph_manager: &mut MorphManager) {
        if self.state == AnimationLayerState::Transitioning {
            // 过渡模式：混合缓存姿态和新动画
            self.evaluate_transition(bone_manager, morph_manager);
        } else if let Some(ref animation) = self.animation {
            if self.effective_weight > 0.001 {
                animation.evaluate_with_weight(
                    self.current_frame,
                    self.effective_weight,
                    bone_manager,
                    morph_manager,
                );
            }
        }
    }
    
    /// 评估过渡动画（混合缓存姿态和新动画）
    fn evaluate_transition(&self, bone_manager: &mut BoneManager, morph_manager: &mut MorphManager) {
        let t = self.transition_progress;
        
        // 平滑过渡曲线（smoothstep）
        let smooth_t = t * t * (3.0 - 2.0 * t);
        
        // 先应用新动画（权重 = 1.0，获取完整的新动画姿态）
        if let Some(ref animation) = self.animation {
            animation.evaluate_with_weight(
                self.current_frame,
                self.effective_weight,
                bone_manager,
                morph_manager,
            );
        }
        
        // 然后混合快照姿态（快照权重 = 1 - smooth_t）
        if let Some(ref snapshot) = self.transition_snapshot {
            let snapshot_weight = 1.0 - smooth_t;
            
            for (&bone_idx, pose) in &snapshot.bone_poses {
                if let Some(bone) = bone_manager.get_bone(bone_idx) {
                    // 混合：当前骨骼动画值（新动画）与快照值
                    let blended_translation = bone.animation_translate.lerp(pose.translation, snapshot_weight);
                    let blended_rotation = bone.animation_rotate.slerp(pose.rotation, snapshot_weight);
                    bone_manager.set_bone_translation(bone_idx, blended_translation);
                    bone_manager.set_bone_rotation(bone_idx, blended_rotation);
                }
            }
            
            for (&morph_idx, &snapshot_morph_weight) in &snapshot.morph_weights {
                let new_anim_weight = morph_manager.get_morph_weight(morph_idx);
                // 手动线性插值: new_anim_weight * (1 - snapshot_weight) + snapshot_morph_weight * snapshot_weight
                let blended = new_anim_weight + (snapshot_morph_weight - new_anim_weight) * snapshot_weight;
                morph_manager.set_morph_weight(morph_idx, blended);
            }
        }
    }

    /// 获取当前状态
    pub fn state(&self) -> AnimationLayerState {
        self.state.clone()
    }

    /// 获取当前帧
    pub fn current_frame(&self) -> f32 {
        self.current_frame
    }

    /// 获取有效权重
    pub fn effective_weight(&self) -> f32 {
        self.effective_weight
    }

    /// 获取配置权重
    pub fn weight(&self) -> f32 {
        self.config.weight
    }

    /// 是否启用
    pub fn is_enabled(&self) -> bool {
        self.enabled
    }

    /// 设置启用状态
    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }

    /// 获取动画最大帧数
    pub fn max_frame(&self) -> u32 {
        self.animation.as_ref().map(|a| a.max_frame()).unwrap_or(0)
    }

    /// 是否正在播放
    pub fn is_playing(&self) -> bool {
        matches!(self.state, AnimationLayerState::Playing | AnimationLayerState::FadingIn)
    }
}

/// 动画层管理器
pub struct AnimationLayerManager {
    /// 所有层
    layers: Vec<AnimationLayer>,
}

impl AnimationLayerManager {
    /// 创建层管理器
    pub fn new(max_layers: usize) -> Self {
        let mut layers = Vec::with_capacity(max_layers);
        for i in 0..max_layers {
            layers.push(AnimationLayer::new(i, format!("Layer_{}", i)));
        }
        
        Self { layers }
    }

    /// 获取层（可变）
    pub fn get_layer_mut(&mut self, layer_id: usize) -> Option<&mut AnimationLayer> {
        self.layers.get_mut(layer_id)
    }

    /// 获取层（只读）
    pub fn get_layer(&self, layer_id: usize) -> Option<&AnimationLayer> {
        self.layers.get(layer_id)
    }

    /// 设置层的动画
    pub fn set_layer_animation(&mut self, layer_id: usize, animation: Option<Arc<VmdAnimation>>) {
        if let Some(layer) = self.layers.get_mut(layer_id) {
            layer.set_animation(animation);
        }
    }

    /// 播放指定层
    pub fn play_layer(&mut self, layer_id: usize) {
        if let Some(layer) = self.layers.get_mut(layer_id) {
            layer.play();
        }
    }

    /// 停止指定层
    pub fn stop_layer(&mut self, layer_id: usize) {
        if let Some(layer) = self.layers.get_mut(layer_id) {
            layer.stop();
        }
    }

    /// 暂停指定层
    pub fn pause_layer(&mut self, layer_id: usize) {
        if let Some(layer) = self.layers.get_mut(layer_id) {
            layer.pause();
        }
    }

    /// 恢复指定层
    pub fn resume_layer(&mut self, layer_id: usize) {
        if let Some(layer) = self.layers.get_mut(layer_id) {
            layer.resume();
        }
    }

    /// 设置层权重
    pub fn set_layer_weight(&mut self, layer_id: usize, weight: f32) {
        if let Some(layer) = self.layers.get_mut(layer_id) {
            layer.set_weight(weight);
        }
    }

    /// 设置层播放速度
    pub fn set_layer_speed(&mut self, layer_id: usize, speed: f32) {
        if let Some(layer) = self.layers.get_mut(layer_id) {
            layer.set_speed(speed);
        }
    }

    /// 跳转到指定帧
    pub fn seek_layer(&mut self, layer_id: usize, frame: f32) {
        if let Some(layer) = self.layers.get_mut(layer_id) {
            layer.seek_to(frame);
        }
    }

    /// 设置层淡入淡出时间
    pub fn set_layer_fade_times(&mut self, layer_id: usize, fade_in: f32, fade_out: f32) {
        if let Some(layer) = self.layers.get_mut(layer_id) {
            layer.config.fade_in_time = fade_in.max(0.0);
            layer.config.fade_out_time = fade_out.max(0.0);
        }
    }
    
    /// 带过渡地切换层动画（姿态缓存过渡）
    /// 
    /// # 参数
    /// - `layer_id`: 层 ID
    /// - `animation`: 新动画
    /// - `transition_time`: 过渡时间（秒）
    /// - `bone_manager`: 当前骨骼管理器（用于捕获姿态）
    /// - `morph_manager`: 当前 Morph 管理器
    pub fn transition_layer_to(
        &mut self,
        layer_id: usize,
        animation: Option<Arc<VmdAnimation>>,
        transition_time: f32,
        bone_manager: &BoneManager,
        morph_manager: &MorphManager,
    ) {
        if let Some(layer) = self.layers.get_mut(layer_id) {
            layer.transition_to(animation, transition_time, bone_manager, morph_manager);
        }
    }

    /// 更新所有层
    pub fn update(&mut self, delta_time: f32) {
        for layer in &mut self.layers {
            layer.update(delta_time);
        }
    }

    /// 评估所有层（带权重归一化）
    pub fn evaluate_normalized(&self, bone_manager: &mut BoneManager, morph_manager: &mut MorphManager) {
        // 收集所有活跃层（包括过渡中的层）
        let active_layers: Vec<usize> = self.layers
            .iter()
            .enumerate()
            .filter(|(_, l)| {
                l.is_enabled() && l.animation.is_some() && 
                (l.effective_weight() > 0.001 || l.state() == AnimationLayerState::Transitioning)
            })
            .map(|(i, _)| i)
            .collect();

        if active_layers.is_empty() {
            return;
        }

        // 对每个活跃层进行评估（使用 layer.evaluate 以支持过渡）
        for layer_idx in active_layers {
            if let Some(layer) = self.layers.get(layer_idx) {
                layer.evaluate(bone_manager, morph_manager);
            }
        }
    }

    /// 获取层数量
    pub fn layer_count(&self) -> usize {
        self.layers.len()
    }

    /// 停止所有层
    pub fn stop_all(&mut self) {
        for layer in &mut self.layers {
            layer.stop();
        }
    }

    /// 重置所有层
    pub fn reset_all(&mut self) {
        for layer in &mut self.layers {
            layer.reset();
        }
    }

    /// 获取活跃层数量
    pub fn active_layer_count(&self) -> usize {
        self.layers
            .iter()
            .filter(|l| l.is_enabled() && l.is_playing())
            .count()
    }
}

impl Default for AnimationLayerManager {
    fn default() -> Self {
        Self::new(4)
    }
}
