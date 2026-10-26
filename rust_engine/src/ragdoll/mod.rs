//! 死亡布娃娃 —— 实体死亡后用 Bullet3 物理接管身体主骨骼
//!
//! 移植自 ragdoll-lab.html（参数见 ragdoll-params.md）：
//! 16 段解剖学刚体 + 6DOF 关节（限位从 SPH/HINGE_LIMITS 换算），
//! 独立 Bullet3 世界，跑在**模型局部 PMX 单位空间**（与 MMDPhysics 同约定：
//! 骨骼右手 / 物理左手，边界用矩阵级 inv_z 转换）。步进后把刚体位姿回写到
//! 骨骼全局矩阵，蒙皮自动跟随（CPU/GPU 同源），渲染侧零改动。
//!
//! 与 MMD 物理的关系：布娃娃在 tick 中先步进并回写身体骨骼，
//! 随后 MMD 物理（头发/衣物/配饰）照常运行 —— 它们的 FollowBone 刚体
//! 读到的就是布娃娃驱动后的姿势，因此自动挂在新身体姿态上。

pub mod spec;

use glam::{Mat4, Vec3};

use crate::physics::bullet_ffi::{
    BulletConstraint, BulletRigidBody, BulletShape, BulletWorld, RigidBodyInfo,
    BT_CONSTRAINT_STOP_CFM, BT_CONSTRAINT_STOP_ERP,
};
use crate::physics::inv_z;
use crate::skeleton::BoneManager;

use spec::{
    auto_fit_legs, build_spec, count_dominant_vertices, match_mmd_bones, measure_radii,
    UNIT_TO_M, BODY_COUNT,
};

/// 布娃娃参数（JNI RagdollEnable 传入，缺省值 = lab 手感）
#[derive(Debug, Clone)]
pub struct RagdollParams {
    /// 模型局部地面高度（单位；0 = 模型原点即脚底）
    pub ground_local_y: f32,
    /// 死亡初速度（模型局部 Bullet 空间，单位/秒；JNI 层从世界速度换算）
    pub initial_velocity: Vec3,
    /// 碰撞体厚度缩放（lab thickness）
    pub thickness: f32,
    /// 摩擦系数（lab 0.55）
    pub friction: f32,
    /// 恢复系数（lab 0.05~0.08）
    pub restitution: f32,
    /// 线性阻尼（lab 0.06）
    pub linear_damping: f32,
    /// 角阻尼（lab 0.35）
    pub angular_damping: f32,
    /// 重力 Y（模型单位/s²；默认 -9.81 m/s² ÷ 0.09 ≈ -109）
    pub gravity_y: f32,
    /// 关节限位强度（lab jointStrength 1.35；越大越硬）
    pub joint_strength: f32,
    /// 总质量 kg（lab bodyMass 50）
    pub total_mass_kg: f32,
    /// 手臂关节限位乘数（lab armLimit 0.8）
    pub arm_limit: f32,
    /// 一个模型单位对应多少米（= 0.09 × 用户模型缩放；JNI 层从 params[14] 传进来）。
    ///
    /// 只用于把"世界量纲"（速度上限、逃逸深度）换算到模型单位；
    /// 碰撞体尺寸用的是模型自身比例（radius_scale），与用户缩放无关。
    pub unit_to_m: f32,
}

impl Default for RagdollParams {
    fn default() -> Self {
        Self {
            ground_local_y: 0.0,
            initial_velocity: Vec3::ZERO,
            thickness: 1.0,
            friction: 0.55,
            restitution: 0.06,
            linear_damping: 0.06,
            angular_damping: 0.35,
            gravity_y: -9.81 / UNIT_TO_M,
            joint_strength: 1.35,
            total_mass_kg: 50.0,
            arm_limit: 0.8,
            unit_to_m: UNIT_TO_M,
        }
    }
}

// 碰撞组（每具尸体独立 Bullet 世界；语义 = lab 外科手术式过滤：
// 身体段只与地面碰撞，腿段与地面 + 其他腿碰撞，自身其余段间无接触力）
const GROUP_GROUND: i32 = 1;
const GROUP_BODY: i32 = 4;
const GROUP_LEG: i32 = 4;
const MASK_GROUND: i32 = 0xFFFF;
/// 身体段：只与地面碰撞（防关节被自身接触力顶开，lab 实测全开拉伸 96mm）
const MASK_BODY: i32 = GROUP_GROUND;
/// 腿段：与地面 + 其他腿碰撞
const MASK_LEG: i32 = GROUP_GROUND | GROUP_LEG;

