//! MMD 物理世界管理器
//!
//! 移植自 babylon-mmd，使用 Bullet3 引擎。
//! 实现 commitBodyStates / syncBodies / stepSimulation / syncBones 全套流程。

use std::collections::{HashMap, HashSet};

use glam::{Mat3, Mat4, Vec3};

use mmd::pmx::joint::Joint as PmxJoint;
use mmd::pmx::rigid_body::RigidBody as PmxRigidBody;

use super::bullet_ffi::{self, BulletWorld};
use super::collision_topology::{
    build_filter_plan, CollisionAabb, CollisionBody, CollisionFilterPlan, CollisionStabilityMode,
};
use super::config::{get_config, PhysicsConfig};
use super::kinematic_target_filter::KinematicTargetFilter;
use super::mmd_joint::MmdJointData;
use super::mmd_rigid_body::{
    body_collider_scale_flags, effective_collision_shape_size_with_static_scale,
    is_skirt_or_lower_garment, is_tail_dynamic_part, MmdRigidBodyData, PhysicsMode,
    STATIC_COLLISION_SHAPE_SCALE,
};
use super::physics_diagnostics::{model_topology_signature, ContactWindow, JointLimitPeak};

/// MMD 物理世界管理器（Bullet3 引擎）
///
/// 移植自 babylon-mmd，管理 Bullet3 世界、刚体、关节。
/// 物理在模型局部空间运行（Minecraft 仅绕 Y 轴旋转，重力方向不变）。
/// 流程：build_physics → 每帧 [sync_bodies → stepSimulation → sync_bones]
pub struct MMDPhysics {
    /// MMD 关节数据列表（Drop 顺序：约束先于刚体先于世界）
    joints: Vec<MmdJointData>,
    /// MMD 刚体数据列表
    pub rigid_bodies: Vec<MmdRigidBodyData>,
    /// Bullet3 物理世界
    world: BulletWorld,
    /// 物理 FPS
    fps: f32,
    /// 最大子步数
    max_substep_count: i32,

    // --- 预分配缓冲区（避免每帧堆分配） ---
    /// 动态刚体关联的骨骼索引集合（构建时计算一次）
    dynamic_bone_indices: HashSet<usize>,
    /// 动态骨骼变换结果缓冲区（复用内存）
    dynamic_bone_buf: Vec<(usize, Mat4)>,
    /// 调试时缓存本帧骨骼所对应的刚体目标位置。
    debug_body_target_positions: Vec<Option<Vec3>>,
    /// 上一帧提交给 FollowBone 刚体的目标矩阵，用于确认静止姿态是否仍在驱动运动学速度。
    debug_kinematic_target_transforms: Vec<Option<Mat4>>,
    /// 抑制 FollowBone 静止姿态的亚毫米级逐帧漂移，避免 Bullet 将其放大为运动学速度。
    kinematic_target_filter: KinematicTargetFilter,
    /// Bullet 刚体地址到模型刚体索引的映射，仅用于接触诊断。
    debug_body_pointer_indices: HashMap<usize, usize>,

    /// 上一帧模型世界位置（用于计算参考系速度）
    prev_model_position: Option<Vec3>,
    /// 调试遥测按模拟时间聚合，避免开启日志后逐帧刷屏。
    debug_telemetry: PhysicsDebugTelemetry,
    /// 等待 Java Log4j 消费的诊断消息；每个聚合窗口只构造一次。
    pending_debug_diagnostic: Option<String>,
    /// 当前 Bullet 世界构建时实际采用的配置，避免日志读取到尚未重建的新配置。
    active_debug_config: ActivePhysicsDebugConfig,
    /// 构建阶段使用的拓扑稳定模式。
    collision_stability_mode: CollisionStabilityMode,
    /// 本次构建的动态自碰撞过滤计划统计。
    collision_filter_plan: CollisionFilterPlan,
    /// Bullet 回读确认已禁用碰撞的计划对数量。
    collision_filter_applied_pairs: usize,
    /// Bullet 回读仍允许碰撞的计划对数量。
    collision_filter_rejected_pairs: usize,
    /// 当前模型刚体与关节拓扑的稳定签名。
    model_topology_signature: String,
}

/// 供命令行诊断工具读取的关节瞬时状态。
///
/// 内容完全来自当前 Bullet 世界；构造快照不会写入刚体或约束参数。
#[derive(Debug, Clone)]
pub struct PhysicsJointSnapshot {
    pub name: String,
    pub body_a_name: String,
    pub body_b_name: String,
    pub anchor_error: f32,
    pub anchor_a: Vec3,
    pub anchor_b: Vec3,
    pub diagnostic: bullet_ffi::ConstraintDiagnostic,
}

impl MMDPhysics {
    /// 创建新的物理世界（C++ OOM 时返回 None）
    pub fn new() -> Option<Self> {
        let config = get_config();
        let world = BulletWorld::new(0.0, config.gravity_y, 0.0)?;

        if config.debug_log {
            log::info!(
                "[Bullet3] 物理世界创建: FPS={}, 重力Y={}",
                config.physics_fps,
                config.gravity_y
            );
        }

        Some(Self {
            joints: Vec::new(),
            rigid_bodies: Vec::new(),
            world,
            fps: config.physics_fps,
            max_substep_count: config.max_substep_count,
            dynamic_bone_indices: HashSet::new(),
            dynamic_bone_buf: Vec::new(),
            debug_body_target_positions: Vec::new(),
            debug_kinematic_target_transforms: Vec::new(),
            kinematic_target_filter: KinematicTargetFilter::default(),
            debug_body_pointer_indices: HashMap::new(),
            prev_model_position: None,
            debug_telemetry: PhysicsDebugTelemetry::default(),
            pending_debug_diagnostic: None,
            active_debug_config: ActivePhysicsDebugConfig::from_config(&config),
            collision_stability_mode: config.collision_stability_mode,
            collision_filter_plan: CollisionFilterPlan::default(),
            collision_filter_applied_pairs: 0,
            collision_filter_rejected_pairs: 0,
            model_topology_signature: "fnv1a64:UNBUILT".to_owned(),
        })
    }

