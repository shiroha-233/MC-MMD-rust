//! MMD 物理配置
//!
//! Bullet3 原生引擎简化配置。Bullet3 内置处理阻尼、约束求解等，
//! 这里只保留可调节的高级参数。

use once_cell::sync::Lazy;
use std::sync::RwLock;

/// 物理配置
#[derive(Debug, Clone)]
pub struct PhysicsConfig {
    /// 重力 Y 分量（负数向下），默认 -98.0（MMD 标准）
    pub gravity_y: f32,
    /// 物理 FPS（Bullet3 固定时间步），默认 60.0
    pub physics_fps: f32,
    /// 每帧最大子步数，默认 5
    pub max_substep_count: i32,
    /// 惯性效果强度（0.0=无惯性, 1.0=正常）
    pub inertia_strength: f32,
    /// 是否启用关节
    pub joints_enabled: bool,
    /// 调试日志
    pub debug_log: bool,
}

impl Default for PhysicsConfig {
    fn default() -> Self {
        Self {
            gravity_y: -98.0,
            physics_fps: 60.0,
            max_substep_count: 5,
            inertia_strength: 1.0,
            joints_enabled: true,
            debug_log: false,
        }
    }
}

static PHYSICS_CONFIG: Lazy<RwLock<PhysicsConfig>> = Lazy::new(|| {
    RwLock::new(PhysicsConfig::default())
});

pub fn get_config() -> PhysicsConfig {
    PHYSICS_CONFIG.read().unwrap_or_else(|e| e.into_inner()).clone()
}

pub fn set_config(config: PhysicsConfig) {
    *PHYSICS_CONFIG.write().unwrap_or_else(|e| e.into_inner()) = config;
}

pub fn reset_config() {
    *PHYSICS_CONFIG.write().unwrap_or_else(|e| e.into_inner()) = PhysicsConfig::default();
}
