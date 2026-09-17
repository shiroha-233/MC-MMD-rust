//! 负责集中管理 native 对象生命周期，并缩短 registry 锁的持有时间。

use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, RwLock};

use once_cell::sync::Lazy;

use crate::animation::fbx_loader::FbxCache;
use crate::animation::VmdAnimation;
use crate::model::MmdModel;
use crate::texture::Texture;

use super::{AnimationHandle, BridgeError, BridgeResult, ModelHandle, TextureHandle};

pub static NATIVE_RUNTIME: Lazy<NativeRuntime> = Lazy::new(NativeRuntime::new);

pub(crate) struct NativeRuntime {
    next_handle: AtomicU64,
    state: RwLock<RuntimeState>,
}

#[derive(Default)]
struct RuntimeState {
    models: HashMap<ModelHandle, Arc<Mutex<MmdModel>>>,
    animations: HashMap<AnimationHandle, Arc<VmdAnimation>>,
    textures: HashMap<TextureHandle, Arc<Texture>>,
    fbx_cache: HashMap<String, Arc<FbxCache>>,
}

impl NativeRuntime {
    pub(crate) fn new() -> Self {
        Self {
            next_handle: AtomicU64::new(1),
            state: RwLock::new(RuntimeState::default()),
        }
    }

    pub(crate) fn register_model(&self, model: MmdModel) -> BridgeResult<ModelHandle> {
        let handle = ModelHandle::new(self.allocate_handle()?)?;
        self.state
            .write()?
            .models
            .insert(handle, Arc::new(Mutex::new(model)));
        Ok(handle)
    }

    pub(crate) fn model(&self, handle: ModelHandle) -> BridgeResult<Arc<Mutex<MmdModel>>> {
        self.state
            .read()?
            .models
            .get(&handle)
            .cloned()
            .ok_or_else(|| BridgeError::InvalidHandle(handle.raw()))
    }

    pub(crate) fn remove_model(&self, handle: ModelHandle) -> BridgeResult<bool> {
        Ok(self.state.write()?.models.remove(&handle).is_some())
    }

    /// 收集当前全部已注册模型的快照（Arc 克隆，避免跨读锁持有迭代）。
    /// 供配置变更等需要遍历所有模型的 JNI 入口使用。
    pub(crate) fn models_snapshot(&self) -> BridgeResult<Vec<Arc<Mutex<MmdModel>>>> {
        Ok(self.state.read()?.models.values().cloned().collect())
    }

    pub(crate) fn register_animation(
        &self,
        animation: VmdAnimation,
    ) -> BridgeResult<AnimationHandle> {
        let handle = AnimationHandle::new(self.allocate_handle()?)?;
        self.state
            .write()?
            .animations
            .insert(handle, Arc::new(animation));
        Ok(handle)
    }

    pub(crate) fn animation(&self, handle: AnimationHandle) -> BridgeResult<Arc<VmdAnimation>> {
        self.state
            .read()?
            .animations
            .get(&handle)
            .cloned()
            .ok_or_else(|| BridgeError::InvalidHandle(handle.raw()))
    }

    pub(crate) fn replace_animation(
        &self,
        handle: AnimationHandle,
        animation: Arc<VmdAnimation>,
    ) -> BridgeResult<()> {
        let previous = self.state.write()?.animations.insert(handle, animation);
        if previous.is_none() {
            return Err(BridgeError::InvalidHandle(handle.raw()));
        }
        Ok(())
    }

    pub(crate) fn remove_animation(&self, handle: AnimationHandle) -> BridgeResult<bool> {
        Ok(self.state.write()?.animations.remove(&handle).is_some())
    }

    pub(crate) fn register_texture(&self, texture: Texture) -> BridgeResult<TextureHandle> {
        let handle = TextureHandle::new(self.allocate_handle()?)?;
        self.state
            .write()?
            .textures
            .insert(handle, Arc::new(texture));
        Ok(handle)
    }

    pub(crate) fn texture(&self, handle: TextureHandle) -> BridgeResult<Arc<Texture>> {
        self.state
            .read()?
            .textures
            .get(&handle)
            .cloned()
            .ok_or_else(|| BridgeError::InvalidHandle(handle.raw()))
    }

    pub(crate) fn remove_texture(&self, handle: TextureHandle) -> BridgeResult<bool> {
        Ok(self.state.write()?.textures.remove(&handle).is_some())
    }

    pub(crate) fn fbx(&self, path: &str) -> BridgeResult<Option<Arc<FbxCache>>> {
        Ok(self.state.read()?.fbx_cache.get(path).cloned())
    }

    pub(crate) fn cache_fbx(&self, path: String, cache: Arc<FbxCache>) -> BridgeResult<()> {
        self.state.write()?.fbx_cache.insert(path, cache);
        Ok(())
    }

