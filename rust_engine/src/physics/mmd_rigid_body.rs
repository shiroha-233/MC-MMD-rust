//! MMD 刚体数据
//!
//! 移植自 babylon-mmd 的 MmdRigidBodyData。
//! 使用 Bullet3 引擎，通过 inv_z 在骨骼（右手）与物理（左手）坐标系之间转换。

use glam::{Mat4, Quat, Vec3};

use mmd::pmx::joint::Joint as PmxJoint;
use mmd::pmx::rigid_body::{RigidBody as PmxRigidBody, RigidBodyMode, RigidBodyShape};

use super::bullet_ffi::{BulletRigidBody, BulletShape, RigidBodyInfo};

/// 跟随骨骼的人体碰撞壳厚度倍率。缩放不移动中心、中心轴或关节锚点。
pub const STATIC_COLLISION_SHAPE_SCALE: f32 = 0.7;

/// 返回实际传给 Bullet 和调试渲染器的碰撞形状尺寸。
pub fn effective_collision_shape_size(pmx_rb: &PmxRigidBody) -> [f32; 3] {
    effective_collision_shape_size_with_static_scale(pmx_rb, STATIC_COLLISION_SHAPE_SCALE, true)
}

/// 使用指定倍率计算碰撞形状尺寸，仅收窄跟随骨骼的人体碰撞壳。
pub fn effective_collision_shape_size_with_static_scale(
    pmx_rb: &PmxRigidBody,
    static_scale: f32,
    shrink_body_collider: bool,
) -> [f32; 3] {
    if pmx_rb.mode != RigidBodyMode::Static || !shrink_body_collider {
        return pmx_rb.size;
    }

    let scale = static_scale.clamp(0.1, 1.0);
    match pmx_rb.shape {
        // CySpring 将人体碰撞体半径与中心轴分开传入；这里同样只缩碰撞壳厚度。
        RigidBodyShape::Sphere => [pmx_rb.size[0] * scale, pmx_rb.size[1], pmx_rb.size[2]],
        RigidBodyShape::Capsule => [pmx_rb.size[0] * scale, pmx_rb.size[1], pmx_rb.size[2]],
        // PMX 箱体的局部 Y 是人体碰撞体高度，保留高度只收窄横截面。
        RigidBodyShape::Box => [
            pmx_rb.size[0] * scale,
            pmx_rb.size[1],
            pmx_rb.size[2] * scale,
        ],
    }
}

/// 按 PMX 的部位、碰撞组和关节用途识别需要收窄的人体碰撞体。
pub fn body_collider_scale_flags(rigid_bodies: &[PmxRigidBody], joints: &[PmxJoint]) -> Vec<bool> {
    rigid_bodies
        .iter()
        .enumerate()
        .map(|(index, body)| {
            body.mode == RigidBodyMode::Static
                && is_lower_body_collider(body)
                && collides_with_skirt_dynamic_body(body, rigid_bodies)
                && !is_dynamic_chain_anchor(index, rigid_bodies, joints)
        })
        .collect()
}

fn is_lower_body_collider(body: &PmxRigidBody) -> bool {
    const PART_NAMES: &[&str] = &[
        "下半身",
        "腰",
        "足",
        "ひざ",
        "膝",
        "腿",
        "thigh",
        "shin",
        "leg",
        "hip",
        "pelvis",
        "waist",
        "body_blocker",
        "skirt_collider",
    ];
    let local = body.local_name.to_lowercase();
    let universal = body.universal_name.to_lowercase();
    PART_NAMES
        .iter()
        .any(|part| local.contains(part) || universal.contains(part))
}

fn collides_with_skirt_dynamic_body(body: &PmxRigidBody, rigid_bodies: &[PmxRigidBody]) -> bool {
    rigid_bodies.iter().any(|dynamic| {
        dynamic.mode != RigidBodyMode::Static
            && is_skirt_or_lower_garment(dynamic)
            && collision_pair_is_enabled(body, dynamic)
    })
}

pub(super) fn is_skirt_or_lower_garment(body: &PmxRigidBody) -> bool {
    const PART_NAMES: &[&str] = &[
        "裙",
        "スカート",
        "skirt",
        "petticoat",
        "下装",
        "下衣",
        "裾",
        "摆",
        "衣摆",
        "后摆",
        "下摆",
        "风衣",
        "外套",
        "コート",
        "coat",
        "cloak",
        "cape",
        "flap",
    ];
    let local = body.local_name.to_lowercase();
    let universal = body.universal_name.to_lowercase();
    PART_NAMES
        .iter()
        .any(|part| local.contains(part) || universal.contains(part))
}

