# MMD 第一人称渲染与相机修复工作日志

## 分支信息

- PR 分支：`codex/mmd-first-person-fixes`
- 上游基线：`origin/1.20-vr` (`5fe18f8`)
- 继承基线：`codex/tacz-action-suppot` (`05f42c6`)
- 关联问题：`shiroha-233/MC-MMD-rust#48`，以及后续游玩测试中独立发现的问题
- 整理日期：2026-08-02
- 当前状态：代码实现完成，等待 IDEA 内最新一轮运行时验收

本分支从 `codex/tacz-action-suppot` 创建，完整继承 `05f42c6 Fix texture upload and native build setup`，并在此基础上承载背包预览、桌面第一人称头部裁切、相机同步和运行稳定性修复。原分支保留，不再继续直接开发。

## 问题来源与范围

Issue #48 的原始范围只有一个问题：背包人物预览错误复用了第一人称隐藏头部状态，因而显示为没有头。本分支保留该问题的修复，但不会把后续发现全部描述成 Issue #48 的内容。

以下问题是在实现和实际游玩测试中独立发现的：

- 背包模型没有正确跟随光标视角。
- 第一人称只能看见头颈开放切口，特殊奔跑动作和身体前倾时更明显。
- 相机若锚定头骨中心，会偏离实际双眼视点。
- `Camera.setup` 与模型动画、物理更新存在一帧时序差。
- Sprint 回正时相机比最终渲染姿态提前约 0.1 秒完成。
- Camera 预更新提前触发纹理 GPU 初始化后，继承外部 PBO/pixel unpack 状态可能使 NVIDIA OpenGL 驱动访问冲突。

因此建议将 PR 描述为“第一人称渲染与相机综合修复”，在正文中注明 Issue #48 是最初触发项，而不是声称所有修复都由该 Issue 报告。

## 修复目标

1. 本地桌面第一人称隐藏头部主体，避免脸、眼睛、发片和头部内部遮挡。
2. 奔跑前倾、特殊动作以及动作回正期间不显示后脑或后颈开放断口。
3. 相机位于动画后左右眼的实际中点，并与模型当前显示出来的最终姿态保持同帧。
4. 背包、第三人称、远端玩家、阴影、VR、CPU/GPU 蒙皮保持原有行为。
5. 第一人称专用状态不得通过全局开关污染其他玩家或其他渲染 pass。

## 问题演进与根因

### 阶段一：Issue #48 场景隔离

Issue #48 的缺头问题来自背包渲染错误复用第一人称隐藏状态。修复方向是把第一人称可见性从持久化模型状态改为单次 `RenderScene` Draw 决策，并为背包建立独立渲染作用域。背包模型跟随光标的修正属于测试期间发现的相邻问题，不作为 Issue #48 原始描述的一部分。

### 阶段二：开放网格断口

仅按材质或固定比例隐藏头部会留下开放网格边界。奔跑前倾、低头和特殊动作会让该边界进入视野。修复改为：

- 预计算完整头部主体和有限头颈拓扑候选。
- 第一人称 Draw 使用独立动态 EBO。
- Draw 前根据最终 model-view、projection 和当前蒙皮姿态补充剔除进入扩张视锥的候选三角形。
- 通过极小空间焊接容差连接 PMX 在 UV、法线或材质接缝处复制的同位置顶点。
- CPU/GPU 共用 Rust 判定；GPU 只对有限候选做局部 CPU 蒙皮，不执行 GPU readback。

### 阶段三：相机空间位置错误

将相机固定到头骨中心会把视点放在双眼后下方。低头时相机会落入被裁掉的头部内部，并从颈口看见身体。

桌面第一人称锚点因此改为动画后真实左右眼位置的中点。即使模型存在 `両目` 控制骨，也单独缓存 `左目`、`右目` 给桌面相机；原 `GetEyeBonePosition` 语义保留给 VR 和公共 API。

### 阶段四：Camera.setup 一帧时序差

`Camera.setup` 原本早于本地模型本帧的动画和物理更新，只能读取上一帧渲染后的眼位。快速前倾时，相机和当前绘制模型会错开。

Fabric/Forge 现在在 `GameRenderer.renderLevel` HEAD 触发本地第一人称姿态预更新：

