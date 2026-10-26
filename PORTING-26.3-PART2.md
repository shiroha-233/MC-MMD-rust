---

# 第二阶段更新（2026-10-24 02:30）

## ✅ 布娃娃（你的功能）—— Rust + Java 两侧都完成了

### Rust 侧（编译通过，dll 已产出）
| 文件 | 改动 |
|---|---|
| `rust_engine/src/ragdoll/{mod.rs,spec.rs}` | 从你的 1.20.1 分支整体搬过来（54KB） |
| `rust_engine/src/lib.rs` | 加 `pub mod ragdoll;` |
| `rust_engine/src/model/runtime.rs` | 字段 + 7 个方法 + 每帧步进挂接（物理步进之后，**不受 MMD 物理开关影响**）+ 接管时关 IK |
| `rust_engine/src/skeleton/bone_set.rs` | `ragdoll_bone_indices` + 3 方法 + 2 处骨骼传播 ragdoll-aware |
| `rust_engine/src/physics/bullet_ffi.rs` | 补 `bw_world_refresh_rigid_body` FFI 声明 + `BulletWorld::refresh_rigid_body` |
| `rust_engine/src/bullet_wrapper/bw_api.{h,cpp}` | **补 C 实现**（⚠️ 必须同时在 `bw_api.h` 的 `extern "C"` 块里声明，否则会被 name mangling，链接报 LNK1120） |
| `rust_engine/src/jni_bridge/runtime_bindings/physics.rs` | 202 行，7 个 JNI 导出（类名 `NativeFunc`→`bridge.NativeBindings`） |
| `rust_engine/src/jni_bridge/runtime_bindings.rs` | 补 `JFloatArray` 导入 |

**产物**：`rust_engine/target/release/mmd_engine.dll`（3.8 MB，`cargo build --release` 42s）
**导出核验**：`dumpbin /exports` 确认 7 个 `..._bridge_NativeBindings_Ragdoll*` 全部在位 ✅

### Java 侧（common 模块 389 个 class，0 错误）
**新建 4 个**：`player/ragdoll/RagdollManager.java`（872 行）、`player/ragdoll/RagdollRenderEntrypoint.java`、
`bridge/runtime/NativeRagdollPort.java`、`config/IRagdollConfig.java`
**改动 7 个**：`bridge/NativeBindings.java`（+7 个 native 声明）、`bridge/NativePortAdapters.java`、
`config/{ConfigData,ConfigManager,AbstractMmdSkinConfig}.java`（+10 项配置）、
`client/MmdClientRenderRuntime.java`（tick 推进 + flushWorldFrame 里加尸体渲染贡献）、
`client/frame/MmdSnapshotFactory.java`（**死亡触发点** `tryEnterRagdoll`）

### 尸体在 26.3 上怎么渲染
**不走 `RenderType`**，走基底已有的 MMD 帧队列链：
```
MmdClientRenderRuntime.flushWorldFrame(...) 
  └─ RagdollRenderEntrypoint.contribute(...)
       └─ RagdollManager.contribute()
            ├─ 相机相对矩阵 + 绝对世界坐标 motion（synchronizeNative=true）
            ├─ frameUpdater().updateOnce(...)   → 动画求值 + MMD 物理 + 布娃娃步进 + 回写骨骼
            └─ frameQueue().enqueue(MmdDrawRequest) → MmdGpuDrawDevice 开 RenderPass 统一提交
```

---

## 🔴 还需你拍板的取舍（布娃娃 agent 提出的，我没替你决定）

