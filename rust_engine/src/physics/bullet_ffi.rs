//! Bullet3 FFI 安全封装
//!
//! 将 C Wrapper (bw_api.h) 的原始指针封装为 Rust 安全类型，
//! 所有类型实现 Drop 自动释放资源。

use glam::{Mat4, Vec3};

// ===== C FFI 声明 =====
#[allow(non_camel_case_types, dead_code)]
mod ffi {
    use std::os::raw::{c_float, c_int};

    #[repr(C)]
    pub struct BW_World {
        _private: [u8; 0],
    }
    #[repr(C)]
    pub struct BW_Shape {
        _private: [u8; 0],
    }
    #[repr(C)]
    pub struct BW_RigidBody {
        _private: [u8; 0],
    }
    #[repr(C)]
    pub struct BW_Constraint {
        _private: [u8; 0],
    }

    /// C++ 分配计数器
    #[repr(C)]
    #[derive(Debug, Clone, Copy, Default)]
    pub struct BW_AllocStats {
        pub worlds: c_int,
        pub shapes: c_int,
        pub rigid_bodies: c_int,
        pub constraints: c_int,
        pub motion_states: c_int,
    }

    #[repr(C)]
    pub struct BW_RigidBodyInfo {
        pub mass: c_float,
        pub linear_damping: c_float,
        pub angular_damping: c_float,
        pub friction: c_float,
        pub restitution: c_float,
        pub additional_damping: bool,
        pub is_kinematic: bool,
        pub disable_deactivation: bool,
        pub no_contact_response: bool,
        pub shape: *mut BW_Shape,
        pub initial_transform: [c_float; 16],
    }

    #[repr(C)]
    #[derive(Clone, Copy)]
    pub struct BW_ContactManifold {
        pub body_a: *mut BW_RigidBody,
        pub body_b: *mut BW_RigidBody,
        pub contact_count: c_int,
        pub max_penetration_depth: c_float,
        pub max_applied_impulse: c_float,
        pub total_applied_impulse: c_float,
        pub point_a: [c_float; 3],
        pub point_b: [c_float; 3],
        pub normal_on_b: [c_float; 3],
    }

    #[repr(C)]
    #[derive(Clone, Copy, Default)]
    pub struct BW_ConstraintDiagnostic {
        pub linear_position: [c_float; 3],
        pub angular_position: [c_float; 3],
        pub linear_violation: [c_float; 3],
        pub angular_violation: [c_float; 3],
        pub frame_a: [c_float; 16],
        pub frame_b: [c_float; 16],
        pub linear_lower: [c_float; 3],
        pub linear_upper: [c_float; 3],
        pub angular_lower: [c_float; 3],
        pub angular_upper: [c_float; 3],
        pub stiffness: [c_float; 6],
        pub damping: [c_float; 6],
        pub equilibrium: [c_float; 6],
        pub spring_enabled: [c_int; 6],
        pub use_frame_offset: c_int,
    }

