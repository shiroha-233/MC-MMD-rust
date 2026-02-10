//! JNI 原生函数实现
//!
//! 对照 C++ 版 NativeFunc.h 实现所有接口
//! 使用标准 jni 0.21 API

use jni::objects::{JByteBuffer, JClass, JString};
use jni::sys::{jboolean, jbyte, jfloat, jint, jlong, jstring};
use jni::JNIEnv;
use std::ptr;
use std::sync::Arc;

use crate::animation::{VmdAnimation, VmdFile};
use crate::model::load_pmx;
use crate::texture::load_texture;

use super::{register_animation, register_model, register_texture, ANIMATIONS, MODELS, TEXTURES};

const VERSION: &str = "Rust-20260125";

// ============================================================================
// 基础函数
// ============================================================================

/// 获取版本号
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetVersion(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    match env.new_string(VERSION) {
        Ok(s) => s.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

/// 读取字节
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_ReadByte(
    _env: JNIEnv,
    _class: JClass,
    data: jlong,
    pos: jlong,
) -> jbyte {
    if data == 0 {
        return 0;
    }
    unsafe {
        let ptr = (data + pos) as *const u8;
        *ptr as jbyte
    }
}

/// 复制数据到 ByteBuffer
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_CopyDataToByteBuffer(
    env: JNIEnv,
    _class: JClass,
    buffer: JByteBuffer,
    data: jlong,
    len: jlong,
) {
    if data == 0 {
        return;
    }

    if let Ok(dst) = env.get_direct_buffer_address(&buffer) {
        unsafe {
            let src = data as *const u8;
            ptr::copy_nonoverlapping(src, dst, len as usize);
        }
    }
}

// ============================================================================
// 模型相关函数
// ============================================================================

/// 加载 PMX 模型
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_LoadModelPMX(
    mut env: JNIEnv,
    _class: JClass,
    filename: JString,
    _dir: JString,
    _layer_count: jlong,
) -> jlong {
    let filename_str: String = match env.get_string(&filename) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    match load_pmx(&filename_str) {
        Ok(mut model) => {
            // 自动初始化物理系统
            if !model.rigid_bodies.is_empty() {
                log::info!("模型包含 {} 个刚体, {} 个关节, 自动初始化物理", 
                    model.rigid_bodies.len(), model.joints.len());
                model.init_physics();
            }
            register_model(model)
        },
        Err(e) => {
            log::error!("Failed to load PMX: {}", e);
            0
        }
    }
}

/// 加载 PMD 模型（暂不支持）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_LoadModelPMD(
    _env: JNIEnv,
    _class: JClass,
    _filename: JString,
    _dir: JString,
    _layer_count: jlong,
) -> jlong {
    log::warn!("PMD format not supported yet");
    0
}

/// 删除模型
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_DeleteModel(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) {
    let mut models = MODELS.write().unwrap();
    models.remove(&model);
}

/// 更新模型
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_UpdateModel(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    delta_time: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        // 更新动画（内部已包含物理更新）
        model.tick_animation(delta_time);
    }
}

// ============================================================================
// 顶点数据函数
// ============================================================================

/// 获取顶点数量
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetVertexCount(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| m.lock().unwrap().vertex_count() as jlong)
        .unwrap_or(0)
}

/// 获取顶点位置数据指针
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetPoss(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| m.lock().unwrap().get_positions_ptr() as jlong)
        .unwrap_or(0)
}

/// 获取法线数据指针
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetNormals(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| m.lock().unwrap().get_normals_ptr() as jlong)
        .unwrap_or(0)
}

/// 获取 UV 数据指针
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetUVs(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| m.lock().unwrap().get_uvs_ptr() as jlong)
        .unwrap_or(0)
}

// ============================================================================
// 索引数据函数
// ============================================================================

/// 获取索引元素大小
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetIndexElementSize(
    _env: JNIEnv,
    _class: JClass,
    _model: jlong,
) -> jlong {
    4 // u32 索引
}

/// 获取索引数量
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetIndexCount(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| m.lock().unwrap().index_count() as jlong)
        .unwrap_or(0)
}

/// 获取索引数据指针
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetIndices(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| m.lock().unwrap().get_indices_ptr() as jlong)
        .unwrap_or(0)
}

// ============================================================================
// 材质相关函数
// ============================================================================

/// 获取材质数量
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialCount(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| m.lock().unwrap().material_count() as jlong)
        .unwrap_or(0)
}

/// 获取材质纹理路径
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialTex(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    pos: jlong,
) -> jstring {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let idx = pos as usize;
        if idx < model.materials.len() {
            let tex_idx = model.materials[idx].texture_index;
            if tex_idx >= 0 && (tex_idx as usize) < model.texture_paths.len() {
                if let Ok(s) = env.new_string(&model.texture_paths[tex_idx as usize]) {
                    return s.into_raw();
                }
            }
        }
    }
    env.new_string("")
        .map(|s| s.into_raw())
        .unwrap_or(ptr::null_mut())
}

/// 获取材质 Sphere 纹理路径
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialSpTex(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    pos: jlong,
) -> jstring {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let idx = pos as usize;
        if idx < model.materials.len() {
            let env_idx = model.materials[idx].environment_index;
            if env_idx >= 0 && (env_idx as usize) < model.texture_paths.len() {
                if let Ok(s) = env.new_string(&model.texture_paths[env_idx as usize]) {
                    return s.into_raw();
                }
            }
        }
    }
    env.new_string("")
        .map(|s| s.into_raw())
        .unwrap_or(ptr::null_mut())
}

/// 获取材质 Toon 纹理路径
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialToonTex(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    pos: jlong,
) -> jstring {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let idx = pos as usize;
        if idx < model.materials.len() {
            let toon_idx = model.materials[idx].toon_index;
            if toon_idx >= 0 && (toon_idx as usize) < model.texture_paths.len() {
                if let Ok(s) = env.new_string(&model.texture_paths[toon_idx as usize]) {
                    return s.into_raw();
                }
            }
        }
    }
    env.new_string("")
        .map(|s| s.into_raw())
        .unwrap_or(ptr::null_mut())
}

// 颜色数据缓存（用于返回指针）
thread_local! {
    static COLOR_BUFFER: std::cell::RefCell<[f32; 4]> = std::cell::RefCell::new([0.0; 4]);
}