    /// 构建物理系统（移植自 babylon-mmd buildPhysics）
    ///
    /// 一次性创建所有刚体和关节，并添加到 Bullet3 世界。
    /// 先将对象存入 Vec（所有权转移），再添加到世界，保证 panic 安全。
    pub fn build_physics(
        &mut self,
        pmx_rigid_bodies: &[PmxRigidBody],
        pmx_joints: &[PmxJoint],
        bone_transforms: &[Mat4],
    ) {
        let config = get_config();
        self.active_debug_config = ActivePhysicsDebugConfig::from_config(&config);
        self.collision_stability_mode = config.collision_stability_mode;
        self.model_topology_signature = model_topology_signature(pmx_rigid_bodies, pmx_joints);

        // 按整个模型的碰撞用途分类，避免把动态链的静态关节锚点误当成人体碰撞壳。
        let body_collider_flags = body_collider_scale_flags(pmx_rigid_bodies, pmx_joints);

        // 预分配容量
        self.rigid_bodies.reserve(pmx_rigid_bodies.len());

        // 第一步：创建所有刚体并存入 Vec（还未加入世界）
        for (body_index, pmx_rb) in pmx_rigid_bodies.iter().enumerate() {
            let bone_transform =
                if pmx_rb.bone_index >= 0 && (pmx_rb.bone_index as usize) < bone_transforms.len() {
                    Some(bone_transforms[pmx_rb.bone_index as usize])
                } else {
                    None
                };

            let shape_size = effective_collision_shape_size_with_static_scale(
                pmx_rb,
                STATIC_COLLISION_SHAPE_SCALE,
                body_collider_flags[body_index],
            );
            let mut rb_data = MmdRigidBodyData::from_pmx(pmx_rb, bone_transform, shape_size);
            let shape = MmdRigidBodyData::create_shape(pmx_rb, shape_size);
            let body = shape
                .as_ref()
                .and_then(|s| rb_data.create_rigid_body(pmx_rb, s));

            if shape.is_none() || body.is_none() {
                log::warn!("[Bullet3] 刚体 '{}' 创建失败，跳过", rb_data.name);
            }

            // 先转移所有权到 rb_data，再 push 到 Vec
            rb_data.bullet_body = body;
            rb_data.bullet_shape = shape;
            self.rigid_bodies.push(rb_data);
        }

        // 刚体入场前安装过滤规则，避免 broadphase 按旧规则缓存碰撞对。
        self.world.set_kinematic_filter(config.kinematic_filter);

        // 第二步：统一将已存储的刚体添加到世界
        // 此时所有权已在 self.rigid_bodies 中，panic 时 Drop 链会正确清理
        for rb_data in &self.rigid_bodies {
            if let Some(ref body) = rb_data.bullet_body {
                let group = 1i32 << (rb_data.group.min(15) as i32);
                // 临时兼容模式只关闭接触碰撞，不影响重力、关节和骨骼回写。
                let mask =
                    effective_collision_mask(config.collision_enabled, rb_data.collision_mask);
                self.world.add_rigid_body(body, group, mask);
            }
        }

        self.collision_filter_plan = CollisionFilterPlan::default();
        self.collision_filter_applied_pairs = 0;
        self.collision_filter_rejected_pairs = 0;
        if config.collision_enabled && config.joints_enabled {
            // 只构建同组动态子图，保留衣物与跟骨身体刚体之间的碰撞。
            let topology_bodies: Vec<CollisionBody> = self
                .rigid_bodies
                .iter()
                .zip(pmx_rigid_bodies.iter())
                .map(|(body, pmx_body)| CollisionBody {
                    group: body.group,
                    collision_mask: body.collision_mask,
                    is_dynamic: body.physics_mode != PhysicsMode::FollowBone,
                    is_skirt: is_skirt_or_lower_garment(pmx_body),
                    is_tail: is_tail_dynamic_part(pmx_body),
                    is_active: body.bullet_body.is_some(),
                    initial_aabb: body.collision_half_extents.and_then(|extents| {
                        CollisionAabb::from_transform(body.initial_transform, extents)
                    }),
                })
                .collect();
            let topology_edges: Vec<(i32, i32)> = pmx_joints
                .iter()
                .map(|joint| (joint.rigid_body_a_index, joint.rigid_body_b_index))
                .collect();
            self.collision_filter_plan = build_filter_plan(
                &topology_bodies,
                &topology_edges,
                self.collision_stability_mode,
            );
            // 仅屏蔽稳定计划中的同组动态邻域，保留动态与跟骨身体的碰撞。
            for &(a_index, b_index) in &self.collision_filter_plan.pairs {
                let Some(body_a) = self
                    .rigid_bodies
                    .get(a_index)
                    .and_then(|body| body.bullet_body.as_ref())
                else {
                    self.collision_filter_rejected_pairs += 1;
                    log::warn!(
                        "[Bullet3] 碰撞过滤对无效: A 索引 {} 不存在可用刚体，B 索引 {}",
                        a_index,
                        b_index
                    );
                    continue;
                };
                let Some(body_b) = self
                    .rigid_bodies
                    .get(b_index)
                    .and_then(|body| body.bullet_body.as_ref())
                else {
                    self.collision_filter_rejected_pairs += 1;
                    log::warn!(
                        "[Bullet3] 碰撞过滤对无效: A 索引 {}，B 索引 {} 不存在可用刚体",
                        a_index,
                        b_index
                    );
                    continue;
                };

                body_a.set_ignore_collision_check(body_b, true);
                if !body_a.check_collide_with(body_b) && !body_b.check_collide_with(body_a) {
                    self.collision_filter_applied_pairs += 1;
                } else {
                    self.collision_filter_rejected_pairs += 1;
                    log::warn!(
                        "[Bullet3] 碰撞过滤回读失败: A[{}]='{}' B[{}]='{}'",
                        a_index,
                        self.rigid_bodies[a_index].name,
                        b_index,
                        self.rigid_bodies[b_index].name
                    );
                }
            }
        }

        // 第三步：创建关节并存入 Vec
        if config.joints_enabled {
            self.joints.reserve(pmx_joints.len());

            for pmx_joint in pmx_joints {
                let rb_a_idx = pmx_joint.rigid_body_a_index as usize;
                let rb_b_idx = pmx_joint.rigid_body_b_index as usize;

                if rb_a_idx >= self.rigid_bodies.len()
                    || rb_b_idx >= self.rigid_bodies.len()
                    || rb_a_idx == rb_b_idx
                {
                    continue;
                }

                let (rb_a_body, rb_b_body, rb_a_init, rb_b_init) = {
                    let rb_a = &self.rigid_bodies[rb_a_idx];
                    let rb_b = &self.rigid_bodies[rb_b_idx];
                    match (&rb_a.bullet_body, &rb_b.bullet_body) {
                        (Some(a), Some(b)) => {
                            (a, b, rb_a.initial_transform, rb_b.initial_transform)
                        }
                        _ => continue,
                    }
                };

                let joint_data = MmdJointData::from_pmx(
                    pmx_joint,
                    rb_a_body,
                    rb_b_body,
                    &pmx_rigid_bodies[rb_a_idx],
                    &pmx_rigid_bodies[rb_b_idx],
                    rb_a_init,
                    rb_b_init,
                );

                // 先存入 Vec，再添加到世界
                self.joints.push(joint_data);
            }

            // 统一添加约束到世界
            for joint_data in &self.joints {
                if let Some(ref constraint) = joint_data.constraint {
                    self.world.add_constraint(constraint, true);
                }
            }
        }

        // 第四步：预计算动态骨骼索引集合（一次性，避免每帧重算）
        self.dynamic_bone_indices = self
            .rigid_bodies
            .iter()
            .filter(|rb| rb.physics_mode != PhysicsMode::FollowBone && rb.bone_index >= 0)
            .map(|rb| rb.bone_index as usize)
            .collect();

        // 预分配动态骨骼缓冲区
        self.dynamic_bone_buf
            .reserve(self.dynamic_bone_indices.len());
        self.debug_body_target_positions
            .resize(self.rigid_bodies.len(), None);
        self.debug_kinematic_target_transforms
            .resize(self.rigid_bodies.len(), None);
        self.kinematic_target_filter.resize(self.rigid_bodies.len());
        self.debug_body_pointer_indices = self
            .rigid_bodies
            .iter()
            .enumerate()
            .filter_map(|(index, body)| {
                body.bullet_body
                    .as_ref()
                    .map(|bullet| (bullet.as_ptr() as usize, index))
            })
            .collect();

        let kinematic_count = self
            .rigid_bodies
            .iter()
            .filter(|rb| rb.physics_mode == PhysicsMode::FollowBone)
            .count();
        let dynamic_count = self
            .rigid_bodies
            .iter()
            .filter(|rb| rb.physics_mode == PhysicsMode::Physics)
            .count();
        let dynamic_bone_count = self
            .rigid_bodies
            .iter()
            .filter(|rb| rb.physics_mode == PhysicsMode::PhysicsWithBone)
            .count();

        log::info!(
            "Bullet3 物理构建完成: {} 刚体 ({}跟骨 + {}物理 + {}物理跟骨), {} 关节",
            self.rigid_bodies.len(),
            kinematic_count,
            dynamic_count,
            dynamic_bone_count,
            self.joints.len()
        );
    }