/// 单段刚体
///
/// # Drop 顺序安全
/// `bullet_body` 声明在 `shape` 之前（刚体内部引用形状指针）
struct RagdollBody {
    part_index: usize,
    bone_index: usize,
    /// 刚体位姿 → 骨骼全局矩阵的偏移（左手空间）：bone_left = body_pose * offset
    bone_offset: Mat4,
    /// 初始位姿（左手空间，逃逸兜底重置用）
    initial_transform: Mat4,
    #[allow(dead_code)]
    mass_kg: f32,
    is_leg: bool,
    bullet_body: Option<BulletRigidBody>,
    /// 生命周期占位：刚体内部引用形状指针，须比刚体后释放（字段声明顺序保证）
    #[allow(dead_code)]
    shape: Option<BulletShape>,
}

struct RagdollJoint {
    #[allow(dead_code)]
    parent_part: usize,
    #[allow(dead_code)]
    child_part: usize,
    constraint: Option<BulletConstraint>,
}

/// 布娃娃实例
pub struct MmdRagdoll {
    world: BulletWorld,
    bodies: Vec<RagdollBody>,
    /// part_index -> bodies 下标
    body_index_by_part: [Option<usize>; BODY_COUNT],
    joints: Vec<RagdollJoint>,
    ground: Option<BulletRigidBody>,
    #[allow(dead_code)]
    ground_shape: Option<BulletShape>,
    ground_y: f32,
    params: RagdollParams,
    /// 接管的骨骼索引集合（BoneSet 持久跳过用）
    ragdoll_bone_indices: std::collections::HashSet<usize>,
    fps: f32,
    max_substeps: i32,
}

