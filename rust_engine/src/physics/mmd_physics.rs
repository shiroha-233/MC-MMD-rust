//! MMD 物理世界管理器
//!
//! 对应 C++ saba 的 MMDPhysics 类，使用 Rapier3D 实现。
//!
//! ## Bullet3 → Rapier 映射
//! | Bullet3 | Rapier |
//! |---------|--------|
//! | btDiscreteDynamicsWorld | PhysicsPipeline + RigidBodySet + ColliderSet + ImpulseJointSet |
//! | btDbvtBroadphase | BroadPhaseBvh |
//! | btCollisionDispatcher | NarrowPhase |
//! | btSequentialImpulseConstraintSolver | 内置于 PhysicsPipeline |

use glam::{Mat4, Vec3};
use rapier3d::prelude::*;
use rapier3d::math::Real;
use std::num::NonZeroUsize;

use mmd::pmx::rigid_body::RigidBody as PmxRigidBody;
use mmd::pmx::joint::Joint as PmxJoint;

use super::mmd_rigid_body::{MMDRigidBody, RigidBodyType};
use super::mmd_joint::MMDJoint;
use super::config::get_config;

/// MMD 物理世界管理器
/// 
/// 对应 C++ saba 的 MMDPhysics 类，管理：
/// - Rapier PhysicsPipeline (物理流水线)
/// - RigidBodySet (刚体集合)
/// - ColliderSet (碰撞体集合)
/// - ImpulseJointSet (关节集合)
/// - 地面刚体
pub struct MMDPhysics {
    /// 物理流水线
    pub physics_pipeline: PhysicsPipeline,
    /// 积分参数
    pub integration_parameters: IntegrationParameters,
    /// 岛管理器
    pub island_manager: IslandManager,
    /// 宽相检测
    pub broad_phase: DefaultBroadPhase,
    /// 窄相检测
    pub narrow_phase: NarrowPhase,
    /// 刚体集合
    pub rigid_body_set: RigidBodySet,
    /// 碰撞体集合
    pub collider_set: ColliderSet,
    /// 关节集合
    pub impulse_joint_set: ImpulseJointSet,
    /// 多体关节集合
    pub multibody_joint_set: MultibodyJointSet,
    /// CCD 求解器
    pub ccd_solver: CCDSolver,
    /// 地面刚体句柄
    pub ground_handle: Option<RigidBodyHandle>,
    /// MMD 刚体列表
    pub mmd_rigid_bodies: Vec<MMDRigidBody>,
    /// MMD 关节列表
    pub mmd_joints: Vec<MMDJoint>,
    /// FPS（用于计算固定时间步长）
    pub fps: f32,
    /// 最大子步数
    pub max_substep_count: i32,
    /// 重力向量
    pub gravity: Vector<Real>,
    /// 是否启用关节（调试用）
    pub joints_enabled: bool,
    /// 上一帧的模型变换（用于计算模型整体移动速度）
    pub prev_model_transform: Option<Mat4>,
}

