//! MMD 物理配置

use once_cell::sync::Lazy;
use std::sync::RwLock;

use super::collision_topology::CollisionStabilityMode;

/// 物理配置
#[derive(Debug, Clone)]
pub struct PhysicsConfig {
    /// 是否启用物理模拟
    pub enabled: bool,
    /// 重力 Y 分量（负数向下），默认 -98.0（MMD 标准）
    pub gravity_y: f32,
    /// 物理 FPS（Bullet3 固定时间步），默认 60.0
    pub physics_fps: f32,
    /// 每帧最大子步数，默认 5
    pub max_substep_count: i32,
    /// 惯性效果强度（0.0=无惯性, 1.0=正常）
    pub inertia_strength: f32,
    /// 最大线速度（防止物理爆炸），默认 20.0
    pub max_linear_velocity: f32,
    /// 最大角速度（防止物理爆炸），默认 20.0
    pub max_angular_velocity: f32,
    /// 是否启用关节
    pub joints_enabled: bool,
    /// 是否启用刚体接触碰撞；关闭时仍保留重力和关节模拟
    pub collision_enabled: bool,
    /// 关节拓扑内部的成对碰撞稳定模式
    pub collision_stability_mode: CollisionStabilityMode,
    /// 是否禁用所有运动学刚体与动态刚体之间的碰撞（实验性）
    pub kinematic_filter: bool,
    /// 调试日志
    pub debug_log: bool,
}

impl Default for PhysicsConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            gravity_y: -98.0,
            physics_fps: 60.0,
            max_substep_count: 5,
            inertia_strength: 0.5,
            max_linear_velocity: 20.0,
            max_angular_velocity: 20.0,
            joints_enabled: true,
            collision_enabled: true,
            collision_stability_mode: CollisionStabilityMode::Stable,
            kinematic_filter: false,
            debug_log: false,
        }
    }
}

static PHYSICS_CONFIG: Lazy<RwLock<PhysicsConfig>> =
    Lazy::new(|| RwLock::new(PhysicsConfig::default()));

pub fn get_config() -> PhysicsConfig {
    PHYSICS_CONFIG
        .read()
        .unwrap_or_else(|e| e.into_inner())
        .clone()
}

pub fn set_config(config: PhysicsConfig) {
    *PHYSICS_CONFIG.write().unwrap_or_else(|e| e.into_inner()) = config;
}

pub fn reset_config() {
    *PHYSICS_CONFIG.write().unwrap_or_else(|e| e.into_inner()) = PhysicsConfig::default();
}

#[cfg(test)]
mod tests {
    use super::PhysicsConfig;

    #[test]
    fn normal_collision_mode_is_enabled_by_default() {
        let config = PhysicsConfig::default();
        assert!(config.collision_enabled);
        assert_eq!(
            config.collision_stability_mode,
            super::CollisionStabilityMode::Stable
        );
        assert!(!config.kinematic_filter);
    }
}
