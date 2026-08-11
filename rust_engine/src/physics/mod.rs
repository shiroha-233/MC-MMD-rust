//! MMD 物理系统模块

use glam::{Mat4, Vec3};

pub mod bullet_ffi;
pub mod collision_topology;
pub mod config;
mod initialization_diagnostics;
mod joint_parameters;
mod kinematic_target_filter;
mod mmd_joint;
mod mmd_physics;
mod mmd_rigid_body;
mod physics_diagnostics;

/// Z 轴翻转变换（左手 ↔ 右手坐标系转换，与 saba InvZ 一致）
///
/// 骨骼系统在右手坐标（Z 翻转），Bullet3 物理在左手坐标（MMD 原生）。
/// InvZ(M) = Z * M * Z，其中 Z = diag(1, 1, -1, 1)。
#[inline]
pub(crate) fn inv_z(m: Mat4) -> Mat4 {
    let z = Mat4::from_scale(Vec3::new(1.0, 1.0, -1.0));
    z * m * z
}

#[cfg(test)]
mod coordinate_tests {
    use super::inv_z;
    use glam::{Mat4, Quat, Vec3};

    #[test]
    fn inv_z_preserves_joint_anchor_composition() {
        let body = Mat4::from_rotation_translation(
            Quat::from_euler(glam::EulerRot::XYZ, 0.31, -0.47, 0.83),
            Vec3::new(1.2, -0.7, 2.4),
        );
        let joint = Mat4::from_rotation_translation(
            Quat::from_rotation_z(-0.39)
                * Quat::from_rotation_y(0.22)
                * Quat::from_rotation_x(0.61),
            Vec3::new(-0.4, 1.1, -2.0),
        );
        let frame = body.inverse() * joint;

        // 镜像必须同时作用于刚体和关节；局部 frame 才能保持世界锚点等价。
        let mirrored_body = inv_z(body);
        let mirrored_joint = inv_z(joint);
        let mirrored_frame = mirrored_body.inverse() * mirrored_joint;
        assert!(mirrored_frame.abs_diff_eq(inv_z(frame), 1e-5));
        assert!((mirrored_body * mirrored_frame).abs_diff_eq(mirrored_joint, 1e-5));
    }

    #[test]
    fn inv_z_is_an_involution_for_non_symmetric_joint_pose() {
        let pose = Mat4::from_rotation_translation(
            Quat::from_euler(glam::EulerRot::XYZ, 0.17, -0.52, 0.91),
            Vec3::new(0.3, -1.4, 2.7),
        );
        assert!(inv_z(inv_z(pose)).abs_diff_eq(pose, 1e-5));
    }
}

pub use bullet_ffi::{get_alloc_stats, BulletAllocStats};
pub use collision_topology::CollisionStabilityMode;
pub use config::{get_config, reset_config, set_config, PhysicsConfig};
pub use mmd_joint::MmdJointData;
pub use mmd_physics::{MMDPhysics, PhysicsJointSnapshot};
pub use mmd_rigid_body::{
    body_collider_scale_flags, effective_collision_shape_size,
    effective_collision_shape_size_with_static_scale, MmdRigidBodyData, PhysicsMode,
    STATIC_COLLISION_SHAPE_SCALE,
};