    /// 同步运动学刚体位置（babylon-mmd syncBodies）
    ///
    /// 在每帧物理步进前调用。将 FollowBone 模式的刚体位置
    /// 同步为骨骼变换推导的物理空间（左手）变换。
    pub fn sync_bodies(&mut self, bone_transforms: &[Mat4]) {
        for (body_index, rb_data) in self.rigid_bodies.iter().enumerate() {
            if rb_data.physics_mode != PhysicsMode::FollowBone {
                continue;
            }
            let bone_idx = rb_data.bone_index;
            if bone_idx < 0 || (bone_idx as usize) >= bone_transforms.len() {
                continue;
            }
            if let Some(ref body) = rb_data.bullet_body {
                // 骨骼(右手) → inv_z → 左手，再计算刚体位置
                let bone_left = super::inv_z(bone_transforms[bone_idx as usize]);
                let body_matrix = rb_data.compute_body_matrix(bone_left);
                let filtered = self.kinematic_target_filter.filter(body_index, body_matrix);
                if filtered.suppressed && get_config().debug_log {
                    self.debug_telemetry.observe_kinematic_suppression(
                        filtered.translation_delta,
                        filtered.rotation_delta,
                    );
                }
                if filtered.suppressed {
                    // 精确跟随当前姿态，但不要让静止噪声被 Bullet 差分成周期性速度脉冲。
                    body.set_transform(filtered.transform);
                    body.set_linear_velocity(0.0, 0.0, 0.0);
                    body.set_angular_velocity(0.0, 0.0, 0.0);
                } else {
                    // 明显动作保留上一物理姿态，让 Bullet 生成真实运动学碰撞速度。
                    body.set_kinematic_target(filtered.transform);
                }
            }
        }
    }

    /// 缓存当前骨骼姿态对应的刚体目标位置，仅供碰撞异常诊断使用。
    fn update_debug_body_targets(&mut self, bone_transforms: &[Mat4], delta_time: f32) {
        for (index, rb_data) in self.rigid_bodies.iter().enumerate() {
            let bone_index = rb_data.bone_index;
            let target = if bone_index >= 0 && (bone_index as usize) < bone_transforms.len() {
                let bone_left = super::inv_z(bone_transforms[bone_index as usize]);
                Some(rb_data.compute_body_matrix(bone_left))
            } else {
                None
            };
            self.debug_body_target_positions[index] = target.map(|matrix| matrix.w_axis.truncate());

            // 只有 FollowBone 的目标会被 Bullet 解释为运动学碰撞体的驱动输入。
            if rb_data.physics_mode == PhysicsMode::FollowBone {
                if let (Some(previous), Some(current)) =
                    (self.debug_kinematic_target_transforms[index], target)
                {
                    self.debug_telemetry
                        .observe_kinematic_target(index, previous, current, delta_time);
                }
                self.debug_kinematic_target_transforms[index] = target;
            }
        }
    }

    /// 同步运动学刚体并传递模型移动速度（实现惯性）
    ///
    /// 原理：物理在模型局部空间运行，角色世界移动对物理不可见。
    /// 第一步：同步 FollowBone 刚体到骨骼位置。
    /// 第二步：计算模型世界速度，转换到物理空间后取反，
    ///        对动态刚体施加 F = m * (-v_local) / dt，
    ///        产生与移动方向相反的惯性力（头发/裙子自然后拽）。
    pub fn sync_bodies_with_model_velocity(
        &mut self,
        bone_transforms: &[Mat4],
        delta_time: f32,
        model_transform: Mat4,
    ) {
        let config = get_config();
        if !delta_time.is_finite() || delta_time <= 0.0 {
            self.reset_motion_history();
            self.sync_bodies(bone_transforms);
            return;
        }

        let dt = delta_time.max(0.001);
        let curr_pos = model_transform.w_axis.truncate();

        if !curr_pos.is_finite() {
            self.reset_motion_history();
            self.sync_bodies(bone_transforms);
            return;
        }

        // 计算模型世界速度
        let model_velocity = if let Some(prev_pos) = self.prev_model_position {
            (curr_pos - prev_pos) / dt
        } else {
            Vec3::ZERO
        };
        self.prev_model_position = Some(curr_pos);

        // 第一步：同步运动学刚体位置
        if config.debug_log {
            self.update_debug_body_targets(bone_transforms, delta_time);
        }
        self.sync_bodies(bone_transforms);

        // 第二步：给动态刚体施加惯性力
        if config.inertia_strength > 0.0 && model_velocity.length_squared() > 1e-8 {
            // 钳制世界速度（20 blocks/s 覆盖疾跑和速度药水，超出视为传送）
            let speed_sq = model_velocity.length_squared();
            let max_speed = 20.0_f32;
            let world_vel = if speed_sq > max_speed * max_speed {
                model_velocity * (max_speed / speed_sq.sqrt())
            } else {
                model_velocity
            };

            // 世界速度 → 模型局部空间（R^T * v_world）
            let rot_inv = Mat3::from_mat4(model_transform).transpose();
            let local_vel = rot_inv * world_vel;

            // 模型局部空间惯性（Z-flip 坐标系中 Z 方向需取反）
            let inertia_vel = Vec3::new(
                -local_vel.x * config.inertia_strength,
                -local_vel.y * config.inertia_strength,
                local_vel.z * config.inertia_strength,
            );

            // 最大加速度 = max_linear_velocity * physics_fps
            // 确保单个物理子步内速度增量不超过 max_linear_velocity
            let max_accel = config.max_linear_velocity * self.fps;

            // F = m * v / dt，再钳制力大小防止衣服拉伸。
            for rb_data in &self.rigid_bodies {
                if rb_data.physics_mode == PhysicsMode::FollowBone {
                    continue;
                }
                if let Some(ref body) = rb_data.bullet_body {
                    let mass = body.get_mass();
                    if mass > 0.0 {
                        let mut force = inertia_vel * mass / dt;
                        // 钳制力：限制加速度上限
                        let max_force = max_accel * mass;
                        let force_sq = force.length_squared();
                        if force_sq > max_force * max_force {
                            force *= max_force / force_sq.sqrt();
                        }
                        body.apply_central_force(force.x, force.y, force.z);
                    }
                }
            }
        }
    }