impl MMDPhysics {
    /// 创建新的物理世界
    pub fn new() -> Self {
        let config = get_config();
        
        let mut rigid_body_set = RigidBodySet::new();
        let mut collider_set = ColliderSet::new();
        
        // 创建地面（静态平面）
        // 使用一个大的静态盒子作为地面
        let ground = RigidBodyBuilder::fixed()
            .translation(Vector::new(0.0, -50.0, 0.0))
            .build();
        let ground_handle = rigid_body_set.insert(ground);
        
        // 地面碰撞体（大盒子）
        let ground_collider = ColliderBuilder::cuboid(1000.0, 50.0, 1000.0)
            .build();
        collider_set.insert_with_parent(ground_collider, ground_handle, &mut rigid_body_set);
        
        // 设置积分参数（从配置读取）
        let mut integration_parameters = IntegrationParameters::default();
        integration_parameters.dt = 1.0 / config.physics_fps;
        integration_parameters.num_solver_iterations = NonZeroUsize::new(config.solver_iterations).unwrap_or(NonZeroUsize::new(4).unwrap());
        integration_parameters.num_internal_pgs_iterations = config.pgs_iterations;
        integration_parameters.normalized_max_corrective_velocity = config.max_corrective_velocity;
        
        if config.debug_log {
            log::info!("[物理配置] FPS={}, 重力Y={}, 求解器迭代={}, PGS迭代={}",
                config.physics_fps, config.gravity_y, config.solver_iterations, config.pgs_iterations);
        }
        
        Self {
            physics_pipeline: PhysicsPipeline::new(),
            integration_parameters,
            island_manager: IslandManager::new(),
            broad_phase: DefaultBroadPhase::new(),
            narrow_phase: NarrowPhase::new(),
            rigid_body_set,
            collider_set,
            impulse_joint_set: ImpulseJointSet::new(),
            multibody_joint_set: MultibodyJointSet::new(),
            ccd_solver: CCDSolver::new(),
            ground_handle: Some(ground_handle),
            mmd_rigid_bodies: Vec::new(),
            mmd_joints: Vec::new(),
            fps: config.physics_fps,
            max_substep_count: config.max_substep_count,
            gravity: Vector::new(0.0, config.gravity_y, 0.0),
            joints_enabled: config.joints_enabled,
            prev_model_transform: None,
        }
    }
    
    /// 设置 FPS
    pub fn set_fps(&mut self, fps: f32) {
        self.fps = fps;
        self.integration_parameters.dt = 1.0 / fps;
    }
    
    /// 获取 FPS
    pub fn get_fps(&self) -> f32 {
        self.fps
    }
    
    /// 设置最大子步数
    pub fn set_max_substep_count(&mut self, count: i32) {
        self.max_substep_count = count;
    }
    
    /// 获取最大子步数
    pub fn get_max_substep_count(&self) -> i32 {
        self.max_substep_count
    }
    
    /// 设置重力
    pub fn set_gravity(&mut self, gravity: Vec3) {
        self.gravity = Vector::new(gravity.x, gravity.y, gravity.z);
    }
    
    /// 从 PMX 数据添加刚体
    /// 
    /// # 参数
    /// - `pmx_rb`: PMX 刚体数据
    /// - `bone_global_transform`: 关联骨骼的全局变换
    pub fn add_rigid_body(
        &mut self,
        pmx_rb: &PmxRigidBody,
        bone_global_transform: Option<Mat4>,
    ) -> usize {
        let mut mmd_rb = MMDRigidBody::from_pmx(pmx_rb, bone_global_transform);
        
        // 创建 Rapier 刚体
        let rb = mmd_rb.build_rigid_body();
        let rb_handle = self.rigid_body_set.insert(rb);
        mmd_rb.rigid_body_handle = Some(rb_handle);
        
        // 创建 Rapier 碰撞体
        let collider = mmd_rb.build_collider(pmx_rb);
        let collider_handle = self.collider_set.insert_with_parent(
            collider,
            rb_handle,
            &mut self.rigid_body_set,
        );
        mmd_rb.collider_handle = Some(collider_handle);
        
        let index = self.mmd_rigid_bodies.len();
        
        // 调试：打印前几个刚体的信息
        if index < 5 {
            let pos = mmd_rb.initial_transform.translation;
            log::info!(
                "[刚体调试] 刚体[{}] '{}': 类型={:?}, 骨骼={}, 质量={}, 初始位置=({:.2},{:.2},{:.2})",
                index, mmd_rb.name, mmd_rb.body_type, mmd_rb.bone_index, mmd_rb.mass,
                pos.x, pos.y, pos.z
            );
        }
        
        self.mmd_rigid_bodies.push(mmd_rb);
        index
    }
    
