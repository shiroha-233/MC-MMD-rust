# MC 26.3 移植 · PART5：崩溃 / 第一人称 / 性能 三轮根因分析

> 记录 2026-10-24 这一轮实机排查的完整证据链与根因，避免以后重复踩坑。
> 三个问题都是**实机复现 + 日志/数据实证**后定位的，不是猜测。

---

## 一、「性能调试 HUD 一开就秒崩」

### 现象

打开配置里的「性能调试 HUD」（`debugHudEnabled`）后，进入世界约 1 秒进程直接消失：
没有 Java 崩溃报告（`crash-reports` 目录里什么都没有），但 stdout 里有：

```
FATAL ERROR in native method: [LWJGL] Thread[#3,Render thread,10,main]: No context is current
or a function that is not available in the current context was called. The JVM will abort execution.
    at org.lwjgl.opengl.GL11C.nglGetString(Native Method)
    at org.lwjgl.opengl.GL11C.glGetString(GL11C.java:594)
    at org.lwjgl.opengl.GL11.glGetString(GL11.java:1868)
    at com.shiroha.mmdskin.bridge.graphics.OpenGlMemoryProbe.sample(OpenGlMemoryProbe.java:17)
    at com.shiroha.mmdskin.debug.client.PerformanceHud.rebuildLines(PerformanceHud.java:69)
    at com.shiroha.mmdskin.debug.client.PerformanceHud.render(PerformanceHud.java:44)
    at com.shiroha.mmdskin.fabric.register.FabricClientRuntimeHooks.lambda$register$4(...:68)
```

### 根因

MC 26.3 默认走 **Vulkan** 后端（日志：`Using graphics backend Vulkan`），进程里**根本没有 OpenGL 上下文**。
而 `OpenGlMemoryProbe` 仍在用 `GL11.glGetString` / `GL46C.glGetInteger` 查显存。

LWJGL 3.4 遇到「无上下文却调 GL 函数」会**直接 abort JVM**——这不是 Java 异常，
`catch (RuntimeException)` 拦不住，只能**在调用前判定后端、压根不去碰 GL**。

1.20.1 只有 OpenGL 后端，所以这段探测当年是安全的；**这是 26.3 移植引入的回归**。

### 修复（提交 `e24323a`）

- `OpenGlMemoryProbe`：两道**纯 Java**判定，都不会触发原生调用
  1. `RenderSystem.getDevice().getDeviceInfo().backendName()` 命中
     `vulkan`/`direct3d`/`d3d`/`metal` → 直接放弃；
  2. `org.lwjgl.opengl.GL.getCapabilities()`（无上下文时抛 `IllegalStateException`，可捕获）。
  缓存结果，避免每帧重复判定。`clearErrors()` 同样加护栏。
- `PerformanceHud`：整个 `render` 包 `try/catch(Throwable)`，调试面板绝不允许把整帧带崩。

实机验证：HUD 正常显示七行，`GPU memory N/A`（Vulkan 下正确），进世界后不再崩溃。

---

## 二、「第一人称视角卡在模型头里」

### 现象

开启「在第一人称视角下显示 MMD 模型」后，画面变成模型内部：能看到大块头部、尖耳朵内部，
像相机卡在头里；低头更是直接看到「自己身体的内部」。

### 根因 1：材质级隐藏被无视（已修，提交 `8f91e65`）

`apply_first_person_material_mask()` 直接改 `material_visible` 这个 `Vec`，
**绕过了 `replace_material_visibility()`**——而后者是**唯一**会自增 `material_revision` 的入口。

Java 侧靠 `material_revision` 判断要不要重新拷贝 draw command：

```java
// MmdGpuDrawDevice.GpuMesh.prepare()
if (commandRevision != description.materialRevision() || commands.size() != description.drawCommandCount()) {
    ... copyDrawCommands ...   // 重新拷贝，刷新 DRAW_VISIBLE 标志
}
```

revision 不变 → 一直用「全部可见」的旧命令 → **头部材质虽然在 Rust 侧被标成隐藏，渲染时照样画出来**。

修复：掩码函数改为构造新 `Vec` 后走 `replace_material_visibility()`（VR 手部掩码同样处理）。

### 根因 2：眼睛骨骼选错（已修，提交 `8f91e65`）

眼睛骨骼原先只按名字优先级取「両目 > 目 > 左目+右目」。但有些模型（如纳西妲）的
「両目」是放在**头顶上方**的控制骨骼，并不在眼睛位置——直接采用会把相机顶到头顶外面，
第一人称变成「俯视自己的头」。

实测数据（纳西妲.pmx）：`両目 y=15.39`，而真正的眼睛骨骼 `左目/右目 y=12.08`，网格最高才 15.05。

修复：收集所有候选，挑**离头骨最近**的那个，距离相同时仍按原优先级。
眼睛一定紧挨头骨，这个判据与模型缩放无关。

### 根因 3：材质级隐藏救不了「身体+头部同材质」的模型（待修）

材质级隐藏只能整块隐藏一个材质。纳西妲的 `肌2` 材质**同时含身体和头部**：