1. 刷新本地动作状态和模型缩放。
2. 同步实体、身体、头部和眼球输入。
3. 提前推进一次当前帧动画/物理。
4. 立即缓存当前帧双眼锚点。
5. 阴影和世界 pass 复用该实例姿态。
6. 正式 `RenderScene.FIRST_PERSON` Draw 在 `finally` 中消费预更新标记，禁止重复推进 Bullet。

预更新状态保存在单个 `BaseModelInstance` 中，不会影响远端玩家。没有发生正式第一人称 Draw 时，下一帧会覆盖旧标记，不会永久冻结。

### 阶段五：动作回正期间相机快于模型

停止奔跑时，相机仍会比模型约提前 0.1 秒回正。最终根因不是 Sprint 动画资源本身，而是模型网格和相机使用了两套不同的过渡结果：

- 网格使用 0.25 秒 smoothstep 插值后的最终蒙皮矩阵。
- 相机此前读取未应用该矩阵插值的 `bone.position()`。

因此骨骼逻辑位置已经切向 Idle/Walk 时，画面中的模型仍在从 Sprint 姿态逐渐回正。

桌面相机现在使用：

```text
renderedEyePosition = finalSkinningMatrix[eyeBone] * eyeBone.initialPosition
cameraAnchor = (renderedLeftEye + renderedRightEye) / 2
```

相机和网格由此共享完全相同的 smoothstep 过渡进度，不再额外增加相机平滑，也不修改全局动画切换时长。

### 阶段六：世界加载时 NVIDIA 驱动访问冲突

相机预更新会在 `renderLevel` 开始阶段解析本地模型，并可能在该时点完成预解码纹理的 GPU 上传。最新 `hs_err_pid59820.log` 显示崩溃发生在：

```text
nvoglv64.dll
GL11C.glTexImage2D
TextureGpuLoader.uploadPixels
GpuSkinningModelInstance.createFromHandle
ModelRepository.finalizeLoadedModel
```

Windows 退出码 `-1073741819` 对应 `0xC0000005 EXCEPTION_ACCESS_VIOLATION`。根因是调用方遗留的 `GL_PIXEL_UNPACK_BUFFER` 或 pixel unpack 参数：PBO 非零时，OpenGL 会把 Java direct `ByteBuffer` 地址解释成 PBO 偏移，NVIDIA 驱动随后访问无效地址。

`TextureGpuLoader` 现在会在上传前保存全部 pixel unpack 状态、解绑 PBO、清零 row/skip/image 参数并设定 alignment；上传完成后在 `finally` 中完整恢复调用方状态。该实现由继承基线 `05f42c6` 提供，并作为本综合 PR 的正式运行稳定性修复保留。
## 主要实现文件

### Native/Rust

- `rust_engine/src/model/first_person_mesh.rs`
- `rust_engine/src/model/runtime.rs`
- `rust_engine/src/model/mod.rs`
- `rust_engine/src/jni_bridge/render_view.rs`
- `rust_engine/src/jni_bridge/native_func.rs`
- `rust_engine/src/jni_bridge/mod.rs`
- `rust_engine/src/vrm_runtime/first_person.rs`

### Common Java

- `common/src/main/java/com/shiroha/mmdskin/NativeFunc.java`
- `common/src/main/java/com/shiroha/mmdskin/texture/runtime/TextureGpuLoader.java`
- `common/src/main/java/com/shiroha/mmdskin/bridge/runtime/NativeModelPort.java`
- `common/src/main/java/com/shiroha/mmdskin/bridge/runtime/NativeRenderBackendPort.java`
- `common/src/main/java/com/shiroha/mmdskin/bridge/runtime/NativeRuntimeBridge.java`
- `common/src/main/java/com/shiroha/mmdskin/player/runtime/FirstPersonManager.java`
- `common/src/main/java/com/shiroha/mmdskin/player/render/InventoryRenderScope.java`
- `common/src/main/java/com/shiroha/mmdskin/player/render/InventoryRenderHelper.java`
- `common/src/main/java/com/shiroha/mmdskin/player/render/PlayerModelRenderCoordinator.java`
- `common/src/main/java/com/shiroha/mmdskin/render/backend/FirstPersonPoseState.java`
- `common/src/main/java/com/shiroha/mmdskin/render/backend/BaseModelInstance.java`
- CPU/GPU renderer、instance 和 lifecycle 对应文件
- `common/src/main/java/com/shiroha/mmdskin/render/pipeline/HeadAngleHelper.java`
- `common/src/main/java/com/shiroha/mmdskin/render/pipeline/LivingEntityModelStateHelper.java`

### Fabric/Forge