    /// 从 PMX 数据添加关节
    /// 
    /// # 参数
    /// - `pmx_joint`: PMX 关节数据
    pub fn add_joint(&mut self, pmx_joint: &PmxJoint) -> Option<usize> {
        // 调试：如果禁用关节，跳过添加
        if !self.joints_enabled {
            return None;
        }
        
        let rb_a_idx = pmx_joint.rigid_body_a_index as usize;
        let rb_b_idx = pmx_joint.rigid_body_b_index as usize;
        
        if rb_a_idx >= self.mmd_rigid_bodies.len() || rb_b_idx >= self.mmd_rigid_bodies.len() {
            return None;
        }
        
        let rb_a_handle = self.mmd_rigid_bodies[rb_a_idx].rigid_body_handle?;
        let rb_b_handle = self.mmd_rigid_bodies[rb_b_idx].rigid_body_handle?;
        
        // 获取刚体的世界变换
        let rb_a = self.rigid_body_set.get(rb_a_handle)?;
        let rb_b = self.rigid_body_set.get(rb_b_handle)?;
        
        let rb_a_transform = *rb_a.position();
        let rb_b_transform = *rb_b.position();
        
        let mut mmd_joint = MMDJoint::from_pmx(pmx_joint, rb_a_transform, rb_b_transform);
        
        // 调试：打印关节限制（特别关注头发相关的关节）
        let index = self.mmd_joints.len();
        let is_hair = mmd_joint.name.contains("髪") || mmd_joint.name.contains("hair") 
            || mmd_joint.name.contains("Hair") || mmd_joint.name.contains("毛");
        if index < 5 || is_hair {
            log::info!(
                "[关节调试] 关节[{}] '{}': 刚体A={}, 刚体B={}",
                index, mmd_joint.name, rb_a_idx, rb_b_idx
            );
            log::info!(
                "  线性限制: ({:.4}~{:.4}, {:.4}~{:.4}, {:.4}~{:.4})",
                mmd_joint.linear_lower.x, mmd_joint.linear_upper.x,
                mmd_joint.linear_lower.y, mmd_joint.linear_upper.y,
                mmd_joint.linear_lower.z, mmd_joint.linear_upper.z,
            );
            log::info!(
                "  角度限制: ({:.4}~{:.4}, {:.4}~{:.4}, {:.4}~{:.4})",
                mmd_joint.angular_lower.x, mmd_joint.angular_upper.x,
                mmd_joint.angular_lower.y, mmd_joint.angular_upper.y,
                mmd_joint.angular_lower.z, mmd_joint.angular_upper.z,
            );
            log::info!(
                "  弹簧: 线性=({:.2}, {:.2}, {:.2}), 角度=({:.2}, {:.2}, {:.2})",
                mmd_joint.linear_spring.x, mmd_joint.linear_spring.y, mmd_joint.linear_spring.z,
                mmd_joint.angular_spring.x, mmd_joint.angular_spring.y, mmd_joint.angular_spring.z,
            );
        }
        
        // 判断关联的刚体是否为胸部刚体（任一为胸部则使用胸部参数）
        let is_bust = self.mmd_rigid_bodies[rb_a_idx].is_bust
            || self.mmd_rigid_bodies[rb_b_idx].is_bust;
        
        // 如果是胸部关节但胸部物理未启用，跳过
        if is_bust && !get_config().bust_physics_enabled {
            return None;
        }
        
        // 创建 Rapier 关节
        let joint = mmd_joint.build_joint(is_bust);
        let joint_handle = self.impulse_joint_set.insert(
            rb_a_handle,
            rb_b_handle,
            joint,
            true,
        );
        mmd_joint.joint_handle = Some(joint_handle);
        
        self.mmd_joints.push(mmd_joint);
        Some(index)
    }
    
    /// 更新物理模拟
    /// 
    /// # 参数
    /// - `delta_time`: 经过的时间（秒）
    pub fn update(&mut self, delta_time: f32) {
        let fixed_dt = 1.0 / self.fps;
        let num_substeps = ((delta_time / fixed_dt).ceil() as i32).min(self.max_substep_count);
        
        for _ in 0..num_substeps {
            self.physics_pipeline.step(
                &self.gravity,
                &self.integration_parameters,
                &mut self.island_manager,
                &mut self.broad_phase,
                &mut self.narrow_phase,
                &mut self.rigid_body_set,
                &mut self.collider_set,
                &mut self.impulse_joint_set,
                &mut self.multibody_joint_set,
                &mut self.ccd_solver,
                None,
                &(),
                &(),
            );
        }
        
        self.clamp_velocities();
    }
    
