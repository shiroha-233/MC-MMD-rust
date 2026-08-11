//! MMD 关节（约束）封装
//!
//! 移植自 babylon-mmd 的关节构建逻辑。
//! 使用 Bullet3 btGeneric6DofSpringConstraint。

use glam::{Mat4, Quat, Vec3};

use mmd::pmx::joint::Joint as PmxJoint;
use mmd::pmx::rigid_body::{RigidBody as PmxRigidBody, RigidBodyMode};

use super::bullet_ffi::{BulletConstraint, BulletRigidBody, BT_CONSTRAINT_STOP_ERP};
use super::joint_parameters::JointParameters;
use super::mmd_rigid_body::mmd_physics_rotation;

/// 沿用既有 MMD Bullet 实现的限位纠偏比例，避免混入未经验证的参数实验。
const JOINT_STOP_ERP: f32 = 0.475;
/// 宽松零弹簧裙根的兼容阈值；正常模型通常远小于该范围并自带恢复弹簧。
const WIDE_SKIRT_ROOT_ANGLE: f32 = std::f32::consts::FRAC_PI_3;
/// Rin 一类异常裙根直接采用与正常模型接近的 ±16° 旋转范围。
const SKIRT_ROOT_FALLBACK_ANGLE: f32 = 16.0_f32.to_radians();
/// 低于常见优质模型的裙根刚度，既抵抗静止下垂，又尽量保留摆动空间。
const SKIRT_ROOT_FALLBACK_STIFFNESS: [f32; 3] = [16.0, 6.0, 12.0];
/// 细密裙链必须按整列限制累计角度，而不能只收紧单个宽限位关节。
/// Rin 的九层裙链采用 16° + 8° + 7 * 5°，整列最大偏转约为 59°。
const SKIRT_CHAIN_FIRST_ANGLE: f32 = 8.0_f32.to_radians();
const SKIRT_CHAIN_LATER_ANGLE: f32 = 5.0_f32.to_radians();
const SKIRT_CHAIN_FIRST_STIFFNESS: f32 = 12.0;
const SKIRT_CHAIN_LATER_STIFFNESS: f32 = 10.0;