/// 尾巴是独立动态链，不能并入裙摆分类，否则跨部位碰撞无法单独诊断和过滤。
pub(super) fn is_tail_dynamic_part(body: &PmxRigidBody) -> bool {
    let local = body.local_name.to_ascii_lowercase();
    let universal = body.universal_name.to_ascii_lowercase();
    local.contains("tail") || universal.contains("tail")
}

fn collision_pair_is_enabled(a: &PmxRigidBody, b: &PmxRigidBody) -> bool {
    let a_mask = pmx_collision_mask(a.un_collision_group_flag);
    let b_mask = pmx_collision_mask(b.un_collision_group_flag);
    (a_mask & (1u16 << b.group.min(15))) != 0 && (b_mask & (1u16 << a.group.min(15))) != 0
}

fn is_dynamic_chain_anchor(
    body_index: usize,
    rigid_bodies: &[PmxRigidBody],
    joints: &[PmxJoint],
) -> bool {
    joints.iter().any(|joint| {
        let Ok(a) = usize::try_from(joint.rigid_body_a_index) else {
            return false;
        };
        let Ok(b) = usize::try_from(joint.rigid_body_b_index) else {
            return false;
        };
        let other = if a == body_index {
            b
        } else if b == body_index {
            a
        } else {
            return false;
        };
        rigid_bodies
            .get(other)
            .is_some_and(|body| body.mode != RigidBodyMode::Static)
    })
}

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
///
/// # Drop 顺序安全
/// `bullet_body` 必须声明在 `bullet_shape` 之前，Rust 按字段声明顺序 drop，
/// 确保刚体先于碰撞形状释放（刚体内部引用了形状指针）。
pub struct MmdRigidBodyData {
    /// 刚体名称
    pub name: String,
    /// 关联骨骼索引
    pub bone_index: i32,
    /// 物理模式
    pub physics_mode: PhysicsMode,
    /// 碰撞组
    pub group: u8,
    /// Bullet 允许碰撞的组掩码
    pub collision_mask: u16,
    /// 偏移矩阵 = B0⁻¹ * R0（刚体在骨骼局部空间的变换，saba 右乘约定）
    pub body_offset_matrix: Mat4,
    /// 偏移矩阵的逆 = R0⁻¹ * B0
    pub body_offset_matrix_inverse: Mat4,
    /// 刚体初始世界变换（MMD 坐标空间）
    pub initial_transform: Mat4,
    /// PMX 中未经修正的刚体位置，用于核对初始化数据。
    pub raw_position: [f32; 3],
    /// PMX 中未经修正的刚体旋转，用于识别异常资产编码。
    pub raw_rotation: [f32; 3],
    /// 送入欧拉角构造前的弧度值。
    pub decoded_rotation: [f32; 3],
    /// PMX 原始形状尺寸。
    pub shape_size: [f32; 3],
    /// 便于 JNI 日志稳定输出的形状名称。
    pub shape_name: &'static str,
    /// 形状在局部空间中的保守半尺寸，供构建期初始穿插检测使用。
    pub collision_half_extents: Option<Vec3>,
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
        bind_bone_transform: Option<Mat4>,
        shape_size: [f32; 3],
    ) -> Self {
        let physics_mode = PhysicsMode::from(pmx_rb.mode);

        let decoded_rotation = normalize_rigid_body_rotation(pmx_rb.rotation);
        // PMX 刚体与关节都按 Bullet setEulerZYX 的 X-Y-Z 输入语义构造，
        // 避免多轴旋转时碰撞形状与约束轴落入不同的局部坐标系。
        let rotation = rigid_body_rotation(decoded_rotation);

        let position = Vec3::new(pmx_rb.position[0], pmx_rb.position[1], pmx_rb.position[2]);

        // 刚体世界变换（直接使用 MMD 坐标，无 inv_z）
        let rb_world_matrix = Mat4::from_rotation_translation(rotation, position);

        // saba 约定: offset = B0_left⁻¹ * R0_left
        // 骨骼在右手坐标（Z 翻转），刚体在左手坐标（MMD 原生），需 InvZ 对齐
        let body_offset_matrix = if let Some(bone_transform) = bind_bone_transform {
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
            // PMX 保存的是“不碰撞组”，Bullet 接收的是“允许碰撞组”，
            // 两者语义相反，因此必须在 16 位组范围内取反。
            collision_mask: pmx_collision_mask(pmx_rb.un_collision_group_flag),
            body_offset_matrix,
            body_offset_matrix_inverse,
            initial_transform: rb_world_matrix,
            raw_position: pmx_rb.position,
            raw_rotation: pmx_rb.rotation,
            decoded_rotation,
            shape_size: pmx_rb.size,
            shape_name: rigid_body_shape_name(pmx_rb.shape),
            collision_half_extents: collision_half_extents(pmx_rb.shape, shape_size),
            bullet_body: None,
            bullet_shape: None,
            mass: pmx_rb.mass,
        }
    }

    /// 创建 Bullet3 碰撞形状（C++ OOM 时返回 None）
    pub fn create_shape(pmx_rb: &PmxRigidBody, size: [f32; 3]) -> Option<BulletShape> {
        match pmx_rb.shape {
            RigidBodyShape::Sphere => BulletShape::sphere(size[0]),
            RigidBodyShape::Box => BulletShape::r#box(size[0], size[1], size[2]),
            RigidBodyShape::Capsule => BulletShape::capsule(size[0], size[1]),
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
            RigidBodyShape::Box => {
                pmx_rb.size[0] <= 0.0 || pmx_rb.size[1] <= 0.0 || pmx_rb.size[2] <= 0.0
            }
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
    pub fn compute_bone_matrix_rotation_only(&self, rb_matrix: Mat4, bone_position: Vec3) -> Mat4 {
        let mut result = rb_matrix * self.body_offset_matrix_inverse;
        result.w_axis.x = bone_position.x;
        result.w_axis.y = bone_position.y;
        result.w_axis.z = bone_position.z;
        result
    }
}

/// 将 PMX 形状转换为局部保守 AABB 半尺寸，不参与 Bullet 求解。
fn collision_half_extents(shape: RigidBodyShape, size: [f32; 3]) -> Option<Vec3> {
    let extents = match shape {
        RigidBodyShape::Sphere => Vec3::splat(size[0]),
        RigidBodyShape::Box => Vec3::from_array(size),
        // Bullet 胶囊沿 Y 轴，size[1] 是圆柱段高度。
        RigidBodyShape::Capsule => Vec3::new(size[0], size[0] + size[1] * 0.5, size[0]),
    };
    (extents.is_finite() && extents.cmpgt(Vec3::ZERO).all()).then_some(extents)
}

/// 将 PMX 的“不碰撞组”标志转换为 Bullet 的“允许碰撞组”掩码。
fn pmx_collision_mask(un_collision_group_flag: u16) -> u16 {
    !un_collision_group_flag
}

/// 按 Bullet `setEulerZYX(x, y, z)` 的语义构造 PMX 物理旋转。
pub(super) fn mmd_physics_rotation(rotation: [f32; 3]) -> Quat {
    Quat::from_rotation_z(rotation[2])
        * Quat::from_rotation_y(rotation[1])
        * Quat::from_rotation_x(rotation[0])
}

fn rigid_body_rotation(rotation: [f32; 3]) -> Quat {
    mmd_physics_rotation(rotation)
}

fn rigid_body_shape_name(shape: RigidBodyShape) -> &'static str {
    match shape {
        RigidBodyShape::Sphere => "sphere",
        RigidBodyShape::Box => "box",
        RigidBodyShape::Capsule => "capsule_y",
    }
}

