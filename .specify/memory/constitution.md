# MC-MMD-rust 26.3 移植项目章程

## 核心原则

### I. 反编译产物是唯一权威
**任何对 Minecraft 26.3 行为的断言，必须以客户端 jar（`S:\client-26.3.jar`，未混淆）
的字节码或官方着色器源码为证据，禁止凭记忆或类比其它版本下结论。**

- 查证手段优先级：`javap -c/-v` 读字节码 → 读 `S:\vanilla-shaders\` 下的原版着色器 →
  读 Fabric API 模块字节码 → 最后一律标记为"未确认"。
- 版本差异是本项目最大的错误来源：26.3 删除了 GLFW 输入栈、uniform 全部改为 std140 UBO、
  引入 frame graph 与 render pearl 新管线，1.21.5/1.20.1 的经验大多已失效。
- 每次修复都要能回答："这段证据在 jar 的哪个方法、哪个字节码偏移？"

### II. 渲染约定必须与 vanilla 逐位一致
**模组自建渲染管线时，所有影响画面正确性的约定（深度方向、深度清除值、混合、
剔除、顶点格式、UBO 布局）必须与 vanilla 26.3 完全一致，且差异要写成注释。**

- 典型反例（已发生）：26.3 使用 **reversed-Z**（近平面 1.0、远平面 0.0），
  深度比较必须是 `GREATER_THAN_OR_EQUAL`；模组曾用 `LESS_THAN_OR_EQUAL`，
  方向相反导致 MMD 模型无视地形遮挡、盖在方块前面。
- 判定某个约定时，直接看 `net.minecraft.client.renderer.RenderPipelines` 用的
  `DepthStencilState` / `CompareOp`，不要靠"看起来对不对"。

### III. 最小改动、随时可回滚
**每次提交只解决一个问题；能用配置/参数解决的，不改结构；改动前先确认基线可复现。**

- 1.20.1 分支 `handover/.../MC-MMD-rust-1.1.0` 的 tracked 文件**保持零改动**，
  它是行为基准。
- 26.3 移植分支为 `mc263` 的 `port/26.3`，与上游 `origin/1.21.5` 的差异即为移植内容。
- 临时调试代码（`[MMD调试]`、临时类）在交付前必须清除，jar 内不得残留。

### IV. 每个阶段都要实机验证
**没有实机截图或日志佐证的"已修复"，一律视为未修复。**

- 验证要在真实游戏实例 `D:\PCl\.minecraft\versions\26.3-Fabric 0.20.1` 上跑，
  不能只靠编译通过或静态分析。
- 判定渲染类问题必须设计**有对照的实验**（例如用 vanilla 实体当遮挡物），
  避免因相机碰撞、几何误判而得出错误结论。
- 验证失败时，先补日志取事实，再改代码；禁止连续盲改。

### V. 上下文工程优先于蛮力搜索
**面对陌生的大代码库，先建索引再提问，不要靠反复通读源码定位问题。**

- 仓库已用 `cgh` 建立符号级代码图谱（Java + Rust），用
  `cgh lookup` / `callers` / `callees` / `impact` 代替全文阅读。
- `cgh` 已注册为 MCode 的 MCP 服务器（名字 `codegraph`），可被模型直接查询。
- 大工程按 spec-kit 流程走：constitution → specify → plan → tasks → implement，
  产物落在 `specs/<feature>/`。

## 工具链与运行环境约束

- **不新建 JDK**：使用 Minecraft 自带运行时
  `C:\Users\naxid\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon`（JDK 25）。
- **不在用户机器上乱装东西**：新增工具前先说明体积与用途并取得同意；
  已在用户级 PATH 注册的工具：`uv`、`specify`（spec-kit CLI）、`cgh`、`lynx`、`codesurf`。
- **网络**：走代理 `http://127.0.0.1:10808`；Gradle 代理参数必须用带引号的 `-D` 传，
  且 `maven.fabricmc.net` 需 `-Dhttp.nonProxyHosts=maven.fabricmc.net`。
- **PowerShell 5.1 按 GBK 读无 BOM 的 `.ps1`**：脚本必须纯 ASCII，
  含中文路径时改用 `$PSScriptRoot` 推导。
- **源码统一 CRLF**；`write`/Python 写文件后用 `tools/normalize_crlf.py` 还原。

## 开发流程与质量门禁

1. **定位**：先用 `cgh` 缩小范围，再用 `javap` 取字节码事实；必要时派只读子代理
   并行挖 API，结论要带证据。
2. **修复**：最小改动 + 写清"为什么"注释（尤其是与 vanilla 约定相关的地方）。
3. **构建**：`.\gradlew.bat :fabric:build --no-daemon --console=plain -x test`（带代理参数）。
4. **部署**：`robocopy` 覆盖实例 `mods\` 下的同名 jar，并用 SHA-256 核对。
5. **实机验证**：启动 → 进世界 → 构造对照实验 → 截图确认。
6. **复查**：每个阶段结束后派子代理做只读复查，抓"能编译但结果错"的问题。
7. **清理**：删除临时调试日志与临时类，重建并核对 jar 内无残留。

## 治理

- 本章程优先级高于任何单次任务的临时约定；确需变更时，先更新本文件再执行。
- 修订原则条款 = MINOR 递增，措辞澄清 = PATCH，删除或重定义原则 = MAJOR 递增。
- 每次修订都要在本文件顶部附一段 Sync Impact Report（版本变化、条款增删、遗留 TODO），
  供人工复核；该报告在提交前移除。
- 每次提交都要核对是否符合"II. 渲染约定"与"III. 最小改动"两条；违反需在提交说明中写明理由。

**版本**: 1.0.0 | **批准日期**: 2026-10-24 | **最后修订**: 2026-10-24