/// MMD 关节数据
pub struct MmdJointData {
    /// 关节名称
    pub name: String,
    /// 刚体 A 索引
    pub rigid_body_a_index: i32,
    /// 刚体 B 索引
    pub rigid_body_b_index: i32,
    /// 关节在刚体 A 局部空间中的 frame，供运行时诊断锚点误差。
    pub frame_a: Mat4,
    /// 关节在刚体 B 局部空间中的 frame，供运行时诊断锚点误差。
    pub frame_b: Mat4,
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
        pmx_rb_a: &PmxRigidBody,
        pmx_rb_b: &PmxRigidBody,
        rb_a_initial_transform: Mat4,
        rb_b_initial_transform: Mat4,
    ) -> Self {
        let position = Vec3::new(
            pmx_joint.position[0],
            pmx_joint.position[1],
            pmx_joint.position[2],
        );

        // 关节沿用 Bullet setEulerZYX 对应的 X-Y-Z intrinsic 组合。
        let rotation = joint_rotation(pmx_joint.rotation);

        let joint_transform = Mat4::from_rotation_translation(rotation, position);
        let mut parameters = JointParameters::from_pmx(
            pmx_joint.position_min,
            pmx_joint.position_max,
            pmx_joint.rotation_min,
            pmx_joint.rotation_max,
            pmx_joint.position_spring,
            pmx_joint.rotation_spring,
        );
        let clamped_wide_root = apply_wide_skirt_root_fallback(&mut parameters, pmx_rb_a, pmx_rb_b);
        apply_wide_skirt_chain_fallback(&mut parameters, pmx_rb_a, pmx_rb_b);
        if !clamped_wide_root {
            apply_skirt_root_outward_limit(
                &mut parameters,
                pmx_rb_a,
                pmx_rb_b,
                position,
                rotation,
                rb_b_initial_transform.w_axis.truncate(),
            );
        }

        // frameA = rbA_initialTransform.inverse() * jointTransform
        // frameB = rbB_initialTransform.inverse() * jointTransform
        let frame_a = rb_a_initial_transform.inverse() * joint_transform;
        let frame_b = rb_b_initial_transform.inverse() * joint_transform;

        // 创建 Bullet3 6DOF 弹簧约束
        let constraint = BulletConstraint::new_6dof_spring(rb_a, rb_b, frame_a, frame_b, true);

        // 配置约束参数（仅在创建成功时）
        if let Some(ref c) = constraint {
            for axis in 0..6 {
                c.set_param(BT_CONSTRAINT_STOP_ERP, JOINT_STOP_ERP, axis);
            }

            c.set_linear_lower_limit(
                parameters.linear[0].lower,
                parameters.linear[1].lower,
                parameters.linear[2].lower,
            );
            c.set_linear_upper_limit(
                parameters.linear[0].upper,
                parameters.linear[1].upper,
                parameters.linear[2].upper,
            );
            c.set_angular_lower_limit(
                parameters.angular[0].lower,
                parameters.angular[1].lower,
                parameters.angular[2].lower,
            );
            c.set_angular_upper_limit(
                parameters.angular[0].upper,
                parameters.angular[1].upper,
                parameters.angular[2].upper,
            );

            for (axis, axis_parameters) in parameters
                .linear
                .iter()
                .chain(parameters.angular.iter())
                .enumerate()
            {
                if axis_parameters.spring_enabled {
                    c.set_stiffness(axis as i32, axis_parameters.stiffness);
                }
                c.enable_spring(axis as i32, axis_parameters.spring_enabled);
            }

            // 构建阶段先建立默认平衡点；运行姿态提交后会再次刷新，避免首帧弹簧预载。
            c.set_equilibrium_point();
        }

        Self {
            name: pmx_joint.local_name.clone(),
            rigid_body_a_index: pmx_joint.rigid_body_a_index,
            rigid_body_b_index: pmx_joint.rigid_body_b_index,
            frame_a,
            frame_b,
            constraint,
        }
    }

    /// 将当前两端刚体的相对姿态设为弹簧零点。
    ///
    /// 约束先于运行姿态初始化创建，因此刚体重置后必须重新取样 equilibrium；
    /// 否则弹簧会把无碰撞、锚点重合的初态主动拉回旧绑定姿态。
    pub(crate) fn rebase_equilibrium(&self) {
        if let Some(constraint) = self.constraint.as_ref() {
            constraint.set_equilibrium_point();
        }
    }
}

/// 将 PMX 关节欧拉角转换为 Bullet setEulerZYX 等价旋转。
fn joint_rotation(rotation: [f32; 3]) -> Quat {
    // 刚体形状与 6DOF frame 必须共享同一旋转约定。
    mmd_physics_rotation(rotation)
}

fn apply_skirt_root_outward_limit(
    parameters: &mut JointParameters,
    body_a: &PmxRigidBody,
    body_b: &PmxRigidBody,
    joint_position: Vec3,
    joint_rotation: Quat,
    dynamic_body_position: Vec3,
) {
    if body_a.mode != RigidBodyMode::Static
        || body_b.mode == RigidBodyMode::Static
        || !is_skirt_body(body_b)
    {
        return;
    }

    let center_offset = dynamic_body_position - joint_position;
    let radial = Vec3::new(center_offset.x, 0.0, center_offset.z).length();
    if radial <= 1e-5 {
        return;
    }

    // 计算中心线向外的径向单位向量
    let outward = Vec3::new(center_offset.x, 0.0, center_offset.z).normalize();

    // 分别评估关节局部 X 轴（Pitch 俯仰）与 Z 轴（Roll 侧滚）正向旋转对径向位移的贡献得分。
    // 得分为正表示正向旋转将摆片推向外侧；得分为负表示负向旋转将摆片推向外侧。
    let axis_x = joint_rotation * Vec3::X;
    let axis_z = joint_rotation * Vec3::Z;

    let score_x = axis_x.cross(center_offset).dot(outward);
    let score_z = axis_z.cross(center_offset).dot(outward);

    if score_x.abs() > 1e-4 {
        parameters.angular[0].restrict_inward_rotation(score_x > 0.0);
    }

    if score_z.abs() > 1e-4 {
        parameters.angular[2].restrict_inward_rotation(score_z > 0.0);
    }
}

