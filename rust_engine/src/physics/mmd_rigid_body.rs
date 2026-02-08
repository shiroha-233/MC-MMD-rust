//! MMD 刚体封装
//!
//! 对应 C++ saba 的 MMDRigidBody 类，使用 Rapier3D 实现。

use glam::{Mat4, Vec3, Quat};
use rapier3d::prelude::*;
use rapier3d::math::Real;

/// Rapier Isometry 类型别名
pub type Pose = rapier3d::math::Isometry<Real>;

use mmd::pmx::rigid_body::{RigidBody as PmxRigidBody, RigidBodyShape, RigidBodyMode};

use super::config::get_config;

/// 刚体类型
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RigidBodyType {
    /// 静态/运动学刚体，跟随骨骼
    Kinematic,
    /// 动态刚体，完全由物理驱动
    Dynamic,
    /// 动态刚体，但位置跟随骨骼（只有旋转由物理驱动）
    DynamicWithBonePosition,
}

impl From<RigidBodyMode> for RigidBodyType {
    fn from(mode: RigidBodyMode) -> Self {
        match mode {
            RigidBodyMode::Static => RigidBodyType::Kinematic,
            RigidBodyMode::Dynamic => RigidBodyType::Dynamic,
            RigidBodyMode::DynamicWithBonePosition => RigidBodyType::DynamicWithBonePosition,
        }
    }
}

/// MMD 刚体
/// 
/// 封装 Rapier 的 RigidBody 和 Collider，对应 C++ saba 的 MMDRigidBody。
pub struct MMDRigidBody {
    /// 刚体名称
    pub name: String,
    /// 关联的骨骼索引
    pub bone_index: i32,
    /// 刚体类型
    pub body_type: RigidBodyType,
    /// 碰撞组
    pub group: u8,
    /// 碰撞掩码
    pub group_mask: u16,
    /// 刚体句柄
    pub rigid_body_handle: Option<RigidBodyHandle>,
    /// 碰撞体句柄
    pub collider_handle: Option<ColliderHandle>,
    /// 刚体相对于骨骼的偏移矩阵
    pub offset_matrix: Mat4,
    /// 偏移矩阵的逆
    pub inv_offset_matrix: Mat4,
    /// 初始变换（用于重置）
    pub initial_transform: Pose,
    /// 上一帧的世界变换（用于计算速度）
    pub prev_transform: Option<Pose>,
    /// 质量
    pub mass: f32,
    /// 线性阻尼
    pub linear_damping: f32,
    /// 角阻尼
    pub angular_damping: f32,
    /// 弹性
    pub restitution: f32,
    /// 摩擦力
    pub friction: f32,
    /// 是否为胸部刚体（通过名称自动识别）
    pub is_bust: bool,
}

