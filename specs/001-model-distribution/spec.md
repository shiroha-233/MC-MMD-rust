# 功能规格：模型分发（多人联机）

**特性编号**：001
**创建日期**：2026-10-24
**状态**：进行中（阶段 1 已完成）
**来源**：MC-MMD-rust 1.20.1 分支 `feature/model-distribution`（tag `v1.1.0`，HEAD `93d329a`）
**参考实现**：`D:\尝试文件夹\MC MMD\_ref_1.20.1\handover\work\src\MC-MMD-rust-1.1.0`
**移植目标**：`D:\尝试文件夹\MC MMD\mc263`（分支 `port/26.3`）

---

## 1. 为什么需要这个功能

26.3 移植的基底是上游 `origin/1.21.5`，而 1.20.1 的功能完备版本在用户自研分支
`feature/model-distribution` 上，**领先上游 6 个提交**（模型分发 / 断线崩溃 / 死亡闪烁 / 死亡姿势）。
这 6 个提交在 1.21.5 基底上不存在，所以 26.3 移植天然缺这一块——不是"漏搬"，是**基底选错了功能基线**。

模型分发解决的是：绝大多数 MMD 模型禁止二次配布，但联机时房主希望把自己已获授权的模型
分给同房间的玩家使用。**模型文件永远不经过任何第三方**，服务器只做传输。

---

## 2. 用户故事

### US1 房主授权分发
作为房主，我希望能**逐个模型**显式授权分发，并自行承担二次配布责任。
没授权的模型绝不外传。

### US2 玩家在客户端看到可下载列表
作为联机玩家，我希望能收到服务器推来的"可分发模型清单"，并看到**体积、来源、带宽影响**，
再决定要不要下载。

### US3 玩家必须显式担责
作为玩家，我需要在下载前**勾选确认自己拥有该模型的二次配布权**，否则不能开始下载。

### US4 下载可断点续传
作为玩家，下载中断后重连应当能从断点继续，而不是从头再来。

### US5 完整性保证
作为玩家，下载的文件必须通过 MD5 校验，且**只有全部校验通过才替换正式目录**，
不能出现"半个模型"被模组加载。

### US6 房主自己不受影响
作为房主，我本地已有的模型不应该出现在我的下载列表里。

### US7 限速与总量控制
作为房主，我希望能限制单客户端下载速度和服务器总出口带宽，避免带宽被打满。

### US8 服务端可部署
作为服主，我用的是**专用服务器**（没有客户端），这个功能也必须能跑。

---

## 3. 功能需求

### FR-1 配置与授权
- FR-1.1 房主侧配置文件位于 `<游戏目录>/config/mmdskin/distribution.json`。
- FR-1.2 配置项：`acknowledged`（责任确认，默认 false）、`enabled`（总开关，默认 false）、
  `rateLimitKbPerSec`（单客户端限速，默认 256）、`globalRateLimitKbPerSec`（服务器总配额，默认 1024）、
  `models`（按模型名配置 `{ distributable, ossUrl }`）。
- FR-1.3 **只有 `acknowledged && enabled` 且该模型 `distributable=true` 时才允许分发**。
- FR-1.4 首次运行自动写一份带注释的模板文件，房主不用猜字段含义。
- FR-1.5 限速值为 0 表示不限；负数按 0 处理。

### FR-2 清单协议
- FR-2.1 服务器向客户端推送**可分发模型摘要清单**（模型名 + 文件数 + 总大小 + 限速）。
- FR-2.2 客户端请求某个模型后，服务器下发**分页详细清单**（每页若干文件条目：
  相对路径 + 大小 + MD5），页数在包里声明，客户端攒齐所有页才开始下载。
- FR-2.3 详细清单必须分页下发——完整清单可能有上万个文件，单包会撞代理插件消息上限
  （BungeeCord 默认 32767 字节）。
- FR-2.4 文件 MD5 为小写十六进制。

### FR-3 传输
- FR-3.1 传输单位是**文件分块**，块大小按代理消息上限设计（16 KB 量级）。
- FR-3.2 客户端请求格式为 `模型名|首个未完成的文件相对路径`，服务端据此从断点继续。
- FR-3.3 服务端按单客户端限速与服务器总配额节流。
- FR-3.4 传输结束有显式的"完成"通知。
- FR-3.5 可选的 **OSS 直链旁路**：房主填直链后，服务器只下发清单与校验值，
  字节由客户端直接从对象存储取，服务器出口为 0。
