Fix: 背包人物无头状态，特定情况下写入虚空内存导致崩溃

第一人称遮罩改为单次 Draw 状态
[render_view.rs (line 1)](C:/tmp/mc-mmd-rust/rust_engine/src/jni_bridge/render_view.rs:1) 新增按视图读取子网格数据的 JNI 入口。第一人称状态只在模型锁内临时切换，数据写出后立即恢复，避免污染背包、阴影、镜像和其他玩家的绘制。
RenderScene 已贯通两套渲染后端
[BaseModelInstance.java (line 68)](C:/tmp/mc-mmd-rust/common/src/main/java/com/shiroha/mmdskin/render/backend/BaseModelInstance.java:68) 会把场景继续传到 CPU OpenGL 和 GPU Skinning renderer。
GPU 后端的子网格可见性现在每次 Draw 都会刷新，不再错误地跟随动画 revision 缓存。
第一人称管理器不再持久修改材质
[FirstPersonManager.java (line 80)](C:/tmp/mc-mmd-rust/common/src/main/java/com/shiroha/mmdskin/player/runtime/FirstPersonManager.java:80) 不再调用 SetFirstPersonMode 控制长期可见性，只保留相机、眼骨和 VR 状态管理。
背包人物采用显式渲染作用域
新增 [InventoryRenderScope.java (line 4)](C:/tmp/mc-mmd-rust/common/src/main/java/com/shiroha/mmdskin/player/render/InventoryRenderScope.java:4)，并在 Fabric、Forge 的 InventoryScreenMixin 中进入和退出作用域。
因此“当前打开背包界面”不再等同于“当前 Draw 是背包人物”，联机背景世界的玩家不会被误判为背包预览。
背包模型跟随光标
[InventoryRenderHelper.java (line 27)](C:/tmp/mc-mmd-rust/common/src/main/java/com/shiroha/mmdskin/player/render/InventoryRenderHelper.java:27) 使用原版临时设置的 yBodyRot/yHeadRot/getXRot，不再使用实体调度器传入的固定零 yaw。
[HeadAngleHelper.java (line 37)](C:/tmp/mc-mmd-rust/common/src/main/java/com/shiroha/mmdskin/render/pipeline/HeadAngleHelper.java:37) 删除了旧的背包二次取反，使头部相对于身体保持正确的反向 yaw 差。