impl MMDRigidBody {
    /// 从 PMX 刚体数据创建
    /// 
    /// # 参数
    /// - `pmx_rb`: PMX 刚体数据
    /// - `bone_global_transform`: 关联骨骼的全局变换（如果有）
    pub fn from_pmx(
        pmx_rb: &PmxRigidBody,
        bone_global_transform: Option<Mat4>,
    ) -> Self {
        let body_type = RigidBodyType::from(pmx_rb.mode);
        
        // saba 的计算方式：
        // rotMat = ry * rx * rz  (Y-X-Z 欧拉角顺序)
        // translateMat = translate(m_translate)
        // rbMat = InvZ(translateMat * rotMat)
        // offsetMat = inverse(node->GetGlobalTransform()) * rbMat
        
        // 欧拉角转四元数（Y-X-Z 顺序，与 saba 一致）
        let rx = Quat::from_rotation_x(pmx_rb.rotation[0]);
        let ry = Quat::from_rotation_y(pmx_rb.rotation[1]);
        let rz = Quat::from_rotation_z(pmx_rb.rotation[2]);
        let rotation = ry * rx * rz;
        
        let position = Vec3::new(
            pmx_rb.position[0],
            pmx_rb.position[1],
            pmx_rb.position[2],
        );
        
        // translateMat * rotMat
        let rb_mat_mmd = Mat4::from_rotation_translation(rotation, position);
        
        // 应用 InvZ 变换: invZ * m * invZ
        // InvZ 矩阵是 scale(1, 1, -1)
        let rb_mat = inv_z(rb_mat_mmd);
        
        // 计算相对于骨骼的偏移矩阵
        let offset_matrix = if let Some(bone_transform) = bone_global_transform {
            bone_transform.inverse() * rb_mat
        } else {
            rb_mat
        };
        
        let inv_offset_matrix = offset_matrix.inverse();
        
        // 创建初始 Isometry
        let initial_transform = mat4_to_isometry(rb_mat);
        
        let is_bust = is_bust_name(&pmx_rb.local_name);
        
        Self {
            name: pmx_rb.local_name.clone(),
            bone_index: pmx_rb.bone_index,
            body_type,
            group: pmx_rb.group,
            group_mask: pmx_rb.un_collision_group_flag,
            rigid_body_handle: None,
            collider_handle: None,
            offset_matrix,
            inv_offset_matrix,
            initial_transform,
            prev_transform: None,
            mass: pmx_rb.mass,
            linear_damping: pmx_rb.move_attenuation,
            angular_damping: pmx_rb.rotation_attenuation,
            restitution: pmx_rb.repulsion,
            friction: pmx_rb.friction,
            is_bust,
        }
    }
    
    /// 创建 Rapier 刚体
    pub fn build_rigid_body(&self) -> RigidBody {
        let config = get_config();
        
        let rb_type = match self.body_type {
            RigidBodyType::Kinematic => rapier3d::dynamics::RigidBodyType::KinematicPositionBased,
            RigidBodyType::Dynamic | RigidBodyType::DynamicWithBonePosition => {
                rapier3d::dynamics::RigidBodyType::Dynamic
            }
        };
        
        // 根据是否为胸部刚体选用不同的参数组
        let (lin_damp_scale, ang_damp_scale) = if self.is_bust {
            (config.bust_linear_damping_scale, config.bust_angular_damping_scale)
        } else {
            (config.linear_damping_scale, config.angular_damping_scale)
        };
        
        let linear_damping = self.linear_damping * lin_damp_scale;
        let angular_damping = self.angular_damping * ang_damp_scale;
        
        RigidBodyBuilder::new(rb_type)
            .position(self.initial_transform)
            .linear_damping(linear_damping)
            .angular_damping(angular_damping)
            .ccd_enabled(false)
            .can_sleep(false) // MMD 物理不使用休眠
            .build()
    }
    
    /// 创建 Rapier 碰撞体
    pub fn build_collider(&self, pmx_rb: &PmxRigidBody) -> Collider {
        let config = get_config();
        
        let shape = match pmx_rb.shape {
            RigidBodyShape::Sphere => {
                SharedShape::ball(pmx_rb.size[0])
            }
            RigidBodyShape::Box => {
                SharedShape::cuboid(
                    pmx_rb.size[0],
                    pmx_rb.size[1],
                    pmx_rb.size[2],
                )
            }
            RigidBodyShape::Capsule => {
                // Rapier capsule: 沿 Y 轴，半径 + 半高
                let radius = pmx_rb.size[0];
                let half_height = pmx_rb.size[1] / 2.0;
                SharedShape::capsule_y(half_height, radius)
            }
        };
        
        // 碰撞组设置
        let collision_groups = InteractionGroups::new(
            Group::from_bits_truncate(1 << self.group),
            Group::from_bits_truncate(self.group_mask as u32),
        );
        
        let builder = ColliderBuilder::new(shape)
            .restitution(self.restitution);
        
        // 设置质量（根据部位应用不同缩放）
        let mass_scale = if self.is_bust { config.bust_mass_scale } else { config.mass_scale };
        let builder = if self.body_type == RigidBodyType::Kinematic {
            builder.density(0.0)
        } else {
            builder.mass(self.mass * mass_scale)
        };
        
        builder
            .friction(self.friction)
            .collision_groups(collision_groups)
            .solver_groups(collision_groups)
            .build()
    }
    
