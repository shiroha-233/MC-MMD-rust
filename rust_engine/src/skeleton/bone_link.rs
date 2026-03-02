//! 骨骼节点 - 参考 nphysics MultibodyLink 设计

use glam::{Vec3, Quat, Mat4};
use mmd::pmx::bone as pmx_bone;
use mmd::pmx::types::DefaultConfig;
use bitflags::bitflags;


bitflags! {
    /// 骨骼标志位
    #[derive(Clone, Copy, Debug, Default)]
    pub struct BoneFlags: u32 {
        /// 可旋转
        const ROTATABLE = 1 << 0;
        /// 可移动
        const MOVABLE = 1 << 1;
        /// 是 IK 骨骼
        const IK = 1 << 2;
        /// 附加旋转
        const APPEND_ROTATE = 1 << 3;
        /// 附加平移
        const APPEND_TRANSLATE = 1 << 4;
        /// 附加变换使用本地坐标
        const APPEND_LOCAL = 1 << 5;
        /// 固定轴
        const FIXED_AXIS = 1 << 6;
        /// 本地轴
        const LOCAL_AXIS = 1 << 7;
        /// 物理后变形
        const DEFORM_AFTER_PHYSICS = 1 << 8;
        /// IK 启用
        const IK_ENABLED = 1 << 9;
    }
}


/// IK 链接信息
#[derive(Clone, Debug)]
pub struct IkLink {
    /// 链接骨骼索引
    pub bone_index: i32,
    /// 是否有角度限制
    pub has_limits: bool,
    /// 角度下限 (弧度)
    pub limit_min: Vec3,
    /// 角度上限 (弧度)
    pub limit_max: Vec3,
}

/// IK 配置
#[derive(Clone, Debug)]
pub struct IkConfig {
    /// 目标骨骼索引
    pub target_bone: i32,
    /// 迭代次数
    pub iterations: u32,
    /// 单次迭代角度限制
    pub limit_angle: f32,
    /// IK 链接列表
    pub links: Vec<IkLink>,
}

// ============================================================================
// 附加变换配置
// ============================================================================

/// 附加变换配置
#[derive(Clone, Debug, Default)]
pub struct AppendConfig {
    /// 附加变换父骨骼索引
    pub parent: i32,
    /// 附加变换比率
    pub rate: f32,
}

// ============================================================================
// 骨骼节点
// ============================================================================

/// 骨骼节点 - 类似 nphysics MultibodyLink
///
/// 设计原则（参考 nphysics）：
/// - 静态数据：骨骼的固有属性（名称、父子关系、标志等）
/// - 动态数据：每帧更新的变换状态
/// - 变换计算：local_to_world = parent.local_to_world * local_to_parent
#[derive(Clone, Debug)]
pub struct BoneLink {
    // ========================================
    // 静态数据（初始化后不变）
    // ========================================
    
    /// 骨骼名称
    pub name: String,
    
    /// 骨骼内部索引
    pub(crate) internal_id: usize,
    
    /// 父骨骼索引 (-1 表示根骨骼)
    pub parent_index: i32,
    
    /// 变换层级（用于排序）
    pub transform_level: i32,
    
    /// 骨骼标志
    pub flags: BoneFlags,
    
    /// 初始位置（世界空间，来自 PMX）
    pub initial_position: Vec3,
    
    /// 相对于父骨骼的偏移（在 build 时计算）
    /// 类似 nphysics 的 body_shift
    pub body_shift: Vec3,
    
    /// 逆绑定矩阵（用于蒙皮）
    pub inverse_init: Mat4,
    
    /// IK 配置
    pub ik_config: Option<IkConfig>,
    
    /// 附加变换配置
    pub append_config: Option<AppendConfig>,
    
    /// 固定轴方向
    pub fixed_axis: Vec3,
    
    /// 本地 X 轴
    pub local_axis_x: Vec3,
    
    /// 本地 Z 轴
    pub local_axis_z: Vec3,
    
    /// VRM T-pose → A-pose 静息旋转
    pub rest_rotation: Quat,
    
    /// 祖先累积 rest_rotation（共轭补偿用）
    pub parent_rest_rotation: Quat,
    
    // ========================================
    // 动态数据（每帧更新）
    // ========================================
    
    /// 动画平移
    pub animation_translate: Vec3,
    
    /// 动画旋转
    pub animation_rotate: Quat,
    
    /// IK 旋转
    pub ik_rotate: Quat,
    
    /// 附加平移（计算结果）
    pub append_translate: Vec3,
    
    /// 附加旋转（计算结果）
    pub append_rotate: Quat,
    
    /// 本地变换矩阵 (local_to_parent)
    /// 类似 nphysics 的 local_to_parent
    pub local_to_parent: Mat4,
    
    /// 全局变换矩阵 (local_to_world)
    /// 类似 nphysics 的 local_to_world
    pub local_to_world: Mat4,
    
    /// 父骨骼到世界的变换（缓存）
    /// 类似 nphysics 的 parent_to_world
    pub(crate) parent_to_world: Mat4,
    