impl MmdRagdoll {
    /// 从当前骨骼姿势构建布娃娃（模型局部空间）
    ///
    /// 调用时机：骨骼全局矩阵已是死亡姿势（本帧 update_node_animation(false) 之后）。
    pub fn new(model: &crate::model::MmdModel, params: RagdollParams) -> Option<Self> {
        let bones = &model.bone_manager;
        let bone_count = bones.bone_count();
        if bone_count == 0 {
            return None;
        }

        // 包围盒高度（模型单位）→ lab 参数缩放比（1.7m 标定）
        let mut min_y = f32::MAX;
        let mut max_y = f32::MIN;
        for v in &model.vertices {
            min_y = min_y.min(v.position.y);
            max_y = max_y.max(v.position.y);
        }
        let bbox_height = if max_y > min_y { max_y - min_y } else { 20.0 };
        let height_m = bbox_height * UNIT_TO_M;
        let radius_scale = (height_m / 1.7).max(0.25);

        // 1. 骨骼匹配（顶点主导权重 + 位置守卫）
        let vertex_counts = count_dominant_vertices(&model.weights, bone_count);
        let matched = match_mmd_bones(bones, &vertex_counts, bbox_height);

        // 2. 实测半径（缺顶点时 fallback 解剖学表，在 build_spec 内处理）
        let measured = if model.vertices.is_empty() {
            [0.0f32; BODY_COUNT]
        } else {
            measure_radii(bones, &matched, &model.vertices, &model.weights, radius_scale)
        };

        // 3. 构建规格 + 腿部重叠收缩
        let mut spec = build_spec(bones, &matched, radius_scale, &measured, params.arm_limit);
        auto_fit_legs(&mut spec, 0.45);

        let built_count = spec.iter().flatten().count();
        if built_count < 4 {
            log::warn!("[Ragdoll] 骨骼匹配不足（{}段），放弃布娃娃", built_count);
            return None;
        }

        // 4. 物理世界
        let mut p = params.clone();
        p.thickness = p.thickness.max(0.1);
        let world = BulletWorld::new(0.0, p.gravity_y, 0.0)?;

        // 5. 地面（运动学薄盒，可整体平移）
        let ground_half_y = 0.25;
        let ground_shape = BulletShape::r#box(160.0, ground_half_y, 160.0);
        let ground = ground_shape.as_ref().and_then(|shape| {
            let info = RigidBodyInfo {
                mass: 0.0,
                linear_damping: 0.0,
                angular_damping: 0.0,
                friction: 0.75,
                restitution: 0.0,
                additional_damping: false,
                is_kinematic: true,
                disable_deactivation: true,
                no_contact_response: false,
                initial_transform: Mat4::from_translation(Vec3::new(
                    0.0,
                    p.ground_local_y - ground_half_y,
                    0.0,
                )),
            };
            BulletRigidBody::new(&info, shape)
        });
        if let Some(ref g) = ground {
            world.add_rigid_body(g, GROUP_GROUND, MASK_GROUND);
        }

        // 6. 刚体（模型单位；body 原点 = 胶囊中心，旋转 = 胶囊方向）
        let mut bodies: Vec<RagdollBody> = Vec::with_capacity(BODY_COUNT);
        let mut body_index_by_part: [Option<usize>; BODY_COUNT] = [None; BODY_COUNT];
        for body_spec in spec.iter().flatten() {
            let shape = if let Some(box_h) = body_spec.box_half_extents {
                BulletShape::r#box(
                    box_h.x * p.thickness,
                    box_h.y * p.thickness,
                    box_h.z * p.thickness,
                )
            } else {
                BulletShape::capsule(
                    body_spec.capsule_radius * p.thickness,
                    (body_spec.capsule_half_height * 2.0).max(0.01),
                )
            };

            // 刚体初始位姿：右手空间组装后 inv_z 到左手（与引擎边界约定一致）
            let bone_pos_rh = body_spec.bone_bind.w_axis.truncate();
            let body0_rh = Mat4::from_rotation_translation(
                body_spec.col_rot,
                bone_pos_rh + body_spec.col_pos,
            );
            let body0 = inv_z(body0_rh);

            // 骨骼回写偏移：bone_left_new = body_pose_left * offset
            let bone_left = inv_z(body_spec.bone_bind);
            let bone_offset = body0.inverse() * bone_left;

            let mass = body_spec.mass_kg * (p.total_mass_kg / 50.0);
            let info = RigidBodyInfo {
                mass,
                linear_damping: p.linear_damping,
                angular_damping: p.angular_damping,
                friction: p.friction,
                restitution: p.restitution,
                additional_damping: false,
                is_kinematic: false,
                disable_deactivation: true,
                no_contact_response: false,
                initial_transform: body0,
            };

            let bullet_body = shape.as_ref().and_then(|s| BulletRigidBody::new(&info, s));

            body_index_by_part[body_spec.part_index] = Some(bodies.len());
            bodies.push(RagdollBody {
                part_index: body_spec.part_index,
                bone_index: body_spec.bone_index,
                bone_offset,
                initial_transform: body0,
                mass_kg: mass,
                is_leg: body_spec.is_leg,
                bullet_body,
                shape,
            });
        }

        // 统一加入世界（所有权已入 Vec，panic 安全）
        for b in &bodies {
            if let Some(ref body) = b.bullet_body {
                let (group, mask) = if b.is_leg {
                    (GROUP_LEG, MASK_LEG)
                } else {
                    (GROUP_BODY, MASK_BODY)
                };
                world.add_rigid_body(body, group, mask);
            }
        }

