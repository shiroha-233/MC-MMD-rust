// 负责模型物理状态与全局物理配置入口。


// 重置物理系统

// 启用/禁用物理
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetPhysicsEnabled(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    enabled: jboolean,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_physics_enabled(enabled != 0);
    }
}

// 获取物理是否启用

// 获取物理是否已初始化

// 获取物理调试信息（返回 JSON 字符串）

// ============================================================================
// 材质可见性控制（用于脱外套等功能）
// ============================================================================

// 获取材质是否可见
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetPhysicsConfig(
    _env: JNIEnv,
    _class: JClass,
    enabled: jboolean,
    gravity_y: jfloat,
    physics_fps: jfloat,
    max_substep_count: jint,
    inertia_strength: jfloat,
    max_linear_velocity: jfloat,
    max_angular_velocity: jfloat,
    joints_enabled: jboolean,
    kinematic_filter: jboolean,
    debug_log: jboolean,
) {
    use crate::physics::config::{set_config, PhysicsConfig};

    let config = PhysicsConfig {
        enabled: enabled != 0,
        gravity_y,
        physics_fps,
        max_substep_count,
        inertia_strength,
        max_linear_velocity,
        max_angular_velocity,
        joints_enabled: joints_enabled != 0,
        kinematic_filter: kinematic_filter != 0,
        debug_log: debug_log != 0,
    };

    set_config(config);

    if debug_log != 0 {
        log::info!(
            "[Bullet3 物理配置] 重力={}, FPS={}, 惯性={}",
            gravity_y,
            physics_fps,
            inertia_strength
        );
    }
}

// ========== 第一人称模式相关 ==========

// 设置第一人称模式（启用时自动隐藏头部子网格，禁用时恢复）

// ============================================================================
// 死亡布娃娃
// ============================================================================

/// 布娃娃参数数组长度（RagdollEnable 的 params 约定）
const RAGDOLL_PARAMS_LEN: usize = 16;

/// 启用死亡布娃娃
///
/// params 布局（16 float，缺省段填 0 用默认值）：
/// ```text
/// [0]  groundLocalY      模型局部地面高度（模型单位）
/// [1-3] velWorldX/Y/Z    死亡初速度（世界 m/s）
/// [4]  yaw               实体朝向（弧度，速度换算用）
/// [5]  thickness         碰撞体厚度缩放（0 → 1.0）
/// [6]  friction          摩擦系数（0 → 0.55）
/// [7]  restitution       恢复系数（0 → 0.06）
/// [8]  linearDamping     线性阻尼（0 → 0.06）
/// [9]  angularDamping    角阻尼（0 → 0.35）
/// [10] gravityY          重力（模型单位/s²，0 → -109）
/// [11] jointStrength     关节限位强度（0 → 1.35）
/// [12] totalMassKg       总质量 kg（0 → 50）
/// [13] armLimit          手臂限位乘数（0 → 0.8）
/// [14] worldToModelScale 世界米 → 模型单位换算（0.09 × userScale，速度换算用）
/// [15] 保留
/// ```
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_RagdollEnable(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    params: JFloatArray,
) -> jboolean {
    let mut buf = [0.0f32; RAGDOLL_PARAMS_LEN];
    if env.get_float_array_region(&params, 0, &mut buf).is_err() {
        return 0;
    }

    let mut p = crate::ragdoll::RagdollParams::default();
    p.ground_local_y = buf[0];
    let yaw = buf[4];
    // 世界速度 → 模型局部（R^T·v）→ Bullet 空间 Z 翻转 → 模型单位
    let scale = if buf[14].abs() > 1e-6 { buf[14] } else { 0.09 };
    // 把单位换算带给引擎：速度上限、逃逸深度这类"世界量纲"要按同一个换算折算到模型单位。
    p.unit_to_m = scale;
    let (vx, vy, vz) = (buf[1], buf[2], buf[3]);
    let (cos_y, sin_y) = (yaw.cos(), yaw.sin());
    let lx = cos_y * vx + sin_y * vz;
    let lz = -sin_y * vx + cos_y * vz;
    p.initial_velocity = glam::Vec3::new(lx / scale, vy / scale, -lz / scale);
    if buf[5] > 0.0 {
        p.thickness = buf[5];
    }
    if buf[6] > 0.0 {
        p.friction = buf[6];
    }
    if buf[7] > 0.0 {
        p.restitution = buf[7];
    }
    if buf[8] > 0.0 {
        p.linear_damping = buf[8];
    }
    if buf[9] > 0.0 {
        p.angular_damping = buf[9];
    }
    if buf[10] < 0.0 {
        p.gravity_y = buf[10];
    }
    if buf[11] > 0.0 {
        p.joint_strength = buf[11];
    }
    if buf[12] > 0.0 {
        p.total_mass_kg = buf[12];
    }
    if buf[13] > 0.0 {
        p.arm_limit = buf[13];
    }

    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut m = model_arc.lock().unwrap();
        if m.enable_ragdoll(p) {
            1
        } else {
            0
        }
    } else {
        0
    }
}

/// 冻结/解冻布娃娃（冻结 = 姿势保持 + 跳过步进）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_RagdollSetFrozen(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    frozen: jboolean,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut m = model_arc.lock().unwrap();
        m.set_ragdoll_frozen(frozen != 0);
    }
}

/// 禁用并拆除布娃娃
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_RagdollDisable(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut m = model_arc.lock().unwrap();
        m.disable_ragdoll();
    }
}

/// 设置布娃娃地面高度（模型局部单位）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_RagdollSetGround(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    ground_local_y: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut m = model_arc.lock().unwrap();
        m.ragdoll_set_ground_y(ground_local_y);
    }
}

/// 施加冲量（世界 m/s 速度增量，作用于全身或仅躯干+头）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_RagdollApplyVelocityDelta(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    vx: jfloat,
    vy: jfloat,
    vz: jfloat,
    yaw: jfloat,
    world_to_model_scale: jfloat,
    upper_body_only: jboolean,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut m = model_arc.lock().unwrap();
        m.ragdoll_apply_world_velocity_delta(
            vx,
            vy,
            vz,
            yaw,
            world_to_model_scale,
            upper_body_only != 0,
        );
    }
}

/// 布娃娃是否激活
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_RagdollIsActive(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jboolean {
    let models = MODELS.read().unwrap();
    if let Some(m) = models.get(&model) {
        let m = m.lock().unwrap();
        if m.is_ragdoll_active() {
            1
        } else {
            0
        }
    } else {
        0
    }
}

/// 布娃娃调试信息（JSON）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_RagdollGetDebugInfo(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jstring {
    let info = {
        let models = MODELS.read().unwrap();
        match models.get(&model) {
            Some(m) => {
                let m = m.lock().unwrap();
                m.ragdoll_debug_info()
            }
            None => String::from("{\"active\": false}"),
        }
    };
    match env.new_string(&info) {
        Ok(s) => s.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}
