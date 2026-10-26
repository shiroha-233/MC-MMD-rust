# NeoForge 平台模块 —— MC 26.3（已移植）

## 现状

- `settings.gradle` 里 `include 'fabric'` + `include 'neoforge'`，**两个平台模块都参与构建**。
- 平台无关层（`common/`）不再是独立 Gradle 模块：它的源码/资源目录直接挂到
  `:fabric` 和 `:neoforge` 两个模块的 `sourceSets.main` 上（见各自的 `build.gradle`）。
- `:neoforge:build` 在本机可复现通过（compileJava + processResources + jar 全绿）。

## 26.3 上必须换掉 Architectury

MC 26.3 上 **Architectury 多模块路线走不通**：

- `dev.architectury:architectury-neoforge` 最高只有 **21.0.5**（对应 MC 1.21.x），没有 26.x 版本；
- Architectury 的 SRG/transform 链路需要 `de.oceanlabs.mcp:mcp_config:26.3`，这个版本在 Mojang 侧不存在。

所以 NeoForge 端改用 **NeoForge 官方 Gradle 插件 `net.neoforged.moddev`（2.0.148）**，
插件版本与 NeoForge 版本在根 `build.gradle` / `neoforge/build.gradle` 里声明：

```groovy
// 根 build.gradle
plugins {
    id 'net.fabricmc.fabric-loom' version '1.19.3' apply false   // 只给 :fabric
    id 'net.neoforged.moddev'  version '2.0.148' apply false      // 只给 :neoforge
}

// neoforge/build.gradle
neoForge {
    enable {
        version = "26.3.0.97"
        disableRecompilation = true   // 跳过 NeoForm 反编译/重编译流水线
    }
    ...
}
```

`disableRecompilation = true` 只是跳过 NeoForm 的"反编译 → 重编译"步骤，
产物仍然是打过 NeoForge 二进制补丁的等价 jar，只是没有随附源码；
本机没有 26.3 的 NeoForge 实例，只需要编译期可用的 jar，这样能省掉数十分钟与数 GB 内存。

## 怎么构建

```powershell
$env:JAVA_HOME='C:\Users\naxid\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon'
$env:http_proxy='http://127.0.0.1:10808'; $env:https_proxy='http://127.0.0.1:10808'
Set-Location 'R:\'
.\gradlew.bat :neoforge:build --no-daemon --console=plain `
  '-Dhttp.proxyHost=127.0.0.1' '-Dhttp.proxyPort=10808' `
  '-Dhttps.proxyHost=127.0.0.1' '-Dhttps.proxyPort=10808'
```

产物：`neoforge/build/libs/mmdskin-neoforge-<version>.jar`
（内含 `META-INF/jarjar/` 的 cloth-config + jlayer、`natives/windows-x64/mmd_engine.dll`、
`assets/mmdskin/lang/*.json`）。

## 26.3 API 迁移对照表（1.21.5 → 26.3）

| 领域 | 1.21.5 | 26.3（本模块） |
|---|---|---|
| 实体渲染入口 | `render(...)` / `MultiBufferSource` | `submit(...)` / `SubmitNodeCollector` |
| 相机 | `Camera.setup(BlockGetter, Entity, ...)` | `Camera.update(DeltaTracker)` + `alignWithEntity(float)`；FOV 接管落在 `Camera.calculateFov(float)` |
| 准星拾取 | `GameRenderer.pick(...)` | `Minecraft.pick(float)` |
| GUI 绘制 | `GuiGraphics` | `GuiGraphicsExtractor`；`centeredText` 的 x 是**水平中心** |
| 物品栏实体 | `renderEntityInInventory` | `extractEntityInInventoryFollowsMouse` |
| 鼠标输入 | `onPress(long, int, int, int)`（GLFW 0=左 1=右 2=中） | `onButton(long, MouseButtonInfo, int)`（SDL 1=左 2=中 3=右） |
| 死亡屏 | `render(GuiGraphics, ...)` | `extractRenderState(...)` + `extractBackground(...)` 两个入口 |
| 渲染状态扩展 | NeoForge `RegisterRenderStateModifiersEvent` + `ContextKey` | **该事件在 26.3 已被删除**；改为 mixin 直接在 `LivingEntityRenderState` 上挂 `MmdRenderStateExtension` |
| 按键分类 | `KeyMapping.Category`（字符串） | `KeyMapping.Category`（对象）。NeoForge 把 `Category.register(Identifier)` 标成 deprecated，要改用 `RegisterKeyMappingsEvent.registerCategory(Category)` |
| 实体附件 | `AttachmentType.builder(...).serialize(Codec)` | `serialize` 只收 `IAttachmentSerializer` 或 `MapCodec` |
| 服务端对象 | `ServerPlayer.getServer()` | `player.level().getServer()` |
| `GameProfile` | `getName()` | `name()`（record 访问器） |
| 客户端发包 | `PacketDistributor.sendToServer(...)` | `ClientPacketDistributor.sendToServer(...)` |
| 事件总线 | `@EventBusSubscriber(bus = MOD/GAME)` | 26.3 的 `@EventBusSubscriber` **没有 `bus` 属性**了，事件自己实现 `IModBusEvent` 就走 mod 总线 |

## 世界渲染三阶段的 NeoForge 对应

Fabric 端的三个世界渲染钩子在 NeoForge 26.3 里有一一对应的事件：

| Fabric | NeoForge 26.3 | 用途 |
|---|---|---|
| `LevelExtractionEvents.END_EXTRACTION` | `GameRenderer.renderLevel` HEAD（`GameRendererMixin`） | 开帧、清帧队列 |
| `LevelRenderEvents.COLLECT_SUBMITS` | `SubmitCustomGeometryEvent` | 第一人称本地模型提交（此时 collector 还没被消费） |
| `LevelRenderEvents.END_MAIN` | `RenderLevelStageEvent.AfterLevel` | 冲刷世界帧（主 Pass 已关闭、深度缓冲有效） |

## 未在本机验证的部分

本机没有 MC 26.3 的 NeoForge 实例，**没有实机运行过**。已验证的是：

- `:neoforge:build` exit 0（编译期）；
- 每个 mixin 的注入点 / 影子成员都能在 `minecraft-patched-26.3.0.97.jar` 里按
  "名字 + 描述符"找到（用 ASM 解析字节码逐条核对，见 `build/mixin-check/`）。

运行时行为（渲染顺序、事件触发时机、YSM 兼容反射签名等）仍需实机确认。

## 参考

`fabric/` 目录里是同样适配好 26.3 的等价实现，本模块的 mixin 与它一一对应
（`CameraMixin` / `MouseHandlerMixin` / `MinecraftMixin` / `LivingEntityRenderStateMixin` 等）。