fn apply_wide_skirt_root_fallback(
    parameters: &mut JointParameters,
    body_a: &PmxRigidBody,
    body_b: &PmxRigidBody,
) -> bool {
    if body_a.mode != RigidBodyMode::Static
        || body_b.mode == RigidBodyMode::Static
        || !is_skirt_body(body_b)
        || parameters.angular.iter().any(|axis| axis.spring_enabled)
    {
        return false;
    }

    // Rin 一类模型会给裙根约 ±90° 的范围，却完全不提供旋转弹簧。
    // 只在至少一个有效轴明显过宽时兜底，避免修改正常的柔性零弹簧部件。
    let has_wide_axis = parameters
        .angular
        .iter()
        .any(|axis| axis.lower <= axis.upper && axis.upper - axis.lower >= WIDE_SKIRT_ROOT_ANGLE);
    if !has_wide_axis {
        return false;
    }

    for (axis, stiffness) in parameters
        .angular
        .iter_mut()
        .zip(SKIRT_ROOT_FALLBACK_STIFFNESS)
    {
        axis.clamp_symmetric_rotation(SKIRT_ROOT_FALLBACK_ANGLE);
        axis.install_fallback_spring(stiffness);
    }

    true
}

fn apply_wide_skirt_chain_fallback(
    parameters: &mut JointParameters,
    body_a: &PmxRigidBody,
    body_b: &PmxRigidBody,
) -> bool {
    if body_a.mode == RigidBodyMode::Static
        || body_b.mode == RigidBodyMode::Static
        || parameters.angular.iter().any(|axis| axis.spring_enabled)
    {
        return false;
    }

    let Some((parent_row, parent_column)) = skirt_grid_position(body_a) else {
        return false;
    };
    let Some((child_row, child_column)) = skirt_grid_position(body_b) else {
        return false;
    };

    // 仅处理同列向下的纵向裙链；横向裙环和装饰物关节保留 PMX 原始参数。
    if child_row != parent_row + 1 || child_column != parent_column {
        return false;
    }

    let angle = if child_row == 1 {
        SKIRT_CHAIN_FIRST_ANGLE
    } else {
        SKIRT_CHAIN_LATER_ANGLE
    };
    let stiffness = if child_row == 1 {
        SKIRT_CHAIN_FIRST_STIFFNESS
    } else {
        SKIRT_CHAIN_LATER_STIFFNESS
    };

    // Rin 的纵向主轴为 X。这里按整条零弹簧链分配累计角度预算；即使后段
    // 单节原始范围不足 60°，也必须参与预算，否则九节叠加后仍可整体翻折。
    let axis = &mut parameters.angular[0];
    if axis.lower > axis.upper || axis.upper - axis.lower <= 1e-6 {
        return false;
    }
    axis.clamp_symmetric_rotation(angle);
    axis.install_fallback_spring(stiffness);
    true
}

fn skirt_grid_position(body: &PmxRigidBody) -> Option<(u32, u32)> {
    [&body.local_name, &body.universal_name]
        .into_iter()
        .find_map(|name| {
            let mut parts = name.rsplit('_');
            let column = parts.next()?.parse().ok()?;
            let row = parts.next()?.parse().ok()?;
            let prefix = parts.next()?;
            if prefix.contains('裙') || prefix.eq_ignore_ascii_case("skirt") {
                Some((row, column))
            } else {
                None
            }
        })
}

fn is_skirt_body(body: &PmxRigidBody) -> bool {
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
        "tail",
        "flap",
    ];
    let local = body.local_name.to_lowercase();
    let universal = body.universal_name.to_lowercase();
    PART_NAMES
        .iter()
        .any(|part| local.contains(part) || universal.contains(part))
}