/// 获取材质环境光颜色指针
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialAmbient(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    pos: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let idx = pos as usize;
        if idx < model.materials.len() {
            let ambient = model.materials[idx].ambient;
            COLOR_BUFFER.with(|buf| {
                let mut b = buf.borrow_mut();
                b[0] = ambient.x;
                b[1] = ambient.y;
                b[2] = ambient.z;
                b[3] = 1.0;
                b.as_ptr() as jlong
            })
        } else {
            0
        }
    } else {
        0
    }
}

/// 获取材质漫反射颜色指针
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialDiffuse(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    pos: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let idx = pos as usize;
        if idx < model.materials.len() {
            let diffuse = model.materials[idx].diffuse;
            COLOR_BUFFER.with(|buf| {
                let mut b = buf.borrow_mut();
                b[0] = diffuse.x;
                b[1] = diffuse.y;
                b[2] = diffuse.z;
                b[3] = diffuse.w;
                b.as_ptr() as jlong
            })
        } else {
            0
        }
    } else {
        0
    }
}

/// 获取材质高光颜色指针
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialSpecular(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    pos: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let idx = pos as usize;
        if idx < model.materials.len() {
            let specular = model.materials[idx].specular;
            COLOR_BUFFER.with(|buf| {
                let mut b = buf.borrow_mut();
                b[0] = specular.x;
                b[1] = specular.y;
                b[2] = specular.z;
                b[3] = 1.0;
                b.as_ptr() as jlong
            })
        } else {
            0
        }
    } else {
        0
    }
}

/// 获取材质高光强度
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialSpecularPower(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    pos: jlong,
) -> jfloat {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .and_then(|m| {
            let model = m.lock().unwrap();
            model
                .materials
                .get(pos as usize)
                .map(|mat| mat.specular_strength)
        })
        .unwrap_or(1.0)
}

/// 获取材质透明度
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialAlpha(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    pos: jlong,
) -> jfloat {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .and_then(|m| {
            let model = m.lock().unwrap();
            model.materials.get(pos as usize).map(|mat| mat.diffuse.w)
        })
        .unwrap_or(1.0)
}

/// 获取纹理乘法因子指针
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialTextureMulFactor(
    _env: JNIEnv,
    _class: JClass,
    _model: jlong,
    _pos: jlong,
) -> jlong {
    // 返回默认白色乘法因子
    static DEFAULT_MUL: [f32; 4] = [1.0, 1.0, 1.0, 1.0];
    DEFAULT_MUL.as_ptr() as jlong
}

/// 获取纹理加法因子指针
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialTextureAddFactor(
    _env: JNIEnv,
    _class: JClass,
    _model: jlong,
    _pos: jlong,
) -> jlong {
    static DEFAULT_ADD: [f32; 4] = [0.0, 0.0, 0.0, 0.0];
    DEFAULT_ADD.as_ptr() as jlong
}

/// 获取 Sphere 纹理模式
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialSpTextureMode(
    _env: JNIEnv,
    _class: JClass,
    _model: jlong,
    _pos: jlong,
) -> jint {
    0 // 默认无 sphere map
}

/// 获取 Sphere 纹理乘法因子
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialSpTextureMulFactor(
    _env: JNIEnv,
    _class: JClass,
    _model: jlong,
    _pos: jlong,
) -> jlong {
    static DEFAULT_MUL: [f32; 4] = [1.0, 1.0, 1.0, 1.0];
    DEFAULT_MUL.as_ptr() as jlong
}

/// 获取 Sphere 纹理加法因子
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialSpTextureAddFactor(
    _env: JNIEnv,
    _class: JClass,
    _model: jlong,
    _pos: jlong,
) -> jlong {
    static DEFAULT_ADD: [f32; 4] = [0.0, 0.0, 0.0, 0.0];
    DEFAULT_ADD.as_ptr() as jlong
}

/// 获取 Toon 纹理乘法因子
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialToonTextureMulFactor(
    _env: JNIEnv,
    _class: JClass,
    _model: jlong,
    _pos: jlong,
) -> jlong {
    static DEFAULT_MUL: [f32; 4] = [1.0, 1.0, 1.0, 1.0];
    DEFAULT_MUL.as_ptr() as jlong
}

/// 获取 Toon 纹理加法因子
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialToonTextureAddFactor(
    _env: JNIEnv,
    _class: JClass,
    _model: jlong,
    _pos: jlong,
) -> jlong {
    static DEFAULT_ADD: [f32; 4] = [0.0, 0.0, 0.0, 0.0];
    DEFAULT_ADD.as_ptr() as jlong
}

/// 获取材质双面标志
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialBothFace(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    pos: jlong,
) -> jboolean {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .and_then(|m| {
            let model = m.lock().unwrap();
            model
                .materials
                .get(pos as usize)
                .map(|mat| mat.is_double_sided())
        })
        .map(|v| if v { 1u8 } else { 0u8 })
        .unwrap_or(0u8)
}

// ============================================================================
// 子网格函数
// ============================================================================

/// 获取子网格数量
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetSubMeshCount(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| m.lock().unwrap().submesh_count() as jlong)
        .unwrap_or(0)
}

/// 获取子网格材质 ID
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetSubMeshMaterialID(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    pos: jlong,
) -> jint {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let idx = pos as usize;
        if idx < model.submeshes.len() {
            return model.submeshes[idx].material_id;
        }
    }
    0
}

/// 获取子网格起始索引
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetSubMeshBeginIndex(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    pos: jlong,
) -> jint {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let idx = pos as usize;
        if idx < model.submeshes.len() {
            return model.submeshes[idx].begin_index as jint;
        }
    }
    0
}

/// 获取子网格索引数量
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetSubMeshVertexCount(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    pos: jlong,
) -> jint {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let idx = pos as usize;
        if idx < model.submeshes.len() {
            return model.submeshes[idx].index_count as jint;
        }
    }
    0
}

// ============================================================================
// 动画相关函数
// ============================================================================

/// 切换动画（支持多动画层）
/// layer: 动画层ID（0-3），0为基础层，1-3为叠加层
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_ChangeModelAnim(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    anim: jlong,
    layer: jlong,
) {
    let models = MODELS.read().unwrap();
    let animations = ANIMATIONS.read().unwrap();

    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        let layer_id = layer as usize;
        let anim_opt = animations.get(&anim).cloned();
        model.set_layer_animation(layer_id, anim_opt);
        model.play_layer(layer_id);
    }
}