/// 兼容将整组刚体旋转重复做角度转换后写入 PMX 的资产。
///
/// 正常 PMX 使用弧度。该模型中的异常刚体含有 `10313.24` 一类分量，
/// 即 `PI * (180 / PI)^2`；同一组三个分量必须一起还原，避免遗漏接近零的分量。
fn normalize_rigid_body_rotation(rotation: [f32; 3]) -> [f32; 3] {
    let has_double_degree_encoding = rotation
        .iter()
        .any(|value| value.is_finite() && value.abs() > std::f32::consts::TAU);
    if has_double_degree_encoding {
        let radians_per_degree = std::f32::consts::PI / 180.0;
        rotation.map(|value| value * radians_per_degree * radians_per_degree)
    } else {
        rotation
    }
}

#[cfg(test)]
mod tests {
    use super::{
        body_collider_scale_flags, collision_half_extents, effective_collision_shape_size,
        effective_collision_shape_size_with_static_scale, normalize_rigid_body_rotation,
        pmx_collision_mask, rigid_body_rotation,
    };
    use glam::{Mat4, Quat, Vec3};
    use mmd::pmx::joint::{Joint, JointType};
    use mmd::pmx::rigid_body::{RigidBody, RigidBodyMode, RigidBodyShape};

    #[test]
    fn pmx_collision_mask_allows_all_groups_when_none_are_excluded() {
        assert_eq!(pmx_collision_mask(0x0000), 0xFFFF);
    }