    /// 限制刚体速度，防止物理爆炸
    /// 
    /// MMD 物理中，当刚体穿透卡模时会产生极大的恢复力导致速度过高。
    /// 通过限制最大速度可以防止这种情况。
    fn clamp_velocities(&mut self) {
        let config = get_config();
        let max_linear_velocity = config.max_linear_velocity;
        let max_angular_velocity = config.max_angular_velocity;
        
        for mmd_rb in &self.mmd_rigid_bodies {
            if mmd_rb.body_type == RigidBodyType::Kinematic {
                continue;
            }
            
            if let Some(rb_handle) = mmd_rb.rigid_body_handle {
                if let Some(rb) = self.rigid_body_set.get_mut(rb_handle) {
                    // 限制线速度
                    let linvel = rb.linvel();
                    let linvel_mag = linvel.norm();
                    if linvel_mag > max_linear_velocity {
                        let scale = max_linear_velocity / linvel_mag;
                        rb.set_linvel(linvel * scale, true);
                    }
                    
                    // 限制角速度
                    let angvel = rb.angvel();
                    let angvel_mag = angvel.norm();
                    if angvel_mag > max_angular_velocity {
                        let scale = max_angular_velocity / angvel_mag;
                        rb.set_angvel(angvel * scale, true);
                    }
                }
            }
        }
    }
    
    /// 在物理更新前同步运动学刚体（跟随骨骼）
    /// 
    /// 关键改进：计算并设置运动学刚体的速度，使连接的动态刚体产生惯性
    /// 
    /// # 参数
    /// - `bone_transforms`: 骨骼全局变换列表
    /// - `delta_time`: 时间步长（秒）
    pub fn sync_kinematic_bodies(&mut self, bone_transforms: &[Mat4], delta_time: f32) {
        let dt = delta_time.max(0.001); // 防止除零
        
        for mmd_rb in &mut self.mmd_rigid_bodies {
            if mmd_rb.body_type != RigidBodyType::Kinematic {
                continue;
            }
            
            if let Some(rb_handle) = mmd_rb.rigid_body_handle {
                if let Some(rb) = self.rigid_body_set.get_mut(rb_handle) {
                    let bone_idx = mmd_rb.bone_index;
                    if bone_idx >= 0 && (bone_idx as usize) < bone_transforms.len() {
                        let bone_transform = bone_transforms[bone_idx as usize];
                        let new_pose = mmd_rb.compute_world_transform(bone_transform);
                        
                        // 计算速度（基于位置变化）
                        if let Some(prev_pose) = mmd_rb.prev_transform {
                            // 线速度 = (新位置 - 旧位置) / dt
                            let delta_pos = new_pose.translation.vector - prev_pose.translation.vector;
                            let linvel = delta_pos / dt;
                            
                            // 角速度计算：从四元数差计算
                            // delta_rot = new_rot * prev_rot.inverse()
                            let prev_rot = prev_pose.rotation;
                            let new_rot = new_pose.rotation;
                            let delta_rot = new_rot * prev_rot.inverse();
                            
                            // 从四元数提取角速度：
                            // 对于小角度旋转，角速度 ≈ 2 * (qx, qy, qz) / dt
                            // 这是四元数到角速度的近似公式
                            let angvel = Vector::new(
                                2.0 * delta_rot.coords[0] / dt,
                                2.0 * delta_rot.coords[1] / dt,
                                2.0 * delta_rot.coords[2] / dt,
                            );
                            
                            // 设置速度（这会影响通过关节连接的动态刚体）
                            rb.set_linvel(linvel, true);
                            rb.set_angvel(angvel, true);
                        }
                        
                        // 设置目标位置
                        rb.set_next_kinematic_position(new_pose);
                        
                        // 保存当前变换用于下一帧
                        mmd_rb.prev_transform = Some(new_pose);
                    }
                }
            }
        }
    }
    