    /// 是否为叶节点
    pub(crate) is_leaf: bool,
}

impl BoneLink {
    /// 创建新骨骼
    pub fn new(name: String) -> Self {
        Self {
            name,
            internal_id: 0,
            parent_index: -1,
            transform_level: 0,
            flags: BoneFlags::ROTATABLE,
            initial_position: Vec3::ZERO,
            body_shift: Vec3::ZERO,
            inverse_init: Mat4::IDENTITY,
            ik_config: None,
            append_config: None,
            fixed_axis: Vec3::Z,
            local_axis_x: Vec3::X,
            local_axis_z: Vec3::Z,
            rest_rotation: Quat::IDENTITY,
            parent_rest_rotation: Quat::IDENTITY,
            animation_translate: Vec3::ZERO,
            animation_rotate: Quat::IDENTITY,
            ik_rotate: Quat::IDENTITY,
            append_translate: Vec3::ZERO,
            append_rotate: Quat::IDENTITY,
            local_to_parent: Mat4::IDENTITY,
            local_to_world: Mat4::IDENTITY,
            parent_to_world: Mat4::IDENTITY,
            is_leaf: true,
        }
    }
    
    /// 从 PMX 骨骼数据创建
    pub fn from_pmx_bone(pmx: &pmx_bone::Bone<DefaultConfig>) -> Self {
        use mmd::pmx::bone::BoneFlags as PmxBoneFlags;
        
        let pmx_flags = pmx.bone_flags;
        
        // MMD 使用左手坐标系，翻转 Z 轴转换为右手坐标系
        let position = Vec3::new(
            pmx.position[0],
            pmx.position[1],
            -pmx.position[2],
        );
        
        // 转换标志
        let mut flags = BoneFlags::empty();
        if pmx_flags.contains(PmxBoneFlags::Rotatable) {
            flags.insert(BoneFlags::ROTATABLE);
        }
        if pmx_flags.contains(PmxBoneFlags::Movable) {
            flags.insert(BoneFlags::MOVABLE);
        }
        if pmx_flags.contains(PmxBoneFlags::InverseKinematics) {
            flags.insert(BoneFlags::IK);
        }
        if pmx_flags.contains(PmxBoneFlags::AddRotation) {
            flags.insert(BoneFlags::APPEND_ROTATE);
        }
        if pmx_flags.contains(PmxBoneFlags::AddMovement) {
            flags.insert(BoneFlags::APPEND_TRANSLATE);
        }
        if pmx_flags.contains(PmxBoneFlags::AddLocalDeform) {
            flags.insert(BoneFlags::APPEND_LOCAL);
        }
        if pmx_flags.contains(PmxBoneFlags::FixedAxis) {
            flags.insert(BoneFlags::FIXED_AXIS);
        }
        if pmx_flags.contains(PmxBoneFlags::LocalAxis) {
            flags.insert(BoneFlags::LOCAL_AXIS);
        }
        if pmx_flags.contains(PmxBoneFlags::PhysicalTransform) {
            flags.insert(BoneFlags::DEFORM_AFTER_PHYSICS);
        }
        
        let mut bone = Self::new(pmx.local_name.clone());
        bone.parent_index = pmx.parent;
        bone.transform_level = pmx.transform_level;
        bone.initial_position = position;
        bone.flags = flags;
        
        // 附加变换配置
        if let Some(ref additional) = pmx.additional {
            bone.append_config = Some(AppendConfig {
                parent: additional.parent,
                rate: additional.rate,
            });
        }
        
        // 固定轴（Z 轴翻转）
        if let Some(ref axis) = pmx.fixed_axis {
            bone.fixed_axis = Vec3::new(axis[0], axis[1], -axis[2]);
        }
        
        // 本地轴（Z 轴翻转）
        if let Some(ref local) = pmx.local_axis {
            bone.local_axis_x = Vec3::new(local.x[0], local.x[1], -local.x[2]);
            bone.local_axis_z = Vec3::new(local.z[0], local.z[1], -local.z[2]);
        }
        
        // IK 配置
        // 角度限制坐标系转换（与 C++ saba 一致）
        if let Some(ref ik) = pmx.inverse_kinematics {
            let links: Vec<IkLink> = ik.links.iter().map(|link| {
                let (has_limits, limit_min, limit_max) = match link.limits {
                    Some((min, max)) => (
                        true,
                        // 所有分量取负，并交换 min/max
                        Vec3::new(-max[0], -max[1], -max[2]),
                        Vec3::new(-min[0], -min[1], -min[2]),
                    ),
                    None => (false, Vec3::ZERO, Vec3::ZERO),
                };
                IkLink {
                    bone_index: link.ik_bone,
                    has_limits,
                    limit_min,
                    limit_max,
                }
            }).collect();
            
            bone.ik_config = Some(IkConfig {
                target_bone: ik.ik_bone,
                iterations: ik.iterations,
                limit_angle: ik.limit_angle,
                links,
            });
        }
        
        bone
    }
    