/// 重置物理
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_ResetModelPhysics(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.reset_physics();
    }
}

/// 加载动画
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_LoadAnimation(
    mut env: JNIEnv,
    _class: JClass,
    _model: jlong,
    filename: JString,
) -> jlong {
    let filename_str: String = match env.get_string(&filename) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    match VmdFile::load(&filename_str) {
        Ok(vmd) => {
            let animation = VmdAnimation::from_vmd_file(vmd);
            register_animation(animation)
        }
        Err(e) => {
            log::error!("Failed to load VMD: {}", e);
            0
        }
    }
}

/// 删除动画
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_DeleteAnimation(
    _env: JNIEnv,
    _class: JClass,
    anim: jlong,
) {
    let mut animations = ANIMATIONS.write().unwrap();
    animations.remove(&anim);
}

/// 查询动画是否包含相机数据
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_HasCameraData(
    _env: JNIEnv,
    _class: JClass,
    anim: jlong,
) -> jboolean {
    let animations = ANIMATIONS.read().unwrap();
    if let Some(animation) = animations.get(&anim) {
        if animation.has_camera() { 1u8 } else { 0u8 }
    } else {
        0u8
    }
}

/// 获取动画最大帧数（包含相机轨道）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetAnimMaxFrame(
    _env: JNIEnv,
    _class: JClass,
    anim: jlong,
) -> jfloat {
    let animations = ANIMATIONS.read().unwrap();
    if let Some(animation) = animations.get(&anim) {
        animation.max_frame() as jfloat
    } else {
        0.0
    }
}

/// 获取相机变换数据，写入 ByteBuffer
/// 布局: pos_x, pos_y, pos_z (3×f32) + rot_x, rot_y, rot_z (3×f32) + fov (f32) + is_perspective (i32) = 32 字节
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetCameraTransform(
    env: JNIEnv,
    _class: JClass,
    anim: jlong,
    frame: jfloat,
    buffer: JByteBuffer,
) {
    let animations = ANIMATIONS.read().unwrap();
    if let Some(animation) = animations.get(&anim) {
        let transform = animation.get_camera_transform(frame);
        
        if let Ok(dst) = env.get_direct_buffer_address(&buffer) {
            unsafe {
                let ptr = dst as *mut f32;
                // position (3 × f32)
                *ptr.add(0) = transform.position.x;
                *ptr.add(1) = transform.position.y;
                *ptr.add(2) = transform.position.z;
                // rotation (3 × f32, 欧拉角弧度)
                *ptr.add(3) = transform.rotation.x;
                *ptr.add(4) = transform.rotation.y;
                *ptr.add(5) = transform.rotation.z;
                // fov (f32)
                *ptr.add(6) = transform.fov;
                // is_perspective (i32, 0/1)
                let i_ptr = ptr.add(7) as *mut i32;
                *i_ptr = if transform.is_perspective { 1 } else { 0 };
            }
        }
    }
}

/// 查询动画是否包含骨骼关键帧
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_HasBoneData(
    _env: JNIEnv,
    _class: JClass,
    anim: jlong,
) -> jboolean {
    let animations = ANIMATIONS.read().unwrap();
    if let Some(animation) = animations.get(&anim) {
        if animation.has_bones() { 1u8 } else { 0u8 }
    } else {
        0u8
    }
}

/// 查询动画是否包含表情关键帧
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_HasMorphData(
    _env: JNIEnv,
    _class: JClass,
    anim: jlong,
) -> jboolean {
    let animations = ANIMATIONS.read().unwrap();
    if let Some(animation) = animations.get(&anim) {
        if animation.has_morphs() { 1u8 } else { 0u8 }
    } else {
        0u8
    }
}

/// 将 source 动画的骨骼和 Morph 数据合并到 target 动画中
/// 实现方式：克隆 target → 合并 source → 替换回 HashMap
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_MergeAnimation(
    _env: JNIEnv,
    _class: JClass,
    target: jlong,
    source: jlong,
) {
    // 先读取并克隆两个动画，避免借用冲突
    let (target_clone, source_ref) = {
        let animations = ANIMATIONS.read().unwrap();
        let t = animations.get(&target).cloned();
        let s = animations.get(&source).cloned();
        (t, s)
    };

    if let (Some(target_arc), Some(source_arc)) = (target_clone, source_ref) {
        let mut merged = (*target_arc).clone();
        merged.merge(&source_arc);
        // 写回 HashMap，替换原 target
        let mut animations = ANIMATIONS.write().unwrap();
        animations.insert(target, Arc::new(merged));
    }
}

/// 设置模型全局变换（用于人物移动时传递位置给物理系统）
/// 传入 4x4 矩阵的 16 个 float 值（列主序）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetModelTransform(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    m00: jfloat, m01: jfloat, m02: jfloat, m03: jfloat,
    m10: jfloat, m11: jfloat, m12: jfloat, m13: jfloat,
    m20: jfloat, m21: jfloat, m22: jfloat, m23: jfloat,
    m30: jfloat, m31: jfloat, m32: jfloat, m33: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        let transform = glam::Mat4::from_cols_array(&[
            m00, m01, m02, m03,
            m10, m11, m12, m13,
            m20, m21, m22, m23,
            m30, m31, m32, m33,
        ]);
        model.set_model_transform(transform);
    }
}

/// 设置模型位置和朝向（简化版，用于惯性计算）
/// pos_x, pos_y, pos_z: 模型位置（已缩放）
/// yaw: 人物朝向角度（弧度）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetModelPositionAndYaw(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    pos_x: jfloat,
    pos_y: jfloat, 
    pos_z: jfloat,
    yaw: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_model_position_and_yaw(pos_x, pos_y, pos_z, yaw);
    }
}

/// 设置头部角度
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetHeadAngle(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    head_x: jfloat,
    head_y: jfloat,
    head_z: jfloat,
    _is_head_in_sync: jboolean,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_head_angle(head_x, head_y, head_z);
    }
}

/// 设置眼球追踪角度（眼睛看向摄像头）
/// eye_x: 上下看的角度（弧度，正值向上）
/// eye_y: 左右看的角度（弧度，正值向左）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetEyeAngle(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    eye_x: jfloat,
    eye_y: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_eye_angle(eye_x, eye_y);
    }
}