    /// 根据骨骼全局变换计算刚体应有的世界变换
    pub fn compute_world_transform(&self, bone_global_transform: Mat4) -> Pose {
        let world_transform = bone_global_transform * self.offset_matrix;
        mat4_to_isometry(world_transform)
    }
    
    /// 从刚体的世界变换反推骨骼的全局变换
    pub fn compute_bone_transform(&self, rb_world_transform: Pose) -> Mat4 {
        let rb_mat = isometry_to_mat4(rb_world_transform);
        rb_mat * self.inv_offset_matrix
    }
    
    /// 从刚体的世界变换反推骨骼的全局变换（仅旋转，位置保留）
    pub fn compute_bone_transform_rotation_only(
        &self,
        rb_world_transform: Pose,
        bone_position: Vec3,
    ) -> Mat4 {
        let rb_mat = isometry_to_mat4(rb_world_transform);
        let mut result = rb_mat * self.inv_offset_matrix;
        // 保留原始位置
        result.w_axis.x = bone_position.x;
        result.w_axis.y = bone_position.y;
        result.w_axis.z = bone_position.z;
        result
    }
    
    /// 重置刚体变换到初始状态
    pub fn reset_transform(&mut self) {
        // initial_transform 已在创建时保存
    }
}

/// InvZ 变换：将 MMD 左手坐标系转换为右手坐标系
/// 
/// saba 的实现：`invZ * m * invZ`，其中 invZ = scale(1, 1, -1)
pub fn inv_z(m: Mat4) -> Mat4 {
    let inv_z_mat = Mat4::from_scale(Vec3::new(1.0, 1.0, -1.0));
    inv_z_mat * m * inv_z_mat
}

/// 将 glam Mat4 转换为 Rapier Isometry
pub fn mat4_to_isometry(mat: Mat4) -> Pose {
    let (_, rotation, translation) = mat.to_scale_rotation_translation();
    Isometry::new(
        vector![translation.x, translation.y, translation.z],
        vector![rotation.x, rotation.y, rotation.z] * rotation.w.acos() * 2.0 / (1.0 - rotation.w * rotation.w).sqrt().max(1e-6)
    )
}

/// 将 Rapier Isometry 转换为 glam Mat4
pub fn isometry_to_mat4(iso: Pose) -> Mat4 {
    let translation = Vec3::new(
        iso.translation.x,
        iso.translation.y,
        iso.translation.z,
    );
    let rot = iso.rotation;
    // UnitQuaternion: coords 包含 [i, j, k, w]
    let rotation = Quat::from_xyzw(rot.coords[0], rot.coords[1], rot.coords[2], rot.coords[3]);
    Mat4::from_rotation_translation(rotation, translation)
}

/// 将 glam Vec3 转换为 Rapier Vector
#[allow(dead_code)]
pub fn vec3_to_rapier(v: Vec3) -> Vector<Real> {
    Vector::new(v.x, v.y, v.z)
}

/// 将 Rapier Vector 转换为 glam Vec3
#[allow(dead_code)]
pub fn rapier_to_vec3(v: Vector<Real>) -> Vec3 {
    Vec3::new(v.x, v.y, v.z)
}

/// 判断刚体名称是否为胸部相关
/// 
/// MMD 模型中胸部刚体常见命名：
/// - 日文：おっぱ、乳、胸
/// - 英文：bust、Bust、breast、Breast、oppai
fn is_bust_name(name: &str) -> bool {
    let lower = name.to_lowercase();
    lower.contains("おっぱ")
        || lower.contains("乳")
        || lower.contains("胸")
        || lower.contains("bust")
        || lower.contains("breast")
        || lower.contains("oppai")
}