    extern "C" {
        // 分配统计
        pub fn bw_get_alloc_stats() -> BW_AllocStats;

        // 物理世界
        pub fn bw_world_create(gx: c_float, gy: c_float, gz: c_float) -> *mut BW_World;
        pub fn bw_world_destroy(world: *mut BW_World);
        pub fn bw_world_step(
            world: *mut BW_World,
            dt: c_float,
            max_substeps: c_int,
            fixed_dt: c_float,
        );
        pub fn bw_world_detect_collisions(world: *mut BW_World);
        pub fn bw_world_set_gravity(world: *mut BW_World, x: c_float, y: c_float, z: c_float);
        pub fn bw_world_add_rigid_body(
            world: *mut BW_World,
            rb: *mut BW_RigidBody,
            group: c_int,
            mask: c_int,
        );
        pub fn bw_world_remove_rigid_body(world: *mut BW_World, rb: *mut BW_RigidBody);
        pub fn bw_world_add_constraint(
            world: *mut BW_World,
            c: *mut BW_Constraint,
            disable_collision: bool,
        );
        pub fn bw_world_remove_constraint(world: *mut BW_World, c: *mut BW_Constraint);
        pub fn bw_world_set_kinematic_filter(world: *mut BW_World, enabled: bool);
        pub fn bw_world_get_contact_manifold_count(world: *mut BW_World) -> c_int;
        pub fn bw_world_copy_contact_manifolds(
            world: *mut BW_World,
            output: *mut BW_ContactManifold,
            capacity: c_int,
        ) -> c_int;

        // 碰撞形状
        pub fn bw_shape_sphere(radius: c_float) -> *mut BW_Shape;
        pub fn bw_shape_box(hx: c_float, hy: c_float, hz: c_float) -> *mut BW_Shape;
        pub fn bw_shape_capsule(radius: c_float, height: c_float) -> *mut BW_Shape;
        pub fn bw_shape_destroy(shape: *mut BW_Shape);

        // 刚体
        pub fn bw_rigid_body_create(info: *const BW_RigidBodyInfo) -> *mut BW_RigidBody;
        pub fn bw_rigid_body_destroy(rb: *mut BW_RigidBody);
        pub fn bw_rigid_body_get_transform(rb: *mut BW_RigidBody, matrix4x4: *mut c_float);
        pub fn bw_rigid_body_set_transform(rb: *mut BW_RigidBody, matrix4x4: *const c_float);
        pub fn bw_rigid_body_set_kinematic_target(rb: *mut BW_RigidBody, matrix4x4: *const c_float);
        pub fn bw_rigid_body_get_position(
            rb: *mut BW_RigidBody,
            x: *mut c_float,
            y: *mut c_float,
            z: *mut c_float,
        );
        pub fn bw_rigid_body_get_rotation(
            rb: *mut BW_RigidBody,
            x: *mut c_float,
            y: *mut c_float,
            z: *mut c_float,
            w: *mut c_float,
        );
        pub fn bw_rigid_body_set_linear_velocity(
            rb: *mut BW_RigidBody,
            x: c_float,
            y: c_float,
            z: c_float,
        );
        pub fn bw_rigid_body_set_angular_velocity(
            rb: *mut BW_RigidBody,
            x: c_float,
            y: c_float,
            z: c_float,
        );
        pub fn bw_rigid_body_get_linear_velocity(
            rb: *mut BW_RigidBody,
            x: *mut c_float,
            y: *mut c_float,
            z: *mut c_float,
        );
        pub fn bw_rigid_body_get_angular_velocity(
            rb: *mut BW_RigidBody,
            x: *mut c_float,
            y: *mut c_float,
            z: *mut c_float,
        );
        pub fn bw_rigid_body_set_damping(rb: *mut BW_RigidBody, linear: c_float, angular: c_float);
        pub fn bw_rigid_body_set_friction(rb: *mut BW_RigidBody, friction: c_float);
        pub fn bw_rigid_body_set_restitution(rb: *mut BW_RigidBody, restitution: c_float);
        pub fn bw_rigid_body_set_activation_state(rb: *mut BW_RigidBody, state: c_int);
        pub fn bw_rigid_body_force_activation_state(rb: *mut BW_RigidBody, state: c_int);
        pub fn bw_rigid_body_set_kinematic(rb: *mut BW_RigidBody, kinematic: bool);
        pub fn bw_rigid_body_get_mass(rb: *mut BW_RigidBody) -> c_float;
        pub fn bw_rigid_body_clear_forces(rb: *mut BW_RigidBody);
        pub fn bw_rigid_body_apply_central_force(
            rb: *mut BW_RigidBody,
            x: c_float,
            y: c_float,
            z: c_float,
        );
        pub fn bw_rigid_body_set_ignore_collision_check(
            rb: *mut BW_RigidBody,
            other: *mut BW_RigidBody,
            ignore: bool,
        );
        pub fn bw_rigid_body_check_collide_with(
            rb: *mut BW_RigidBody,
            other: *mut BW_RigidBody,
        ) -> bool;

        // 6DOF 弹簧约束
        pub fn bw_6dof_spring_create(
            a: *mut BW_RigidBody,
            b: *mut BW_RigidBody,
            frame_a: *const c_float,
            frame_b: *const c_float,
            use_linear_ref_a: bool,
        ) -> *mut BW_Constraint;
        pub fn bw_constraint_destroy(c: *mut BW_Constraint);
        pub fn bw_6dof_spring_set_linear_lower_limit(
            c: *mut BW_Constraint,
            x: c_float,
            y: c_float,
            z: c_float,
        );
        pub fn bw_6dof_spring_set_linear_upper_limit(
            c: *mut BW_Constraint,
            x: c_float,
            y: c_float,
            z: c_float,
        );
        pub fn bw_6dof_spring_set_angular_lower_limit(
            c: *mut BW_Constraint,
            x: c_float,
            y: c_float,
            z: c_float,
        );
        pub fn bw_6dof_spring_set_angular_upper_limit(
            c: *mut BW_Constraint,
            x: c_float,
            y: c_float,
            z: c_float,
        );
        pub fn bw_6dof_spring_enable_spring(c: *mut BW_Constraint, index: c_int, on: bool);
        pub fn bw_6dof_spring_set_stiffness(
            c: *mut BW_Constraint,
            index: c_int,
            stiffness: c_float,
        );
        pub fn bw_6dof_spring_set_damping(c: *mut BW_Constraint, index: c_int, damping: c_float);
        pub fn bw_6dof_spring_set_equilibrium_point(c: *mut BW_Constraint);
        pub fn bw_6dof_spring_set_param(
            c: *mut BW_Constraint,
            param: c_int,
            value: c_float,
            axis: c_int,
        );
        pub fn bw_6dof_spring_use_frame_offset(c: *mut BW_Constraint, on: bool);
        pub fn bw_6dof_spring_get_diagnostic(
            c: *mut BW_Constraint,
            output: *mut BW_ConstraintDiagnostic,
        ) -> bool;
    }
}

