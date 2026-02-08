//! MMD 关节（约束）封装
//!
//! 对应 C++ saba 的 MMDJoint 类，使用 Rapier3D 的 GenericJoint 实现。
//! 
//! Bullet3 使用 btGeneric6DofSpringConstraint 实现 MMD 的 6DOF 弹簧约束，
//! Rapier 使用 GenericJoint 配合 limits 和 motors 实现类似功能。

use glam::{Vec3, Quat, Mat4};
use rapier3d::prelude::*;

use mmd::pmx::joint::{Joint as PmxJoint, JointType};

use super::mmd_rigid_body::{mat4_to_isometry, isometry_to_mat4, inv_z, Pose};
use super::config::get_config;

/// MMD 关节
/// 
/// 封装 Rapier 的 GenericJoint，对应 C++ saba 的 MMDJoint。
/// 实现 btGeneric6DofSpringConstraint 的功能。
pub struct MMDJoint {
    /// 关节名称
    pub name: String,
    /// 关节类型
    pub joint_type: JointType,
    /// 刚体 A 索引
    pub rigid_body_a_index: i32,
    /// 刚体 B 索引
    pub rigid_body_b_index: i32,
    /// 关节句柄
    pub joint_handle: Option<ImpulseJointHandle>,
    /// 线性下限
    pub linear_lower: Vec3,
    /// 线性上限
    pub linear_upper: Vec3,
    /// 角度下限
    pub angular_lower: Vec3,
    /// 角度上限
    pub angular_upper: Vec3,
    /// 线性弹簧刚度
    pub linear_spring: Vec3,
    /// 角度弹簧刚度
    pub angular_spring: Vec3,
    /// 关节在刚体 A 局部空间的变换
    pub local_frame_a: Pose,
    /// 关节在刚体 B 局部空间的变换
    pub local_frame_b: Pose,
}

impl MMDJoint {
    /// 从 PMX 关节数据创建
    /// 
    /// # 参数
    /// - `pmx_joint`: PMX 关节数据
    /// - `rb_a_transform`: 刚体 A 的世界变换
    /// - `rb_b_transform`: 刚体 B 的世界变换
    pub fn from_pmx(
        pmx_joint: &PmxJoint,
        rb_a_transform: Pose,
        rb_b_transform: Pose,
    ) -> Self {
        // saba 的关节创建方式：
        // rotMat.setEulerZYX(x, y, z)  - 注意：Bullet 的 setEulerZYX 实际是按 Z*Y*X 顺序
        // transform.setOrigin(translate) - 直接使用原始坐标，不翻转
        // invA = rigidBodyA->getWorldTransform().inverse() * transform
        // invB = rigidBodyB->getWorldTransform().inverse() * transform
        
        // 注意：saba 的关节变换没有应用 InvZ！
        // 因为 Bullet 的关节是相对于刚体的局部空间计算的
        
        // saba/Bullet 的关节创建方式：
        // 1. rotMat.setEulerZYX(x, y, z) - Bullet 的欧拉角顺序
        // 2. transform = translate * rotate（使用原始 MMD 坐标，不应用 InvZ！）
        // 3. invA = rigidBodyA.getWorldTransform().inverse() * transform
        // 4. invB = rigidBodyB.getWorldTransform().inverse() * transform
        
        let position = Vec3::new(
            pmx_joint.position[0],
            pmx_joint.position[1],
            pmx_joint.position[2],
        );
        
        // Bullet 的 setEulerZYX(x, y, z) 按 Z*Y*X 顺序应用旋转
        let rx = Quat::from_rotation_x(pmx_joint.rotation[0]);
        let ry = Quat::from_rotation_y(pmx_joint.rotation[1]);
        let rz = Quat::from_rotation_z(pmx_joint.rotation[2]);
        let rotation = rz * ry * rx;  // Z-Y-X 顺序
        
        // 关节的世界变换（原始 MMD 坐标系，不应用 InvZ！）
        // 这与 saba 的实现一致
        let joint_transform = Mat4::from_rotation_translation(rotation, position);
        
        // 计算关节相对于刚体的局部变换
        // saba: invA = rigidBodyA->getWorldTransform().inverse() * transform
        // 注意：rb_a_transform 和 rb_b_transform 已经是 Bullet/Rapier 坐标系（应用了 InvZ）
        // 但 joint_transform 是原始 MMD 坐标系
        // 我们需要将 joint_transform 也转换到 Bullet/Rapier 坐标系
        let rb_a_mat = isometry_to_mat4(rb_a_transform);
        let rb_b_mat = isometry_to_mat4(rb_b_transform);
        
        // 将关节变换转换到 Rapier 坐标系（与刚体坐标系匹配）
        let joint_transform_rapier = inv_z(joint_transform);
        
        let local_frame_a_mat = rb_a_mat.inverse() * joint_transform_rapier;
        let local_frame_b_mat = rb_b_mat.inverse() * joint_transform_rapier;
        
        let local_frame_a = mat4_to_isometry(local_frame_a_mat);
        let local_frame_b = mat4_to_isometry(local_frame_b_mat);
        
        // 线性限制（直接使用，saba 也没有翻转）
        let linear_lower = Vec3::new(
            pmx_joint.position_min[0],
            pmx_joint.position_min[1],
            pmx_joint.position_min[2],
        );
        let linear_upper = Vec3::new(
            pmx_joint.position_max[0],
            pmx_joint.position_max[1],
            pmx_joint.position_max[2],
        );
        
        // 角度限制（直接使用，saba 也没有翻转）
        let angular_lower = Vec3::new(
            pmx_joint.rotation_min[0],
            pmx_joint.rotation_min[1],
            pmx_joint.rotation_min[2],
        );
        let angular_upper = Vec3::new(
            pmx_joint.rotation_max[0],
            pmx_joint.rotation_max[1],
            pmx_joint.rotation_max[2],
        );
        
        // 弹簧刚度
        let linear_spring = Vec3::new(
            pmx_joint.position_spring[0],
            pmx_joint.position_spring[1],
            pmx_joint.position_spring[2],
        );
        let angular_spring = Vec3::new(
            pmx_joint.rotation_spring[0],
            pmx_joint.rotation_spring[1],
            pmx_joint.rotation_spring[2],
        );
        
        Self {
            name: pmx_joint.local_name.clone(),
            joint_type: pmx_joint.type_,
            rigid_body_a_index: pmx_joint.rigid_body_a_index,
            rigid_body_b_index: pmx_joint.rigid_body_b_index,
            joint_handle: None,
            linear_lower,
            linear_upper,
            angular_lower,
            angular_upper,
            linear_spring,
            angular_spring,
            local_frame_a,
            local_frame_b,
        }
    }
    
