//! MMD 物理系统模块

use glam::{Mat4, Vec3};

pub mod bullet_ffi;
pub mod config;
mod mmd_joint;
mod mmd_physics;
mod mmd_rigid_body;

/// Z 轴翻转变换（左手 ↔ 右手坐标系转换，与 saba InvZ 一致）
///
/// 骨骼系统在右手坐标（Z 翻转），Bullet3 物理在左手坐标（MMD 原生）。
/// InvZ(M) = Z * M * Z，其中 Z = diag(1, 1, -1, 1)。
#[inline]
pub(crate) fn inv_z(m: Mat4) -> Mat4 {
    let z = Mat4::from_scale(Vec3::new(1.0, 1.0, -1.0));
    z * m * z
}

pub use bullet_ffi::{get_alloc_stats, BulletAllocStats};
pub use config::{get_config, reset_config, set_config, PhysicsConfig};
pub use mmd_joint::MmdJointData;
pub use mmd_physics::MMDPhysics;
pub use mmd_rigid_body::{MmdRigidBodyData, PhysicsMode};