/// 计算两个刚体上的关节锚点在 Bullet 世界空间中的距离。
pub(crate) fn joint_anchor_position_error(
    body_a_transform: Mat4,
    frame_a: Mat4,
    body_b_transform: Mat4,
    frame_b: Mat4,
) -> (f32, Vec3, Vec3) {
    let anchor_a = (body_a_transform * frame_a).w_axis.truncate();
    let anchor_b = (body_b_transform * frame_b).w_axis.truncate();
    (anchor_a.distance(anchor_b), anchor_a, anchor_b)
}

#[cfg(test)]
mod tests {
    use super::{
        apply_skirt_root_outward_limit, apply_wide_skirt_chain_fallback,
        apply_wide_skirt_root_fallback, joint_anchor_position_error, joint_rotation, MmdJointData,
        JOINT_STOP_ERP,
    };
    use crate::physics::bullet_ffi::{BulletRigidBody, BulletShape, RigidBodyInfo};
    use glam::{Mat4, Quat, Vec3};
    use mmd::pmx::{
        joint::{Joint, JointType},
        rigid_body::{RigidBody, RigidBodyMode, RigidBodyShape},
    };

    fn test_body(shape: &BulletShape) -> BulletRigidBody {
        BulletRigidBody::new(
            &RigidBodyInfo {
                mass: 1.0,
                linear_damping: 0.0,
                angular_damping: 0.0,
                friction: 0.5,
                restitution: 0.0,
                additional_damping: false,
                is_kinematic: false,
                disable_deactivation: true,
                no_contact_response: false,
                initial_transform: Mat4::IDENTITY,
            },
            shape,
        )
        .expect("应能创建关节测试刚体")
    }

    fn test_pmx_body(name: &str, mode: RigidBodyMode) -> RigidBody {
        RigidBody {
            local_name: name.to_owned(),
            universal_name: String::new(),
            bone_index: -1,
            group: 0,
            un_collision_group_flag: 0,
            shape: RigidBodyShape::Box,
            size: [0.5; 3],
            position: [0.0; 3],
            rotation: [0.0; 3],
            mass: 1.0,
            move_attenuation: 0.0,
            rotation_attenuation: 0.0,
            repulsion: 0.0,
            friction: 0.0,
            mode,
        }
    }

    #[test]
    fn joint_rotation_matches_bullet_zyx_matrix_composition() {
        let [x, y, z] = [0.31, -0.47, 0.83];
        let expected =
            Quat::from_rotation_z(z) * Quat::from_rotation_y(y) * Quat::from_rotation_x(x);
        assert!(joint_rotation([x, y, z]).abs_diff_eq(expected, 1e-6));
    }

    #[test]
    fn stabilization_parameters_stay_in_bullet_ranges() {
        assert!((0.0..=1.0).contains(&JOINT_STOP_ERP));
    }

    #[test]
    fn skirt_root_limit_uses_geometry_to_choose_outward_x_direction() {
        let anchor = test_pmx_body("下半身", RigidBodyMode::Static);
        let skirt = test_pmx_body("裙_0_0", RigidBodyMode::Dynamic);
        let mut negative_outward = crate::physics::joint_parameters::JointParameters::from_pmx(
            [0.0; 3],
            [0.0; 3],
            [-1.5, 0.0, 0.0],
            [1.5, 0.0, 0.0],
            [0.0; 3],
            [0.0; 3],
        );

        apply_skirt_root_outward_limit(
            &mut negative_outward,
            &anchor,
            &skirt,
            Vec3::ZERO,
            Quat::IDENTITY,
            Vec3::new(0.0, -1.0, -0.2),
        );
        assert_eq!(negative_outward.angular[0].lower, 0.0);
        assert_eq!(negative_outward.angular[0].upper, 1.5);

        let mut positive_outward = crate::physics::joint_parameters::JointParameters::from_pmx(
            [0.0; 3],
            [0.0; 3],
            [-1.5, 0.0, 0.0],
            [1.5, 0.0, 0.0],
            [0.0; 3],
            [0.0; 3],
        );
        apply_skirt_root_outward_limit(
            &mut positive_outward,
            &anchor,
            &skirt,
            Vec3::ZERO,
            Quat::IDENTITY,
            Vec3::new(0.0, -1.0, 0.2),
        );
        assert_eq!(positive_outward.angular[0].lower, -1.5);
        assert_eq!(positive_outward.angular[0].upper, 0.0);

        // 两种判向都必须允许绑定姿态，不能在首步制造限位越界。
        for parameters in [&negative_outward, &positive_outward] {
            assert!(parameters.angular[0].lower <= 0.0);
            assert!(parameters.angular[0].upper >= 0.0);
        }
    }