/// 设置眼球最大转动角度
/// max_angle: 最大角度（弧度），默认 0.35（约 20 度）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetEyeMaxAngle(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    max_angle: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_eye_max_angle(max_angle);
    }
}

/// 启用/禁用眼球追踪
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetEyeTrackingEnabled(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    enabled: jboolean,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_eye_tracking_enabled(enabled != 0);
    }
}

/// 获取眼球追踪是否启用
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_IsEyeTrackingEnabled(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jboolean {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| {
            let model = m.lock().unwrap();
            if model.is_eye_tracking_enabled() { 1u8 } else { 0u8 }
        })
        .unwrap_or(0u8)
}

/// 启用/禁用自动眨眼
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetAutoBlinkEnabled(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    enabled: jboolean,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_auto_blink_enabled(enabled != 0);
    }
}

/// 获取自动眨眼是否启用
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_IsAutoBlinkEnabled(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jboolean {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| {
            let model = m.lock().unwrap();
            if model.is_auto_blink_enabled() { 1u8 } else { 0u8 }
        })
        .unwrap_or(0u8)
}

/// 设置眨眼参数
/// interval: 眨眼间隔（秒），默认 4.0
/// duration: 眨眼持续时间（秒），默认 0.15
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetBlinkParams(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    interval: jfloat,
    duration: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_blink_params(interval, duration);
    }
}

// ============================================================================
// 动画层控制函数（新增）
// ============================================================================

/// 播放指定层的动画
/// layer: 动画层ID（0-3）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_PlayLayer(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    layer: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.play_layer(layer as usize);
    }
}

/// 停止指定层的动画
/// layer: 动画层ID（0-3）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_StopLayer(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    layer: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.stop_layer(layer as usize);
    }
}

/// 暂停指定层的动画
/// layer: 动画层ID（0-3）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_PauseLayer(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    layer: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.pause_layer(layer as usize);
    }
}

/// 恢复指定层的动画
/// layer: 动画层ID（0-3）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_ResumeLayer(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    layer: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.resume_layer(layer as usize);
    }
}

/// 设置动画层权重
/// layer: 动画层ID（0-3）
/// weight: 权重值（0.0 - 1.0）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetLayerWeight(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    layer: jlong,
    weight: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_layer_weight(layer as usize, weight);
    }
}

/// 设置动画层播放速度
/// layer: 动画层ID（0-3）
/// speed: 速度倍率（0.0+，1.0为正常速度）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetLayerSpeed(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    layer: jlong,
    speed: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_layer_speed(layer as usize, speed);
    }
}

/// 跳转到指定帧
/// layer: 动画层ID（0-3）
/// frame: 目标帧号
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SeekLayer(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    layer: jlong,
    frame: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.seek_layer(layer as usize, frame);
    }
}

/// 设置动画层淡入淡出时间
/// layer: 动画层ID（0-3）
/// fadeIn: 淡入时间（秒）
/// fadeOut: 淡出时间（秒）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetLayerFadeTimes(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    layer: jlong,
    fade_in: jfloat,
    fade_out: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_layer_fade_times(layer as usize, fade_in, fade_out);
    }
}

/// 带过渡地切换指定层的动画（姿态缓存过渡）
/// 
/// 从当前骨骼姿态平滑过渡到新动画，避免动作切换时的突兀感。
/// 
/// # 参数
/// - model: 模型句柄
/// - layer: 动画层ID（0-3）
/// - animation: 动画句柄（0表示清除动画）
/// - transition_time: 过渡时间（秒），推荐 0.2 ~ 0.5 秒
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_TransitionLayerTo(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    layer: jlong,
    animation: jlong,
    transition_time: jfloat,
) {
    let models = MODELS.read().unwrap();
    let animations = ANIMATIONS.read().unwrap();
    
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        let anim = if animation != 0 {
            animations.get(&animation).cloned()
        } else {
            None
        };
        model.transition_layer_to(layer as usize, anim, transition_time);
    }
}

/// 获取动画层最大帧数
/// layer: 动画层ID（0-3）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetLayerMaxFrame(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    layer: jlong,
) -> jfloat {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        return model.get_layer_max_frame(layer as usize) as jfloat;
    }
    0.0
}

// ============================================================================
// 纹理相关函数
// ============================================================================

/// 加载纹理
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_LoadTexture(
    mut env: JNIEnv,
    _class: JClass,
    filename: JString,
) -> jlong {
    let filename_str: String = match env.get_string(&filename) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    match load_texture(&filename_str) {
        Ok(texture) => register_texture(texture),
        Err(e) => {
            log::error!("Failed to load texture: {}", e);
            0
        }
    }
}

/// 删除纹理
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_DeleteTexture(
    _env: JNIEnv,
    _class: JClass,
    tex: jlong,
) {
    let mut textures = TEXTURES.write().unwrap();
    textures.remove(&tex);
}

/// 获取纹理宽度
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetTextureX(
    _env: JNIEnv,
    _class: JClass,
    tex: jlong,
) -> jint {
    let textures = TEXTURES.read().unwrap();
    textures.get(&tex).map(|t| t.width as jint).unwrap_or(0)
}

/// 获取纹理高度
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetTextureY(
    _env: JNIEnv,
    _class: JClass,
    tex: jlong,
) -> jint {
    let textures = TEXTURES.read().unwrap();
    textures.get(&tex).map(|t| t.height as jint).unwrap_or(0)
}

/// 获取纹理数据指针
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetTextureData(
    _env: JNIEnv,
    _class: JClass,
    tex: jlong,
) -> jlong {
    let textures = TEXTURES.read().unwrap();
    textures
        .get(&tex)
        .map(|t| t.data.as_ptr() as jlong)
        .unwrap_or(0)
}

/// 检查纹理是否有透明通道
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_TextureHasAlpha(
    _env: JNIEnv,
    _class: JClass,
    tex: jlong,
) -> jboolean {
    let textures = TEXTURES.read().unwrap();
    textures
        .get(&tex)
        .map(|t| if t.has_alpha { 1u8 } else { 0u8 })
        .unwrap_or(0u8)
}

// ============================================================================
// 矩阵相关函数
// ============================================================================

use once_cell::sync::Lazy;
use std::sync::Mutex;

/// 矩阵存储（使用 HashMap + ID，避免 Vec 扩容导致指针失效）
static MATRICES: Lazy<Mutex<std::collections::HashMap<i64, glam::Mat4>>> = Lazy::new(|| Mutex::new(std::collections::HashMap::new()));

