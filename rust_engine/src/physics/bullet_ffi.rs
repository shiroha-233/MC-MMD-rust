//! Bullet3 FFI 安全封装
//!
//! 将 C Wrapper (bw_api.h) 的原始指针封装为 Rust 安全类型，
//! 所有类型实现 Drop 自动释放资源。

use glam::Mat4;

// ===== C FFI 声明 =====
#[allow(non_camel_case_types, dead_code)]
mod ffi {
    use std::os::raw::{c_float, c_int};

    #[repr(C)]
    pub struct BW_World { _private: [u8; 0] }
    #[repr(C)]
    pub struct BW_Shape { _private: [u8; 0] }
    #[repr(C)]
    pub struct BW_RigidBody { _private: [u8; 0] }
    #[repr(C)]
    pub struct BW_Constraint { _private: [u8; 0] }

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

    extern "C" {
        // 分配统计
        pub fn bw_get_alloc_stats() -> BW_AllocStats;

        // 物理世界
        pub fn bw_world_create(gx: c_float, gy: c_float, gz: c_float) -> *mut BW_World;
        pub fn bw_world_destroy(world: *mut BW_World);
        pub fn bw_world_step(world: *mut BW_World, dt: c_float, max_substeps: c_int, fixed_dt: c_float);
        pub fn bw_world_set_gravity(world: *mut BW_World, x: c_float, y: c_float, z: c_float);
        pub fn bw_world_add_rigid_body(world: *mut BW_World, rb: *mut BW_RigidBody, group: c_int, mask: c_int);
        pub fn bw_world_remove_rigid_body(world: *mut BW_World, rb: *mut BW_RigidBody);
        pub fn bw_world_add_constraint(world: *mut BW_World, c: *mut BW_Constraint, disable_collision: bool);
        pub fn bw_world_remove_constraint(world: *mut BW_World, c: *mut BW_Constraint);

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
        pub fn bw_rigid_body_get_position(rb: *mut BW_RigidBody, x: *mut c_float, y: *mut c_float, z: *mut c_float);
        pub fn bw_rigid_body_get_rotation(rb: *mut BW_RigidBody, x: *mut c_float, y: *mut c_float, z: *mut c_float, w: *mut c_float);
        pub fn bw_rigid_body_set_linear_velocity(rb: *mut BW_RigidBody, x: c_float, y: c_float, z: c_float);
        pub fn bw_rigid_body_set_angular_velocity(rb: *mut BW_RigidBody, x: c_float, y: c_float, z: c_float);
        pub fn bw_rigid_body_get_linear_velocity(rb: *mut BW_RigidBody, x: *mut c_float, y: *mut c_float, z: *mut c_float);
        pub fn bw_rigid_body_set_damping(rb: *mut BW_RigidBody, linear: c_float, angular: c_float);
        pub fn bw_rigid_body_set_friction(rb: *mut BW_RigidBody, friction: c_float);
        pub fn bw_rigid_body_set_restitution(rb: *mut BW_RigidBody, restitution: c_float);
        pub fn bw_rigid_body_set_activation_state(rb: *mut BW_RigidBody, state: c_int);
        pub fn bw_rigid_body_force_activation_state(rb: *mut BW_RigidBody, state: c_int);
        pub fn bw_rigid_body_set_kinematic(rb: *mut BW_RigidBody, kinematic: bool);
        pub fn bw_rigid_body_get_mass(rb: *mut BW_RigidBody) -> c_float;
        pub fn bw_rigid_body_clear_forces(rb: *mut BW_RigidBody);

        // 6DOF 弹簧约束
        pub fn bw_6dof_spring_create(
            a: *mut BW_RigidBody, b: *mut BW_RigidBody,
            frame_a: *const c_float, frame_b: *const c_float,
            use_linear_ref_a: bool,
        ) -> *mut BW_Constraint;
        pub fn bw_constraint_destroy(c: *mut BW_Constraint);
        pub fn bw_6dof_spring_set_linear_lower_limit(c: *mut BW_Constraint, x: c_float, y: c_float, z: c_float);
        pub fn bw_6dof_spring_set_linear_upper_limit(c: *mut BW_Constraint, x: c_float, y: c_float, z: c_float);
        pub fn bw_6dof_spring_set_angular_lower_limit(c: *mut BW_Constraint, x: c_float, y: c_float, z: c_float);
        pub fn bw_6dof_spring_set_angular_upper_limit(c: *mut BW_Constraint, x: c_float, y: c_float, z: c_float);
        pub fn bw_6dof_spring_enable_spring(c: *mut BW_Constraint, index: c_int, on: bool);
        pub fn bw_6dof_spring_set_stiffness(c: *mut BW_Constraint, index: c_int, stiffness: c_float);
        pub fn bw_6dof_spring_set_damping(c: *mut BW_Constraint, index: c_int, damping: c_float);
        pub fn bw_6dof_spring_set_equilibrium_point(c: *mut BW_Constraint);
        pub fn bw_6dof_spring_set_param(c: *mut BW_Constraint, param: c_int, value: c_float, axis: c_int);
        pub fn bw_6dof_spring_use_frame_offset(c: *mut BW_Constraint, on: bool);
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
        if ptr.is_null() { return None; }
        Some(Self { ptr })
    }

    pub fn r#box(hx: f32, hy: f32, hz: f32) -> Option<Self> {
        let ptr = unsafe { ffi::bw_shape_box(hx, hy, hz) };
        if ptr.is_null() { return None; }
        Some(Self { ptr })
    }

    pub fn capsule(radius: f32, height: f32) -> Option<Self> {
        let ptr = unsafe { ffi::bw_shape_capsule(radius, height) };
        if ptr.is_null() { return None; }
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

    pub fn get_linear_velocity(&self) -> glam::Vec3 {
        let (mut x, mut y, mut z) = (0.0f32, 0.0f32, 0.0f32);
        unsafe { ffi::bw_rigid_body_get_linear_velocity(self.ptr, &mut x, &mut y, &mut z) }
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
                rb_a.as_ptr(), rb_b.as_ptr(),
                fa.as_ptr(), fb.as_ptr(),
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