    #[test]
    fn skirt_root_limit_supports_side_skirt_flaps_via_z_axis() {
        let anchor = test_pmx_body("下半身", RigidBodyMode::Static);
        let skirt = test_pmx_body("裙_侧摆", RigidBodyMode::Dynamic);

        // 左侧裙摆 (x < 0)
        let mut left_skirt = crate::physics::joint_parameters::JointParameters::from_pmx(
            [0.0; 3],
            [0.0; 3],
            [0.0, 0.0, -1.5],
            [0.0, 0.0, 1.5],
            [0.0; 3],
            [0.0; 3],
        );
        apply_skirt_root_outward_limit(
            &mut left_skirt,
            &anchor,
            &skirt,
            Vec3::ZERO,
            Quat::IDENTITY,
            Vec3::new(-0.2, -1.0, 0.0),
        );
        assert_eq!(left_skirt.angular[2].lower, -1.5);
        assert_eq!(left_skirt.angular[2].upper, 0.0);

        // 右侧裙摆 (x > 0)
        let mut right_skirt = crate::physics::joint_parameters::JointParameters::from_pmx(
            [0.0; 3],
            [0.0; 3],
            [0.0, 0.0, -1.5],
            [0.0, 0.0, 1.5],
            [0.0; 3],
            [0.0; 3],
        );
        apply_skirt_root_outward_limit(
            &mut right_skirt,
            &anchor,
            &skirt,
            Vec3::ZERO,
            Quat::IDENTITY,
            Vec3::new(0.2, -1.0, 0.0),
        );
        assert_eq!(right_skirt.angular[2].lower, 0.0);
        assert_eq!(right_skirt.angular[2].upper, 1.5);
    }

    #[test]
    fn skirt_root_limit_preserves_valid_bounds_for_positive_pmx_offsets() {
        let anchor = test_pmx_body("下半身", RigidBodyMode::Static);
        let skirt = test_pmx_body("后摆", RigidBodyMode::Dynamic);

        // PMX 中初始给出了正数的 lower/upper 限制 (例如 lower = 0.1, upper = 1.5)
        let mut parameters = crate::physics::joint_parameters::JointParameters::from_pmx(
            [0.0; 3],
            [0.0; 3],
            [0.1, 0.0, 0.0],
            [1.5, 0.0, 0.0],
            [0.0; 3],
            [0.0; 3],
        );
        apply_skirt_root_outward_limit(
            &mut parameters,
            &anchor,
            &skirt,
            Vec3::ZERO,
            Quat::IDENTITY,
            Vec3::new(0.0, -1.0, 0.2), // 后摆，负向旋转为向外
        );

        // 裁切后 lower 必须 <= upper 且 0.0 处于合法区间内，决不能造成 lower > upper 的 Bullet 自由轴
        assert!(parameters.angular[0].lower <= parameters.angular[0].upper);
        assert_eq!(parameters.angular[0].upper, 0.0);
        assert!(parameters.angular[0].lower <= 0.0);
    }