// Bullet3 约束参数常量
pub const BT_CONSTRAINT_STOP_ERP: i32 = 2;
#[allow(dead_code)]
pub const BT_CONSTRAINT_STOP_CFM: i32 = 3;

// 激活状态常量
#[allow(dead_code)]
pub const DISABLE_DEACTIVATION: i32 = 4;

// ===== 安全封装类型 =====

/// Bullet3 物理世界
pub struct BulletWorld {
    ptr: *mut ffi::BW_World,
}

// 单线程使用，但需要跨线程传递所有权
unsafe impl Send for BulletWorld {}

impl BulletWorld {
    /// 创建物理世界（C++ OOM 时返回 None）
    pub fn new(gravity_x: f32, gravity_y: f32, gravity_z: f32) -> Option<Self> {
        let ptr = unsafe { ffi::bw_world_create(gravity_x, gravity_y, gravity_z) };
        if ptr.is_null() {
            log::error!("[Bullet3] bw_world_create 失败：C++ 内存分配失败");
            return None;
        }
        Some(Self { ptr })
    }

    pub fn step(&self, dt: f32, max_substeps: i32, fixed_dt: f32) {
        unsafe { ffi::bw_world_step(self.ptr, dt, max_substeps, fixed_dt) }
    }

    /// 仅刷新碰撞检测结果，供初始化阶段读取首个求解步之前的接触。
    pub fn detect_collisions(&self) {
        unsafe { ffi::bw_world_detect_collisions(self.ptr) }
    }

    /// 复制当前求解步中仍处于穿透状态的真实接触流形。
    pub fn contact_manifolds(&self) -> Vec<ContactManifold> {
        let count = unsafe { ffi::bw_world_get_contact_manifold_count(self.ptr) }.max(0) as usize;
        if count == 0 {
            return Vec::new();
        }
        let empty = ffi::BW_ContactManifold {
            body_a: std::ptr::null_mut(),
            body_b: std::ptr::null_mut(),
            contact_count: 0,
            max_penetration_depth: 0.0,
            max_applied_impulse: 0.0,
            total_applied_impulse: 0.0,
            point_a: [0.0; 3],
            point_b: [0.0; 3],
            normal_on_b: [0.0; 3],
        };
        let mut raw = vec![empty; count];
        let written = unsafe {
            ffi::bw_world_copy_contact_manifolds(self.ptr, raw.as_mut_ptr(), count as i32)
        }
        .clamp(0, count as i32) as usize;
        raw.truncate(written);
        raw.into_iter().map(ContactManifold::from).collect()
    }

    pub fn set_gravity(&self, x: f32, y: f32, z: f32) {
        unsafe { ffi::bw_world_set_gravity(self.ptr, x, y, z) }
    }

    /// 添加刚体到世界（不获取所有权，刚体生命周期由调用方管理）
    pub fn add_rigid_body(&self, rb: &BulletRigidBody, group: i32, mask: i32) {
        unsafe { ffi::bw_world_add_rigid_body(self.ptr, rb.ptr, group, mask) }
    }

    pub fn remove_rigid_body(&self, rb: &BulletRigidBody) {
        unsafe { ffi::bw_world_remove_rigid_body(self.ptr, rb.ptr) }
    }

    pub fn add_constraint(&self, constraint: &BulletConstraint, disable_collision: bool) {
        unsafe { ffi::bw_world_add_constraint(self.ptr, constraint.ptr, disable_collision) }
    }

    pub fn remove_constraint(&self, constraint: &BulletConstraint) {
        unsafe { ffi::bw_world_remove_constraint(self.ptr, constraint.ptr) }
    }

    pub fn set_kinematic_filter(&self, enabled: bool) {
        unsafe { ffi::bw_world_set_kinematic_filter(self.ptr, enabled) }
    }
}

impl Drop for BulletWorld {
    fn drop(&mut self) {
        unsafe { ffi::bw_world_destroy(self.ptr) }
    }
}

/// Bullet3 碰撞形状
pub struct BulletShape {
    ptr: *mut ffi::BW_Shape,
}

unsafe impl Send for BulletShape {}

impl BulletShape {
    pub fn sphere(radius: f32) -> Option<Self> {
        let ptr = unsafe { ffi::bw_shape_sphere(radius) };
        if ptr.is_null() {
            return None;
        }
        Some(Self { ptr })
    }

    pub fn r#box(hx: f32, hy: f32, hz: f32) -> Option<Self> {
        let ptr = unsafe { ffi::bw_shape_box(hx, hy, hz) };
        if ptr.is_null() {
            return None;
        }
        Some(Self { ptr })
    }

    pub fn capsule(radius: f32, height: f32) -> Option<Self> {
        let ptr = unsafe { ffi::bw_shape_capsule(radius, height) };
        if ptr.is_null() {
            return None;
        }
        Some(Self { ptr })
    }

    /// 获取原始指针（用于构建刚体）
    pub fn as_ptr(&self) -> *mut ffi::BW_Shape {
        self.ptr
    }
}

