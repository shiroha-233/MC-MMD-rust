//! 单次 Draw 的模型视图可见性 JNI 入口。

use glam::Mat4;
use jni::objects::{JByteBuffer, JClass};
use jni::sys::{jboolean, jint, jlong};
use jni::JNIEnv;

use crate::model::MmdModel;

use super::MODELS;

pub(crate) fn batch_get_sub_mesh_data_for_view(
    model: &mut MmdModel,
    output: &mut [u8],
    first_person_view: bool,
) -> usize {
    let previous_mode = model.is_first_person_enabled();
    if previous_mode != first_person_view {
        model.set_first_person_mode(first_person_view);
    }

    // 在返回 Java 前恢复原状态，避免背包、阴影和其他玩家 Draw 互相污染。
    let written = model.batch_get_sub_mesh_data_for_view(output, first_person_view);
    if previous_mode != first_person_view {
        model.set_first_person_mode(previous_mode);
    }
    written
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_BatchGetSubMeshData(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    buffer: JByteBuffer,
    first_person_view: jboolean,
) -> jint {
    let out_ptr = match env.get_direct_buffer_address(&buffer) {
        Ok(pointer) => pointer,
        Err(_) => return 0,
    };
    let out_capacity = match env.get_direct_buffer_capacity(&buffer) {
        Ok(capacity) => capacity,
        Err(_) => return 0,
    };
    let output = unsafe { std::slice::from_raw_parts_mut(out_ptr, out_capacity) };

    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        batch_get_sub_mesh_data_for_view(&mut model, output, first_person_view != 0) as jint
    } else {
        0
    }
}

/// 使用当前绘制矩阵刷新第一人称索引，并复制到 Java direct buffer。
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_RefreshFirstPersonIndices(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    matrix_buffer: JByteBuffer,
    gpu_skinning: jboolean,
    index_buffer: JByteBuffer,
) -> jint {
    let matrix_ptr = match env.get_direct_buffer_address(&matrix_buffer) {
        Ok(pointer) => pointer,
        Err(_) => return 0,
    };
    let matrix_capacity = match env.get_direct_buffer_capacity(&matrix_buffer) {
        Ok(capacity) => capacity,
        Err(_) => return 0,
    };
    if matrix_capacity < 32 * std::mem::size_of::<f32>() {
        return 0;
    }
    let matrices = unsafe { std::slice::from_raw_parts(matrix_ptr as *const f32, 32) };
    let model_view = Mat4::from_cols_array(&matrices[0..16].try_into().unwrap());
    let projection = Mat4::from_cols_array(&matrices[16..32].try_into().unwrap());

    let output_ptr = match env.get_direct_buffer_address(&index_buffer) {
        Ok(pointer) => pointer,
        Err(_) => return 0,
    };
    let output_capacity = match env.get_direct_buffer_capacity(&index_buffer) {
        Ok(capacity) => capacity,
        Err(_) => return 0,
    };
    let output = unsafe { std::slice::from_raw_parts_mut(output_ptr, output_capacity) };

    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|model| {
            model.lock().unwrap().refresh_first_person_indices(
                model_view,
                projection,
                gpu_skinning != 0,
                output,
            ) as jint
        })
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn view_query_restores_disabled_mode() {
        let mut model = MmdModel::new();
        let mut output = [];

        batch_get_sub_mesh_data_for_view(&mut model, &mut output, true);

        assert!(!model.is_first_person_enabled());
    }

    #[test]
    fn view_query_restores_enabled_mode() {
        let mut model = MmdModel::new();
        let mut output = [];
        model.set_first_person_mode(true);

        batch_get_sub_mesh_data_for_view(&mut model, &mut output, false);

        assert!(model.is_first_person_enabled());
    }
}
