//! MMD 物理系统模块
//!
//! 使用 Bullet3 物理引擎（通过 C FFI）实现 MMD 物理模拟。

use glam::{Mat4, Vec3};

pub mod bullet_ffi;
mod mmd_physics;
mod mmd_rigid_body;
mod mmd_joint;
pub mod config;

/// Z 轴翻转变换（左手 ↔ 右手坐标系转换，与 saba InvZ 一致）
///
/// 骨骼系统在右手坐标（Z 翻转），Bullet3 物理在左手坐标（MMD 原生）。
/// InvZ(M) = Z * M * Z，其中 Z = diag(1, 1, -1, 1)。
#[inline]
pub(crate) fn inv_z(m: Mat4) -> Mat4 {
    let z = Mat4::from_scale(Vec3::new(1.0, 1.0, -1.0));
    z * m * z
}

pub use mmd_physics::MMDPhysics;
pub use mmd_rigid_body::{MmdRigidBodyData, PhysicsMode};
pub use mmd_joint::MmdJointData;
pub use config::{PhysicsConfig, get_config, set_config, reset_config};
pub use bullet_ffi::{BulletAllocStats, get_alloc_stats};