    // ========================================
    // 访问器（类似 nphysics MultibodyLink）
    // ========================================
    
    /// 骨骼索引
    #[inline]
    pub fn link_id(&self) -> usize {
        self.internal_id
    }
    
    /// 父骨骼索引
    #[inline]
    pub fn parent_id(&self) -> Option<usize> {
        if self.parent_index >= 0 {
            Some(self.parent_index as usize)
        } else {
            None
        }
    }
    
    /// 是否为根骨骼
    #[inline]
    pub fn is_root(&self) -> bool {
        self.parent_index < 0
    }
    
    /// 获取世界位置
    #[inline]
    pub fn position(&self) -> Vec3 {
        self.local_to_world.col(3).truncate()
    }
    
    /// 获取世界旋转
    #[inline]
    pub fn rotation(&self) -> Quat {
        Quat::from_mat4(&self.local_to_world)
    }
    
    // ========================================
    // 变换计算
    // ========================================
    
    /// 重置动画状态
    #[inline]
    pub fn reset_animation(&mut self) {
        self.animation_translate = Vec3::ZERO;
        self.animation_rotate = Quat::IDENTITY;
        self.ik_rotate = Quat::IDENTITY;
        self.append_translate = Vec3::ZERO;
        self.append_rotate = Quat::IDENTITY;
    }
    
    /// 计算本地变换 (local_to_parent)
    pub fn compute_local_transform(&mut self) {
        let mut translate = self.body_shift + self.animation_translate;
        if self.flags.contains(BoneFlags::APPEND_TRANSLATE) {
            translate += self.append_translate;
        }
        
        let p = self.parent_rest_rotation;
        let mut rotation = p.inverse() * self.animation_rotate * p * self.rest_rotation;
        if self.flags.contains(BoneFlags::IK_ENABLED) {
            rotation = self.ik_rotate * rotation;
        }
        if self.flags.contains(BoneFlags::APPEND_ROTATE) {
            rotation = rotation * self.append_rotate;
        }
        
        self.local_to_parent = Mat4::from_rotation_translation(rotation, translate);
    }
    
    /// 获取蒙皮矩阵
    /// skinning_matrix = local_to_world * inverse_init
    #[inline]
    pub fn get_skinning_matrix(&self) -> Mat4 {
        self.local_to_world * self.inverse_init
    }
    
    // ========================================
    // 便捷方法（别名）
    // ========================================
    
    /// 获取全局变换
    #[inline]
    pub fn global_transform(&self) -> Mat4 {
        self.local_to_world
    }
    
    /// 设置全局变换
    #[inline]
    pub fn set_global_transform(&mut self, transform: Mat4) {
        self.local_to_world = transform;
    }
    
    /// 获取本地变换
    #[inline]
    pub fn local_transform(&self) -> Mat4 {
        self.local_to_parent
    }
    
    /// 设置本地变换
    #[inline]
    pub fn set_local_transform(&mut self, transform: Mat4) {
        self.local_to_parent = transform;
    }
    
    /// 更新本地变换
    #[inline]
    pub fn update_local_transform(&mut self) {
        self.compute_local_transform();
    }
    
    /// 骨骼偏移
    #[inline]
    pub fn bone_offset(&self) -> Vec3 {
        self.body_shift
    }
    
    /// 逆绑定矩阵
    #[inline]
    pub fn inverse_bind_matrix(&self) -> Mat4 {
        self.inverse_init
    }
    
    // ========================================
    // 标志检查方法
    // ========================================
    
    #[inline]
    pub fn is_rotatable(&self) -> bool {
        self.flags.contains(BoneFlags::ROTATABLE)
    }
    
    #[inline]
    pub fn is_movable(&self) -> bool {
        self.flags.contains(BoneFlags::MOVABLE)
    }
    
    #[inline]
    pub fn is_ik(&self) -> bool {
        self.flags.contains(BoneFlags::IK)
    }
    
    #[inline]
    pub fn is_append_rotate(&self) -> bool {
        self.flags.contains(BoneFlags::APPEND_ROTATE)
    }
    
    #[inline]
    pub fn is_append_translate(&self) -> bool {
        self.flags.contains(BoneFlags::APPEND_TRANSLATE)
    }
    
    #[inline]
    pub fn is_append_local(&self) -> bool {
        self.flags.contains(BoneFlags::APPEND_LOCAL)
    }
    
    #[inline]
    pub fn deform_after_physics(&self) -> bool {
        self.flags.contains(BoneFlags::DEFORM_AFTER_PHYSICS)
    }
    
    #[inline]
    pub fn enable_ik(&self) -> bool {
        self.flags.contains(BoneFlags::IK_ENABLED)
    }
    
    #[inline]
    pub fn set_enable_ik(&mut self, enabled: bool) {
        if enabled {
            self.flags.insert(BoneFlags::IK_ENABLED);
        } else {
            self.flags.remove(BoneFlags::IK_ENABLED);
        }
    }
}

impl Default for BoneLink {
    fn default() -> Self {
        Self::new(String::new())
    }
}
