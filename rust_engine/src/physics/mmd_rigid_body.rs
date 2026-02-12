//! MMD 刚体数据
//!
//! 移植自 babylon-mmd 的 MmdRigidBodyData。
//! 使用 Bullet3 引擎，通过 inv_z 在骨骼（右手）与物理（左手）坐标系之间转换。

use glam::{Mat4, Vec3, Quat};

use mmd::pmx::rigid_body::{RigidBody as PmxRigidBody, RigidBodyShape, RigidBodyMode};

use super::bullet_ffi::{BulletShape, BulletRigidBody, RigidBodyInfo};

/// 物理模式（对应 babylon-mmd 的 PhysicsMode）
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PhysicsMode {
    /// 跟随骨骼（Kinematic）
    FollowBone,
    /// 完全物理驱动
    Physics,
    /// 物理驱动但位置跟随骨骼（仅旋转由物理控制）
    PhysicsWithBone,
}

impl From<RigidBodyMode> for PhysicsMode {
    fn from(mode: RigidBodyMode) -> Self {
        match mode {
            RigidBodyMode::Static => PhysicsMode::FollowBone,
            RigidBodyMode::Dynamic => PhysicsMode::Physics,
            RigidBodyMode::DynamicWithBonePosition => PhysicsMode::PhysicsWithBone,
        }
    }
}

/// MMD 刚体数据（移植自 babylon-mmd MmdRigidBodyData）
pub struct MmdRigidBodyData {
    /// 刚体名称
    pub name: String,
    /// 关联骨骼索引
    pub bone_index: i32,
    /// 物理模式
    pub physics_mode: PhysicsMode,
    /// 碰撞组
    pub group: u8,
    /// 碰撞掩码
    pub group_mask: u16,
    /// 偏移矩阵 = B0⁻¹ * R0（刚体在骨骼局部空间的变换，saba 右乘约定）
    pub body_offset_matrix: Mat4,
    /// 偏移矩阵的逆 = R0⁻¹ * B0
    pub body_offset_matrix_inverse: Mat4,
    /// 刚体初始世界变换（MMD 坐标空间）
    pub initial_transform: Mat4,
    /// Bullet3 刚体
    pub bullet_body: Option<BulletRigidBody>,
    /// Bullet3 碰撞形状（必须比刚体存活更久）
    pub bullet_shape: Option<BulletShape>,
    /// 质量
    pub mass: f32,
}

impl MmdRigidBodyData {
    /// 从 PMX 刚体数据创建
    ///
    /// 骨骼(右手)通过 inv_z 转为左手后计算 offset = B0_left⁻¹ * R0_left。
    pub fn from_pmx(
        pmx_rb: &PmxRigidBody,
        bone_global_transform: Option<Mat4>,
    ) -> Self {
        let physics_mode = PhysicsMode::from(pmx_rb.mode);

        // 欧拉角 → 四元数（Y-X-Z 顺序，与 babylon-mmd FromEulerAngles 一致）
        let rotation = Quat::from_euler(
            glam::EulerRot::YXZ,
            pmx_rb.rotation[1], // Y
            pmx_rb.rotation[0], // X
            pmx_rb.rotation[2], // Z
        );

        let position = Vec3::new(
            pmx_rb.position[0],
            pmx_rb.position[1],
            pmx_rb.position[2],
        );

        // 刚体世界变换（直接使用 MMD 坐标，无 inv_z）
        let rb_world_matrix = Mat4::from_rotation_translation(rotation, position);

        // saba 约定: offset = B0_left⁻¹ * R0_left
        // 骨骼在右手坐标（Z 翻转），刚体在左手坐标（MMD 原生），需 InvZ 对齐
        let body_offset_matrix = if let Some(bone_transform) = bone_global_transform {
            let bone_left = super::inv_z(bone_transform);
            bone_left.inverse() * rb_world_matrix
        } else {
            rb_world_matrix
        };
        let body_offset_matrix_inverse = body_offset_matrix.inverse();

        Self {
            name: pmx_rb.local_name.clone(),
            bone_index: pmx_rb.bone_index,
            physics_mode,
            group: pmx_rb.group,
            group_mask: pmx_rb.un_collision_group_flag,
            body_offset_matrix,
            body_offset_matrix_inverse,
            initial_transform: rb_world_matrix,
            bullet_body: None,
            bullet_shape: None,
            mass: pmx_rb.mass,
        }
    }

    /// 创建 Bullet3 碰撞形状（C++ OOM 时返回 None）
    pub fn create_shape(pmx_rb: &PmxRigidBody) -> Option<BulletShape> {
        match pmx_rb.shape {
            RigidBodyShape::Sphere => BulletShape::sphere(pmx_rb.size[0]),
            RigidBodyShape::Box => BulletShape::r#box(
                pmx_rb.size[0],
                pmx_rb.size[1],
                pmx_rb.size[2],
            ),
            RigidBodyShape::Capsule => {
                BulletShape::capsule(pmx_rb.size[0], pmx_rb.size[1])
            }
        }
    }

    /// 创建 Bullet3 刚体（C++ OOM 时返回 None）
    pub fn create_rigid_body(
        &self,
        pmx_rb: &PmxRigidBody,
        shape: &BulletShape,
    ) -> Option<BulletRigidBody> {
        let is_kinematic = self.physics_mode == PhysicsMode::FollowBone;

        // 零体积检测
        let is_zero_volume = match pmx_rb.shape {
            RigidBodyShape::Sphere => pmx_rb.size[0] <= 0.0,
            RigidBodyShape::Box => pmx_rb.size[0] <= 0.0 || pmx_rb.size[1] <= 0.0 || pmx_rb.size[2] <= 0.0,
            RigidBodyShape::Capsule => pmx_rb.size[0] <= 0.0 || pmx_rb.size[1] <= 0.0,
        };

        let info = RigidBodyInfo {
            mass: pmx_rb.mass,
            linear_damping: pmx_rb.move_attenuation,
            angular_damping: pmx_rb.rotation_attenuation,
            friction: pmx_rb.friction,
            restitution: pmx_rb.repulsion,
            additional_damping: true,
            is_kinematic,
            disable_deactivation: true,
            no_contact_response: is_zero_volume,
            initial_transform: self.initial_transform,
        };

        BulletRigidBody::new(&info, shape)
    }

    /// 根据骨骼变换计算刚体世界变换: R = B * offset = B * B0⁻¹ * R0
    pub fn compute_body_matrix(&self, bone_world_matrix: Mat4) -> Mat4 {
        bone_world_matrix * self.body_offset_matrix
    }

    /// 从刚体变换反推骨骼变换: B = R * offset⁻¹ = R * R0⁻¹ * B0
    pub fn compute_bone_matrix(&self, rb_matrix: Mat4) -> Mat4 {
        rb_matrix * self.body_offset_matrix_inverse
    }

    /// 从刚体变换反推骨骼变换（仅旋转，保留原位置）
    pub fn compute_bone_matrix_rotation_only(
        &self,
        rb_matrix: Mat4,
        bone_position: Vec3,
    ) -> Mat4 {
        let mut result = rb_matrix * self.body_offset_matrix_inverse;
        result.w_axis.x = bone_position.x;
        result.w_axis.y = bone_position.y;
        result.w_axis.z = bone_position.z;
        result
    }
}
