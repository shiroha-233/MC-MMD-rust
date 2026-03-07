//! 骨骼系统 - 参考 nphysics Multibody 设计

mod bone_link;
mod bone_set;
mod ik_solver;

pub use bone_link::{BoneLink, BoneFlags, IkConfig, IkLink, AppendConfig};
pub use bone_set::BoneSet;
pub use ik_solver::IkSolver;

use glam::{Vec3, Quat, Mat4};


/// 骨骼变换数据
#[derive(Clone, Copy, Debug)]
pub struct BoneTransform {
    pub translation: Vec3,
    pub rotation: Quat,
    pub scale: Vec3,
}

impl Default for BoneTransform {
    fn default() -> Self {
        Self {
            translation: Vec3::ZERO,
            rotation: Quat::IDENTITY,
            scale: Vec3::ONE,
        }
    }
}

impl BoneTransform {
    /// 转换为 4x4 矩阵
    #[inline]
    pub fn to_matrix(&self) -> Mat4 {
        Mat4::from_scale_rotation_translation(self.scale, self.rotation, self.translation)
    }
    
    /// 从矩阵分解
    #[inline]
    pub fn from_matrix(m: Mat4) -> Self {
        let (scale, rotation, translation) = m.to_scale_rotation_translation();
        Self { translation, rotation, scale }
    }
}

// ============================================================================
// 类型别名
// ============================================================================

/// Bone 别名
pub type Bone = BoneLink;

/// BoneManager 别名
pub type BoneManager = BoneSet;
