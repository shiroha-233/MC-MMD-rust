//! TaCZ 瞬态双臂目标的 JNI 缓冲区。
//!
//! 目标按模型句柄暂存一次；主线程在动画评估后取走时会原子清除，绝不回放上一帧。

use std::collections::HashMap;
use std::sync::Mutex;

use jni::objects::{JClass, JFloatArray};
use jni::sys::{jboolean, jint, jlong};
use jni::JNIEnv;
use once_cell::sync::Lazy;

use crate::model::tacz_arm_targets::{TaczArmApplyOutcome, TaczArmTargets, DIAGNOSTIC_FLOAT_COUNT};
static PENDING_TARGETS: Lazy<Mutex<HashMap<i64, TaczArmTargets>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));
static LAST_APPLY_RESULTS: Lazy<Mutex<HashMap<i64, i32>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));
static LAST_APPLY_DIAGNOSTICS: Lazy<Mutex<HashMap<i64, [f32; DIAGNOSTIC_FLOAT_COUNT]>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));

/// 供主线程在唯一接线点取走当前帧目标；取走即清除。
///
pub(crate) fn take_tacz_arm_targets(model: i64) -> Option<TaczArmTargets> {
    PENDING_TARGETS
        .lock()
        .unwrap_or_else(|error| error.into_inner())
        .remove(&model)
}

/// 保存本次 native 更新的实际结果：低 2 位为收到目标，高 2 位为成功应用。
pub(crate) fn record_tacz_arm_apply_result(
    model: i64,
    received_mask: u8,
    outcome: TaczArmApplyOutcome,
) {
    let packed = i32::from(received_mask & 0b11) | (i32::from(outcome.applied_mask & 0b11) << 2);
    LAST_APPLY_RESULTS
        .lock()
        .unwrap_or_else(|error| error.into_inner())
        .insert(model, packed);
    LAST_APPLY_DIAGNOSTICS
        .lock()
        .unwrap_or_else(|error| error.into_inner())
        .insert(model, outcome.diagnostics);
}

fn take_tacz_arm_apply_result(model: i64) -> i32 {
    LAST_APPLY_RESULTS
        .lock()
        .unwrap_or_else(|error| error.into_inner())
        .remove(&model)
        .unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetLastTaczArmDiagnostics(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    output: JFloatArray,
) -> jboolean {
    let Ok(length) = env.get_array_length(&output) else {
        return 0;
    };
    if length != DIAGNOSTIC_FLOAT_COUNT as i32 {
        return 0;
    }
    let diagnostics = LAST_APPLY_DIAGNOSTICS
        .lock()
        .unwrap_or_else(|error| error.into_inner())
        .remove(&model);
    let Some(diagnostics) = diagnostics else {
        return 0;
    };
    if env
        .set_float_array_region(&output, 0, &diagnostics)
        .is_err()
    {
        return 0;
    }
    1
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetLastTaczArmApplyResult(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jint {
    take_tacz_arm_apply_result(model)
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetTaczArmTargets(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    matrices: JFloatArray,
    valid_mask: jint,
) -> jboolean {
    if model == 0 {
        return 0;
    }
    let Ok(length) = env.get_array_length(&matrices) else {
        clear_tacz_arm_targets(model);
        return 0;
    };
    if length != 32 {
        clear_tacz_arm_targets(model);
        return 0;
    }
    let mut values = [0.0f32; 32];
    if env
        .get_float_array_region(&matrices, 0, &mut values)
        .is_err()
    {
        clear_tacz_arm_targets(model);
        return 0;
    }
    let Some(targets) = TaczArmTargets::from_column_major_slice(&values, valid_mask as u8) else {
        // 非法输入不能留下旧目标，避免下一帧误用。
        clear_tacz_arm_targets(model);
        return 0;
    };
    let mut pending = PENDING_TARGETS
        .lock()
        .unwrap_or_else(|error| error.into_inner());
    pending.insert(model, targets);
    1
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_ClearTaczArmTargets(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) {
    clear_tacz_arm_targets(model);
}

pub(crate) fn clear_tacz_arm_targets(model: i64) {
    PENDING_TARGETS
        .lock()
        .unwrap_or_else(|error| error.into_inner())
        .remove(&model);
}

pub(crate) fn clear_tacz_arm_diagnostics(model: i64) {
    LAST_APPLY_RESULTS
        .lock()
        .unwrap_or_else(|error| error.into_inner())
        .remove(&model);
    LAST_APPLY_DIAGNOSTICS
        .lock()
        .unwrap_or_else(|error| error.into_inner())
        .remove(&model);
}

#[cfg(test)]
mod tests {
    use super::*;
    use glam::Mat4;

    #[test]
    fn take_clears_the_pending_frame() {
        let id = 991;
        PENDING_TARGETS.lock().unwrap().insert(
            id,
            TaczArmTargets {
                left: Some(Mat4::IDENTITY),
                right: Some(Mat4::IDENTITY),
            },
        );
        assert!(take_tacz_arm_targets(id).is_some());
        assert!(take_tacz_arm_targets(id).is_none());
    }

    #[test]
    fn apply_result_is_packed_and_consumed_once() {
        let id = 992;
        record_tacz_arm_apply_result(
            id,
            0b11,
            TaczArmApplyOutcome {
                applied_mask: 0b10,
                diagnostics: [1.0; DIAGNOSTIC_FLOAT_COUNT],
            },
        );
        assert_eq!(take_tacz_arm_apply_result(id), 0b10_11);
        assert_eq!(take_tacz_arm_apply_result(id), 0);
    }
}