- FR-3.6 OSS 直链用"本地副本 MD5 vs OSS ETag"比对；**分片上传的 ETag 不是 MD5，
  判为不一致时自动回退到服务器转发**。

### FR-4 客户端下载与落盘
- FR-4.1 下载写入临时目录，全量校验通过后**原子替换**到正式模型目录。
- FR-4.2 下载中断保留临时目录与已完成的文件，下次继续（断点续传）。
- FR-4.3 全部文件 MD5 校验通过后才替换正式目录。
- FR-4.4 替换完成后刷新模型列表缓存，新模型立即可选。
- FR-4.5 客户端**按本地是否已存在该模型目录过滤**清单，房主自己不会看到自己的模型。

### FR-5 UI
- FR-5.1 模型列表顶部出现「⬇ 服务器模型分发 (N)」入口（N = 过滤后可下载数量）。
- FR-5.2 一级提示页：条件说明 + 责任分离说明 + 带宽告知 + 风险提醒。
- FR-5.3 二级确认页：列出模型、体积、来源，**必须勾选"我确认拥有分发权"才能开始下载**。
- FR-5.4 下载中要有进度与失败提示。

### FR-6 服务端可部署（专用服务器）
- FR-6.1 **服务端可达的类里不许出现 `net.minecraft.client.*` 的 import**——
  专用服务器 jar 里根本没有 `net.minecraft.client.Minecraft` 这个类，引用即 NoClassDefFoundError。
- FR-6.2 游戏根目录由平台层注入（Fabric 的 `onInitialize()` → `FabricLoader.getGameDir()`），
  不能依赖 `Minecraft.getInstance().gameDirectory`（服主侧为 null）。
- FR-6.3 服务端每 2 秒轮询配置文件时间戳，改完立即重新载入并把新清单推给所有在线玩家，
  不用重启、玩家不用重连；撤销某个模型的分发也会立刻从玩家列表里消失。

### FR-7 安全默认
- FR-7.1 全关时（默认）行为与本模组发布版**完全一致**，零影响。
- FR-7.2 本模组**不附带任何 MMD 模型**，只提供文件传输能力。

---

## 4. 非功能需求

- NFR-1 分发功能关闭时不产生任何网络流量与文件 IO。
- NFR-2 单个清单页与单个数据块都必须小于代理插件消息上限。
- NFR-3 服务端内存占用与模型文件总数无关（流式分页下发，不一次性读全量）。
- NFR-4 断线/换世界时清理分发会话，不留悬挂任务。

---

## 5. 成功标准

- SC-1 房主改配置后 **2 秒内**所有在线玩家的模型列表顶部出现/消失对应条目，无需重启服务器或重连。
- SC-2 玩家下载 1 GB 级模型目录，中断后重连能从断点继续，**不重复下载已完成文件**。
- SC-3 下载完成后模型**立即**出现在本地模型列表中可选，且文件 MD5 全部校验通过。
- SC-4 专用服务器（只有服务端、无客户端）启动时该功能不崩溃，且能正常分发。
- SC-5 全关状态下，启动与联机的网络流量、磁盘写入与未启用时**逐字节一致**。
- SC-6 单客户端限速设为 256 KB/s 时，实测平均速率不超过该值的 1.2 倍。

---

## 6. 阶段划分（每阶段独立提交 + 验证）