    /// 在物理更新前同步运动学刚体，同时考虑模型整体移动
    /// 
    /// 关键改进：直接给 Dynamic 刚体施加惯性速度，而不是依赖 Kinematic 传递
    /// 
    /// # 参数
    /// - `bone_transforms`: 骨骼局部空间的全局变换列表
    /// - `delta_time`: 时间步长（秒）
    /// - `model_transform`: 当前模型的世界变换
    pub fn sync_kinematic_bodies_with_model_velocity(
        &mut self, 
        bone_transforms: &[Mat4], 
        delta_time: f32,
        model_transform: Mat4,
    ) {
        let dt = delta_time.max(0.001);
        
        // 计算模型整体移动的速度（仅使用位置差）
        let model_velocity = if let Some(prev_transform) = self.prev_model_transform {
            let curr_pos = model_transform.w_axis.truncate();
            let prev_pos = prev_transform.w_axis.truncate();
            (curr_pos - prev_pos) / dt
        } else {
            Vec3::ZERO
        };
        
        // 保存当前模型变换
        self.prev_model_transform = Some(model_transform);
        
        // 模型速度（保留用于调试）
        let _model_linvel = Vector::new(model_velocity.x, model_velocity.y, model_velocity.z);
        
        // 第一步：同步 Kinematic 刚体位置
        for mmd_rb in &mut self.mmd_rigid_bodies {
            if mmd_rb.body_type != RigidBodyType::Kinematic {
                continue;
            }
            
            if let Some(rb_handle) = mmd_rb.rigid_body_handle {
                if let Some(rb) = self.rigid_body_set.get_mut(rb_handle) {
                    let bone_idx = mmd_rb.bone_index;
                    if bone_idx >= 0 && (bone_idx as usize) < bone_transforms.len() {
                        let bone_transform = bone_transforms[bone_idx as usize];
                        let new_pose = mmd_rb.compute_world_transform(bone_transform);
                        
                        // Kinematic 刚体：只设置位置，速度设置无意义
                        rb.set_next_kinematic_position(new_pose);
                        mmd_rb.prev_transform = Some(new_pose);
                    }
                }
            }
        }
        
        // 第二步：给 Dynamic 刚体施加惯性速度
        // 关键：需要将世界空间的速度转换到模型局部空间
        // 从 model_transform 提取旋转矩阵（3x3 部分）
        let rot_col0 = model_transform.x_axis.truncate(); // 第一列
        let rot_col2 = model_transform.z_axis.truncate(); // 第三列
        
        // 将世界速度转换到模型局部空间：v_local = R^T * v_world
        // R^T 的行就是 R 的列
        let world_vel = model_velocity;
        let local_vel_x = rot_col0.x * world_vel.x + rot_col0.y * world_vel.y + rot_col0.z * world_vel.z;
        let local_vel_y = world_vel.y; // Y轴不变
        let local_vel_z = rot_col2.x * world_vel.x + rot_col2.y * world_vel.y + rot_col2.z * world_vel.z;
        
        // 惯性速度（模型局部空间，反方向），乘以惯性强度系数
        let strength = get_config().inertia_strength;
        let inertia_velocity = Vector::new(
            -local_vel_x * strength, 
            -local_vel_y * strength, 
            -local_vel_z * strength
        );
        
        for mmd_rb in &self.mmd_rigid_bodies {
            // 只处理动态刚体
            if mmd_rb.body_type == RigidBodyType::Kinematic {
                continue;
            }
            
            if let Some(rb_handle) = mmd_rb.rigid_body_handle {
                if let Some(rb) = self.rigid_body_set.get_mut(rb_handle) {
                    // 获取当前速度并叠加惯性速度
                    let current_vel = rb.linvel().clone();
                    let new_vel = current_vel + inertia_velocity;
                    rb.set_linvel(new_vel, true);
                }
            }
        }
    }
    