    /// 创建 Rapier GenericJoint
    /// 
    /// 使用 GenericJoint 实现 6DOF 弹簧约束，对应 Bullet3 的 btGeneric6DofSpringConstraint
    /// 
    /// 关键修复：根据 limits 范围确定哪些轴需要锁定
    /// - 如果 lower == upper == 0，则锁定该轴
    /// - 如果 lower < upper，则该轴自由但有限制
    /// - 如果 lower > upper，则该轴完全自由（无限制）
    pub fn build_joint(&self, is_bust: bool) -> GenericJoint {
        // Bullet3 的 btGeneric6DofSpringConstraint 行为：
        // - 不锁定任何轴，而是设置限制范围
        // - 当 lower <= upper 时，设置限制
        // - 当 lower > upper 时，该轴完全自由（无限制）
        // - 即使 lower == upper == 0，也不是完全锁死，而是有一个紧的限制
        //
        // 在 Rapier 中，我们只锁定线性轴（当 lower == upper 时），
        // 但**不锁定角度轴**，让弹簧和限制来控制旋转
        
        let mut locked_axes = JointAxesMask::empty();
        
        const EPSILON: f32 = 0.0001;
        
        // 只锁定线性轴（当 lower == upper 时，表示不允许线性移动）
        if (self.linear_upper.x - self.linear_lower.x).abs() < EPSILON {
            locked_axes |= JointAxesMask::LIN_X;
        }
        if (self.linear_upper.y - self.linear_lower.y).abs() < EPSILON {
            locked_axes |= JointAxesMask::LIN_Y;
        }
        if (self.linear_upper.z - self.linear_lower.z).abs() < EPSILON {
            locked_axes |= JointAxesMask::LIN_Z;
        }
        
        // 注意：不锁定角度轴！让 set_limits 和弹簧来控制
        // 这是与 Bullet3 行为一致的关键
        
        // 创建关节
        let mut joint = GenericJointBuilder::new(locked_axes)
            .local_frame1(self.local_frame_a)
            .local_frame2(self.local_frame_b)
            .build();
        
        // 禁用关节连接的刚体之间的碰撞
        joint.contacts_enabled = false;
        
        // 设置线性限制（只对未锁定的轴）
        if !locked_axes.contains(JointAxesMask::LIN_X) && self.linear_lower.x <= self.linear_upper.x {
            joint.set_limits(JointAxis::LinX, [self.linear_lower.x, self.linear_upper.x]);
        }
        if !locked_axes.contains(JointAxesMask::LIN_Y) && self.linear_lower.y <= self.linear_upper.y {
            joint.set_limits(JointAxis::LinY, [self.linear_lower.y, self.linear_upper.y]);
        }
        if !locked_axes.contains(JointAxesMask::LIN_Z) && self.linear_lower.z <= self.linear_upper.z {
            joint.set_limits(JointAxis::LinZ, [self.linear_lower.z, self.linear_upper.z]);
        }
        
        // 设置角度限制
        // 关键修复：
        // - 当 lower < upper 时，设置限制范围
        // - 当 lower > upper 时，该轴完全自由（无限制）
        // - 当 lower == upper 时，设置一个小范围限制（±0.1弧度）
        //   因为 [0,0] 会完全锁死，但完全不设置又没有约束
        const MIN_RANGE: f32 = 0.1; // 约 5.7 度
        
        let ang_range_x = (self.angular_upper.x - self.angular_lower.x).abs();
        let ang_range_y = (self.angular_upper.y - self.angular_lower.y).abs();
        let ang_range_z = (self.angular_upper.z - self.angular_lower.z).abs();
        
        // 设置角度限制，当范围太小时使用最小范围
        if self.angular_lower.x <= self.angular_upper.x {
            if ang_range_x < EPSILON {
                // lower == upper，使用小范围限制
                let center = self.angular_lower.x;
                joint.set_limits(JointAxis::AngX, [center - MIN_RANGE, center + MIN_RANGE]);
            } else {
                joint.set_limits(JointAxis::AngX, [self.angular_lower.x, self.angular_upper.x]);
            }
        }
        if self.angular_lower.y <= self.angular_upper.y {
            if ang_range_y < EPSILON {
                let center = self.angular_lower.y;
                joint.set_limits(JointAxis::AngY, [center - MIN_RANGE, center + MIN_RANGE]);
            } else {
                joint.set_limits(JointAxis::AngY, [self.angular_lower.y, self.angular_upper.y]);
            }
        }
        if self.angular_lower.z <= self.angular_upper.z {
            if ang_range_z < EPSILON {
                let center = self.angular_lower.z;
                joint.set_limits(JointAxis::AngZ, [center - MIN_RANGE, center + MIN_RANGE]);
            } else {
                joint.set_limits(JointAxis::AngZ, [self.angular_lower.z, self.angular_upper.z]);
            }
        }
        
        // 设置弹簧电机（用于模拟弹簧效果）
        // saba/Bullet 使用 enableSpring + setStiffness
        // Rapier 使用电机来模拟弹簧：target_pos=0, stiffness=刚度, damping=阻尼
        let config = get_config();
        
        // 根据是否为胸部关节选用不同的弹簧参数组
        let (lin_stiff_scale, ang_stiff_scale, lin_damp_factor, ang_damp_factor) = if is_bust {
            (
                config.bust_linear_spring_stiffness_scale,
                config.bust_angular_spring_stiffness_scale,
                config.bust_linear_spring_damping_factor,
                config.bust_angular_spring_damping_factor,
            )
        } else {
            (
                config.linear_spring_stiffness_scale,
                config.angular_spring_stiffness_scale,
                config.linear_spring_damping_factor,
                config.angular_spring_damping_factor,
            )
        };
        
        // 线性弹簧
        if self.linear_spring.x != 0.0 && !locked_axes.contains(JointAxesMask::LIN_X) {
            let stiffness = self.linear_spring.x * lin_stiff_scale;
            let damping = (stiffness * lin_damp_factor).sqrt();
            joint.set_motor(JointAxis::LinX, 0.0, 0.0, stiffness, damping);
        }
        if self.linear_spring.y != 0.0 && !locked_axes.contains(JointAxesMask::LIN_Y) {
            let stiffness = self.linear_spring.y * lin_stiff_scale;
            let damping = (stiffness * lin_damp_factor).sqrt();
            joint.set_motor(JointAxis::LinY, 0.0, 0.0, stiffness, damping);
        }
        if self.linear_spring.z != 0.0 && !locked_axes.contains(JointAxesMask::LIN_Z) {
            let stiffness = self.linear_spring.z * lin_stiff_scale;
            let damping = (stiffness * lin_damp_factor).sqrt();
            joint.set_motor(JointAxis::LinZ, 0.0, 0.0, stiffness, damping);
        }
        // 角度弹簧（角度轴不锁定，总是可以设置）
        if self.angular_spring.x != 0.0 {
            let stiffness = self.angular_spring.x * ang_stiff_scale;
            let damping = (stiffness * ang_damp_factor).sqrt();
            joint.set_motor(JointAxis::AngX, 0.0, 0.0, stiffness, damping);
        }
        if self.angular_spring.y != 0.0 {
            let stiffness = self.angular_spring.y * ang_stiff_scale;
            let damping = (stiffness * ang_damp_factor).sqrt();
            joint.set_motor(JointAxis::AngY, 0.0, 0.0, stiffness, damping);
        }
        if self.angular_spring.z != 0.0 {
            let stiffness = self.angular_spring.z * ang_stiff_scale;
            let damping = (stiffness * ang_damp_factor).sqrt();
            joint.set_motor(JointAxis::AngZ, 0.0, 0.0, stiffness, damping);
        }
        
        joint
    }
}
