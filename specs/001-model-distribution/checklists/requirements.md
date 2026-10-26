# 规格质量检查清单：模型分发（多人联机）

**目的**：在进入 `/speckit-plan` 之前验证规格的完整性与质量
**创建日期**：2026-10-24
**规格**：[spec.md](../spec.md)

## 内容质量

- [x] 无实现细节（语言、框架、API 只出现在"26.3 适配要点"这一节，且是必须记录的坑，不是方案）
- [x] 聚焦用户价值与业务需求（房主授权 / 玩家担责 / 完整性 / 限速）
- [x] 面向非技术相关方可读（用户故事部分）
- [x] 所有强制章节已完成

## 需求完整性

- [x] 无 [NEEDS CLARIFICATION] 标记残留
- [x] 需求可测且无歧义（FR-2.1~2.4 有明确的分页/大小/格式约定）
- [x] 成功标准可度量（SC-1 2 秒内、SC-2 不重复下载、SC-5 逐字节一致、SC-6 1.2 倍容差）
- [x] 成功标准与实现无关（不点名类与方法）
- [x] 所有验收场景已定义（US1~US8 各对应 FR 编号）
- [x] 边界情况已识别（分页超限 / ETag 非 MD5 / 断线 / 限速为 0 / 负数 / 撤销授权 / 房主自己过滤）
- [x] 范围边界清晰（"全关时零影响"、"模组不附带任何模型"）
- [x] 依赖与假设已识别（专用服务器无客户端类、代理插件消息上限、OSS ETag 语义）

## 特性就绪度

- [x] 所有功能需求都有明确验收标准
- [x] 用户故事覆盖主要流程
- [x] 特性满足成功标准中定义的可度量结果
- [x] 无实现细节泄漏进规格

## 26.3 适配风险已登记

- [x] A1 单 CustomPacketPayload 架构（复用现有 binary/string 通道）
- [x] A2 服主侧 `Minecraft.getInstance()` 为 null → 必须走 `GameDirectoryProvider`
- [x] A3 服务端类禁止 import `net.minecraft.client.*`
- [x] A4 GUI 抽取式重写（`extractRenderState` / `extractBackground`）
- [x] A5 SDL 鼠标编号 1=左
- [x] A6 `mouseScrolled` 四参数
- [x] A7 分发二进制分派必须早于字符串分派
- [x] A8 类名改名（ModelCatalogEntry→ModelInfo 等）

## 阶段可交付性

- [x] ⑤-1 协议与配置层：已完成并提交（`bcba254`），实机验证 0 错误
- [ ] ⑤-2 服务端：`ModelDistributionService` + 4 个 S→C 载荷
- [ ] ⑤-3 客户端：`ModelDistributionClient` + 收包分派 + tick/断线钩子
- [ ] ⑤-4 UI：两个界面 + 模型列表入口 + 29 个 lang 键 × 3 语言
- [ ] ⑤-5 自动下载配置项：`ConfigManager` 两个新方法 + 配置界面开关

## 备注

- 本特性只做 **Fabric**；NeoForge 端 26.3 整体未移植（见 `neoforge/README-26.3-NOT-PORTED.md`），
  协议与数据层是跨加载器共用的，移植 NeoForge 时只需再写一层载荷注册。
- 阶段 1 已经把最容易踩的服务端安全坑（游戏目录注入）先解决掉了，
  后续阶段的服务端代码可以直接引用 `PathConstants`。