    #[test]
    fn dynamic_skirt_chain_keeps_pmx_limits() {
        let dynamic_a = test_pmx_body("裙_0_0", RigidBodyMode::Dynamic);
        let dynamic_b = test_pmx_body("裙_1_0", RigidBodyMode::Dynamic);
        let mut parameters = crate::physics::joint_parameters::JointParameters::from_pmx(
            [0.0; 3],
            [0.0; 3],
            [-1.5, 0.0, 0.0],
            [1.5, 0.0, 0.0],
            [0.0; 3],
            [0.0; 3],
        );

        apply_skirt_root_outward_limit(
            &mut parameters,
            &dynamic_a,
            &dynamic_b,
            Vec3::ZERO,
            Quat::IDENTITY,
            Vec3::new(0.0, -1.0, 0.2),
        );

        assert_eq!(parameters.angular[0].lower, -1.5);
        assert_eq!(parameters.angular[0].upper, 1.5);
    }

    #[test]
    fn wide_zero_spring_skirt_root_gets_conservative_restoring_spring() {
        let anchor = test_pmx_body("下半身", RigidBodyMode::Static);
        let skirt = test_pmx_body("裙_0_0", RigidBodyMode::Dynamic);
        let mut parameters = crate::physics::joint_parameters::JointParameters::from_pmx(
            [0.0; 3],
            [0.0; 3],
            [-std::f32::consts::FRAC_PI_2, 0.0, 0.0],
            [std::f32::consts::FRAC_PI_2, 0.0, 0.0],
            [0.0; 3],
            [0.0; 3],
        );

        assert!(apply_wide_skirt_root_fallback(
            &mut parameters,
            &anchor,
            &skirt
        ));

        assert!(parameters.angular[0].spring_enabled);
        assert_eq!(parameters.angular[0].stiffness, 16.0);
        assert!((parameters.angular[0].lower + 16.0_f32.to_radians()).abs() < 1e-6);
        assert!((parameters.angular[0].upper - 16.0_f32.to_radians()).abs() < 1e-6);
        // 锁死轴无需额外弹簧，避免安装无意义的 motor。
        assert!(!parameters.angular[1].spring_enabled);
        assert!(!parameters.angular[2].spring_enabled);
    }

    #[test]
    fn authored_skirt_root_springs_are_preserved() {
        let anchor = test_pmx_body("下半身", RigidBodyMode::Static);
        let skirt = test_pmx_body("裙_0_0", RigidBodyMode::Dynamic);
        let mut parameters = crate::physics::joint_parameters::JointParameters::from_pmx(
            [0.0; 3],
            [0.0; 3],
            [-0.28, -0.1, -0.2],
            [0.28, 0.1, 0.2],
            [0.0; 3],
            [26.0, 10.0, 20.8],
        );

        assert!(!apply_wide_skirt_root_fallback(
            &mut parameters,
            &anchor,
            &skirt
        ));

        assert_eq!(parameters.angular[0].stiffness, 26.0);
        assert_eq!(parameters.angular[1].stiffness, 10.0);
        assert_eq!(parameters.angular[2].stiffness, 20.8);
    }

    #[test]
    fn wide_zero_spring_vertical_skirt_chain_gets_layered_fallback() {
        let parent = test_pmx_body("裙_0_0", RigidBodyMode::Dynamic);
        let child = test_pmx_body("裙_1_0", RigidBodyMode::Dynamic);
        let mut parameters = crate::physics::joint_parameters::JointParameters::from_pmx(
            [0.0; 3],
            [0.0; 3],
            [-1.5, 0.0, 0.0],
            [1.5, 0.0, 0.0],
            [0.0; 3],
            [0.0; 3],
        );

        assert!(apply_wide_skirt_chain_fallback(
            &mut parameters,
            &parent,
            &child
        ));

        assert!(parameters.angular[0].spring_enabled);
        assert_eq!(parameters.angular[0].stiffness, 12.0);
        assert!((parameters.angular[0].lower + 8.0_f32.to_radians()).abs() < 1e-6);
        assert!((parameters.angular[0].upper - 8.0_f32.to_radians()).abs() < 1e-6);
        assert!(!parameters.angular[1].spring_enabled);
        assert!(!parameters.angular[2].spring_enabled);
    }

