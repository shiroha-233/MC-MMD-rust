//! JNI 绑定层 - 与 Java 代码交互

mod native_func;
mod model_handle;
mod animation_handle;

pub use native_func::*;
pub use model_handle::ModelHandle;
pub use animation_handle::AnimationHandle;

use std::collections::HashMap;
use std::sync::{Arc, Mutex, RwLock};
use once_cell::sync::Lazy;

use crate::model::MmdModel;
use crate::animation::VmdAnimation;
use crate::animation::fbx_loader::FbxCache;
use crate::texture::Texture;

/// FBX 文件解析缓存（避免重复解析大文件）
pub static FBX_CACHE: Lazy<RwLock<HashMap<String, Arc<FbxCache>>>> =
    Lazy::new(|| RwLock::new(HashMap::new()));

/// 全局模型存储
pub static MODELS: Lazy<RwLock<HashMap<i64, Arc<Mutex<MmdModel>>>>> = 
    Lazy::new(|| RwLock::new(HashMap::new()));

/// 全局动画存储
pub static ANIMATIONS: Lazy<RwLock<HashMap<i64, Arc<VmdAnimation>>>> = 
    Lazy::new(|| RwLock::new(HashMap::new()));

/// 全局纹理存储
pub static TEXTURES: Lazy<RwLock<HashMap<i64, Arc<Texture>>>> = 
    Lazy::new(|| RwLock::new(HashMap::new()));

/// 生成唯一句柄 ID
fn next_handle_id() -> i64 {
    use std::sync::atomic::{AtomicI64, Ordering};
    static COUNTER: AtomicI64 = AtomicI64::new(1);
    COUNTER.fetch_add(1, Ordering::SeqCst)
}

/// 注册模型并返回句柄
pub fn register_model(model: MmdModel) -> i64 {
    let id = next_handle_id();
    let mut models = MODELS.write().unwrap();
    models.insert(id, Arc::new(Mutex::new(model)));
    id
}

/// 注册动画并返回句柄
pub fn register_animation(animation: VmdAnimation) -> i64 {
    let id = next_handle_id();
    let mut animations = ANIMATIONS.write().unwrap();
    animations.insert(id, Arc::new(animation));
    id
}

/// 注册纹理并返回句柄
pub fn register_texture(texture: Texture) -> i64 {
    let id = next_handle_id();
    let mut textures = TEXTURES.write().unwrap();
    textures.insert(id, Arc::new(texture));
    id
}