impl Drop for BulletShape {
    fn drop(&mut self) {
        unsafe { ffi::bw_shape_destroy(self.ptr) }
    }
}

/// 刚体构建参数
pub struct RigidBodyInfo {
    pub mass: f32,
    pub linear_damping: f32,
    pub angular_damping: f32,
    pub friction: f32,
    pub restitution: f32,
    pub additional_damping: bool,
    pub is_kinematic: bool,
    pub disable_deactivation: bool,
    pub no_contact_response: bool,
    pub initial_transform: Mat4,
}

/// Bullet3 刚体
pub struct BulletRigidBody {
    ptr: *mut ffi::BW_RigidBody,
}

unsafe impl Send for BulletRigidBody {}

impl BulletRigidBody {
    /// 创建刚体（shape 不被获取所有权，调用方需保证 shape 生命周期覆盖刚体）
    pub fn new(info: &RigidBodyInfo, shape: &BulletShape) -> Option<Self> {
        let transform = mat4_to_col_major(info.initial_transform);
        let ffi_info = ffi::BW_RigidBodyInfo {
            mass: info.mass,
            linear_damping: info.linear_damping,
            angular_damping: info.angular_damping,
            friction: info.friction,
            restitution: info.restitution,
            additional_damping: info.additional_damping,
            is_kinematic: info.is_kinematic,
            disable_deactivation: info.disable_deactivation,
            no_contact_response: info.no_contact_response,
            shape: shape.as_ptr(),
            initial_transform: transform,
        };
        let ptr = unsafe { ffi::bw_rigid_body_create(&ffi_info) };
        if ptr.is_null() {
            log::error!("[Bullet3] bw_rigid_body_create 失败：C++ 内存分配失败");
            return None;
        }
        Some(Self { ptr })
    }

    /// 获取世界变换（4x4 列主序矩阵）
    pub fn get_transform(&self) -> Mat4 {
        let mut m = [0.0f32; 16];
        unsafe { ffi::bw_rigid_body_get_transform(self.ptr, m.as_mut_ptr()) }
        col_major_to_mat4(m)
    }

    /// 设置世界变换
    pub fn set_transform(&self, transform: Mat4) {
        let m = mat4_to_col_major(transform);
        unsafe { ffi::bw_rigid_body_set_transform(self.ptr, m.as_ptr()) }
    }

    /// 设置运动学刚体的下一物理目标，不覆盖 Bullet 保存的上一姿态。
    pub fn set_kinematic_target(&self, transform: Mat4) {
        let m = mat4_to_col_major(transform);
        unsafe { ffi::bw_rigid_body_set_kinematic_target(self.ptr, m.as_ptr()) }
    }

    /// 获取位置
    pub fn get_position(&self) -> glam::Vec3 {
        let (mut x, mut y, mut z) = (0.0f32, 0.0f32, 0.0f32);
        unsafe { ffi::bw_rigid_body_get_position(self.ptr, &mut x, &mut y, &mut z) }
        glam::Vec3::new(x, y, z)
    }

    /// 获取旋转四元数
    pub fn get_rotation(&self) -> glam::Quat {
        let (mut x, mut y, mut z, mut w) = (0.0f32, 0.0f32, 0.0f32, 0.0f32);
        unsafe { ffi::bw_rigid_body_get_rotation(self.ptr, &mut x, &mut y, &mut z, &mut w) }
        glam::Quat::from_xyzw(x, y, z, w)
    }

    pub fn set_linear_velocity(&self, x: f32, y: f32, z: f32) {
        unsafe { ffi::bw_rigid_body_set_linear_velocity(self.ptr, x, y, z) }
    }

    pub fn set_angular_velocity(&self, x: f32, y: f32, z: f32) {
        unsafe { ffi::bw_rigid_body_set_angular_velocity(self.ptr, x, y, z) }
    }

    /// 对称设置 Bullet 的精确刚体对忽略标志。
    ///
    /// 该调用只使用 Bullet 已有的 btCollisionObject 标志，不改变碰撞算法；
    /// 两个刚体均由调用方 Vec 持有，故指针在模型生命周期内保持有效。
    pub fn set_ignore_collision_check(&self, other: &Self, ignore: bool) {
        unsafe {
            ffi::bw_rigid_body_set_ignore_collision_check(self.ptr, other.ptr, ignore);
            ffi::bw_rigid_body_set_ignore_collision_check(other.ptr, self.ptr, ignore);
        }
    }

    /// 回读 Bullet 对该对象对的最终碰撞判定。
    pub fn check_collide_with(&self, other: &Self) -> bool {
        unsafe { ffi::bw_rigid_body_check_collide_with(self.ptr, other.ptr) }
    }