| # | 取舍 | 影响 |
|---|---|---|
| 1 | **死亡瞬间本体与尸体会同时显示（双影）** | 26.3 的"取消本体绘制"注入点在 `client/draw/MmdRenderRouter.route` 或平台 mixin（当时在禁改目录）。判定已暴露成 `RagdollManager.shouldSuppressEntityRender(LivingEntity)`，需要路由/mixin 的 owner 接一下 |
| 2 | **死亡白烟还在** | `shouldHideDeathPoof(Entity)` 已暴露，缺 `LivingEntityDeathParticle` mixin |
| 3 | **死亡界面劫持只做了"自动第三人称"** | 透明死亡屏 / 单击观战 / 双击复活需要 `DeathScreenMixin`+`CameraMixin`（fabric/mixin 当时禁改） |
| 4 | **`params[14]` 用固定 0.09** | 与 1.20.1 等价（Rust `UNIT_TO_M` 也是固定 0.09）。副作用：**用户把模型缩放调到 2.0 时，布娃娃物理尺寸只有视觉的一半**。要不要改成 `0.09 × modelScale`？改了会影响初速度换算 |
| 5 | 远离视线的尸体也会推进模型 | 1.20.1 只在绘制时推进。因为 26.3 的"渲染满 3 帧"判据实际是"**推进**满 3 帧" —— 只在绘制时推进的话，远离视线的尸体骨骼永远停在 T-pose，2 秒兜底启物理就会按 T-pose 摊开 |
| 6 | `ConfigData.schemaVersion` 没升 | 其它 agent 也在加配置项，避免冲突；新字段靠 Gson 缺省值补齐 |
| 7 | 配置 UI 没加 | `fabric/config/**` 当时禁改 |

---

## ⚠️ 布娃娃 agent 列出的 26.3 语义变化隐患（"能编译但结果错"）

| # | 隐患 |
|---|---|
| a | **相机相对 vs 世界绝对** —— 26.3 的模型矩阵是相机相对的，native 模型位置却是绝对世界坐标 ×0.09。两套坐标混用是这版最容易错的点 |
| b | 模型推进与绘制解耦 —— 动画/物理在 `UpdateModel` 里发生，绘制在 flush 时 |
| c | `LevelRenderer.getLightColor` 已删 → 实体路径用 `EntityRenderState.lightCoords`，独立尸体只能自算（已用 `LightCoordsUtil`） |
| d | **`ModelRepository` 是租约模型** —— 活人模型每帧 acquire/close；**尸体必须常驻持有租约**，否则会被 `evictUnused` 回收；而且 `acquire` 每次调用都 +1，尸体只能 acquire 一次 |
| e | 销毁入口变了 —— 26.3 没有 `releaseSubject`，真正销毁是 `ModelRepository.invalidateOwner(ownerId)`；且 `drawDevice.releaseModel` 有 `assertOnRenderThread()`，必须 tick 间隙执行 |
| f | `MmdModelFramePreparation` 会用 `snapshot.motion()` 覆盖 native 位置 → 尸体必须每帧给 `synchronizeNative=true` |
| g | `ModelAnimationController.apply` 只在 `intent.active()` 时生效 → 尸体用 `MmdAnimationIntent.none()`，die 动画才不会被每帧重置 |
| h | `state.appearsGlowing` 是**方法**不是字段（已顺手修） |
| i | **Rust dll 未重编时 `RagdollEnable` 抛 `UnsatisfiedLinkError`（是 Error 不是 Exception）** → 已全部 native 调用改 catch `Throwable`，降级为"尸体照常渲染+播死亡动画，只是没有布娃娃"，不会崩客户端 |
| j | `FabricClientRuntimeHooks.java:74` 还在用 `Lightmap.FULL_BRIGHT`，26.3 已无该常量 |

---

## 编译进度

| 批次 | 文件数 | 状态 |
|---|---|---|
| 机械重命名 | 37 | ✅ |
| GPU 渲染层 | 3 | ✅ 单独 javac 0 错 |
| GUI 层 | 19 | ✅ 单独 javac 0 错 |
| 相机控制等 | 8 | ✅ 单独 javac 0 错 |
| 渲染核心（我） | 8 | ✅ |
| 布娃娃 Java | 11 | ✅ common 389 class 0 错 |
| **Fabric 平台层** | 4 | 🔄 剩 48 个错误，代理重跑中 |

**⇒ 全部文件改动数：73**