/// 矩阵 ID 计数器
fn next_matrix_id() -> i64 {
    use std::sync::atomic::{AtomicI64, Ordering};
    static MAT_COUNTER: AtomicI64 = AtomicI64::new(1);
    MAT_COUNTER.fetch_add(1, Ordering::SeqCst)
}

/// 创建矩阵
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_CreateMat(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    let id = next_matrix_id();
    let mut matrices = MATRICES.lock().unwrap();
    matrices.insert(id, glam::Mat4::IDENTITY);
    id
}

/// 删除矩阵
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_DeleteMat(
    _env: JNIEnv,
    _class: JClass,
    mat: jlong,
) {
    let mut matrices = MATRICES.lock().unwrap();
    matrices.remove(&mat);
}

/// 将矩阵数据复制到 ByteBuffer（64 字节 = 16 floats）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_CopyMatToBuffer(
    env: JNIEnv,
    _class: JClass,
    mat: jlong,
    buffer: JByteBuffer,
) -> jboolean {
    let matrices = MATRICES.lock().unwrap();
    if let Some(m) = matrices.get(&mat) {
        if let Ok(dst) = env.get_direct_buffer_address(&buffer) {
            unsafe {
                let src = m as *const glam::Mat4 as *const u8;
                ptr::copy_nonoverlapping(src, dst, 64);
            }
            return 1;
        }
    }
    0
}

/// 获取右手矩阵
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetRightHandMat(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    mat: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model_guard = model_arc.lock().unwrap();
        let hand_mat = model_guard.get_right_hand_matrix();
        drop(model_guard);
        drop(models);
        let mut matrices = MATRICES.lock().unwrap();
        if let Some(m) = matrices.get_mut(&mat) {
            *m = hand_mat;
        }
    }
}

/// 获取左手矩阵
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetLeftHandMat(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    mat: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model_guard = model_arc.lock().unwrap();
        let hand_mat = model_guard.get_left_hand_matrix();
        drop(model_guard);
        drop(models);
        let mut matrices = MATRICES.lock().unwrap();
        if let Some(m) = matrices.get_mut(&mat) {
            *m = hand_mat;
        }
    }
}

// ============================================================================
// 物理系统相关函数
// ============================================================================

/// 初始化模型物理系统
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_InitPhysics(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jboolean {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        if model.init_physics() {
            return 1;
        }
    }
    0
}

/// 重置物理系统
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_ResetPhysics(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.reset_physics();
    }
}

/// 启用/禁用物理
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetPhysicsEnabled(
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

/// 获取物理是否启用
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_IsPhysicsEnabled(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jboolean {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        if model.is_physics_enabled() {
            return 1;
        }
    }
    0
}

/// 获取物理是否已初始化
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_HasPhysics(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jboolean {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        if model.has_physics() {
            return 1;
        }
    }
    0
}

/// 获取物理调试信息（返回 JSON 字符串）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetPhysicsDebugInfo(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jstring {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let debug_info = model.get_physics_debug_info();
        if let Ok(s) = env.new_string(&debug_info) {
            return s.into_raw();
        }
    }
    env.new_string("{}")
        .map(|s| s.into_raw())
        .unwrap_or(ptr::null_mut())
}

// ============================================================================
// 材质可见性控制（用于脱外套等功能）
// ============================================================================

/// 获取材质是否可见
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_IsMaterialVisible(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    index: jint,
) -> jboolean {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        if model.is_material_visible(index as usize) { 1 } else { 0 }
    } else {
        1 // 默认可见
    }
}

/// 设置材质可见性
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetMaterialVisible(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    index: jint,
    visible: jboolean,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_material_visible(index as usize, visible != 0);
    }
}

/// 根据材质名称设置可见性（支持部分匹配，返回匹配的材质数量）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetMaterialVisibleByName(
    mut env: JNIEnv,
    _class: JClass,
    model: jlong,
    name: JString,
    visible: jboolean,
) -> jint {
    let name_str: String = match env.get_string(&name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_material_visible_by_name(&name_str, visible != 0) as jint
    } else {
        0
    }
}

/// 设置所有材质可见性
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetAllMaterialsVisible(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    visible: jboolean,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_all_materials_visible(visible != 0);
    }
}

/// 获取材质名称
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialName(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    index: jint,
) -> jstring {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        if let Some(name) = model.get_material_name(index as usize) {
            if let Ok(s) = env.new_string(name) {
                return s.into_raw();
            }
        }
    }
    env.new_string("")
        .map(|s| s.into_raw())
        .unwrap_or(ptr::null_mut())
}

/// 获取所有材质名称（JSON 数组格式）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialNames(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jstring {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let names = model.get_material_names();
        // 构建简单的 JSON 数组
        let json = format!("[{}]", 
            names.iter()
                .map(|n| format!("\"{}\"", n.replace('"', "\\\"")))
                .collect::<Vec<_>>()
                .join(",")
        );
        if let Ok(s) = env.new_string(&json) {
            return s.into_raw();
        }
    }
    env.new_string("[]")
        .map(|s| s.into_raw())
        .unwrap_or(ptr::null_mut())
}

// ============================================================================
// GPU 蒙皮相关函数
// ============================================================================

/// 获取骨骼数量
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetBoneCount(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jint {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| m.lock().unwrap().bone_manager.bone_count() as jint)
        .unwrap_or(0)
}

/// 获取蒙皮矩阵数据指针（用于 GPU 蒙皮）
/// 返回所有骨骼的蒙皮矩阵（skinning matrix = global * inverse_bind）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetSkinningMatrices(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let matrices = model.bone_manager.get_skinning_matrices();
        if !matrices.is_empty() {
            return matrices.as_ptr() as jlong;
        }
    }
    0
}

/// 复制蒙皮矩阵到 ByteBuffer（线程安全，用于 GPU 蒙皮）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_CopySkinningMatricesToBuffer(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    buffer: JByteBuffer,
) -> jint {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let matrices = model.bone_manager.get_skinning_matrices();
        if matrices.is_empty() {
            return 0;
        }
        
        if let Ok(dst) = env.get_direct_buffer_address(&buffer) {
            let bone_count = matrices.len();
            
            // 检查缓冲区容量
            if let Ok(capacity) = env.get_direct_buffer_capacity(&buffer) {
                let required = bone_count * 64;
                if capacity < required {
                    log::warn!("蒙皮矩阵缓冲区容量不足: {} < {}", capacity, required);
                    return 0;
                }
            }
            
            let byte_size = bone_count * 64; // 每个 Mat4 = 16 floats * 4 bytes
            unsafe {
                let src = matrices.as_ptr() as *const u8;
                ptr::copy_nonoverlapping(src, dst, byte_size);
            }
            return bone_count as jint;
        }
    }
    0
}