    pub fn get_linear_velocity(&self) -> glam::Vec3 {
        let (mut x, mut y, mut z) = (0.0f32, 0.0f32, 0.0f32);
        unsafe { ffi::bw_rigid_body_get_linear_velocity(self.ptr, &mut x, &mut y, &mut z) }
        glam::Vec3::new(x, y, z)
    }

    pub fn get_angular_velocity(&self) -> glam::Vec3 {
        let (mut x, mut y, mut z) = (0.0f32, 0.0f32, 0.0f32);
        unsafe { ffi::bw_rigid_body_get_angular_velocity(self.ptr, &mut x, &mut y, &mut z) }
        glam::Vec3::new(x, y, z)
    }

    pub fn set_damping(&self, linear: f32, angular: f32) {
        unsafe { ffi::bw_rigid_body_set_damping(self.ptr, linear, angular) }
    }

    pub fn set_kinematic(&self, kinematic: bool) {
        unsafe { ffi::bw_rigid_body_set_kinematic(self.ptr, kinematic) }
    }

    pub fn get_mass(&self) -> f32 {
        unsafe { ffi::bw_rigid_body_get_mass(self.ptr) }
    }

    pub fn clear_forces(&self) {
        unsafe { ffi::bw_rigid_body_clear_forces(self.ptr) }
    }

    pub fn apply_central_force(&self, x: f32, y: f32, z: f32) {
        unsafe { ffi::bw_rigid_body_apply_central_force(self.ptr, x, y, z) }
    }

    pub fn force_activation_state(&self, state: i32) {
        unsafe { ffi::bw_rigid_body_force_activation_state(self.ptr, state) }
    }

    /// 获取原始指针（用于创建约束）
    pub fn as_ptr(&self) -> *mut ffi::BW_RigidBody {
        self.ptr
    }
}

impl Drop for BulletRigidBody {
    fn drop(&mut self) {
        unsafe { ffi::bw_rigid_body_destroy(self.ptr) }
    }
}

/// Bullet3 6DOF 弹簧约束
pub struct BulletConstraint {
    ptr: *mut ffi::BW_Constraint,
}

unsafe impl Send for BulletConstraint {}

impl BulletConstraint {
    /// 创建 6DOF 弹簧约束（C++ OOM 时返回 None）
    pub fn new_6dof_spring(
        rb_a: &BulletRigidBody,
        rb_b: &BulletRigidBody,
        frame_a: Mat4,
        frame_b: Mat4,
        use_linear_ref_a: bool,
    ) -> Option<Self> {
        let fa = mat4_to_col_major(frame_a);
        let fb = mat4_to_col_major(frame_b);
        let ptr = unsafe {
            ffi::bw_6dof_spring_create(
                rb_a.as_ptr(),
                rb_b.as_ptr(),
                fa.as_ptr(),
                fb.as_ptr(),
                use_linear_ref_a,
            )
        };
        if ptr.is_null() {
            log::error!("[Bullet3] bw_6dof_spring_create 失败：C++ 内存分配失败");
            return None;
        }
        Some(Self { ptr })
    }

    pub fn set_linear_lower_limit(&self, x: f32, y: f32, z: f32) {
        unsafe { ffi::bw_6dof_spring_set_linear_lower_limit(self.ptr, x, y, z) }
    }

    pub fn set_linear_upper_limit(&self, x: f32, y: f32, z: f32) {
        unsafe { ffi::bw_6dof_spring_set_linear_upper_limit(self.ptr, x, y, z) }
    }

    pub fn set_angular_lower_limit(&self, x: f32, y: f32, z: f32) {
        unsafe { ffi::bw_6dof_spring_set_angular_lower_limit(self.ptr, x, y, z) }
    }

    pub fn set_angular_upper_limit(&self, x: f32, y: f32, z: f32) {
        unsafe { ffi::bw_6dof_spring_set_angular_upper_limit(self.ptr, x, y, z) }
    }

    pub fn enable_spring(&self, index: i32, on: bool) {
        unsafe { ffi::bw_6dof_spring_enable_spring(self.ptr, index, on) }
    }

    pub fn set_stiffness(&self, index: i32, stiffness: f32) {
        unsafe { ffi::bw_6dof_spring_set_stiffness(self.ptr, index, stiffness) }
    }

    pub fn set_damping(&self, index: i32, damping: f32) {
        unsafe { ffi::bw_6dof_spring_set_damping(self.ptr, index, damping) }
    }

    pub fn set_equilibrium_point(&self) {
        unsafe { ffi::bw_6dof_spring_set_equilibrium_point(self.ptr) }
    }

    pub fn set_param(&self, param: i32, value: f32, axis: i32) {
        unsafe { ffi::bw_6dof_spring_set_param(self.ptr, param, value, axis) }
    }

    pub fn use_frame_offset(&self, on: bool) {
        unsafe { ffi::bw_6dof_spring_use_frame_offset(self.ptr, on) }
    }