| 阶段 | 内容 | 涉及文件 | 状态 |
|---|---|---|---|
| ⑤-1 | 协议与配置层 | `GameDirectoryProvider`、`PathConstants.getGameDirectory()`、`MmdSkinFabric`、`distrib/ModelDistributionConfig`、`distrib/ModelDistributionManifest`、`NetworkOpCode`（+5 操作码） | ✅ 已提交 `bcba254`，实机验证 0 错误 |
| ⑤-2 | 服务端 | `distrib/ModelDistributionService`（1019 行，逐字节照搬）、`MmdSkinRegisterCommon` 的 4 个 S→C 载荷 + JOIN/DISCONNECT/tick 接线 | ✅ 已提交 `e63593b`，实机验证通过 |
| ⑤-3 | 客户端 | `distrib/ModelDistributionClient`（709 行）、`MmdSkinNetworkPack` 分发二进制分派、`FabricClientNetworkBindings` 请求注入、`FabricClientRuntimeHooks` tick/断线钩子 | ✅ 已提交 `e63593b`，实机验证通过 |
| ⑤-4 | UI | `ui/selector/DistributionNoticeScreen`、`DistributionConfirmScreen`、`ModelSelectorScreen` 接入点、29 个 lang 键 × 3 语言 | 🔄 进行中 |
| ⑤-5 | 自动下载与配置项 | `ConfigManager` 新增 `isAutoDownloadNearbyModels()` / `getAutoDownloadRadius()`（默认 48）、配置界面开关 | ⬜ |

### 已完成的实机验证（2026-10-24）

| 项 | 结果 |
|---|---|
| 服务端在 26.3 加载分发配置 | ✅ `[Server thread] 模型分发配置: ... (已确认=false 启用=false ...)` |
| 专用服务器安全（无 `net.minecraft.client.*` 引用） | ✅ 0 `NoClassDefFoundError` |
| 配置热更新（2 秒轮询） | ✅ `[Server] 检测到分发配置变化，已重新载入` |
| 清单推送到客户端 | ✅ `[Render thread] 收到模型分发清单: 1 个模型` |
| 分发单元测试 | ✅ 10/10 PASSED（路径安全 / 目录逃逸 / 分块帧编解码 / 截断帧拒绝 / 分页重组 / gzip 往返 / 默认三重关闭 / 安装失败不丢原模型） |
| 全量测试（顺带接进构建） | ✅ 47 个测试类 0 失败（此前这批测试从未运行过） |

---

## 7. 26.3 适配要点（已核实的坑）

| # | 坑 | 结论 |
|---|---|---|
| A1 | 26.3 是**单个 `CustomPacketPayload`**（`MmdSkinPayload`，6 字段 record），不是 1.20.1 的双通道 `ResourceLocation` | 分发载荷直接复用现有 `binaryData` / `stringArg` 通道，**不需要新增载荷类型** |
| A2 | `PathConstants.getGameDirectory()` 原本用 `Minecraft.getInstance().gameDirectory` | 服主侧 NPE，必须改走 `GameDirectoryProvider` |
| A3 | 服务端类不能引用 `net.minecraft.client.*` | 1.20.1 用 `Object handle` 规避，这个设计要保留 |
| A4 | 26.3 GUI 架构重写：`render(GuiGraphics,…)` → `extractRenderState(GuiGraphicsExtractor,…)`；背景是独立入口 `extractBackground(...)` | 两个 UI 界面要按 26.3 抽取式重写 |
| A5 | 26.3 鼠标左键编号是 **1**（SDL），不是 GLFW 的 0 | UI 里的点击判定要用 `InputConstants.MOUSE_BUTTON_LEFT` |
| A6 | 26.3 `mouseScrolled` 是 **4 个 double**（x, y, scrollX, scrollY） | 滚轮相关逻辑要改签名 |
| A7 | 分发二进制载荷的分派**必须放在 `isStringPayload` 判断之前** | 否则二进制载荷会被当成字符串载荷解析 |
| A8 | `ModelCatalogEntry` 在 26.3 叫 `ModelInfo`；`PlayerModelSyncService` 叫 `PlayerModelSyncManager` | 改名 + import |

---

## 8. 待核实点（不在本阶段猜测）

- 26.3 `FriendlyByteBuf.writeByteArray` 是否有额外大小上限（16 KB 块是否够安全）。
- 26.3 `common` 模块能否引用服务端专属 API（`ServerPlayer`）——1.20.1 用 `Object handle` 规避，需确认 26.3 也这么做。
- NeoForge 侧 26.3 的 `CustomPacketPayload` 注册方式是否与 Fabric 一致（NeoForge 整体未移植，本阶段只做 Fabric）。
- 26.3 `Screen.extractBackground` 的默认实现是否画暗化背景（若画，UI 需一并覆盖）。