/// 获取顶点骨骼索引数据指针（ivec4 格式，用于 GPU 蒙皮）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetBoneIndices(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        return model.get_bone_indices_ptr() as jlong;
    }
    0
}

/// 复制骨骼索引到 ByteBuffer（线程安全）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_CopyBoneIndicesToBuffer(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    buffer: JByteBuffer,
    vertex_count: jint,
) -> jint {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let ptr = model.get_bone_indices_ptr();
        if ptr.is_null() {
            return 0;
        }
        
        if let Ok(dst) = env.get_direct_buffer_address(&buffer) {
            let byte_size = (vertex_count as usize) * 16; // 4 int * 4 bytes
            unsafe {
                let src = ptr as *const u8;
                ptr::copy_nonoverlapping(src, dst, byte_size);
            }
            return vertex_count;
        }
    }
    0
}

/// 获取顶点骨骼权重数据指针（vec4 格式，用于 GPU 蒙皮）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetBoneWeights(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        return model.get_bone_weights_ptr() as jlong;
    }
    0
}

/// 复制骨骼权重到 ByteBuffer（线程安全）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_CopyBoneWeightsToBuffer(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    buffer: JByteBuffer,
    vertex_count: jint,
) -> jint {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let ptr = model.get_bone_weights_ptr();
        if ptr.is_null() {
            return 0;
        }
        
        if let Ok(dst) = env.get_direct_buffer_address(&buffer) {
            let byte_size = (vertex_count as usize) * 16; // 4 float * 4 bytes
            unsafe {
                let src = ptr as *const u8;
                ptr::copy_nonoverlapping(src, dst, byte_size);
            }
            return vertex_count;
        }
    }
    0
}

/// 获取原始顶点位置数据指针（未蒙皮，用于 GPU 蒙皮）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetOriginalPositions(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        return model.get_original_positions_ptr() as jlong;
    }
    0
}

/// 复制原始顶点位置到 ByteBuffer（线程安全）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_CopyOriginalPositionsToBuffer(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    buffer: JByteBuffer,
    vertex_count: jint,
) -> jint {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let ptr = model.get_original_positions_ptr();
        if ptr.is_null() {
            return 0;
        }
        
        if let Ok(dst) = env.get_direct_buffer_address(&buffer) {
            let byte_size = (vertex_count as usize) * 12; // 3 float * 4 bytes
            unsafe {
                let src = ptr as *const u8;
                ptr::copy_nonoverlapping(src, dst, byte_size);
            }
            return vertex_count;
        }
    }
    0
}

/// 获取原始法线数据指针（未蒙皮，用于 GPU 蒙皮）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetOriginalNormals(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        return model.get_original_normals_ptr() as jlong;
    }
    0
}

/// 复制原始法线到 ByteBuffer（线程安全）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_CopyOriginalNormalsToBuffer(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    buffer: JByteBuffer,
    vertex_count: jint,
) -> jint {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let ptr = model.get_original_normals_ptr();
        if ptr.is_null() {
            return 0;
        }
        
        if let Ok(dst) = env.get_direct_buffer_address(&buffer) {
            let byte_size = (vertex_count as usize) * 12; // 3 float * 4 bytes
            unsafe {
                let src = ptr as *const u8;
                ptr::copy_nonoverlapping(src, dst, byte_size);
            }
            return vertex_count;
        }
    }
    0
}

/// 获取 GPU 蒙皮调试信息（返回 JSON 字符串）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetGpuSkinningDebugInfo<'a>(
    env: JNIEnv<'a>,
    _class: JClass,
    model: jlong,
) -> jstring {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        
        let vertex_count = model.vertices.len();
        let bone_count = model.bone_manager.bone_count();
        let bone_indices = model.get_bone_indices();
        let bone_weights = model.get_bone_weights();
        
        // 统计信息
        let mut max_bone_idx = -1i32;
        let mut invalid_idx_count = 0usize;
        let mut zero_weight_count = 0usize;
        let mut bdef1_count = 0usize;
        let mut bdef2_count = 0usize;
        let mut bdef4_count = 0usize;
        
        for i in 0..vertex_count {
            let base = i * 4;
            let mut total_weight = 0.0f32;
            let mut valid_bones = 0;
            let mut used_slots = 0;
            
            for j in 0..4 {
                let idx = bone_indices[base + j];
                let weight = bone_weights[base + j];
                
                if idx > max_bone_idx {
                    max_bone_idx = idx;
                }
                if idx >= 0 {
                    used_slots += 1;
                    if idx < bone_count as i32 {
                        valid_bones += 1;
                        total_weight += weight;
                    } else {
                        invalid_idx_count += 1;
                    }
                }
            }
            
            if valid_bones > 0 && total_weight < 0.001 {
                zero_weight_count += 1;
            }
            
            // 统计权重类型
            match used_slots {
                1 => bdef1_count += 1,
                2 => bdef2_count += 1,
                _ => bdef4_count += 1,
            }
        }
        
        // 物理信息
        let physics_enabled = model.is_physics_enabled();
        let dynamic_bones = model.get_dynamic_bone_count();
        
        let info = format!(
            "顶点:{}, 骨骼:{}, 最大索引:{}, 无效索引:{}, 零权重:{}, BDEF1:{}, BDEF2:{}, BDEF4+:{}, 物理:{}, 动态骨骼:{}",
            vertex_count, bone_count, max_bone_idx, invalid_idx_count, zero_weight_count,
            bdef1_count, bdef2_count, bdef4_count, physics_enabled, dynamic_bones
        );
        
        return env.new_string(&info).unwrap().into_raw();
    }
    
    env.new_string("模型未找到").unwrap().into_raw()
}

/// 仅更新动画（不执行 CPU 蒙皮，用于 GPU 蒙皮模式）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_UpdateAnimationOnly(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    delta_time: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.tick_animation_no_skinning(delta_time);
    }
}

/// 初始化 GPU 蒙皮数据
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_InitGpuSkinningData(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.init_gpu_skinning_data();
    }
}