    /// 读取 Bullet 按自身 XYZ 欧拉语义计算的逐轴位置与限位违规。
    pub fn diagnostic(&self) -> Option<ConstraintDiagnostic> {
        let mut raw = ffi::BW_ConstraintDiagnostic::default();
        unsafe { ffi::bw_6dof_spring_get_diagnostic(self.ptr, &mut raw) }
            .then(|| ConstraintDiagnostic::from(raw))
    }
}

impl Drop for BulletConstraint {
    fn drop(&mut self) {
        unsafe { ffi::bw_constraint_destroy(self.ptr) }
    }
}

// ===== 分配统计 =====

/// C++ 侧存活对象计数
#[derive(Debug, Clone, Copy, Default)]
pub struct BulletAllocStats {
    pub worlds: i32,
    pub shapes: i32,
    pub rigid_bodies: i32,
    pub constraints: i32,
    pub motion_states: i32,
}

impl BulletAllocStats {
    /// 检查是否所有计数为零（无泄漏）
    pub fn is_clean(&self) -> bool {
        self.worlds == 0
            && self.shapes == 0
            && self.rigid_bodies == 0
            && self.constraints == 0
            && self.motion_states == 0
    }
}

/// 获取 C++ 侧当前存活的 Bullet3 对象计数
pub fn get_alloc_stats() -> BulletAllocStats {
    let s = unsafe { ffi::bw_get_alloc_stats() };
    BulletAllocStats {
        worlds: s.worlds,
        shapes: s.shapes,
        rigid_bodies: s.rigid_bodies,
        constraints: s.constraints,
        motion_states: s.motion_states,
    }
}

// ===== 工具函数 =====

/// glam Mat4 → 列主序 float[16]
fn mat4_to_col_major(m: Mat4) -> [f32; 16] {
    m.to_cols_array()
}

/// 列主序 float[16] → glam Mat4
fn col_major_to_mat4(m: [f32; 16]) -> Mat4 {
    Mat4::from_cols_array(&m)
}

#[derive(Debug, Clone, Copy)]
pub struct ContactManifold {
    pub body_a: usize,
    pub body_b: usize,
    pub contact_count: i32,
    pub max_penetration_depth: f32,
    pub max_applied_impulse: f32,
    pub total_applied_impulse: f32,
    pub point_a: Vec3,
    pub point_b: Vec3,
    /// Bullet 原始 normalWorldOnB，方向从 B 指向 A。
    pub normal_on_b: Vec3,
}

impl From<ffi::BW_ContactManifold> for ContactManifold {
    fn from(value: ffi::BW_ContactManifold) -> Self {
        Self {
            body_a: value.body_a as usize,
            body_b: value.body_b as usize,
            contact_count: value.contact_count,
            max_penetration_depth: value.max_penetration_depth,
            max_applied_impulse: value.max_applied_impulse,
            total_applied_impulse: value.total_applied_impulse,
            point_a: Vec3::from_array(value.point_a),
            point_b: Vec3::from_array(value.point_b),
            normal_on_b: Vec3::from_array(value.normal_on_b),
        }
    }
}

#[derive(Debug, Clone, Copy)]
pub struct ConstraintDiagnostic {
    pub linear_position: Vec3,
    pub angular_position: Vec3,
    pub linear_violation: Vec3,
    pub angular_violation: Vec3,
    pub frame_a: Mat4,
    pub frame_b: Mat4,
    pub linear_lower: Vec3,
    pub linear_upper: Vec3,
    pub angular_lower: Vec3,
    pub angular_upper: Vec3,
    pub stiffness: [f32; 6],
    pub damping: [f32; 6],
    pub equilibrium: [f32; 6],
    pub spring_enabled: [bool; 6],
    pub use_frame_offset: bool,
}

impl From<ffi::BW_ConstraintDiagnostic> for ConstraintDiagnostic {
    fn from(value: ffi::BW_ConstraintDiagnostic) -> Self {
        Self {
            linear_position: Vec3::from_array(value.linear_position),
            angular_position: Vec3::from_array(value.angular_position),
            linear_violation: Vec3::from_array(value.linear_violation),
            angular_violation: Vec3::from_array(value.angular_violation),
            frame_a: col_major_to_mat4(value.frame_a),
            frame_b: col_major_to_mat4(value.frame_b),
            linear_lower: Vec3::from_array(value.linear_lower),
            linear_upper: Vec3::from_array(value.linear_upper),
            angular_lower: Vec3::from_array(value.angular_lower),
            angular_upper: Vec3::from_array(value.angular_upper),
            stiffness: value.stiffness,
            damping: value.damping,
            equilibrium: value.equilibrium,
            spring_enabled: value.spring_enabled.map(|enabled| enabled != 0),
            use_frame_offset: value.use_frame_offset != 0,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::{BulletConstraint, BulletRigidBody, BulletShape, BulletWorld, RigidBodyInfo};
    use glam::{Mat4, Quat, Vec3};

    fn body(shape: &BulletShape, transform: Mat4) -> BulletRigidBody {
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
                initial_transform: transform,
            },
            shape,
        )
        .expect("应能创建 Bullet 测试刚体")
    }

