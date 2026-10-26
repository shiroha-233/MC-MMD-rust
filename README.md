# MC-MMD-rust

面向 Minecraft **26.3** 的 MMD（MikuMikuDance）模型、动画和物理模组。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

> 26.3 移植说明见 [PORTING-26.3-STATUS.md](PORTING-26.3-STATUS.md)。
> 上游 1.21.5 版本见 [shiroha-233/MC-MMD-rust](https://github.com/shiroha-233/MC-MMD-rust)。

## 功能特性

- **PMX / VRM 模型加载**: 在 Minecraft 中加载和渲染角色模型
- **VMD / FBX / VPD**: 支持骨骼动画、表情、Morph 和姿态数据
- **物理模拟**: 使用 Bullet3 实现头发、衣物和配饰的实时物理效果
- **GPU 蒙皮与 CPU 回退**: Rust 统一执行动画、Morph 和物理，RenderPipeline 顶点着色器执行 palette 蒙皮，不可用时回退 CPU
- **26.3 渲染管线**: 使用 `RenderPipeline`、`GpuBuffer`、`GpuTexture` 与批量 `RenderPass`
- **多层动画**: 支持多个动画同时混合播放

### 26.3 新增

- **死亡布娃娃**: 死亡时生成带物理模拟的尸体，到期自动冻结（姿势保持、跳过模拟）
- **死亡屏劫持 + 死亡观战相机**: 透明死亡屏，右下角「自由观战 / 复活」两个按钮；自由观战支持鼠标环视与滚轮缩放
- **模型分发**: 服务器转发或 OSS 直链两种分发方式，客户端按需下载周围玩家的模型
- **第一人称模型显示**: 第一人称下正确裁剪头部（三角形级掩码）并对齐眼骨骼锚点
- **本地指定玩家模型**: 在**自己屏幕上**把某个玩家渲染成指定的模型（纯客户端本地生效，不写服务器、不影响他人）
- **性能优化**: 加载前距离闸门、物理 LOD、动画 LOD、模型池上限、纹理缓存预算等

## 架构

本项目由两个主要部分组成：

1. **rust_engine**: 基于 Rust 的 MMD 物理和动画引擎
   - PMX/VMD 格式解析
   - 骨骼层次管理
   - Bullet3 物理模拟
   - 受检 JNI bridge，不向 Java 暴露裸 native 指针

2. **Minecraft Mod**（Java）: 基于 Java 的渲染和集成
   - Fabric 加载器适配（26.3 上为**单模块**，无 Architectury）
   - Blaze3D `RenderPipeline` 与 GPU 资源生命周期
   - 异步模型/纹理解码、帧队列和兼容回退

26.3 后端使用 48 骨骼 palette 的顶点着色器蒙皮，不依赖 Compute Shader、SSBO 或裸 OpenGL。Morph 与 Bullet 物理仍由 Rust CPU 完整求值，只有 Morph 结果变化时才更新顶点缓冲；骨骼姿态通过独立的 `f32 mat4` palette revision 每帧同步。

> **为什么是单模块**：Architectury 多模块需要 `de.oceanlabs.mcp:mcp_config:26.3`，
> 而该版本在 Mojang 侧**不存在**。因此 26.3 移植把 `common/` 的源码与资源目录
> 直接挂到 `fabric/` 与 `neoforge/` 两个模块的 sourceSet 上（见各自的 `build.gradle`）。

## 支持平台

| 平台 | 状态 | 说明 |
|---|---|---|
| **Fabric** | ✅ 已实机验证 | Fabric Loader 0.20.1 + Fabric API 0.165.0+26.3 |
| **NeoForge** | ⚠️ 编译通过，未实机验证 | NeoForge 26.3.0.97 + ModDevGradle 2.0.148 |

两个平台的实现语义等价，共用同一份 `common/` 源码。
NeoForge 端的构建与 API 迁移细节见 [neoforge/README-26.3.md](neoforge/README-26.3.md)。

## 使用教程

### 安装

1. 将模组 `.jar` 文件放入 `.minecraft/mods/` 目录
2. 启动游戏，模组会自动创建 `3d-skin` 资源目录
3. 将自己的模型和动画文件放入对应目录（详见下文）

### 目录结构

模组使用 `.minecraft/3d-skin/` 作为资源根目录，结构如下：

```
.minecraft/
└── 3d-skin/
    ├── EntityPlayer/          # 玩家模型目录
    │   ├── 模型A/             # 每个子文件夹是一个模型，文件夹名就是模型名
    │   │   ├── model.pmx      # 模型文件（支持 .pmx/.pmd）
    │   │   ├── *.png          # 贴图文件
    │   │   ├── anims/         # 模型专属动画子文件夹（推荐）
    │   │   │   ├── idle.vmd   # 覆盖该模型的待机动画
    │   │   │   └── walk.vmd   # 覆盖该模型的行走动画
    │   │   ├── animations.json # 动画槽位映射配置（UI 自动生成）
    │   │   ├── dance.vmd      # 根目录动画（向后兼容）
    │   │   └── smile.vpd      # 模型专属表情（可选）
    │   └── 模型B/
    │       └── ...
    ├── DefaultAnim/           # 系统预设动画（模组自动释放）
    ├── CustomAnim/            # 用户自定义动画
    ├── DefaultMorph/          # 系统预设表情
    ├── CustomMorph/           # 用户自定义表情
```

### 文件夹详解

#### `EntityPlayer/` - 玩家模型目录

存放玩家可用的 MMD 模型。**每个模型必须放在独立的子文件夹中**。

| 文件类型 | 扩展名 | 说明 |
|---------|--------|------|
| 模型文件 | `.pmx` / `.pmd` | 必需，PMX 优先于 PMD |
| 贴图文件 | `.png` / `.jpg` / `.bmp` / `.tga` | 模型引用的贴图 |
| 专属动画 | `.vmd` | 可选，推荐放入 `anims/` 子文件夹 |
| 动画映射 | `animations.json` | 可选，通过 UI 自动生成 |
| 专属表情 | `.vpd` | 可选，仅该模型可用 |

**模型识别规则**：
- 扫描每个子文件夹，查找 `.pmx` 或 `.pmd` 文件
- 若文件夹内有多个模型文件，优先选择 `model.pmx` 或 `model.pmd`
- 若无 `model.*`，则按文件名排序选择第一个

**示例**：
```
EntityPlayer/
├── miku/
│   ├── model.pmx          # 被加载
│   ├── body.png
│   └── face.png
└── shiroha/
    ├── cirno_v2.pmx       # 被加载（文件夹内唯一 pmx）
    ├── cirno_old.pmd      # 忽略（pmx 优先）
    └── texture.png
```

#### `DefaultAnim/` - 系统预设动画

模组首次启动时自动从内置资源释放，包含游戏状态对应的基础动画：

| 动画文件名 | 触发条件 |
|-----------|----------|
| `idle.vmd` | 站立静止 |
| `walk.vmd` | 行走 |
| `sprint.vmd` | 疾跑 |
| `sneak.vmd` | 潜行 |
| `swim.vmd` | 游泳 |
| `crawl.vmd` | 匍匐 |
| `sleep.vmd` | 睡觉 |
| `die.vmd` | 死亡 |
| `elytraFly.vmd` | 鞘翅飞行 |
| `onClimbable.vmd` | 攀爬（静止） |
| `onClimbableUp.vmd` | 攀爬（上） |
| `onClimbableDown.vmd` | 攀爬（下） |
| `onHorse.vmd` / `ride.vmd` | 骑乘 |
| `lieDown.vmd` | 躺下 |
| `swingLeft.vmd` | 左手挥动 |
| `swingRight.vmd` | 右手挥动 |
| `itemActive_*.vmd` | 物品使用动画 |

> **注意**：可直接替换这些文件来自定义基础动画，但建议备份原文件。

#### `CustomAnim/` - 用户自定义动画

存放用户添加的 `.vmd` 动画文件，可通过动作轮盘手动触发播放。

**命名建议**：文件名即为显示名称，建议使用有意义的名称如 `跳舞.vmd`、`打招呼.vmd`。

#### `DefaultMorph/` - 系统预设表情

存放系统预设的 `.vpd` 表情文件（如眨眼、微笑等）。

#### `CustomMorph/` - 用户自定义表情

存放用户添加的 `.vpd` 表情文件，可通过表情轮盘手动触发。

### 动画加载优先级

当需要播放动画时，模组按以下顺序查找（从高到低）：

| 优先级 | 来源 | 路径 | 说明 |
|:---:|------|------|------|
| 1 | **animations.json 映射** | `EntityPlayer/模型名/animations.json` | 用户通过 UI 显式配置 |
| 2 | **anims/ 子文件夹** | `EntityPlayer/模型名/anims/*.vmd` | 同名自动匹配 |
| 3 | **模型根目录** | `EntityPlayer/模型名/*.vmd` | 同名自动匹配（向后兼容） |
| 4 | **自定义动画目录** | `CustomAnim/*.vmd` | 全局自定义动画 |
| 5 | **默认动画目录** | `DefaultAnim/*.vmd` | 系统预设（最低） |

只需配置想要覆盖的槽位，未配置的自动 fallback 到低优先级来源。

**示例**：若 `EntityPlayer/初音未来/anims/idle.vmd` 存在，则该模型的待机动画使用此文件，而非 `DefaultAnim/idle.vmd`。

### 模型专属动画配置

为特定模型定制动画有两种方式：

#### 方式一：自动同名匹配（零配置）

在模型文件夹下创建 `anims/` 子文件夹，放入与动画槽位同名的 VMD 文件即可：

```
EntityPlayer/
└── 初音未来/
    ├── model.pmx
    └── anims/
        ├── idle.vmd      # 自动替代默认待机动画
        ├── walk.vmd      # 自动替代默认行走动画
        └── sprint.vmd    # 自动替代默认疾跑动画
```

可用的槽位名称（即文件名）：

| 槽位名 | 触发条件 | 槽位名 | 触发条件 |
|--------|----------|--------|----------|
| `idle` | 站立静止 | `walk` | 行走 |
| `sprint` | 疾跑 | `sneak` | 潜行 |
| `air` | 空中 | `swim` | 游泳 |
| `crawl` | 匍匐 | `sleep` | 睡觉 |
| `die` | 死亡 | `ride` | 骑乘 |
| `elytraFly` | 鞘翅飞行 | `onHorse` | 骑马 |
| `onClimbable` | 攀爬（静止） | `onClimbableUp` | 攀爬（上） |
| `onClimbableDown` | 攀爬（下） | `lieDown` | 趴下 |
| `swingRight` | 右手挥动 | `swingLeft` | 左手挥动 |
| `itemRight` | 右手物品 | `itemLeft` | 左手物品 |
| `ridden` | 被骑乘 | `driven` | 驾驶 |

#### 方式二：UI 映射（自定义文件名）

当 VMD 文件名与槽位名不同时（如 `我的待机动画_v3.vmd`），可通过游戏内 UI 配置映射：

1. 将 VMD 文件放入模型的 `anims/` 文件夹
2. 进入游戏，打开**模型设置** → 点击底部 **「动画」** 按钮
3. 点击槽位名称（如「待机」）→ 展开下拉列表 → 选择对应的 VMD 文件
4. 点击 **「保存」**

映射关系会自动保存到模型目录下的 `animations.json`：

```json
{
  "idle": "我的待机动画_v3.vmd",
  "walk": "走路_custom.vmd"
}
```

> **提示**：也可以手动编辑 `animations.json`，格式为 `"槽位名": "VMD文件名"`。

### 模型设置

在模型选择界面中选中模型后，可进入模型独立设置界面，包含以下功能：

| 设置项 | 说明 |
|--------|------|
| **眼球追踪** | 开启/关闭眼球追踪，调整最大转动角度 |
| **模型缩放** | 调整模型整体大小（0.5x ~ 2.0x） |
| **快捷绑定** | 将模型绑定到快捷键槽位（1~4），按键快速切换 |
| **动画配置** | 打开动画映射界面，为模型配置专属动画（详见上文） |

### 快捷键操作

#### 主配置轮盘（按住 `Alt` 键）

按住 `Alt` 键打开主配置轮盘，移动鼠标选择功能，松开 `Alt` 确认：

| 选项 | 功能 |
|------|------|
| 🎭 **模型切换** | 打开模型选择界面，切换玩家使用的 MMD 模型 |
| 🎬 **动作选择** | 打开动作轮盘，手动播放 `CustomAnim/` 中的动画 |
| 😊 **表情选择** | 打开表情轮盘，应用 `CustomMorph/` 中的表情 |
| 👕 **材质控制** | 控制模型各部位材质的显示/隐藏 |
| ⚙ **模组设置** | 打开模组配置界面 |

#### 女仆配置轮盘（对准女仆按 `B` 键）

若安装了 TouhouLittleMaid 模组，对准女仆实体按 `B` 键可打开女仆专用配置轮盘。

> **提示**：快捷键可在游戏设置 → 控制 → MMD Skin 分类中自定义。

### 模型切换界面

按住 `Alt` → 选择「模型切换」进入：

- 显示 `EntityPlayer/` 下所有可用模型
- 显示模型格式（PMX/PMD）、文件名、文件大小
- 点击模型卡片立即切换
- 选择「默认」恢复原版玩家皮肤渲染
- 点击「刷新」重新扫描模型目录

### 动作/表情轮盘配置

动作轮盘和表情轮盘支持自定义槽位：

1. 打开对应轮盘界面
2. 点击右下角 ⚙ 按钮进入配置界面
3. 从可用列表中添加/移除槽位
4. 拖拽调整顺序

配置保存在 `.minecraft/config/mmdskin/` 目录下。

### 死亡布娃娃与死亡观战

在「模组设置 → 布娃娃系统」中可配置：

| 设置项 | 说明 |
|--------|------|
| **启用布娃娃** | 死亡时生成带物理的 ragdoll 尸体 |
| **尸体保留方式** | 按时长 / 按半径 / 不保留 |
| **尸体物理保持时长** | 0 ~ 3600 秒；到期冻结尸体物理（姿势保持、跳过模拟），0 = 永久模拟 |
| **隐藏死亡白烟** | 隐藏原版死亡粒子 |
| **劫持死亡屏** | 透明死亡屏 + 自由观战相机（困难模式完全放行原版行为） |

死亡屏被劫持后，屏幕右下角会出现两个按钮：

- **自由观战**：接管相机，鼠标环视、滚轮缩放
- **复活**：立即复活

> **注意**：死亡屏交互在原始输入层（`MouseHandler.onButton`）处理，
> 因为 26.3 的输入层会把点击在派发到 `Screen` 之前消费掉。

### 模型分发

管理员可在服务端配置允许分发的模型；客户端按需下载。

- **服务器转发**：服务端把模型分块推给客户端，适合内网/自建服
- **OSS 直链**：客户端直接从对象存储拉取，服务端零转发，适合公网

配置说明见 [specs/001-model-distribution/OSS配置说明.md](specs/001-model-distribution/OSS配置说明.md)。

### 本地指定玩家模型

在「模型切换」界面顶部点「本地指定玩家模型」，可以让你**在自己屏幕上**把某个玩家
渲染成你指定的模型。

- **纯本地**：不写进服务器、不广播、对方也不需要同意；服务器该怎么渲染怎么渲染
- **名字为主键**：输入玩家名，按 `TAB` 补全在线玩家；离线玩家的名字也能手输，
  他上线后自动生效
- **优先级最高**：盖过对方自选的模型，也盖过自动下载的结果；所有设置值都原样保留
- 记录存在你自己的 `config/mmdskin/model_overrides.json` 里

> 想让某个模型能被你指定，需要它在本机存在（或服务器授权分发、且你开了自动下载）。

## 构建

### 前置要求

- Rust 1.88+（用于 rust_engine）
- JDK **25**（用 Minecraft 自带的 `java-runtime-epsilon` 即可，不要装新 JDK）
- Gradle **9.8.2**（Loom 1.19.3 要求 ≥ 9.8.0）

> 拉取依赖需要走代理时，记得把 `maven.fabricmc.net` 加进 `nonProxyHosts`。

### 构建 rust_engine

```bash
cd rust_engine
cargo build --release --locked
```

### 构建 Minecraft Mod

```bash
./gradlew build
```

产物在 `fabric/build/libs/`。

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件。

## 致谢

本项目基于众多开源项目和贡献者的工作。
完整致谢请参阅 [ACKNOWLEDGMENTS.md](ACKNOWLEDGMENTS.md)。

### 核心依赖

| 库 | 许可证 | 说明 |
|----|--------|------|
| [Bullet3](https://github.com/bulletphysics/bullet3) | zlib | 3D 物理引擎 |
| [glam](https://github.com/bitshifter/glam-rs) | MIT/Apache-2.0 | 3D 数学库 |
| [mmd-rs](https://github.com/aankor/mmd-rs) | BSD-2-Clause | MMD 格式解析器 |

### 设计参考

| 项目 | 许可证 | 参考内容 |
|------|--------|----------|
| [KAIMyEntity](https://github.com/kjkjkAIStudio/KAIMyEntity) | MIT | 原始 Minecraft MMD 模组 |
| [KAIMyEntity-C](https://github.com/Gengorou-C/KAIMyEntity-C) | MIT | 本项目的直接前身（二次开发基础） |
| [Saba](https://github.com/benikabocha/saba) | MIT | 物理系统架构 |
| [nphysics](https://github.com/dimforge/nphysics) | Apache-2.0 | 骨骼层次设计 |
| [mdanceio](https://github.com/ReaNAiveD/mdanceio) | MIT | 动画系统 |

完整的第三方许可证信息请参阅 [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)。

## 相关项目

- [MikuMikuDance](https://sites.google.com/view/vpvp/) - 樋口優开发的原版 MMD 软件
- [Saba](https://github.com/benikabocha/saba) - C++ MMD 库
- [Rapier](https://rapier.rs) - Rust 物理引擎