    fn allocate_handle(&self) -> BridgeResult<u64> {
        self.next_handle
            .fetch_update(Ordering::Relaxed, Ordering::Relaxed, |current| {
                (current <= i64::MAX as u64).then_some(current + 1)
            })
            .map_err(|_| BridgeError::SizeOverflow)
    }
}

// 以下只读视图让 JNI 域代码复用唯一 NativeRuntime，不拥有第二份 registry 状态。
pub(crate) struct LegacyLockResult<T>(T);

impl<T> LegacyLockResult<T> {
    pub(crate) fn unwrap(self) -> T {
        self.0
    }
}

#[derive(Clone, Copy)]
pub(crate) struct LegacyModelRegistryPort;

impl LegacyModelRegistryPort {
    pub(crate) fn read(&self) -> LegacyLockResult<Self> {
        LegacyLockResult(*self)
    }

    pub(crate) fn get(&self, raw: &i64) -> Option<Arc<Mutex<MmdModel>>> {
        ModelHandle::from_raw(*raw)
            .and_then(|handle| NATIVE_RUNTIME.model(handle))
            .ok()
    }
}

#[derive(Clone, Copy)]
pub(crate) struct LegacyAnimationRegistryPort;

impl LegacyAnimationRegistryPort {
    pub(crate) fn read(&self) -> LegacyLockResult<Self> {
        LegacyLockResult(*self)
    }

    pub(crate) fn write(&self) -> LegacyLockResult<Self> {
        LegacyLockResult(*self)
    }

    pub(crate) fn get(&self, raw: &i64) -> Option<Arc<VmdAnimation>> {
        AnimationHandle::from_raw(*raw)
            .and_then(|handle| NATIVE_RUNTIME.animation(handle))
            .ok()
    }

    pub(crate) fn insert(
        &mut self,
        raw: i64,
        animation: Arc<VmdAnimation>,
    ) -> Option<Arc<VmdAnimation>> {
        let handle = AnimationHandle::from_raw(raw).ok()?;
        let previous = NATIVE_RUNTIME.animation(handle).ok()?;
        NATIVE_RUNTIME.replace_animation(handle, animation).ok()?;
        Some(previous)
    }
}

#[derive(Clone, Copy)]
pub(crate) struct LegacyFbxRegistryPort;

impl LegacyFbxRegistryPort {
    pub(crate) fn read(&self) -> LegacyLockResult<Self> {
        LegacyLockResult(*self)
    }

    pub(crate) fn write(&self) -> LegacyLockResult<Self> {
        LegacyLockResult(*self)
    }

    pub(crate) fn get(&self, path: &str) -> Option<Arc<FbxCache>> {
        NATIVE_RUNTIME.fbx(path).ok().flatten()
    }

    pub(crate) fn insert(&mut self, path: String, cache: Arc<FbxCache>) -> Option<Arc<FbxCache>> {
        let previous = self.get(&path);
        NATIVE_RUNTIME.cache_fbx(path, cache).ok()?;
        previous
    }
}

pub(crate) static MODELS: LegacyModelRegistryPort = LegacyModelRegistryPort;
pub(crate) static ANIMATIONS: LegacyAnimationRegistryPort = LegacyAnimationRegistryPort;
pub(crate) static FBX_CACHE: LegacyFbxRegistryPort = LegacyFbxRegistryPort;

#[cfg(test)]
mod tests {
    use std::sync::Arc;
    use std::thread;

    use crate::model::MmdModel;

    use super::NativeRuntime;

    #[test]
    fn model_lookup_should_release_registry_lock_before_model_lock() {
        let runtime = NativeRuntime::new();
        let handle = runtime
            .register_model(MmdModel::new())
            .expect("model should register");
        let model = runtime.model(handle).expect("model should exist");

        runtime.remove_model(handle).expect("model should delete");

        assert!(runtime.model(handle).is_err());
        assert!(model.lock().is_ok());
    }

    #[test]
    fn model_lookup_should_reject_unknown_typed_handle() {
        let runtime = NativeRuntime::new();
        let handle = super::ModelHandle::from_raw(999).expect("positive handle should parse");

        assert!(runtime.model(handle).is_err());
    }

    #[test]
    fn concurrent_delete_should_not_invalidate_existing_lease() {
        let runtime = Arc::new(NativeRuntime::new());
        let handle = runtime
            .register_model(MmdModel::new())
            .expect("model should register");
        let lease = runtime.model(handle).expect("model should exist");
        let deleting_runtime = Arc::clone(&runtime);

        thread::spawn(move || deleting_runtime.remove_model(handle))
            .join()
            .expect("delete thread should finish")
            .expect("delete should succeed");

        assert!(lease.lock().is_ok());
        assert!(runtime.model(handle).is_err());
    }
}