- `fabric/src/main/java/com/shiroha/mmdskin/mixin/fabric/GameRendererMixin.java`
- `forge/src/main/java/com/shiroha/mmdskin/mixin/forge/GameRendererMixin.java`
- Fabric/Forge 的 `InventoryScreenMixin.java`
- Fabric/Forge mixin 配置文件

## 自动验证

- `cargo test --lib`：85 passed，0 failed。
- `cargo fmt -- --check`：通过。
- `git diff --check`：通过。
- 第一人称姿态状态类已使用独立 `javac` 做语法检查。
- 新增回归测试覆盖：
  - 双眼中点优先于 `両目` 控制骨。
  - 单眼和无眼回退。
  - PMX 重复顶点接缝拓扑扩张。
  - 动态视锥内外的边界三角形。
  - 骨骼逻辑位置已回正、最终蒙皮仍处于过渡中时，相机跟随最终蒙皮位置。

完整 Gradle 编译未在命令行重复执行。此前 Loom mappings 临时文件被占用，用户计划在 IDEA 中完成 Java/Minecraft 运行时验证。

## IDEA 手动验收记录

| 场景 | 状态 | 备注 |
| --- | --- | --- |
| 加载世界并完成模型纹理上传 | 待复测 | 已隔离 PBO 与全部 pixel unpack 状态 |
| 背包模型跟随光标 | 已由用户验证 | 第一阶段完成 |
| 静止第一人称低头 | 待最新构建复测 | 应位于真实双眼中点 |
| 持续 Sprint 前倾 | 待最新构建复测 | Camera 预更新应消除上一帧错位 |
| Sprint 松键回正 | 待最新构建复测 | 最终蒙皮锚点应消除约 0.1 秒速度差 |
| Sprint -> Walk | 待验证 | 检查过渡全程是否出现断口 |
| Sprint -> Idle | 待验证 | 检查急停和回正 |
| 特殊 Mod 奔跑动作 | 待验证 | 重点观察大幅前倾和后颈 |
| CPU 蒙皮 | 待验证 | 与 GPU 结果比较 |
| GPU 蒙皮 | 待验证 | 与 CPU 结果比较 |
| Iris 主视图和阴影 | 待验证 | 阴影继续使用完整模型 |
| 第三人称与远端玩家 | 待验证 | 头部必须完整 |
| VR 进入/退出桌面 | 待验证 | 已处理双向一帧状态泄漏 |

## PR 范围注意事项

当前工作区存在一些尚未分类的开发环境或其他对话遗留文件。创建 PR 前应按上面的“主要实现文件”选择性暂存，并单独检查以下内容：

- `05f42c6` 已提交的 `.gitignore`、`common/build.gradle`、`TextureGpuLoader.java`、子模块清理属于新分支继承基线，不应从综合 PR 中拆除。
- 当前尚未提交的根目录、Fabric、Forge Gradle 配置修改需要按实际用途确认。
- `gradle.properties`、Gradle wrapper 和 `settings.gradle`。
- `.upstream-reference/`。
- `ForkMessage.md`。
- `implementation_plan.md`、阶段计划文件。
- 本工作日志 `first_person_fix_worklog.md` 应进入 PR。

不要直接执行 `git add -A`。

## 建议 PR 信息

标题：

```text
Fix first-person MMD head clipping and camera pose synchronization
```

摘要：

```text
- isolate first-person head visibility per render scene
- add topology-aware dynamic first-person index filtering
- anchor the desktop camera to the rendered eye midpoint
- prepare the local first-person pose before Camera.setup
- reuse the prepared pose across shadow/world passes without double physics updates
- protect predecoded texture uploads from inherited PBO/pixel unpack state
- keep inventory, multiplayer, VR and CPU/GPU skinning paths isolated
```

## 提交前清单

- [ ] IDEA 编译 common、Fabric 和 Forge。
- [ ] 确认加载世界时不再在 `glTexImage2D` / `nvoglv64.dll` 崩溃。
- [ ] 完成上表中的运行时测试。
- [ ] 确认 Gradle 配置是否属于本 PR。
- [ ] 选择性暂存综合修复文件和本工作日志。
- [ ] 检查 `git diff --cached --check`。
- [ ] 检查暂存文件列表没有包含本地缓存、日志或参考仓库。
- [ ] 提交后确认 PR 对 `origin/1.20-vr` 的差异包含继承的 `05f42c6` 和本次综合修复。