        // 7. 关节（6DOF；帧 = 各刚体当前位姿逆 × 关节世界位姿）
        let mut joints: Vec<RagdollJoint> = Vec::with_capacity(BODY_COUNT);
        for (ci, child_spec) in spec.iter().enumerate() {
            let Some(child) = child_spec else { continue };
            let (Some(parent_part), Some(js)) = (child.parent, child.joint_spec.as_ref()) else {
                continue;
            };
            let (Some(child_body_idx), Some(parent_body_idx)) = (
                body_index_by_part[ci],
                body_index_by_part[parent_part],
            ) else {
                continue;
            };
            let (Some(child_body), Some(parent_body)) = (
                bodies[child_body_idx].bullet_body.as_ref(),
                bodies[parent_body_idx].bullet_body.as_ref(),
            ) else {
                continue;
            };

            // 关节世界位姿：锚点 = 子骨骼原点，旋转 = 约束帧（右手组装 → inv_z）
            let anchor_rh = child.bone_bind.w_axis.truncate();
            let frame_world = inv_z(Mat4::from_rotation_translation(js.frame_rot, anchor_rh));
            let frame_a = parent_body.get_transform().inverse() * frame_world;
            let frame_b = child_body.get_transform().inverse() * frame_world;

            let constraint =
                BulletConstraint::new_6dof_spring(parent_body, child_body, frame_a, frame_b, true);

            if let Some(ref c) = constraint {
                // 柔软度：ERP/CFM 从 jointStrength 推导（lab 1.35 → ERP≈0.34）
                let erp = (0.25 * p.joint_strength).clamp(0.1, 0.6);
                let cfm = (0.02 / p.joint_strength.max(0.1)).clamp(0.002, 0.05);
                for axis in 0..6 {
                    c.set_param(BT_CONSTRAINT_STOP_ERP, erp, axis);
                    c.set_param(BT_CONSTRAINT_STOP_CFM, cfm, axis);
                }

                // 线性全锁（锚点刚性），角度限位从 spec
                c.set_linear_lower_limit(-0.001, -0.001, -0.001);
                c.set_linear_upper_limit(0.001, 0.001, 0.001);
                c.set_angular_lower_limit(js.ang_lower.x, js.ang_lower.y, js.ang_lower.z);
                c.set_angular_upper_limit(js.ang_upper.x, js.ang_upper.y, js.ang_upper.z);

                // 不启用弹簧（柔软来自限位 ERP/CFM + 阻尼，弹簧会把姿势拽回绑定态）
                for i in 0..6 {
                    c.enable_spring(i, false);
                }
                c.set_equilibrium_point();

                world.add_constraint(c, true);
            }

            joints.push(RagdollJoint {
                parent_part,
                child_part: ci,
                constraint,
            });
        }

        // 8. 接管骨骼集合 = 各段驱动骨
        let mut ragdoll_bone_indices = std::collections::HashSet::new();
        for b in &bodies {
            ragdoll_bone_indices.insert(b.bone_index);
        }

        // 9. 初速度
        for b in &bodies {
            if let Some(ref body) = b.bullet_body {
                let v = p.initial_velocity;
                body.set_linear_velocity(v.x, v.y, v.z);
            }
        }

        log::info!(
            "[Ragdoll] 构建: {} 刚体 / {} 关节 / 身高 {:.1}u ({:.2}m) / 重力 {:.0}",
            bodies.len(),
            joints.len(),
            bbox_height,
            height_m,
            p.gravity_y
        );