    /// 步进物理模拟（Bullet3 stepSimulation）+ 速度钳制
    ///
    /// Bullet3 没有内置全局速度限制，需在每步后手动截断超速刚体，
    /// 防止卡顿帧或极端力导致的物理爆炸。
    pub fn step_simulation(&mut self, delta_time: f32) {
        if !delta_time.is_finite() || delta_time <= 0.0 {
            if get_config().debug_log {
                self.debug_telemetry.invalid_step_count += 1;
            }
            return;
        }
        let fixed_dt = 1.0 / self.fps;
        self.world
            .step(delta_time, self.max_substep_count, fixed_dt);

        // 速度钳制
        let config = get_config();
        let max_lin = config.max_linear_velocity;
        let max_ang = config.max_angular_velocity;
        let max_lin_sq = max_lin * max_lin;
        let max_ang_sq = max_ang * max_ang;

        for (index, rb_data) in self.rigid_bodies.iter().enumerate() {
            if let Some(ref body) = rb_data.bullet_body {
                let lin_vel = body.get_linear_velocity();
                let ang_vel = body.get_angular_velocity();
                if config.debug_log && rb_data.physics_mode == PhysicsMode::FollowBone {
                    self.debug_telemetry
                        .observe_kinematic_body(index, lin_vel, ang_vel);
                    if is_skirt_body_name(&rb_data.name) {
                        self.debug_telemetry
                            .observe_skirt_kinematic_body(index, lin_vel, ang_vel);
                    }
                }
                if rb_data.physics_mode == PhysicsMode::FollowBone {
                    continue;
                }
                let lin_sq = lin_vel.length_squared();
                if config.debug_log {
                    // 峰值必须与同一次求解后的速度、位置和目标偏差配套记录。
                    let position = body.get_transform().w_axis.truncate();
                    self.debug_telemetry.observe_body(
                        index,
                        lin_vel,
                        ang_vel,
                        position,
                        self.debug_body_target_positions[index],
                    );
                }
                if lin_sq > max_lin_sq {
                    let clamped = lin_vel * (max_lin / lin_sq.sqrt());
                    body.set_linear_velocity(clamped.x, clamped.y, clamped.z);
                }

                let ang_sq = ang_vel.length_squared();
                if ang_sq > max_ang_sq {
                    let clamped = ang_vel * (max_ang / ang_sq.sqrt());
                    body.set_angular_velocity(clamped.x, clamped.y, clamped.z);
                }
            }
        }

        if config.debug_log {
            // 接触与关节数据均在求解后读取，不参与 Bullet 状态更新。
            for manifold in self.world.contact_manifolds() {
                let (Some(&body_a), Some(&body_b)) = (
                    self.debug_body_pointer_indices.get(&manifold.body_a),
                    self.debug_body_pointer_indices.get(&manifold.body_b),
                ) else {
                    continue;
                };
                self.debug_telemetry
                    .contacts
                    .observe(manifold, body_a, body_b);
            }
            for (joint_index, joint) in self.joints.iter().enumerate() {
                if let Some(diagnostic) = joint
                    .constraint
                    .as_ref()
                    .and_then(|constraint| constraint.diagnostic())
                {
                    self.debug_telemetry
                        .joint_limit_peak
                        .observe(joint_index, diagnostic);
                }
            }

            self.debug_telemetry.elapsed += delta_time;
            self.debug_telemetry.step_count += 1;
            self.debug_telemetry.max_delta_time =
                self.debug_telemetry.max_delta_time.max(delta_time);
            if self.debug_telemetry.elapsed >= 1.0 {
                self.pending_debug_diagnostic = Some(self.build_debug_diagnostic());
                self.debug_telemetry.reset_window();
            }
        }
    }

    /// 将动态刚体变换同步回骨骼（babylon-mmd syncBones）
    ///
    /// 从 Bullet3 读取左手空间变换，通过 inv_z 转回右手空间写入骨骼。
    pub fn sync_bones(&self, bone_transforms: &mut [Mat4]) {
        for rb_data in &self.rigid_bodies {
            if rb_data.physics_mode == PhysicsMode::FollowBone {
                continue;
            }
            let bone_idx = rb_data.bone_index;
            if bone_idx < 0 || (bone_idx as usize) >= bone_transforms.len() {
                continue;
            }
            if let Some(ref body) = rb_data.bullet_body {
                let rb_matrix = body.get_transform();
                let new_bone_left = match rb_data.physics_mode {
                    PhysicsMode::Physics => rb_data.compute_bone_matrix(rb_matrix),
                    PhysicsMode::PhysicsWithBone => {
                        // 骨骼位置(右手) → inv_z → 左手
                        let bone_right = bone_transforms[bone_idx as usize];
                        let pos_left = Vec3::new(
                            bone_right.w_axis.x,
                            bone_right.w_axis.y,
                            -bone_right.w_axis.z,
                        );
                        rb_data.compute_bone_matrix_rotation_only(rb_matrix, pos_left)
                    }
                    PhysicsMode::FollowBone => unreachable!(),
                };
                // 左手 → inv_z → 右手
                bone_transforms[bone_idx as usize] = super::inv_z(new_bone_left);
            }
        }
    }