    #[test]
    fn pmx_collision_mask_excludes_marked_groups() {
        assert_eq!(pmx_collision_mask(0x0001), 0xFFFE);
    }

    #[test]
    fn pmx_collision_mask_disallows_all_groups_when_all_are_excluded() {
        assert_eq!(pmx_collision_mask(0xFFFF), 0x0000);
    }

    #[test]
    fn rigid_body_rotation_matches_bullet_zyx_matrix_composition() {
        let [x, y, z] = [0.31, -0.47, 0.83];
        let expected =
            Quat::from_rotation_z(z) * Quat::from_rotation_y(y) * Quat::from_rotation_x(x);
        assert!(rigid_body_rotation([x, y, z]).abs_diff_eq(expected, 1e-6));
    }

    #[test]
    fn decoded_exported_rotation_uses_the_same_frame_as_pmx_joint() {
        let radians = [0.355_930_42, -0.000_011_105_393, std::f32::consts::PI];
        let export_scale = (180.0_f32 / std::f32::consts::PI).powi(2);
        let encoded = radians.map(|value| value * export_scale);
        let expected = Quat::from_rotation_z(radians[2])
            * Quat::from_rotation_y(radians[1])
            * Quat::from_rotation_x(radians[0]);

        assert!(
            rigid_body_rotation(normalize_rigid_body_rotation(encoded)).abs_diff_eq(expected, 1e-5)
        );
    }

    #[test]
    fn rigid_body_rotation_recovers_double_degree_encoded_vector() {
        let original = [0.004_436, -std::f32::consts::PI, std::f32::consts::PI];
        let double_degree_scale = (180.0_f32 / std::f32::consts::PI).powi(2);
        let encoded = original.map(|value| value * double_degree_scale);
        let decoded = normalize_rigid_body_rotation(encoded);
        for (actual, expected) in decoded.into_iter().zip(original) {
            assert!((actual - expected).abs() < 1e-5);
        }
    }

    #[test]
    fn rigid_body_rotation_preserves_normal_pmx_radians() {
        let rotation = [0.31, -0.47, 0.83];
        assert_eq!(normalize_rigid_body_rotation(rotation), rotation);
    }

    fn test_rigid_body(shape: RigidBodyShape, size: [f32; 3]) -> RigidBody {
        RigidBody {
            local_name: String::new(),
            universal_name: String::new(),
            bone_index: -1,
            group: 0,
            un_collision_group_flag: 0,
            shape,
            size,
            position: [0.0; 3],
            rotation: [0.0; 3],
            mass: 1.0,
            move_attenuation: 0.0,
            rotation_attenuation: 0.0,
            repulsion: 0.0,
            friction: 0.0,
            mode: RigidBodyMode::Dynamic,
        }
    }

    #[test]
    fn capsule_bounds_include_caps_and_cylinder_height() {
        let body = test_rigid_body(RigidBodyShape::Capsule, [0.5, 2.0, 0.0]);
        assert_eq!(
            collision_half_extents(body.shape, body.size),
            Some(Vec3::new(0.5, 1.5, 0.5))
        );
    }

    #[test]
    fn dynamic_shape_keeps_original_size() {
        let body = test_rigid_body(RigidBodyShape::Box, [2.0, 4.0, 6.0]);
        assert_eq!(effective_collision_shape_size(&body), body.size);
    }

    #[test]
    fn static_capsule_only_shrinks_collision_radius() {
        let mut body = test_rigid_body(RigidBodyShape::Capsule, [1.0, 4.0, 0.0]);
        body.mode = RigidBodyMode::Static;
        assert_eq!(effective_collision_shape_size(&body), [0.7, 4.0, 0.0]);
    }