| 材质 | Y 范围 | 高于眼位(12.08)的顶点 | 高于脖子线(10.92)的顶点 |
|---|---|---|---|
| `肌2` (#17) | 1.22 .. 12.51 | **54**（脖子皮肤 + 尖耳朵） | 327 |
| `髮` (#10) | 8.60 .. 14.29 | 1117（已整块隐藏） | 1834 |
| `体` (#13) | 0.03 .. 11.28 | 0 | 0 |

结果：头（颜/眉/睫/目/髮/前髮/头饰…共 14 个材质）确实藏掉了，但**脖子皮肤还围着相机**，
低头就是看自己身体内部。

⇒ 必须做**三角形级**的头部几何裁剪（见下）。

---

## 三、附带的模型结构实测数据

`纳西妲.pmx`（PMX **2.1** 布局，解析器见 `tools/pmx_inspect.py`）：

- 顶点数 29511，索引数 109764，**材质 23 个**（= 23 个 submesh），骨骼 444 根，纹理 5 张。
- 骨骼：`首`(脖子) y=10.92、`頭`(头) y=11.48、`両目` y=15.39（控制骨骼，偏上）、
  `左目`/`右目` y=12.08（真正眼位）。
- 材质级头部判定：脖子线以上 >50% 顶点 → `颜/颜2/齿/口舌/二重/眉/睫/目/白目/星目/髮/前髮/头饰/髮+` 共 14 个。

**PMX 2.1 解析要点**（踩过坑，官方 mmd-rs 的 `reader/header.rs` 是权威）：
- globals 是 **8 个纯字节**，没有 type/value 对：
  `[text_encoding, additional_vec4_count, vertex_index_size, texture_index_size,
    material_index_size, bone_index_size, morph_index_size, rigidbody_index_size]`
- 头部有 **4 个文本字段**（本地名 / 通用名 / 本地备注 / 通用备注），只读 2 个会导致后面全错位。
- 材质也有 **2 个名称字段**（本地名 / 通用名）。
- 顶点：`pos(12) normal(12) uv(8) [additional vec4] 形变类型(1) 形变数据 edge_scale(4)`。

---

## 四、性能：实测出来的结论（提交前已由子代理落地）

用户猜测「看不见的模型也会被加载」——**部分成立**：

| 场景 | 是否完整加载 | 依据 |
|---|---|---|
| 背后 / 视锥外 | 否 | vanilla `LevelExtractor.extractVisibleEntities` 先做视锥剔除，模组根本拿不到这些实体 |
| 视锥内但被地形遮挡 | **是** | 模组侧无遮挡剔除、**加载前无距离判断** |
| 视锥内但 50~150 格外 | **是**，且每帧完整推进 | 同上 |
| 隐身 | 否 | `MmdSnapshotFactory` 判 `Visibility.HIDDEN` → 路由直接 FALLTHROUGH |
| 已冻结的尸体 | 模型已加载；冻结后仍每帧跑动画/骨骼 | `RagdollManager.contribute` 照常每帧 `updateOnce` |

「完整加载」的昂贵步骤：PMX 全量解析 → 骨骼层级 + 初始蒙皮 → **每模型一个独立 Bullet world**
→ 4 个 GPU buffer（vertex/index/outline/bonePalette）→ 该实例专属的一份 VMD/FBX 解析。

**根因**：`ModelKey` 含实体 UUID → **每个实体一份完整 native 模型**。10 个玩家用同一个 PMX
= 10 份全部。修它要动 `rust_engine/**`（共享不可变资源 + per-instance 运行时两层），本次未做。

**另外发现 6 个性能配置项是死配置**（只有配置层/UI/单测引用，没有任何运行时调用点）：
`maxVisibleModelsPerFrame`、`animationLodMediumDistance/FarDistance/MediumUpdateInterval/FarUpdateInterval`、
`maxPhysicsModelsPerFrame`、`physicsLodMaxDistance`。已在本次补上运行时消费。

---

## 五、26.3 渲染 API 备忘（本次新增）

- `RenderSystem.getDevice().getDeviceInfo().backendName()` → 图形后端名（`Vulkan` / …），
  `DeviceInfo` 里有 `name/vendorName/driverInfo/isZZeroToOne/backendName/limits/features/…`。
- `net.minecraft.client.renderer.DynamicGpuData` 是 vanilla 的动态变换 UBO：
  `reset()` 会调用内部 storage 的 `endFrame()`；`writeTransform(Matrix4f)` 用的是 `WHITE` 颜色、
  `NO_OFFSET` 偏移、`IDENTITY_TEXTURE_TRANSFORM`，所以它块里的 `ColorModulator` 恒为 `(1,1,1,1)`。
- `RenderSystem.bindDefaultUniforms(pass)` **只绑** `Projection`/`Fog`/`Globals`/`Lighting`，
  **不碰** `DynamicTransforms` —— 所以我们的管线可以不声明那个 bind group layout。
- vanilla `DynamicGpuData` 的 "Resizing Dynamic Transforms UBO, capacity limit of N reached during a single
  frame" 是**正常现象**（每次进世界都会出现 3 次：2→4→8→16），不是崩溃原因，别再误判。
