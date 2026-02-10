//! 关键帧定义 - 复刻 mdanceio 实现
//!
//! 定义骨骼关键帧和 Morph 关键帧的数据结构

use glam::{Vec3, Quat};

/// 骨骼关键帧
#[derive(Debug, Clone)]
pub struct BoneKeyframe {
    /// 帧索引
    pub frame_index: u32,
    /// 平移
    pub translation: Vec3,
    /// 旋转（四元数）
    pub orientation: Quat,
    /// X 平移插值参数 [c0.x, c0.y, c1.x, c1.y]
    pub interpolation_x: [u8; 4],
    /// Y 平移插值参数
    pub interpolation_y: [u8; 4],
    /// Z 平移插值参数
    pub interpolation_z: [u8; 4],
    /// 旋转插值参数
    pub interpolation_r: [u8; 4],
    /// 是否启用物理模拟
    pub is_physics_simulation_enabled: bool,
}

impl Default for BoneKeyframe {
    fn default() -> Self {
        Self {
            frame_index: 0,
            translation: Vec3::ZERO,
            orientation: Quat::IDENTITY,
            interpolation_x: [20, 20, 107, 107],
            interpolation_y: [20, 20, 107, 107],
            interpolation_z: [20, 20, 107, 107],
            interpolation_r: [20, 20, 107, 107],
            is_physics_simulation_enabled: true,
        }
    }
}

impl BoneKeyframe {
    /// 默认的贝塞尔控制点（线性）
    pub const DEFAULT_BEZIER_CONTROL_POINT: [u8; 4] = [20, 20, 107, 107];

    /// 创建新的骨骼关键帧
    pub fn new(frame_index: u32) -> Self {
        Self {
            frame_index,
            ..Default::default()
        }
    }

    /// 使用指定的平移和旋转创建关键帧
    pub fn with_transform(frame_index: u32, translation: Vec3, orientation: Quat) -> Self {
        Self {
            frame_index,
            translation,
            orientation,
            ..Default::default()
        }
    }
}

/// Morph 关键帧
#[derive(Debug, Clone)]
pub struct MorphKeyframe {
    /// 帧索引
    pub frame_index: u32,
    /// Morph 权重 [0, 1]
    pub weight: f32,
}

impl Default for MorphKeyframe {
    fn default() -> Self {
        Self {
            frame_index: 0,
            weight: 0.0,
        }
    }
}

impl MorphKeyframe {
    /// 创建新的 Morph 关键帧
    pub fn new(frame_index: u32, weight: f32) -> Self {
        Self { frame_index, weight }
    }
}

/// 相机关键帧（可选，用于未来扩展）
#[derive(Debug, Clone)]
pub struct CameraKeyframe {
    /// 帧索引
    pub frame_index: u32,
    /// 目标点
    pub look_at: Vec3,
    /// 角度
    pub angle: Vec3,
    /// 距离
    pub distance: f32,
    /// 视场角
    pub fov: f32,
    /// 是否透视
    pub is_perspective: bool,
    /// 插值参数
    pub interpolation: CameraInterpolation,
}

/// 相机插值参数
#[derive(Debug, Clone)]
pub struct CameraInterpolation {
    pub lookat_x: [u8; 4],
    pub lookat_y: [u8; 4],
    pub lookat_z: [u8; 4],
    pub angle: [u8; 4],
    pub fov: [u8; 4],
    pub distance: [u8; 4],
}

impl Default for CameraInterpolation {
    fn default() -> Self {
        Self {
            lookat_x: [20, 20, 107, 107],
            lookat_y: [20, 20, 107, 107],
            lookat_z: [20, 20, 107, 107],
            angle: [20, 20, 107, 107],
            fov: [20, 20, 107, 107],
            distance: [20, 20, 107, 107],
        }
    }
}

impl Default for CameraKeyframe {
    fn default() -> Self {
        Self {
            frame_index: 0,
            look_at: Vec3::ZERO,
            angle: Vec3::ZERO,
            distance: -45.0,
            fov: 30.0,
            is_perspective: true,
            interpolation: CameraInterpolation::default(),
        }
    }
}

/// IK 关键帧
#[derive(Debug, Clone)]
pub struct IkKeyframe {
    /// 帧索引
    pub frame_index: u32,
    /// IK 名称
    pub ik_name: String,
    /// 是否启用
    pub enabled: bool,
}

impl IkKeyframe {
    /// 创建新的 IK 关键帧
    pub fn new(frame_index: u32, ik_name: String, enabled: bool) -> Self {
        Self { frame_index, ik_name, enabled }
    }
}