    /// 在物理更新后将动态刚体的变换反映到骨骼
    /// 
    /// # 参数
    /// - `bone_transforms`: 骨骼全局变换列表（会被修改）
    pub fn reflect_to_bones(&self, bone_transforms: &mut [Mat4]) {
        for mmd_rb in &self.mmd_rigid_bodies {
            if mmd_rb.body_type == RigidBodyType::Kinematic {
                continue;
            }
            
            if let Some(rb_handle) = mmd_rb.rigid_body_handle {
                if let Some(rb) = self.rigid_body_set.get(rb_handle) {
                    let bone_idx = mmd_rb.bone_index;
                    if bone_idx >= 0 && (bone_idx as usize) < bone_transforms.len() {
                        let rb_pose = *rb.position();
                        
                        let new_bone_transform = match mmd_rb.body_type {
                            RigidBodyType::Dynamic => {
                                mmd_rb.compute_bone_transform(rb_pose)
                            }
                            RigidBodyType::DynamicWithBonePosition => {
                                // 只应用旋转，保留原位置
                                let current_pos = bone_transforms[bone_idx as usize]
                                    .w_axis
                                    .truncate();
                                mmd_rb.compute_bone_transform_rotation_only(rb_pose, current_pos)
                            }
                            RigidBodyType::Kinematic => unreachable!(),
                        };
                        
                        bone_transforms[bone_idx as usize] = new_bone_transform;
                    }
                }
            }
        }
    }
    
    /// 重置所有刚体到初始状态
    pub fn reset(&mut self) {
        for mmd_rb in &self.mmd_rigid_bodies {
            if let Some(rb_handle) = mmd_rb.rigid_body_handle {
                if let Some(rb) = self.rigid_body_set.get_mut(rb_handle) {
                    // 重置位置
                    rb.set_position(mmd_rb.initial_transform, true);
                    // 重置速度
                    rb.set_linvel(Vector::new(0.0, 0.0, 0.0), true);
                    rb.set_angvel(Vector::new(0.0, 0.0, 0.0), true);
                    // 唤醒刚体
                    rb.wake_up(true);
                }
            }
        }
    }
    
    /// 获取刚体数量
    pub fn rigid_body_count(&self) -> usize {
        self.mmd_rigid_bodies.len()
    }
    
    /// 获取关节数量
    pub fn joint_count(&self) -> usize {
        self.mmd_joints.len()
    }
    
    /// 获取动态刚体关联的骨骼变换列表
    /// 
    /// 返回 (骨骼索引, 新变换) 列表，按刚体顺序排列
    /// 用于物理更新后只更新被物理驱动的骨骼
    pub fn get_dynamic_bone_transforms(&self, current_bone_transforms: &[Mat4]) -> Vec<(usize, Mat4)> {
        let mut result = Vec::new();
        
        for mmd_rb in &self.mmd_rigid_bodies {
            if mmd_rb.body_type == RigidBodyType::Kinematic {
                continue;
            }
            
            if let Some(rb_handle) = mmd_rb.rigid_body_handle {
                if let Some(rb) = self.rigid_body_set.get(rb_handle) {
                    let bone_idx = mmd_rb.bone_index;
                    if bone_idx >= 0 && (bone_idx as usize) < current_bone_transforms.len() {
                        let rb_pose = *rb.position();
                        
                        let new_bone_transform = match mmd_rb.body_type {
                            RigidBodyType::Dynamic => {
                                mmd_rb.compute_bone_transform(rb_pose)
                            }
                            RigidBodyType::DynamicWithBonePosition => {
                                let current_pos = current_bone_transforms[bone_idx as usize]
                                    .w_axis
                                    .truncate();
                                mmd_rb.compute_bone_transform_rotation_only(rb_pose, current_pos)
                            }
                            RigidBodyType::Kinematic => unreachable!(),
                        };
                        
                        result.push((bone_idx as usize, new_bone_transform));
                    }
                }
            }
        }
        
        result
    }
    
    /// 获取所有动态刚体关联的骨骼索引集合
    pub fn get_dynamic_bone_indices(&self) -> std::collections::HashSet<usize> {
        self.mmd_rigid_bodies
            .iter()
            .filter(|rb| rb.body_type != RigidBodyType::Kinematic && rb.bone_index >= 0)
            .map(|rb| rb.bone_index as usize)
            .collect()
    }
}

impl Default for MMDPhysics {
    fn default() -> Self {
        Self::new()
    }
}
