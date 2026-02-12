//! MMD 物理世界管理器
//!
//! 移植自 babylon-mmd，使用 Bullet3 引擎。
//! 实现 commitBodyStates / syncBodies / stepSimulation / syncBones 全套流程。

use std::collections::HashSet;

use glam::{Mat4, Vec3};

use mmd::pmx::rigid_body::RigidBody as PmxRigidBody;
use mmd::pmx::joint::Joint as PmxJoint;

use super::bullet_ffi::{self, BulletWorld};
use super::mmd_rigid_body::{MmdRigidBodyData, PhysicsMode};
use super::mmd_joint::MmdJointData;
use super::config::get_config;

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
}

impl MMDPhysics {
    /// 创建新的物理世界（C++ OOM 时返回 None）
    pub fn new() -> Option<Self> {
        let config = get_config();
        let world = BulletWorld::new(0.0, config.gravity_y, 0.0)?;

        if config.debug_log {
            log::info!("[Bullet3] 物理世界创建: FPS={}, 重力Y={}", config.physics_fps, config.gravity_y);
        }

        Some(Self {
            joints: Vec::new(),
            rigid_bodies: Vec::new(),
            world,
            fps: config.physics_fps,
            max_substep_count: config.max_substep_count,
            dynamic_bone_indices: HashSet::new(),
            dynamic_bone_buf: Vec::new(),
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

        // 预分配容量
        self.rigid_bodies.reserve(pmx_rigid_bodies.len());

        // 第一步：创建所有刚体并存入 Vec（还未加入世界）
        for pmx_rb in pmx_rigid_bodies {
            let bone_transform = if pmx_rb.bone_index >= 0 && (pmx_rb.bone_index as usize) < bone_transforms.len() {
                Some(bone_transforms[pmx_rb.bone_index as usize])
            } else {
                None
            };

            let mut rb_data = MmdRigidBodyData::from_pmx(pmx_rb, bone_transform);
            let shape = MmdRigidBodyData::create_shape(pmx_rb);
            let body = shape.as_ref().and_then(|s| rb_data.create_rigid_body(pmx_rb, s));

            if shape.is_none() || body.is_none() {
                log::warn!("[Bullet3] 刚体 '{}' 创建失败，跳过", rb_data.name);
            }

            // 先转移所有权到 rb_data，再 push 到 Vec
            rb_data.bullet_body = body;
            rb_data.bullet_shape = shape;
            self.rigid_bodies.push(rb_data);
        }

        // 第二步：统一将已存储的刚体添加到世界
        // 此时所有权已在 self.rigid_bodies 中，panic 时 Drop 链会正确清理
        for rb_data in &self.rigid_bodies {
            if let Some(ref body) = rb_data.bullet_body {
                let group = 1i32 << (rb_data.group.min(15) as i32);
                let mask = rb_data.group_mask as i32;
                self.world.add_rigid_body(body, group, mask);
            }
        }

        // 第三步：创建关节并存入 Vec
        if config.joints_enabled {
            self.joints.reserve(pmx_joints.len());

            for pmx_joint in pmx_joints {
                let rb_a_idx = pmx_joint.rigid_body_a_index as usize;
                let rb_b_idx = pmx_joint.rigid_body_b_index as usize;

                if rb_a_idx >= self.rigid_bodies.len() || rb_b_idx >= self.rigid_bodies.len()
                    || rb_a_idx == rb_b_idx
                {
                    continue;
                }

                let (rb_a_body, rb_b_body, rb_a_init, rb_b_init) = {
                    let rb_a = &self.rigid_bodies[rb_a_idx];
                    let rb_b = &self.rigid_bodies[rb_b_idx];
                    match (&rb_a.bullet_body, &rb_b.bullet_body) {
                        (Some(a), Some(b)) => (a, b, rb_a.initial_transform, rb_b.initial_transform),
                        _ => continue,
                    }
                };

                let joint_data = MmdJointData::from_pmx(
                    pmx_joint,
                    rb_a_body,
                    rb_b_body,
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
        self.dynamic_bone_indices = self.rigid_bodies.iter()
            .filter(|rb| rb.physics_mode != PhysicsMode::FollowBone && rb.bone_index >= 0)
            .map(|rb| rb.bone_index as usize)
            .collect();

        // 预分配动态骨骼缓冲区
        self.dynamic_bone_buf.reserve(self.dynamic_bone_indices.len());

        let kinematic_count = self.rigid_bodies.iter()
            .filter(|rb| rb.physics_mode == PhysicsMode::FollowBone).count();
        let dynamic_count = self.rigid_bodies.iter()
            .filter(|rb| rb.physics_mode == PhysicsMode::Physics).count();
        let dynamic_bone_count = self.rigid_bodies.iter()
            .filter(|rb| rb.physics_mode == PhysicsMode::PhysicsWithBone).count();

        log::info!(
            "Bullet3 物理构建完成: {} 刚体 ({}跟骨 + {}物理 + {}物理跟骨), {} 关节",
            self.rigid_bodies.len(),
            kinematic_count, dynamic_count, dynamic_bone_count,
            self.joints.len()
        );
    }

    /// 同步运动学刚体位置（babylon-mmd syncBodies）
    ///
    /// 在每帧物理步进前调用。将 FollowBone 模式的刚体位置
    /// 同步为骨骼变换推导的物理空间（左手）变换。
    pub fn sync_bodies(&self, bone_transforms: &[Mat4]) {
        for rb_data in &self.rigid_bodies {
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
                body.set_transform(body_matrix);
            }
        }
    }

    /// 同步运动学刚体，供 update_physics 调用
    pub fn sync_bodies_with_model_velocity(
        &mut self,
        bone_transforms: &[Mat4],
        _delta_time: f32,
        _model_transform: Mat4,
    ) {
        self.sync_bodies(bone_transforms);
    }

    /// 步进物理模拟（Bullet3 stepSimulation）
    pub fn step_simulation(&self, delta_time: f32) {
        let fixed_dt = 1.0 / self.fps;
        self.world.step(delta_time, self.max_substep_count, fixed_dt);
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
                    PhysicsMode::Physics => {
                        rb_data.compute_bone_matrix(rb_matrix)
                    }
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
        for rb_data in &self.rigid_bodies {
            let bone_idx = rb_data.bone_index;
            if bone_idx < 0 || (bone_idx as usize) >= bone_transforms.len() {
                continue;
            }
            if let Some(ref body) = rb_data.bullet_body {
                // 骨骼(右手) → inv_z → 左手
                let bone_left = super::inv_z(bone_transforms[bone_idx as usize]);
                let body_matrix = rb_data.compute_body_matrix(bone_left);
                body.set_transform(body_matrix);
                body.set_linear_velocity(0.0, 0.0, 0.0);
                body.set_angular_velocity(0.0, 0.0, 0.0);
                body.clear_forces();
            }
        }
    }

    /// 重置物理系统
    pub fn reset(&mut self) {
        for rb_data in &self.rigid_bodies {
            if let Some(ref body) = rb_data.bullet_body {
                body.set_transform(rb_data.initial_transform);
                body.set_linear_velocity(0.0, 0.0, 0.0);
                body.set_angular_velocity(0.0, 0.0, 0.0);
                body.clear_forces();
            }
        }
    }

    /// 设置重力
    pub fn set_gravity(&self, x: f32, y: f32, z: f32) {
        self.world.set_gravity(x, y, z);
    }

    pub fn rigid_body_count(&self) -> usize { self.rigid_bodies.len() }
    pub fn joint_count(&self) -> usize { self.joints.len() }

    /// 获取动态刚体关联的骨骼变换（复用内部缓冲区，零堆分配）
    ///
    /// 从 Bullet3 读取左手空间变换，通过 inv_z 转回右手空间返回。
    pub fn get_dynamic_bone_transforms(&mut self, current_bone_transforms: &[Mat4]) -> &[(usize, Mat4)] {
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
                self.dynamic_bone_buf.push((bone_idx as usize, super::inv_z(new_bone_left)));
            }
        }
        &self.dynamic_bone_buf
    }

    /// 获取动态骨骼索引集合的引用（构建时已预计算，零分配）
    pub fn get_dynamic_bone_indices(&self) -> &HashSet<usize> {
        &self.dynamic_bone_indices
    }

    /// 获取 C++ 侧存活对象计数（调试用）
    pub fn alloc_stats() -> bullet_ffi::BulletAllocStats {
        bullet_ffi::get_alloc_stats()
    }
}

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