// ============================================================================
// GPU Morph 相关函数
// ============================================================================

/// 初始化 GPU Morph 数据
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_InitGpuMorphData(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.init_gpu_morph_data();
    }
}

/// 获取顶点 Morph 数量
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetVertexMorphCount(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jint {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| m.lock().unwrap().get_vertex_morph_count() as jint)
        .unwrap_or(0)
}

/// 获取 GPU Morph 偏移数据指针
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetGpuMorphOffsets(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        return model.get_gpu_morph_offsets_ptr() as jlong;
    }
    0
}

/// 获取 GPU Morph 偏移数据大小（字节）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetGpuMorphOffsetsSize(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| m.lock().unwrap().get_gpu_morph_offsets_size() as jlong)
        .unwrap_or(0)
}

/// 获取 GPU Morph 权重数据指针
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetGpuMorphWeights(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        return model.get_gpu_morph_weights_ptr() as jlong;
    }
    0
}

/// 同步 GPU Morph 权重（从 MorphManager 更新到 GPU 缓冲区）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SyncGpuMorphWeights(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.sync_gpu_morph_weights();
    }
}

/// 复制 GPU Morph 偏移数据到 ByteBuffer
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_CopyGpuMorphOffsetsToBuffer(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    buffer: JByteBuffer,
) -> jlong {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let size = model.get_gpu_morph_offsets_size();
        if size == 0 {
            return 0;
        }
        
        if let Ok(dst) = env.get_direct_buffer_address(&buffer) {
            unsafe {
                let src = model.get_gpu_morph_offsets_ptr() as *const u8;
                ptr::copy_nonoverlapping(src, dst, size);
            }
            return size as jlong;
        }
    }
    0
}

/// 复制 GPU Morph 权重数据到 ByteBuffer
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_CopyGpuMorphWeightsToBuffer(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    buffer: JByteBuffer,
) -> jint {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let morph_count = model.get_vertex_morph_count();
        if morph_count == 0 {
            return 0;
        }
        
        let byte_size = morph_count * 4; // float = 4 bytes
        if let Ok(dst) = env.get_direct_buffer_address(&buffer) {
            unsafe {
                let src = model.get_gpu_morph_weights_ptr() as *const u8;
                ptr::copy_nonoverlapping(src, dst, byte_size);
            }
            return morph_count as jint;
        }
    }
    0
}

/// 获取 GPU Morph 是否已初始化
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_IsGpuMorphInitialized(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jboolean {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        if model.is_gpu_morph_initialized() {
            return 1;
        }
    }
    0
}

// ============================================================================
// VPD 表情预设相关函数
// ============================================================================

/// 加载 VPD 表情预设并应用到模型
/// 
/// VPD 文件可以同时包含骨骼姿势（Bone）和表情权重（Morph）数据，此函数会同时应用两者。
/// 
/// 返回值编码: 高16位为骨骼匹配数，低16位为 Morph 匹配数
/// - 成功: (bone_count << 16) | morph_count
/// - -1: 文件加载失败
/// - -2: 模型不存在
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_ApplyVpdMorph(
    mut env: JNIEnv,
    _class: JClass,
    model: jlong,
    filename: JString,
) -> jint {
    use crate::animation::VpdFile;
    
    let filename_str: String = match env.get_string(&filename) {
        Ok(s) => s.into(),
        Err(_) => return -1,
    };
    
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        
        match VpdFile::load(&filename_str) {
            Ok(vpd) => {
                let mut morph_count = 0i32;
                let mut bone_count = 0i32;
                
                // 1. 应用 Morph 表情
                for morph_data in &vpd.morphs {
                    if let Some(idx) = model.morph_manager.find_morph_by_name(&morph_data.name) {
                        model.morph_manager.set_morph_weight(idx, morph_data.weight);
                        morph_count += 1;
                    }
                }
                
                // 2. 设置 VPD 骨骼姿势覆盖（会在每帧动画评估后自动应用）
                model.clear_vpd_bone_overrides();
                for bone_data in &vpd.bones {
                    if let Some(idx) = model.bone_manager.find_bone_by_name(&bone_data.name) {
                        model.set_vpd_bone_override(idx, bone_data.translation, bone_data.rotation);
                        bone_count += 1;
                    }
                }
                
                // 同步到 GPU 缓冲区（用于 GPU 蒙皮模式）
                model.sync_gpu_morph_weights();
                
                // 返回编码值: 高16位骨骼数，低16位 Morph 数
                return ((bone_count & 0xFFFF) << 16) | (morph_count & 0xFFFF);
            }
            Err(_) => {
                return -1;
            }
        }
    }
    -2
}

/// 重置所有 Morph 权重和 VPD 骨骼姿势覆盖
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_ResetAllMorphs(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.morph_manager.reset_all_weights();
        model.clear_vpd_bone_overrides();
        model.sync_gpu_morph_weights();
    }
}

/// 设置单个 Morph 权重（通过名称）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetMorphWeightByName(
    mut env: JNIEnv,
    _class: JClass,
    model: jlong,
    morph_name: JString,
    weight: jfloat,
) -> jboolean {
    let name_str: String = match env.get_string(&morph_name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        if let Some(idx) = model.morph_manager.find_morph_by_name(&name_str) {
            model.morph_manager.set_morph_weight(idx, weight);
            model.sync_gpu_morph_weights();
            return 1;
        }
    }
    0
}

/// 获取 Morph 数量
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMorphCount(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        return model.morph_manager.morph_count() as jlong;
    }
    0
}

/// 获取 Morph 名称（通过索引）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMorphName(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    index: jint,
) -> jstring {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        if let Some(morph) = model.morph_manager.get_morph(index as usize) {
            if let Ok(s) = env.new_string(&morph.name) {
                return s.into_raw();
            }
        }
    }
    ptr::null_mut()
}

/// 获取 Morph 权重（通过索引）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMorphWeight(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    index: jint,
) -> jfloat {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        if let Some(morph) = model.morph_manager.get_morph(index as usize) {
            return morph.weight;
        }
    }
    0.0
}

/// 设置 Morph 权重（通过索引）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetMorphWeight(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    index: jint,
    weight: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.morph_manager.set_morph_weight(index as usize, weight);
    }
}

// ====================================================================
// GPU UV Morph 相关函数
// ====================================================================

/// 初始化 GPU UV Morph 数据
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_InitGpuUvMorphData(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.init_gpu_uv_morph_data();
    }
}

