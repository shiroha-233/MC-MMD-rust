//! MMD 关节（约束）封装
//!
//! 移植自 babylon-mmd 的关节构建逻辑。
//! 使用 Bullet3 btGeneric6DofSpringConstraint。

use glam::{Vec3, Quat, Mat4};

use mmd::pmx::joint::Joint as PmxJoint;

use super::bullet_ffi::{BulletConstraint, BulletRigidBody, BT_CONSTRAINT_STOP_ERP};

/// MMD 关节数据
pub struct MmdJointData {
    /// 关节名称
    pub name: String,
    /// 刚体 A 索引
    pub rigid_body_a_index: i32,
    /// 刚体 B 索引
    pub rigid_body_b_index: i32,
    /// Bullet3 约束
    pub constraint: Option<BulletConstraint>,
}

impl MmdJointData {
    /// 从 PMX 关节数据创建 Bullet3 6DOF 弹簧约束
    ///
    /// 移植自 babylon-mmd buildPhysics() 关节部分。
    /// 欧拉角使用 XYZ intrinsic（等价 saba btMatrix3x3::setEulerZYX）。
    pub fn from_pmx(
        pmx_joint: &PmxJoint,
        rb_a: &BulletRigidBody,
        rb_b: &BulletRigidBody,
        rb_a_initial_transform: Mat4,
        rb_b_initial_transform: Mat4,
    ) -> Self {
        let position = Vec3::new(
            pmx_joint.position[0],
            pmx_joint.position[1],
            pmx_joint.position[2],
        );

        // XYZ intrinsic 欧拉角（与 saba btMatrix3x3::setEulerZYX 一致）
        let rotation = Quat::from_euler(
            glam::EulerRot::XYZ,
            pmx_joint.rotation[0], // X
            pmx_joint.rotation[1], // Y
            pmx_joint.rotation[2], // Z
        );

        let joint_transform = Mat4::from_rotation_translation(rotation, position);

        // frameA = rbA_initialTransform.inverse() * jointTransform
        // frameB = rbB_initialTransform.inverse() * jointTransform
        let frame_a = rb_a_initial_transform.inverse() * joint_transform;
        let frame_b = rb_b_initial_transform.inverse() * joint_transform;

        // 创建 Bullet3 6DOF 弹簧约束
        let constraint = BulletConstraint::new_6dof_spring(
            rb_a, rb_b, frame_a, frame_b, true,
        );

        // 配置约束参数（仅在创建成功时）
        if let Some(ref c) = constraint {
            for axis in 0..6 {
                c.set_param(BT_CONSTRAINT_STOP_ERP, 0.475, axis);
            }

            c.set_linear_lower_limit(
                pmx_joint.position_min[0],
                pmx_joint.position_min[1],
                pmx_joint.position_min[2],
            );
            c.set_linear_upper_limit(
                pmx_joint.position_max[0],
                pmx_joint.position_max[1],
                pmx_joint.position_max[2],
            );
            c.set_angular_lower_limit(
                pmx_joint.rotation_min[0],
                pmx_joint.rotation_min[1],
                pmx_joint.rotation_min[2],
            );
            c.set_angular_upper_limit(
                pmx_joint.rotation_max[0],
                pmx_joint.rotation_max[1],
                pmx_joint.rotation_max[2],
            );

            for i in 0..3 {
                let stiffness = pmx_joint.position_spring[i];
                if stiffness.abs() > 1e-6 {
                    c.set_stiffness(i as i32, stiffness);
                    c.enable_spring(i as i32, true);
                } else {
                    c.enable_spring(i as i32, false);
                }
            }

            for i in 0..3 {
                let stiffness = pmx_joint.rotation_spring[i];
                c.set_stiffness(i as i32 + 3, stiffness);
                c.enable_spring(i as i32 + 3, true);
            }
        }

        Self {
            name: pmx_joint.local_name.clone(),
            rigid_body_a_index: pmx_joint.rigid_body_a_index,
            rigid_body_b_index: pmx_joint.rigid_body_b_index,
            constraint,
        }
    }
}