    /// 初始化物理（commitBodyStates 的初始版本）
    ///
    /// 在骨骼初始姿态确定后调用，将所有刚体设置到正确的初始位置（左手空间）。
    pub fn initialize(&mut self, bone_transforms: &[Mat4]) {
        self.reset_motion_history();
        for (body_index, rb_data) in self.rigid_bodies.iter().enumerate() {
            let bone_idx = rb_data.bone_index;
            if bone_idx < 0 || (bone_idx as usize) >= bone_transforms.len() {
                continue;
            }
            if let Some(ref body) = rb_data.bullet_body {
                // 骨骼(右手) → inv_z → 左手
                let bone_left = super::inv_z(bone_transforms[bone_idx as usize]);
                let body_matrix = rb_data.compute_body_matrix(bone_left);
                body.set_transform(body_matrix);
                if rb_data.physics_mode == PhysicsMode::FollowBone {
                    self.kinematic_target_filter.seed(body_index, body_matrix);
                }
                body.set_linear_velocity(0.0, 0.0, 0.0);
                body.set_angular_velocity(0.0, 0.0, 0.0);
                body.clear_forces();
            }
        }

        // 所有刚体到达同一运行姿态后再记录弹簧零点。约束在 build_physics 阶段
        // 创建，若沿用当时的 equilibrium，首步会把当前无预载姿态拉回旧基准。
        for joint in &self.joints {
            joint.rebase_equilibrium();
        }

        if get_config().debug_log {
            super::initialization_diagnostics::log_initialized_bodies(
                &self.rigid_bodies,
                bone_transforms,
            );
            super::initialization_diagnostics::log_joint_anchor_baseline(
                &self.rigid_bodies,
                &self.joints,
            );
            super::initialization_diagnostics::log_joint_constraints_pre_step(
                &self.rigid_bodies,
                &self.joints,
            );
            super::initialization_diagnostics::log_initial_contacts_pre_step(
                &self.world,
                &self.rigid_bodies,
                &self.debug_body_pointer_indices,
            );
        }
    }

    /// 跳帧后恢复连续模拟状态。
    ///
    /// 大时间步并不等同于动画或模型被重置。若把每个动态刚体重新从已回写的
    /// 骨骼推导，会让同一关节两端落在不同锚点，随后被约束强行拉开。这里只把
    /// 跟骨刚体提交到当前动画姿态，并清除动态体速度与累积力，保留关节拓扑。
    pub fn recover_after_large_delta(&mut self, bone_transforms: &[Mat4]) {
        self.reset_motion_history();
        for (body_index, rb_data) in self.rigid_bodies.iter().enumerate() {
            let Some(ref body) = rb_data.bullet_body else {
                continue;
            };

            if rb_data.physics_mode == PhysicsMode::FollowBone {
                let bone_idx = rb_data.bone_index;
                if bone_idx < 0 || (bone_idx as usize) >= bone_transforms.len() {
                    continue;
                }
                // 直接提交避免 Bullet 将暂停期间的位移解释为运动学碰撞速度。
                let bone_left = super::inv_z(bone_transforms[bone_idx as usize]);
                let body_matrix = rb_data.compute_body_matrix(bone_left);
                body.set_transform(body_matrix);
                self.kinematic_target_filter.seed(body_index, body_matrix);
            } else {
                body.set_linear_velocity(0.0, 0.0, 0.0);
                body.set_angular_velocity(0.0, 0.0, 0.0);
                body.clear_forces();
            }
        }
    }

    /// 重置物理系统
    pub fn reset(&mut self, bone_transforms: &[Mat4]) {
        self.initialize(bone_transforms);
    }

    /// 清除模型参考系运动历史，防止暂停、传送或重置后产生虚假惯性。
    pub fn reset_motion_history(&mut self) {
        self.prev_model_position = None;
        // 重同步后的首帧没有连续目标历史，避免诊断把姿态切换误报为静止跳变。
        self.debug_kinematic_target_transforms.fill(None);
        self.debug_telemetry = PhysicsDebugTelemetry::default();
        self.pending_debug_diagnostic = None;
    }

    /// 当前配置允许一次调用实际消化的最大时间。
    pub fn max_step_delta_time(&self) -> f32 {
        self.max_substep_count.max(1) as f32 / self.fps.max(1.0)
    }

    /// 设置重力
    pub fn set_gravity(&self, x: f32, y: f32, z: f32) {
        self.world.set_gravity(x, y, z);
    }

    pub fn rigid_body_count(&self) -> usize {
        self.rigid_bodies.len()
    }
    pub fn joint_count(&self) -> usize {
        self.joints.len()
    }

    /// 返回名称包含 `needle` 的关节当前求解状态，供独立命令行探针使用。
    pub fn joint_snapshots_matching(&self, needle: &str) -> Vec<PhysicsJointSnapshot> {
        self.joints
            .iter()
            .filter(|joint| joint.name.contains(needle))
            .filter_map(|joint| {
                let body_a_index = usize::try_from(joint.rigid_body_a_index).ok()?;
                let body_b_index = usize::try_from(joint.rigid_body_b_index).ok()?;
                let body_a = self.rigid_bodies.get(body_a_index)?.bullet_body.as_ref()?;
                let body_b = self.rigid_bodies.get(body_b_index)?.bullet_body.as_ref()?;
                let diagnostic = joint.constraint.as_ref()?.diagnostic()?;
                let (anchor_error, anchor_a, anchor_b) =
                    super::mmd_joint::joint_anchor_position_error(
                        body_a.get_transform(),
                        joint.frame_a,
                        body_b.get_transform(),
                        joint.frame_b,
                    );
                Some(PhysicsJointSnapshot {
                    name: joint.name.clone(),
                    body_a_name: self.rigid_bodies.get(body_a_index)?.name.clone(),
                    body_b_name: self.rigid_bodies.get(body_b_index)?.name.clone(),
                    anchor_error,
                    anchor_a,
                    anchor_b,
                    diagnostic,
                })
            })
            .collect()
    }

    /// 获取动态刚体关联的骨骼变换（复用内部缓冲区，零堆分配）
    ///
    /// 从 Bullet3 读取左手空间变换，通过 inv_z 转回右手空间返回。
    pub fn get_dynamic_bone_transforms(
        &mut self,
        current_bone_transforms: &[Mat4],
    ) -> &[(usize, Mat4)] {
        self.dynamic_bone_buf.clear();
        for rb_data in &self.rigid_bodies {
            if rb_data.physics_mode == PhysicsMode::FollowBone {
                continue;
            }
            let bone_idx = rb_data.bone_index;
            if bone_idx < 0 || (bone_idx as usize) >= current_bone_transforms.len() {
                continue;
            }
            if let Some(ref body) = rb_data.bullet_body {
                let rb_matrix = body.get_transform();
                let new_bone_left = match rb_data.physics_mode {
                    PhysicsMode::Physics => rb_data.compute_bone_matrix(rb_matrix),
                    PhysicsMode::PhysicsWithBone => {
                        let bone_right = current_bone_transforms[bone_idx as usize];
                        let pos_left = Vec3::new(
                            bone_right.w_axis.x,
                            bone_right.w_axis.y,
                            -bone_right.w_axis.z,
                        );
                        rb_data.compute_bone_matrix_rotation_only(rb_matrix, pos_left)
                    }
                    PhysicsMode::FollowBone => unreachable!(),
                };
                self.dynamic_bone_buf
                    .push((bone_idx as usize, super::inv_z(new_bone_left)));
            }
        }
        &self.dynamic_bone_buf
    }