    fn kinematic_body(shape: &BulletShape, transform: Mat4) -> BulletRigidBody {
        BulletRigidBody::new(
            &RigidBodyInfo {
                mass: 0.0,
                linear_damping: 0.0,
                angular_damping: 0.0,
                friction: 0.5,
                restitution: 0.0,
                additional_damping: false,
                is_kinematic: true,
                disable_deactivation: true,
                no_contact_response: false,
                initial_transform: transform,
            },
            shape,
        )
        .expect("应能创建 Bullet 运动学测试刚体")
    }

    fn assert_vec3_close(actual: Vec3, expected: Vec3) {
        assert!(
            actual.abs_diff_eq(expected, 1e-5),
            "actual={actual:?}, expected={expected:?}"
        );
    }

    #[test]
    fn six_dof_round_trip_preserves_frames_limits_and_defaults() {
        let shape = BulletShape::sphere(0.25).expect("应能创建 Bullet 测试形状");
        let transform_a = Mat4::from_rotation_translation(
            Quat::from_euler(glam::EulerRot::XYZ, 0.21, -0.37, 0.44),
            Vec3::new(1.2, -0.8, 2.4),
        );
        let transform_b = Mat4::from_rotation_translation(
            Quat::from_euler(glam::EulerRot::XYZ, -0.18, 0.29, -0.51),
            Vec3::new(-0.7, 1.6, 0.3),
        );
        let joint_transform = Mat4::from_rotation_translation(
            Quat::from_rotation_z(0.63)
                * Quat::from_rotation_y(-0.42)
                * Quat::from_rotation_x(0.17),
            Vec3::new(0.4, 0.9, -1.1),
        );
        let frame_a = transform_a.inverse() * joint_transform;
        let frame_b = transform_b.inverse() * joint_transform;
        let body_a = body(&shape, transform_a);
        let body_b = body(&shape, transform_b);
        let constraint =
            BulletConstraint::new_6dof_spring(&body_a, &body_b, frame_a, frame_b, true)
                .expect("应能创建 Bullet 测试约束");

        let linear_lower = Vec3::new(-0.3, -0.2, -0.1);
        let linear_upper = Vec3::new(0.4, 0.5, 0.6);
        let angular_lower = Vec3::new(-0.7, -0.5, -0.3);
        let angular_upper = Vec3::new(0.2, 0.4, 0.8);
        constraint.set_linear_lower_limit(linear_lower.x, linear_lower.y, linear_lower.z);
        constraint.set_linear_upper_limit(linear_upper.x, linear_upper.y, linear_upper.z);
        constraint.set_angular_lower_limit(angular_lower.x, angular_lower.y, angular_lower.z);
        constraint.set_angular_upper_limit(angular_upper.x, angular_upper.y, angular_upper.z);

        let diagnostic = constraint.diagnostic().expect("应能回读约束状态");
        assert!(diagnostic.frame_a.abs_diff_eq(frame_a, 1e-5));
        assert!(diagnostic.frame_b.abs_diff_eq(frame_b, 1e-5));
        assert_vec3_close(diagnostic.linear_position, Vec3::ZERO);
        assert_vec3_close(diagnostic.angular_position, Vec3::ZERO);
        assert_vec3_close(diagnostic.linear_lower, linear_lower);
        assert_vec3_close(diagnostic.linear_upper, linear_upper);
        assert_vec3_close(diagnostic.angular_lower, angular_lower);
        assert_vec3_close(diagnostic.angular_upper, angular_upper);
        assert_eq!(diagnostic.equilibrium, [0.0; 6]);
        assert_eq!(diagnostic.damping, [1.0; 6]);
        assert_eq!(diagnostic.spring_enabled, [false; 6]);
        assert!(diagnostic.use_frame_offset);
    }

    #[test]
    fn kinematic_target_preserves_motion_for_bullet_velocity_calculation() {
        let shape = BulletShape::sphere(0.25).expect("应能创建 Bullet 测试形状");
        let body = kinematic_body(&shape, Mat4::IDENTITY);
        let world = BulletWorld::new(0.0, 0.0, 0.0).expect("应能创建 Bullet 测试世界");
        world.add_rigid_body(&body, 1, -1);

        let dt = 1.0 / 60.0;
        body.set_kinematic_target(Mat4::from_translation(Vec3::X));
        world.step(dt, 1, dt);

        assert_vec3_close(body.get_transform().w_axis.truncate(), Vec3::X);
        assert_vec3_close(body.get_linear_velocity(), Vec3::new(60.0, 0.0, 0.0));
        world.remove_rigid_body(&body);
    }