/// 获取 UV Morph 数量
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetUvMorphCount(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jint {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| m.lock().unwrap().get_uv_morph_count() as jint)
        .unwrap_or(0)
}

/// 获取 GPU UV Morph 偏移数据大小（字节）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetGpuUvMorphOffsetsSize(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| m.lock().unwrap().get_gpu_uv_morph_offsets_size() as jlong)
        .unwrap_or(0)
}

/// 复制 GPU UV Morph 偏移数据到 ByteBuffer
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_CopyGpuUvMorphOffsetsToBuffer(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    buffer: JByteBuffer,
) -> jlong {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let size = model.get_gpu_uv_morph_offsets_size();
        if size == 0 {
            return 0;
        }

        if let Ok(dst) = env.get_direct_buffer_address(&buffer) {
            unsafe {
                let src = model.get_gpu_uv_morph_offsets_ptr() as *const u8;
                ptr::copy_nonoverlapping(src, dst, size);
            }
            return size as jlong;
        }
    }
    0
}

/// 复制 GPU UV Morph 权重数据到 ByteBuffer
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_CopyGpuUvMorphWeightsToBuffer(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    buffer: JByteBuffer,
) -> jint {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        let morph_count = model.get_uv_morph_count();
        if morph_count == 0 {
            return 0;
        }

        let byte_size = morph_count * 4;
        if let Ok(dst) = env.get_direct_buffer_address(&buffer) {
            unsafe {
                let src = model.get_gpu_uv_morph_weights_ptr() as *const u8;
                ptr::copy_nonoverlapping(src, dst, byte_size);
            }
            return morph_count as jint;
        }
    }
    0
}

// ====================================================================
// 材质 Morph 结果相关函数
// ====================================================================

/// 获取材质 Morph 结果数量
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetMaterialMorphResultCount(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jint {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        return model.get_material_morph_result_count() as jint;
    }
    0
}

/// 复制材质 Morph 结果到 ByteBuffer
/// 每个材质 56 个 float:
/// mul[diffuse(4) + specular(3) + specular_strength(1) + ambient(3) +
///     edge_color(4) + edge_size(1) + texture_tint(4) + environment_tint(4) + toon_tint(4)] = 28
/// + add[同上布局] = 28
/// 渲染时：final = base * mul + add
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_CopyMaterialMorphResultsToBuffer(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    buffer: JByteBuffer,
) -> jint {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        let result_count = model.get_material_morph_result_count();
        let flat = model.get_material_morph_results_flat();
        if flat.is_empty() {
            return 0;
        }

        let byte_size = flat.len() * 4;
        if let Ok(dst) = env.get_direct_buffer_address(&buffer) {
            unsafe {
                let src = flat.as_ptr() as *const u8;
                ptr::copy_nonoverlapping(src, dst, byte_size);
            }
            return result_count as jint;
        }
    }
    0
}

// ==================== 物理配置相关 ====================

/// 设置全局物理配置（实时调整）
#[no_mangle]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetPhysicsConfig(
    _env: JNIEnv,
    _class: JClass,
    gravity_y: jfloat,
    physics_fps: jfloat,
    max_substep_count: jint,
    solver_iterations: jint,
    pgs_iterations: jint,
    max_corrective_velocity: jfloat,
    linear_damping_scale: jfloat,
    angular_damping_scale: jfloat,
    mass_scale: jfloat,
    linear_spring_stiffness_scale: jfloat,
    _angular_spring_stiffness_scale: jfloat,
    _linear_spring_damping_factor: jfloat,
    _angular_spring_damping_factor: jfloat,
    inertia_strength: jfloat,
    max_linear_velocity: jfloat,
    max_angular_velocity: jfloat,
    _bust_physics_enabled: jboolean,
    _bust_linear_damping_scale: jfloat,
    _bust_angular_damping_scale: jfloat,
    _bust_mass_scale: jfloat,
    _bust_linear_spring_stiffness_scale: jfloat,
    _bust_angular_spring_stiffness_scale: jfloat,
    _bust_linear_spring_damping_factor: jfloat,
    _bust_angular_spring_damping_factor: jfloat,
    _bust_clamp_inward: jboolean,
    joints_enabled: jboolean,
    debug_log: jboolean,
) {
    use crate::physics::config::{PhysicsConfig, set_config};
    
    // 旧参数映射：
    // - linear_spring_stiffness_scale → spring_stiffness_scale
    // - 其他弹簧/胸部参数均已废弃（胸部现在使用统一的 6DOF 弹簧系统）
    let config = PhysicsConfig {
        gravity_y,
        physics_fps,
        max_substep_count: max_substep_count as i32,
        solver_iterations: solver_iterations as usize,
        pgs_iterations: pgs_iterations as usize,
        max_corrective_velocity,
        linear_damping_scale,
        angular_damping_scale,
        mass_scale,
        spring_stiffness_scale: linear_spring_stiffness_scale,
        inertia_strength,
        max_linear_velocity,
        max_angular_velocity,
        joints_enabled: joints_enabled != 0,
        debug_log: debug_log != 0,
    };
    
    set_config(config);
    
    if debug_log != 0 {
        log::info!("[物理配置] 已更新: 重力={}, FPS={}, 阻尼={}/{}, 弹簧缩放={}", 
            gravity_y, physics_fps, 
            linear_damping_scale, angular_damping_scale,
            linear_spring_stiffness_scale);
    }
}

// ========== 第一人称模式相关 ==========

/// 设置第一人称模式（启用时自动隐藏头部子网格，禁用时恢复）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_SetFirstPersonMode(
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

/// 获取第一人称模式是否启用
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_IsFirstPersonMode(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jboolean {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        if model.is_first_person_enabled() { 1 } else { 0 }
    } else {
        0
    }
}

/// 获取头部骨骼的静态 Y 坐标（模型局部空间，用于相机高度计算）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetHeadBonePositionY(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jfloat {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.get_head_bone_rest_position_y()
    } else {
        0.0
    }
}

/// 获取眼睛骨骼的当前动画位置（模型局部空间）
/// 每帧调用，返回经过动画/物理更新后的实时 [x, y, z]
/// 如果传入的 out 数组长度 < 3 则不写入
#[no_mangle]
#[allow(unused_mut)]
pub extern "system" fn Java_com_shiroha_mmdskin_NativeFunc_GetEyeBonePosition(
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