    /// 获取动态骨骼索引集合的引用（构建时已预计算，零分配）
    pub fn get_dynamic_bone_indices(&self) -> &HashSet<usize> {
        &self.dynamic_bone_indices
    }

    /// 一次性取走已完成的诊断窗口，交给 Java Log4j 写入 Minecraft 日志。
    pub fn take_debug_diagnostic(&mut self) -> Option<String> {
        self.pending_debug_diagnostic.take()
    }

    /// 构造当前聚合窗口中的峰值刚体信息，便于定位接触导致的高频振荡。
    fn build_debug_diagnostic(&self) -> String {
        let mut lines = vec![format!(
            "[Bullet3][诊断][窗口] model_signature={} cfg(collision={} joints={} kinematic_filter={} inertia={:.3} stability_mode={}) requested={} applied={} rejected={} largest_dynamic_component={} space=bullet_left steps={} max_dt={:.5}s invalid={}",
            self.model_topology_signature,
            self.active_debug_config.collision_enabled,
            self.active_debug_config.joints_enabled,
            self.active_debug_config.kinematic_filter,
            self.active_debug_config.inertia_strength,
            self.collision_stability_mode.as_str(),
            self.collision_filter_plan.pairs.len(),
            self.collision_filter_applied_pairs,
            self.collision_filter_rejected_pairs,
            self.collision_filter_plan.largest_dynamic_component,
            self.debug_telemetry.step_count,
            self.debug_telemetry.max_delta_time,
            self.debug_telemetry.invalid_step_count,
        )];
        lines.push(format!(
            "[Bullet3][诊断][初始重叠] dynamic_dynamic={} filtered_dynamic_dynamic={} dynamic_kinematic={} filtered_tail_anchor_skirt={} preserved_dynamic_kinematic={}",
            self.collision_filter_plan.initial_overlap_dynamic_dynamic_pairs,
            self.collision_filter_plan.filtered_initial_overlap_pairs,
            self.collision_filter_plan.initial_overlap_dynamic_kinematic_pairs,
            self.collision_filter_plan.filtered_tail_anchor_skirt_pairs,
            self.collision_filter_plan.preserved_dynamic_kinematic_pairs,
        ));

        if let (Some(linear), Some(angular)) = (
            self.debug_telemetry
                .peak_linear_body_index
                .and_then(|index| self.rigid_bodies.get(index)),
            self.debug_telemetry
                .peak_angular_body_index
                .and_then(|index| self.rigid_bodies.get(index)),
        ) {
            lines.push(format!(
                "[Bullet3][诊断][刚体] linear='{}' mode={:?} speed={:.3} vel={} pos={} target_error={:.5} angular='{}' mode={:?} speed={:.3} vel={} pos={} target_error={:.5}",
                linear.name,
                linear.physics_mode,
                self.debug_telemetry.max_linear_speed,
                format_vec3(self.debug_telemetry.peak_linear_velocity),
                format_vec3(self.debug_telemetry.peak_linear_position),
                self.debug_telemetry.peak_linear_body_target_error,
                angular.name,
                angular.physics_mode,
                self.debug_telemetry.max_angular_speed,
                format_vec3(self.debug_telemetry.peak_angular_velocity),
                format_vec3(self.debug_telemetry.peak_angular_position),
                self.debug_telemetry.peak_angular_body_target_error,
            ));
        }

        if let Some(body) = self
            .debug_telemetry
            .peak_kinematic_target_index
            .and_then(|index| self.rigid_bodies.get(index))
        {
            lines.push(format!(
                "[Bullet3][诊断][运动学目标] body='{}' target_delta={:.6} target_speed={:.3} target_angle={:.5}rad target_angular_speed={:.3}",
                body.name,
                self.debug_telemetry.max_kinematic_target_delta,
                self.debug_telemetry.max_kinematic_target_speed,
                self.debug_telemetry.max_kinematic_target_angle,
                self.debug_telemetry.max_kinematic_target_angular_speed,
            ));
        } else {
            lines.push("[Bullet3][诊断][运动学目标] none".to_owned());
        }

        lines.push(format!(
            "[Bullet3][诊断][运动学过滤] suppressed={} frame_translation_peak={:.6} frame_rotation_peak={:.6}rad",
            self.debug_telemetry.kinematic_suppressed_count,
            self.debug_telemetry.max_suppressed_translation_error,
            self.debug_telemetry.max_suppressed_rotation_error,
        ));

        if let (Some(linear), Some(angular)) = (
            self.debug_telemetry
                .peak_kinematic_linear_body_index
                .and_then(|index| self.rigid_bodies.get(index)),
            self.debug_telemetry
                .peak_kinematic_angular_body_index
                .and_then(|index| self.rigid_bodies.get(index)),
        ) {
            lines.push(format!(
                "[Bullet3][诊断][运动学速度] linear='{}' speed={:.3} vel={} angular='{}' speed={:.3} vel={}",
                linear.name,
                self.debug_telemetry.max_kinematic_linear_speed,
                format_vec3(self.debug_telemetry.peak_kinematic_linear_velocity),
                angular.name,
                self.debug_telemetry.max_kinematic_angular_speed,
                format_vec3(self.debug_telemetry.peak_kinematic_angular_velocity),
            ));
        } else {
            lines.push("[Bullet3][诊断][运动学速度] none".to_owned());
        }

        if let (Some(linear), Some(angular)) = (
            self.debug_telemetry
                .peak_skirt_kinematic_linear_body_index
                .and_then(|index| self.rigid_bodies.get(index)),
            self.debug_telemetry
                .peak_skirt_kinematic_angular_body_index
                .and_then(|index| self.rigid_bodies.get(index)),
        ) {
            lines.push(format!(
                "[Bullet3][诊断][裙摆运动学速度] linear='{}' speed={:.3} vel={} angular='{}' speed={:.3} vel={}",
                linear.name,
                self.debug_telemetry.max_skirt_kinematic_linear_speed,
                format_vec3(self.debug_telemetry.peak_skirt_kinematic_linear_velocity),
                angular.name,
                self.debug_telemetry.max_skirt_kinematic_angular_speed,
                format_vec3(self.debug_telemetry.peak_skirt_kinematic_angular_velocity),
            ));
        } else {
            lines.push(
                "[Bullet3][诊断][裙摆运动学速度] not_applicable(no FollowBone skirt collider)"
                    .to_owned(),
            );
        }

        if let Some(body) = self
            .debug_telemetry
            .peak_body_target_index
            .and_then(|index| self.rigid_bodies.get(index))
        {
            // 这里的 expected 是同骨骼当前动画姿态对应的刚体目标，不代表动态体必须贴住目标。
            lines.push(format!(
                "[Bullet3][诊断][刚体偏差] body='{}' mode={:?} error={:.5} actual={} expected={} delta={}",
                body.name,
                body.physics_mode,
                self.debug_telemetry.max_body_target_error,
                format_vec3(self.debug_telemetry.max_body_target_actual),
                format_vec3(self.debug_telemetry.max_body_target_expected),
                format_vec3(self.debug_telemetry.max_body_target_delta),
            ));
        } else {
            lines.push("[Bullet3][诊断][刚体偏差] none".to_owned());
        }

        let contacts = self.debug_telemetry.contacts.top();
        if contacts.is_empty() {
            lines.push("[Bullet3][诊断][接触] none".to_owned());
        }
        for (rank, contact) in contacts.iter().enumerate() {
            let (Some(a), Some(b)) = (
                self.rigid_bodies.get(contact.body_a_index),
                self.rigid_bodies.get(contact.body_b_index),
            ) else {
                continue;
            };
            lines.push(format!(
                "[Bullet3][诊断][接触#{rank}] A='{}' mode={:?} group={} mask=0x{:04X} B='{}' mode={:?} group={} mask=0x{:04X} points={} depth={:.5} impulse_peak={:.5} impulse_sum={:.5} point_a={} point_b={} normal_on_b={}",
                a.name, a.physics_mode, a.group, a.collision_mask,
                b.name, b.physics_mode, b.group, b.collision_mask,
                contact.contact_count,
                contact.max_penetration_depth,
                contact.max_applied_impulse,
                contact.total_applied_impulse,
                format_vec3(contact.point_a),
                format_vec3(contact.point_b),
                format_vec3(contact.normal_on_b),
                rank = rank + 1,
            ));
        }

        let peak = self.debug_telemetry.joint_limit_peak;
        if peak.max_violation > 0.0 {
            if let Some(joint) = self.joints.get(peak.joint_index) {
                let body_a = self
                    .rigid_bodies
                    .get(joint.rigid_body_a_index as usize)
                    .map_or("?", |body| body.name.as_str());
                let body_b = self
                    .rigid_bodies
                    .get(joint.rigid_body_b_index as usize)
                    .map_or("?", |body| body.name.as_str());
                lines.push(format!(
                    "[Bullet3][诊断][关节限位] joint='{}' bodies='{}'/'{}' max_violation={:.5} linear_pos={} linear_violation={} angular_pos={} angular_violation={}",
                    joint.name, body_a, body_b, peak.max_violation,
                    format_vec3(peak.linear_position),
                    format_vec3(peak.linear_violation),
                    format_vec3(peak.angular_position),
                    format_vec3(peak.angular_violation),
                ));
            }
        } else {
            lines.push("[Bullet3][诊断][关节限位] none".to_owned());
        }
        lines.join("\n")
    }