        Some(Self {
            world,
            bodies,
            body_index_by_part,
            joints,
            ground,
            ground_shape,
            ground_y: p.ground_local_y,
            params: p,
            ragdoll_bone_indices,
            fps: 60.0,
            max_substeps: 5,
        })
    }

    /// 步进 + 回写骨骼（每帧调用，在 update_node_animation(false) 之前）
    pub fn step(&mut self, bones: &mut BoneManager, delta_time: f32) {
        let dt = delta_time.clamp(0.001, 0.1);
        let fixed_dt = 1.0 / self.fps;
        self.world.step(dt, self.max_substeps, fixed_dt);

        // 速度钳制（防爆炸，同 MMDPhysics 惯例）
        // 上限是"世界 20 m/s"，换算到模型单位要除以实际单位换算
        // （= 0.09 × 用户模型缩放，JNI 层从 params[14] 传进来）。
        let unit_to_m = self.params.unit_to_m;
        let max_lin = 20.0 / unit_to_m;
        let max_ang = 25.0;
        let (max_lin_sq, max_ang_sq) = (max_lin * max_lin, max_ang * max_ang);
        for b in &self.bodies {
            if let Some(ref body) = b.bullet_body {
                let v = body.get_linear_velocity();
                let sq = v.length_squared();
                if sq > max_lin_sq {
                    let c = v * (max_lin / sq.sqrt());
                    body.set_linear_velocity(c.x, c.y, c.z);
                }
                let w = body.get_angular_velocity();
                let sq = w.length_squared();
                if sq > max_ang_sq {
                    let c = w * (max_ang / sq.sqrt());
                    body.set_angular_velocity(c.x, c.y, c.z);
                }
            }
        }

        // 逃逸兜底（lab rescueEscaped：y<floor 或 NaN → 重置到初始位姿）
        // 深度同样是"世界米"，换算到模型单位要用实际单位换算。
        let rescue_y = self.ground_y - 1.5 / unit_to_m * 10.0;
        for b in &self.bodies {
            let Some(ref body) = b.bullet_body else { continue };
            let t = body.get_transform();
            let pos = t.w_axis.truncate();
            if !pos.is_finite() || pos.y < rescue_y || pos.x.abs() > 700.0 || pos.z.abs() > 700.0
            {
                body.set_transform(b.initial_transform);
                body.set_linear_velocity(0.0, 0.0, 0.0);
                body.set_angular_velocity(0.0, 0.0, 0.0);
            }
        }

        // 回写：刚体 → 骨骼全局矩阵（父段先于子段，BODY_PARTS 顺序保证）
        for b in &self.bodies {
            if let Some(ref body) = b.bullet_body {
                let bone_left = body.get_transform() * b.bone_offset;
                bones.set_global_transform_physics(b.bone_index, inv_z(bone_left));
            }
        }
        // 从属骨（D骨/足先EX/手指/饰品）跟随新姿势
        bones.update_non_physics_children(&self.ragdoll_bone_indices);
    }

    /// 更新地面高度（模型局部单位）
    pub fn set_ground_y(&mut self, y: f32) {
        if (y - self.ground_y).abs() < 1e-4 {
            return;
        }
        self.ground_y = y;
        if let Some(ref g) = self.ground {
            let mut t = g.get_transform();
            t.w_axis.y = y - 0.25;
            g.set_transform(t);
            // 关键：强制刷新 Bullet broadphase/contact cache，并唤醒尸体刚体，
            // 防止地面下移后尸体偶发仍被旧接触对钉在原高度。
            self.world.refresh_rigid_body(g);
        }
    }

    /// 施加速度增量（模型局部 Bullet 空间，单位/秒）
    pub fn apply_velocity_delta(&mut self, delta: Vec3, upper_body_only: bool) {
        for b in &self.bodies {
            if upper_body_only && b.part_index > spec::UPPER_BODY_MAX_PART {
                continue;
            }
            if let Some(ref body) = b.bullet_body {
                let v = body.get_linear_velocity();
                body.set_linear_velocity(v.x + delta.x, v.y + delta.y, v.z + delta.z);
            }
        }
    }

    pub fn params(&self) -> &RagdollParams {
        &self.params
    }

    /// 接管骨骼索引集合（BoneSet 持久跳过用）
    pub fn ragdoll_bone_indices(&self) -> &std::collections::HashSet<usize> {
        &self.ragdoll_bone_indices
    }

    /// 调试信息（JSON）
    pub fn debug_info(&self) -> String {
        let bodies: Vec<String> = self
            .bodies
            .iter()
            .map(|b| {
                let pos = b
                    .bullet_body
                    .as_ref()
                    .map(|bb| bb.get_position())
                    .unwrap_or(Vec3::ZERO);
                format!(
                    r#"{{"part": {}, "bone": {}, "mass": {:.2}, "pos": [{:.2},{:.2},{:.2}]}}"#,
                    b.part_index, b.bone_index, b.mass_kg, pos.x, pos.y, pos.z
                )
            })
            .collect();
        format!(
            r#"{{"active": true, "bodies": {}, "joints": {}, "ground_y": {:.2}, "detail": [{}]}}"#,
            self.bodies.len(),
            self.joints.len(),
            self.ground_y,
            bodies.join(",")
        )
    }
}

impl Drop for MmdRagdoll {
    fn drop(&mut self) {
        // Bullet3 要求：先从世界移除，再释放对象（与 MMDPhysics::drop 同约定）
        for j in &self.joints {
            if let Some(ref c) = j.constraint {
                self.world.remove_constraint(c);
            }
        }
        for b in &self.bodies {
            if let Some(ref body) = b.bullet_body {
                self.world.remove_rigid_body(body);
            }
        }
        if let Some(ref g) = self.ground {
            self.world.remove_rigid_body(g);
        }
    }
}
