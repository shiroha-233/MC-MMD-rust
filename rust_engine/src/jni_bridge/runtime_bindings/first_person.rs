// 负责第一人称、骨骼快照、UV 与模型指标入口。

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetFirstPersonMode(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    enabled: jboolean,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_first_person_mode(enabled != 0);
    }
}

// 获取第一人称模式是否启用

// 第一人称诊断信息（供 Java 侧写日志排查，不影响渲染）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_GetFirstPersonInfo(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jstring {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        let info = model.first_person_debug_info();
        if let Ok(s) = env.new_string(&info) {
            return s.into_raw();
        }
    }
    env.new_string("no-model")
        .map(|s| s.into_raw())
        .unwrap_or(ptr::null_mut())
}

// 获取头部骨骼的静态 Y 坐标（模型局部空间，用于相机高度计算）

// 获取眼睛骨骼的当前动画位置（模型局部空间）
// 每帧调用，返回经过动画/物理更新后的实时 [x, y, z]
// 如果传入的 out 数组长度 < 3 则不写入
#[no_mangle]
#[allow(unused_mut)]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_GetEyeBonePosition(
    mut env: JNIEnv,
    _class: JClass,
    model: jlong,
    out: jni::objects::JFloatArray,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        let pos = model.get_eye_bone_animated_position();
        let buf: [f32; 3] = [pos.x, pos.y, pos.z];
        let _ = env.set_float_array_region(&out, 0, &buf);
    }
}

// ============================================================================
// 批量子网格元数据（G3 优化）
// ============================================================================

// 批量获取所有子网格的渲染元数据，消除 Java 侧逐子网格 JNI 调用
// 获取所有骨骼名称（JSON 数组格式）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_GetBoneNames(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jstring {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let bone_count = model.bone_manager.bone_count();
        let mut names = Vec::with_capacity(bone_count);
        for i in 0..bone_count {
            if let Some(bone) = model.bone_manager.get_bone(i) {
                names.push(format!("\"{}\"", bone.name.replace('"', "\\\"")));
            }
        }
        let json = format!("[{}]", names.join(","));
        if let Ok(s) = env.new_string(&json) {
            return s.into_raw();
        }
    }
    env.new_string("[]")
        .map(|s| s.into_raw())
        .unwrap_or(ptr::null_mut())
}

// 复制所有骨骼的实时世界位置到 ByteBuffer
// 每个骨骼 3 个 float (x, y, z)，共 boneCount * 12 字节
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_CopyBonePositionsToBuffer(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    buffer: JByteBuffer,
) -> jint {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let bone_count = model.bone_manager.bone_count();
        if bone_count == 0 {
            return 0;
        }

        let byte_size = bone_count * 12; // 3 floats * 4 bytes
        let dst = match env.get_direct_buffer_address(&buffer) {
            Ok(p) => p,
            Err(_) => return 0,
        };
        let capacity = env.get_direct_buffer_capacity(&buffer).unwrap_or(0);
        if byte_size > capacity {
            log::error!(
                "CopyBonePositionsToBuffer: 需要 {} 字节, 容量 {}",
                byte_size,
                capacity
            );
            return 0;
        }

        // 逐骨骼写入位置
        let dst_floats = unsafe { std::slice::from_raw_parts_mut(dst as *mut f32, bone_count * 3) };
        for i in 0..bone_count {
            if let Some(bone) = model.bone_manager.get_bone(i) {
                let pos = bone.position();
                dst_floats[i * 3] = pos.x;
                dst_floats[i * 3 + 1] = pos.y;
                dst_floats[i * 3 + 2] = pos.z;
            }
        }
        return bone_count as jint;
    }
    0
}

// 复制实时 UV 数据到 ByteBuffer（经过 UV Morph 变形后）
// 每个顶点 2 个 float (u, v)，共 vertexCount * 8 字节
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_CopyRealtimeUVsToBuffer(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    buffer: JByteBuffer,
) -> jint {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let uv_raw = &model.update_uvs_raw;
        if uv_raw.is_empty() {
            return 0;
        }

        let vertex_count = uv_raw.len() / 2;
        let byte_size = uv_raw.len() * 4; // f32 = 4 bytes
        let dst = match env.get_direct_buffer_address(&buffer) {
            Ok(p) => p,
            Err(_) => return 0,
        };
        let capacity = env.get_direct_buffer_capacity(&buffer).unwrap_or(0);
        if byte_size > capacity {
            log::error!(
                "CopyRealtimeUVsToBuffer: 需要 {} 字节, 容量 {}",
                byte_size,
                capacity
            );
            return 0;
        }

        unsafe {
            let src = uv_raw.as_ptr() as *const u8;
            ptr::copy_nonoverlapping(src, dst, byte_size);
        }
        return vertex_count as jint;
    }
    0
}

// ============================================================================
// 内存统计
// ============================================================================

// 获取模型在 Rust 堆上的内存占用（字节）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_GetModelMemoryUsage(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let m = model_arc.lock().unwrap();
        m.memory_usage() as jlong
    } else {
        0
    }
}

// ============================================================================
// VR 联动
// ============================================================================

// 批量设置 VR 追踪数据（3 追踪点 × 7 float = 21）