    /// 获取 C++ 侧存活对象计数（调试用）
    pub fn alloc_stats() -> bullet_ffi::BulletAllocStats {
        bullet_ffi::get_alloc_stats()
    }
}

#[derive(Clone, Copy)]
struct ActivePhysicsDebugConfig {
    collision_enabled: bool,
    joints_enabled: bool,
    kinematic_filter: bool,
    inertia_strength: f32,
}

impl ActivePhysicsDebugConfig {
    fn from_config(config: &PhysicsConfig) -> Self {
        Self {
            collision_enabled: config.collision_enabled,
            joints_enabled: config.joints_enabled,
            kinematic_filter: config.kinematic_filter,
            inertia_strength: config.inertia_strength,
        }
    }
}

#[derive(Default)]
struct PhysicsDebugTelemetry {
    elapsed: f32,
    step_count: u32,
    invalid_step_count: u32,
    max_delta_time: f32,
    max_linear_speed: f32,
    max_angular_speed: f32,
    peak_linear_body_index: Option<usize>,
    peak_angular_body_index: Option<usize>,
    peak_linear_velocity: Vec3,
    peak_angular_velocity: Vec3,
    peak_linear_position: Vec3,
    peak_angular_position: Vec3,
    peak_linear_body_target_error: f32,
    peak_angular_body_target_error: f32,
    max_body_target_error: f32,
    max_body_target_delta: Vec3,
    max_body_target_actual: Vec3,
    max_body_target_expected: Vec3,
    peak_body_target_index: Option<usize>,
    max_kinematic_target_delta: f32,
    max_kinematic_target_speed: f32,
    max_kinematic_target_angle: f32,
    max_kinematic_target_angular_speed: f32,
    peak_kinematic_target_index: Option<usize>,
    kinematic_suppressed_count: u32,
    max_suppressed_translation_error: f32,
    max_suppressed_rotation_error: f32,
    max_kinematic_linear_speed: f32,
    max_kinematic_angular_speed: f32,
    peak_kinematic_linear_body_index: Option<usize>,
    peak_kinematic_angular_body_index: Option<usize>,
    peak_kinematic_linear_velocity: Vec3,
    peak_kinematic_angular_velocity: Vec3,
    max_skirt_kinematic_linear_speed: f32,
    max_skirt_kinematic_angular_speed: f32,
    peak_skirt_kinematic_linear_body_index: Option<usize>,
    peak_skirt_kinematic_angular_body_index: Option<usize>,
    peak_skirt_kinematic_linear_velocity: Vec3,
    peak_skirt_kinematic_angular_velocity: Vec3,
    contacts: ContactWindow,
    joint_limit_peak: JointLimitPeak,
}

impl PhysicsDebugTelemetry {
    fn observe_kinematic_suppression(&mut self, translation_error: f32, rotation_error: f32) {
        self.kinematic_suppressed_count = self.kinematic_suppressed_count.saturating_add(1);
        self.max_suppressed_translation_error =
            self.max_suppressed_translation_error.max(translation_error);
        self.max_suppressed_rotation_error = self.max_suppressed_rotation_error.max(rotation_error);
    }

