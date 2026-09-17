// 负责模型物理状态与全局物理配置入口。
// include! 进 runtime_bindings.rs，共享其 use（MODELS / jni 类型等）。

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
// 全局物理配置（12 参：含 collision_enabled / collision_stability_mode，
// 移植自 1.20-vr 物理大改，配置变更时触发既有模型物理重建）
// ============================================================================
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
    collision_enabled: jboolean,
    collision_stability_mode: jint,
    debug_log: jboolean,
) {
    use crate::physics::CollisionStabilityMode;
    use crate::physics::config::{get_config, set_config, PhysicsConfig};

    // JNI 传入的浮点参数不做信任：非有限值回退默认，速度类取绝对值，
    // FPS/子步数 clamp 到合法范围，避免 0/NaN/负值把 Bullet 世界推入发散。
    let default = PhysicsConfig::default();
    let finite_or = |value: jfloat, fallback: f32| {
        if value.is_finite() {
            value
        } else {
            fallback
        }
    };
    let abs_or = |value: jfloat, fallback: f32| {
        if value.is_finite() {
            value.abs()
        } else {
            fallback
        }
    };
    let gravity_y = finite_or(gravity_y, default.gravity_y);
    let physics_fps = finite_or(physics_fps, default.physics_fps).clamp(1.0, 240.0);
    let max_substep_count = max_substep_count.clamp(1, 64);
    let inertia_strength = abs_or(inertia_strength, default.inertia_strength);
    let max_linear_velocity = abs_or(max_linear_velocity, default.max_linear_velocity);
    let max_angular_velocity = abs_or(max_angular_velocity, default.max_angular_velocity);

    let previous = get_config();
    // 旧调用方传入未知枚举值时统一回退默认 Stable。
    let collision_stability_mode = CollisionStabilityMode::from_i32(collision_stability_mode);
    let config = PhysicsConfig {
        enabled: enabled != 0,
        gravity_y,
        physics_fps,
        max_substep_count: max_substep_count as i32,
        inertia_strength,
        max_linear_velocity,
        max_angular_velocity,
        joints_enabled: joints_enabled != 0,
        collision_enabled: collision_enabled != 0,
        collision_stability_mode,
        kinematic_filter: kinematic_filter != 0,
        debug_log: debug_log != 0,
    };

    // 这些参数在 Bullet 世界或 MMDPhysics 构造时缓存，变化后需重建现有模型物理。
    let rebuild_required = previous.gravity_y != config.gravity_y
        || previous.physics_fps != config.physics_fps
        || previous.max_substep_count != config.max_substep_count
        || previous.joints_enabled != config.joints_enabled
        || previous.collision_enabled != config.collision_enabled
        || previous.collision_stability_mode != config.collision_stability_mode
        || previous.kinematic_filter != config.kinematic_filter;

    set_config(config);

    if rebuild_required {
        // 26.2 架构：模型注册表在 NativeRuntime 内，取快照遍历（不持有读锁）。
        if let Ok(models) = NATIVE_RUNTIME.models_snapshot() {
            for model_arc in models {
                let mut model = model_arc.lock().unwrap();
                model.request_physics_rebuild();
            }
        }
    }

    if debug_log != 0 {
        log::info!(
            "[Bullet3 物理配置] 重力={}, FPS={}, 惯性={}, 碰撞稳定模式={}",
            gravity_y,
            physics_fps,
            inertia_strength,
            collision_stability_mode.as_str()
        );
    }
}

// ============================================================================
// 材质可见性控制（用于脱外套等功能）
// ============================================================================

// 获取材质是否可见

// ========== 第一人称模式相关 ==========

// 设置第一人称模式（启用时自动隐藏头部子网格，禁用时恢复）