    #[test]
    fn skirt_chain_fallback_preserves_later_layer_flexibility() {
        let parent = test_pmx_body("裙_1_3", RigidBodyMode::Dynamic);
        let child = test_pmx_body("裙_2_3", RigidBodyMode::Dynamic);
        let mut parameters = crate::physics::joint_parameters::JointParameters::from_pmx(
            [0.0; 3],
            [0.0; 3],
            [-1.3, 0.0, -0.04],
            [1.3, 0.0, 0.04],
            [0.0; 3],
            [0.0; 3],
        );

        assert!(apply_wide_skirt_chain_fallback(
            &mut parameters,
            &parent,
            &child
        ));
        assert_eq!(parameters.angular[0].stiffness, 10.0);
        assert!((parameters.angular[0].upper - 5.0_f32.to_radians()).abs() < 1e-6);
    }

    #[test]
    fn narrow_later_skirt_layer_still_participates_in_chain_budget() {
        let parent = test_pmx_body("裙_7_3", RigidBodyMode::Dynamic);
        let child = test_pmx_body("裙_8_3", RigidBodyMode::Dynamic);
        let mut parameters = crate::physics::joint_parameters::JointParameters::from_pmx(
            [0.0; 3],
            [0.0; 3],
            [0.0, 0.0, -0.04],
            [10.0_f32.to_radians(), 0.0, 0.04],
            [0.0; 3],
            [0.0; 3],
        );

        assert!(apply_wide_skirt_chain_fallback(
            &mut parameters,
            &parent,
            &child
        ));
        assert_eq!(parameters.angular[0].lower, 0.0);
        assert!((parameters.angular[0].upper - 5.0_f32.to_radians()).abs() < 1e-6);
        assert_eq!(parameters.angular[0].stiffness, 10.0);
    }

    #[test]
    fn skirt_chain_fallback_ignores_cross_column_and_authored_springs() {
        let parent = test_pmx_body("裙_0_0", RigidBodyMode::Dynamic);
        let cross_column = test_pmx_body("裙_1_1", RigidBodyMode::Dynamic);
        let same_column = test_pmx_body("裙_1_0", RigidBodyMode::Dynamic);
        let mut cross_parameters = crate::physics::joint_parameters::JointParameters::from_pmx(
            [0.0; 3],
            [0.0; 3],
            [-1.5, 0.0, 0.0],
            [1.5, 0.0, 0.0],
            [0.0; 3],
            [0.0; 3],
        );
        let mut authored_parameters = crate::physics::joint_parameters::JointParameters::from_pmx(
            [0.0; 3],
            [0.0; 3],
            [-1.5, 0.0, 0.0],
            [1.5, 0.0, 0.0],
            [0.0; 3],
            [4.0, 0.0, 0.0],
        );

        assert!(!apply_wide_skirt_chain_fallback(
            &mut cross_parameters,
            &parent,
            &cross_column
        ));
        assert!(!apply_wide_skirt_chain_fallback(
            &mut authored_parameters,
            &parent,
            &same_column
        ));
        assert_eq!(cross_parameters.angular[0].upper, 1.5);
        assert_eq!(authored_parameters.angular[0].stiffness, 4.0);
    }