    fn observe_kinematic_target(
        &mut self,
        body_index: usize,
        previous: Mat4,
        current: Mat4,
        delta_time: f32,
    ) {
        let dt = delta_time.max(0.001);
        let position_delta =
            finite_length_or_infinity(current.w_axis.truncate() - previous.w_axis.truncate());
        let previous_rotation = glam::Quat::from_mat3(&Mat3::from_mat4(previous));
        let current_rotation = glam::Quat::from_mat3(&Mat3::from_mat4(current));
        let angle_delta = (2.0
            * previous_rotation
                .dot(current_rotation)
                .abs()
                .clamp(0.0, 1.0)
                .acos())
        .min(std::f32::consts::PI);
        let target_speed = position_delta / dt;
        let angular_speed = angle_delta / dt;

        if position_delta > self.max_kinematic_target_delta {
            self.max_kinematic_target_delta = position_delta;
            self.max_kinematic_target_speed = target_speed;
            self.max_kinematic_target_angle = angle_delta;
            self.max_kinematic_target_angular_speed = angular_speed;
            self.peak_kinematic_target_index = Some(body_index);
        }
    }

    fn observe_kinematic_body(&mut self, body_index: usize, linear: Vec3, angular: Vec3) {
        let linear_speed = finite_length_or_infinity(linear);
        let angular_speed = finite_length_or_infinity(angular);
        // 首个样本即使恰为零也要保留，避免日志把“已观测且静止”误写成 none。
        if self.peak_kinematic_linear_body_index.is_none() {
            self.peak_kinematic_linear_body_index = Some(body_index);
            self.peak_kinematic_linear_velocity = linear;
        }
        if self.peak_kinematic_angular_body_index.is_none() {
            self.peak_kinematic_angular_body_index = Some(body_index);
            self.peak_kinematic_angular_velocity = angular;
        }
        if linear_speed > self.max_kinematic_linear_speed {
            self.max_kinematic_linear_speed = linear_speed;
            self.peak_kinematic_linear_body_index = Some(body_index);
            self.peak_kinematic_linear_velocity = linear;
        }
        if angular_speed > self.max_kinematic_angular_speed {
            self.max_kinematic_angular_speed = angular_speed;
            self.peak_kinematic_angular_body_index = Some(body_index);
            self.peak_kinematic_angular_velocity = angular;
        }
    }

    fn observe_skirt_kinematic_body(&mut self, body_index: usize, linear: Vec3, angular: Vec3) {
        let linear_speed = finite_length_or_infinity(linear);
        let angular_speed = finite_length_or_infinity(angular);
        if self.peak_skirt_kinematic_linear_body_index.is_none() {
            self.peak_skirt_kinematic_linear_body_index = Some(body_index);
            self.peak_skirt_kinematic_linear_velocity = linear;
        }
        if self.peak_skirt_kinematic_angular_body_index.is_none() {
            self.peak_skirt_kinematic_angular_body_index = Some(body_index);
            self.peak_skirt_kinematic_angular_velocity = angular;
        }
        if linear_speed > self.max_skirt_kinematic_linear_speed {
            self.max_skirt_kinematic_linear_speed = linear_speed;
            self.peak_skirt_kinematic_linear_body_index = Some(body_index);
            self.peak_skirt_kinematic_linear_velocity = linear;
        }
        if angular_speed > self.max_skirt_kinematic_angular_speed {
            self.max_skirt_kinematic_angular_speed = angular_speed;
            self.peak_skirt_kinematic_angular_body_index = Some(body_index);
            self.peak_skirt_kinematic_angular_velocity = angular;
        }
    }

    fn observe_body(
        &mut self,
        body_index: usize,
        linear: Vec3,
        angular: Vec3,
        actual_position: Vec3,
        target_position: Option<Vec3>,
    ) {
        let linear_speed = finite_length_or_infinity(linear);
        let angular_speed = finite_length_or_infinity(angular);
        let target_error = target_position
            .map(|target| finite_length_or_infinity(actual_position - target))
            .unwrap_or(0.0);
        if linear_speed > self.max_linear_speed {
            self.max_linear_speed = linear_speed;
            self.peak_linear_body_index = Some(body_index);
            self.peak_linear_velocity = linear;
            self.peak_linear_position = actual_position;
            self.peak_linear_body_target_error = target_error;
        }
        if angular_speed > self.max_angular_speed {
            self.max_angular_speed = angular_speed;
            self.peak_angular_body_index = Some(body_index);
            self.peak_angular_velocity = angular;
            self.peak_angular_position = actual_position;
            self.peak_angular_body_target_error = target_error;
        }
        if let Some(target) = target_position {
            if target_error > self.max_body_target_error {
                self.max_body_target_error = target_error;
                self.max_body_target_delta = actual_position - target;
                self.max_body_target_actual = actual_position;
                self.max_body_target_expected = target;
                self.peak_body_target_index = Some(body_index);
            }
        }
    }

    fn reset_window(&mut self) {
        *self = Self::default();
    }
}

fn finite_length_or_infinity(value: Vec3) -> f32 {
    let length = value.length();
    if length.is_finite() {
        length
    } else {
        f32::INFINITY
    }
}

fn format_vec3(value: Vec3) -> String {
    format!("({:.4},{:.4},{:.4})", value.x, value.y, value.z)
}

fn is_skirt_body_name(name: &str) -> bool {
    let lower = name.to_ascii_lowercase();
    lower.contains("skirt") || name.contains('裙')
}

/// 根据实验性总开关选择 Bullet 的允许碰撞掩码。
fn effective_collision_mask(collision_enabled: bool, pmx_mask: u16) -> i32 {
    if collision_enabled {
        pmx_mask as i32
    } else {
        0
    }
}

#[cfg(test)]
#[path = "mmd_physics_tests.rs"]
mod tests;

impl Drop for MMDPhysics {
    fn drop(&mut self) {
        // Bullet3 要求：必须在 destroy 对象前先从世界中移除。
        // Rust 默认按声明顺序 drop 字段（joints → rigid_bodies → world），
        // 如果不先移除，bw_world_destroy 会访问已释放的指针导致崩溃。
        for joint in &self.joints {
            if let Some(ref constraint) = joint.constraint {
                self.world.remove_constraint(constraint);
            }
        }
        for rb in &self.rigid_bodies {
            if let Some(ref body) = rb.bullet_body {
                self.world.remove_rigid_body(body);
            }
        }
        // 之后 Rust 自动 drop 各字段（约束/刚体/世界），此时世界已为空，安全释放

        // 泄漏检测日志（仅在当前实例释放后检查全局计数）
        let stats = bullet_ffi::get_alloc_stats();
        if !stats.is_clean() {
            log::warn!(
                "[Bullet3] C++ 侧仍有存活对象: worlds={}, shapes={}, bodies={}, constraints={}, motionStates={}",
                stats.worlds, stats.shapes, stats.rigid_bodies,
                stats.constraints, stats.motion_states
            );
        }
    }
}