    #[test]
    fn kinematic_target_is_applied_before_constraint_solving() {
        let shape = BulletShape::sphere(0.25).expect("应能创建 Bullet 测试形状");
        let parent = kinematic_body(&shape, Mat4::IDENTITY);
        let child = body(&shape, Mat4::IDENTITY);
        let world = BulletWorld::new(0.0, 0.0, 0.0).expect("应能创建 Bullet 测试世界");
        world.add_rigid_body(&parent, 1, -1);
        world.add_rigid_body(&child, 1, -1);
        let constraint = BulletConstraint::new_6dof_spring(
            &parent,
            &child,
            Mat4::IDENTITY,
            Mat4::IDENTITY,
            true,
        )
        .expect("应能创建运动学父体约束");
        constraint.set_linear_lower_limit(0.0, 0.0, 0.0);
        constraint.set_linear_upper_limit(0.0, 0.0, 0.0);
        world.add_constraint(&constraint, true);

        parent.set_kinematic_target(Mat4::from_translation(Vec3::X));
        let mut previous_violation = 1.0;
        for _ in 0..6 {
            world.step(1.0 / 60.0, 1, 1.0 / 60.0);

            // Bullet 按 ERP 分步纠偏，锚点误差应持续收敛而不是首步归零。
            let diagnostic = constraint.diagnostic().expect("应能回读约束状态");
            let violation = diagnostic.linear_violation.max_element();
            assert!(
                violation < previous_violation,
                "运动学父体的锚点误差未收敛: previous={previous_violation}, current={violation}"
            );
            previous_violation = violation;
        }
        assert!(
            previous_violation < 0.1,
            "6 个求解步后的锚点残差为 {previous_violation}"
        );
        assert_vec3_close(parent.get_transform().w_axis.truncate(), Vec3::X);
        world.remove_constraint(&constraint);
        world.remove_rigid_body(&child);
        world.remove_rigid_body(&parent);
    }

    #[test]
    fn hard_transform_reset_does_not_create_kinematic_velocity() {
        let shape = BulletShape::sphere(0.25).expect("应能创建 Bullet 测试形状");
        let body = kinematic_body(&shape, Mat4::IDENTITY);
        let world = BulletWorld::new(0.0, 0.0, 0.0).expect("应能创建 Bullet 测试世界");
        world.add_rigid_body(&body, 1, -1);

        let reset_transform = Mat4::from_translation(Vec3::new(20.0, 0.0, 0.0));
        body.set_transform(reset_transform);
        body.set_linear_velocity(0.0, 0.0, 0.0);
        body.set_angular_velocity(0.0, 0.0, 0.0);
        body.clear_forces();
        world.step(1.0 / 60.0, 1, 1.0 / 60.0);

        assert_vec3_close(
            body.get_transform().w_axis.truncate(),
            Vec3::new(20.0, 0.0, 0.0),
        );
        assert_vec3_close(body.get_linear_velocity(), Vec3::ZERO);
        assert_vec3_close(body.get_angular_velocity(), Vec3::ZERO);
        world.remove_rigid_body(&body);
    }

    #[test]
    fn ignored_pair_does_not_disable_other_collision_pairs() {
        let shape = BulletShape::sphere(0.5).expect("应能创建 Bullet 测试形状");
        let body_a = body(&shape, Mat4::IDENTITY);
        let body_b = body(&shape, Mat4::from_translation(Vec3::new(0.25, 0.0, 0.0)));
        let body_c = body(&shape, Mat4::from_translation(Vec3::new(-0.25, 0.0, 0.0)));
        let world = BulletWorld::new(0.0, 0.0, 0.0).expect("应能创建 Bullet 测试世界");
        world.add_rigid_body(&body_a, 1, -1);
        world.add_rigid_body(&body_b, 1, -1);
        world.add_rigid_body(&body_c, 1, -1);

        // 局部过滤只能禁用指定刚体对，不能影响同组中的其他碰撞。
        body_a.set_ignore_collision_check(&body_b, true);
        assert!(!body_a.check_collide_with(&body_b));
        assert!(!body_b.check_collide_with(&body_a));
        assert!(body_a.check_collide_with(&body_c));

        world.step(1.0 / 60.0, 1, 1.0 / 60.0);
        let contacts = world.contact_manifolds();
        assert!(contacts.iter().all(|contact| {
            let pair = (contact.body_a, contact.body_b);
            pair != (body_a.as_ptr() as usize, body_b.as_ptr() as usize)
                && pair != (body_b.as_ptr() as usize, body_a.as_ptr() as usize)
        }));
        assert!(contacts.iter().any(|contact| {
            let pair = (contact.body_a, contact.body_b);
            pair == (body_a.as_ptr() as usize, body_c.as_ptr() as usize)
                || pair == (body_c.as_ptr() as usize, body_a.as_ptr() as usize)
        }));

        world.remove_rigid_body(&body_a);
        world.remove_rigid_body(&body_b);
        world.remove_rigid_body(&body_c);
    }
}