    #[test]
    fn static_box_preserves_local_height() {
        let mut body = test_rigid_body(RigidBodyShape::Box, [2.0, 4.0, 6.0]);
        body.mode = RigidBodyMode::Static;
        assert_eq!(
            effective_collision_shape_size_with_static_scale(&body, 0.5, true),
            [1.0, 4.0, 3.0]
        );
    }

    #[test]
    fn custom_static_scale_is_clamped_to_viewer_range() {
        let mut body = test_rigid_body(RigidBodyShape::Sphere, [1.0, 0.0, 0.0]);
        body.mode = RigidBodyMode::Static;
        assert_eq!(
            super::effective_collision_shape_size_with_static_scale(&body, 0.05, true),
            [0.1, 0.0, 0.0]
        );
        assert_eq!(
            super::effective_collision_shape_size_with_static_scale(&body, 2.0, true),
            body.size
        );
    }

    #[test]
    fn body_collider_filter_uses_part_mask_and_anchor_topology() {
        let mut collider = test_rigid_body(RigidBodyShape::Capsule, [1.0, 2.0, 0.0]);
        collider.local_name = "M-M-M-下半身".to_owned();
        collider.mode = RigidBodyMode::Static;
        collider.group = 0;

        let mut anchor = test_rigid_body(RigidBodyShape::Sphere, [0.2, 0.0, 0.0]);
        anchor.local_name = "下半身".to_owned();
        anchor.mode = RigidBodyMode::Static;
        anchor.group = 0;

        let mut skirt = test_rigid_body(RigidBodyShape::Box, [1.0, 1.0, 0.2]);
        skirt.local_name = "裙_0_0".to_owned();
        skirt.group = 7;

        let bodies = vec![collider, anchor, skirt];
        let joints = vec![Joint {
            local_name: "裙根".to_owned(),
            universal_name: String::new(),
            type_: JointType::Spring6DOF,
            rigid_body_a_index: 1,
            rigid_body_b_index: 2,
            position: [0.0; 3],
            rotation: [0.0; 3],
            position_min: [0.0; 3],
            position_max: [0.0; 3],
            rotation_min: [0.0; 3],
            rotation_max: [0.0; 3],
            position_spring: [0.0; 3],
            rotation_spring: [0.0; 3],
        }];

        assert_eq!(
            body_collider_scale_flags(&bodies, &joints),
            [true, false, false]
        );
    }

    #[test]
    fn lower_body_collider_is_not_scaled_for_sleeve_only_collision() {
        let mut collider = test_rigid_body(RigidBodyShape::Capsule, [1.0, 2.0, 0.0]);
        collider.local_name = "M-M-M-下半身".to_owned();
        collider.mode = RigidBodyMode::Static;
        collider.group = 0;

        let mut sleeve = test_rigid_body(RigidBodyShape::Box, [1.0, 1.0, 0.2]);
        sleeve.local_name = "右袖2".to_owned();
        sleeve.group = 7;

        assert_eq!(
            body_collider_scale_flags(&[collider, sleeve], &[]),
            [false, false]
        );
    }

    #[test]
    fn bind_offset_remains_stable_when_runtime_pose_changes() {
        let mut pmx_body = test_rigid_body(RigidBodyShape::Sphere, [0.5, 0.0, 0.0]);
        pmx_body.bone_index = 0;
        pmx_body.position = [2.5, 4.0, -1.25];
        pmx_body.rotation = [0.2, -0.35, 0.1];

        // 绑定姿态只包含 PMX 初始全局位置；运行旋转不得被烘焙进静态 offset。
        let bind_pose = Mat4::from_translation(Vec3::new(1.0, 3.0, 0.5));
        let runtime_pose =
            Mat4::from_rotation_translation(Quat::from_rotation_y(0.8), Vec3::new(1.4, 3.2, -0.3));
        let data = super::MmdRigidBodyData::from_pmx(&pmx_body, Some(bind_pose), pmx_body.size);
        let expected_offset = super::super::inv_z(bind_pose).inverse() * data.initial_transform;

        assert!(data.body_offset_matrix.abs_diff_eq(expected_offset, 1e-6));
        assert!(data
            .compute_body_matrix(super::super::inv_z(runtime_pose))
            .abs_diff_eq(super::super::inv_z(runtime_pose) * expected_offset, 1e-6));
    }
}