    #[test]
    fn pmx_joint_disables_zero_springs_in_bullet() {
        let shape = BulletShape::sphere(0.25).expect("应能创建关节测试形状");
        let body_a = test_body(&shape);
        let body_b = test_body(&shape);
        let joint = Joint {
            local_name: "测试关节".to_owned(),
            universal_name: "test_joint".to_owned(),
            type_: JointType::Spring6DOF,
            rigid_body_a_index: 0,
            rigid_body_b_index: 1,
            position: [0.0; 3],
            rotation: [0.0; 3],
            position_min: [0.0; 3],
            position_max: [0.0; 3],
            rotation_min: [-0.2; 3],
            rotation_max: [0.2; 3],
            position_spring: [0.0, 3.0, 0.0],
            rotation_spring: [0.0, 0.0, 5.0],
        };

        let pmx_body_a = test_pmx_body("测试锚点", RigidBodyMode::Static);
        let pmx_body_b = test_pmx_body("测试动态体", RigidBodyMode::Dynamic);
        let data = MmdJointData::from_pmx(
            &joint,
            &body_a,
            &body_b,
            &pmx_body_a,
            &pmx_body_b,
            Mat4::IDENTITY,
            Mat4::IDENTITY,
        );
        let diagnostic = data
            .constraint
            .as_ref()
            .and_then(|constraint| constraint.diagnostic())
            .expect("应能回读 PMX 关节配置");

        assert_eq!(
            diagnostic.spring_enabled,
            [false, true, false, false, false, true]
        );
        assert_eq!(diagnostic.stiffness, [0.0, 3.0, 0.0, 0.0, 0.0, 5.0]);
    }

    #[test]
    fn rebasing_equilibrium_removes_runtime_pose_spring_preload() {
        let shape = BulletShape::sphere(0.25).expect("应能创建关节测试形状");
        let body_a = test_body(&shape);
        let body_b = test_body(&shape);
        let joint = Joint {
            local_name: "运行姿态弹簧基准".to_owned(),
            universal_name: "runtime_equilibrium".to_owned(),
            type_: JointType::Spring6DOF,
            rigid_body_a_index: 0,
            rigid_body_b_index: 1,
            position: [0.0; 3],
            rotation: [0.0; 3],
            position_min: [-1.0; 3],
            position_max: [1.0; 3],
            rotation_min: [-1.0; 3],
            rotation_max: [1.0; 3],
            position_spring: [2.0; 3],
            rotation_spring: [3.0; 3],
        };
        let pmx_body_a = test_pmx_body("测试锚点", RigidBodyMode::Static);
        let pmx_body_b = test_pmx_body("测试动态体", RigidBodyMode::Dynamic);
        let data = MmdJointData::from_pmx(
            &joint,
            &body_a,
            &body_b,
            &pmx_body_a,
            &pmx_body_b,
            Mat4::IDENTITY,
            Mat4::IDENTITY,
        );

        // 模拟 build_physics 之后 initialize 提交新的运行姿态。
        body_b.set_transform(Mat4::from_rotation_translation(
            Quat::from_rotation_y(0.3),
            Vec3::new(0.2, -0.1, 0.15),
        ));
        data.rebase_equilibrium();

        let diagnostic = data
            .constraint
            .as_ref()
            .and_then(|constraint| constraint.diagnostic())
            .expect("应能回读重新基准化后的约束");
        let current = [
            diagnostic.linear_position.x,
            diagnostic.linear_position.y,
            diagnostic.linear_position.z,
            diagnostic.angular_position.x,
            diagnostic.angular_position.y,
            diagnostic.angular_position.z,
        ];
        for (actual, expected) in diagnostic.equilibrium.into_iter().zip(current) {
            assert!((actual - expected).abs() < 1e-5);
        }
    }

    #[test]
    fn joint_anchor_error_tracks_world_space_separation() {
        let body_a = Mat4::from_translation(Vec3::new(1.0, 2.0, 3.0));
        let frame_a = Mat4::from_translation(Vec3::X);
        let body_b = Mat4::from_translation(Vec3::new(2.0, 2.0, 3.0));
        let (same_error, _, _) =
            joint_anchor_position_error(body_a, frame_a, body_b, Mat4::IDENTITY);
        assert!(same_error.abs() < 1e-6);

        let shifted_body_b = Mat4::from_translation(Vec3::new(2.5, 2.0, 3.0));
        let (shifted_error, anchor_a, anchor_b) =
            joint_anchor_position_error(body_a, frame_a, shifted_body_b, Mat4::IDENTITY);
        assert!((shifted_error - 0.5).abs() < 1e-6);
        assert_eq!(anchor_b - anchor_a, Vec3::new(0.5, 0.0, 0.0));
    }
}
