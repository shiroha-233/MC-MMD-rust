# 第三方库声明

本项目使用以下开源库。编译并分发二进制文件的用户应包含相应的许可证声明。

---

## 直接依赖

### Rust 引擎 (rust_engine)

| 库名 | 版本 | 许可证 | 仓库地址 |
|------|------|--------|----------|
| rapier3d | 0.32.0 | Apache-2.0 | https://github.com/dimforge/rapier |
| glam | 0.31.0 | MIT 或 Apache-2.0 | https://github.com/bitshifter/glam-rs |
| mmd (mmd-rs) | 0.0.6 | BSD-2-Clause | https://github.com/aankor/mmd-rs |
| jni | 0.21 | MIT 或 Apache-2.0 | https://github.com/jni-rs/jni-rs |
| nalgebra | 0.34 | Apache-2.0 | https://github.com/dimforge/nalgebra |
| rayon | 1.11.0 | MIT 或 Apache-2.0 | https://github.com/rayon-rs/rayon |
| thiserror | 2.0 | MIT 或 Apache-2.0 | https://github.com/dtolnay/thiserror |
| bitflags | 2.6 | MIT 或 Apache-2.0 | https://github.com/bitflags/bitflags |
| byteorder | 1.5 | Unlicense 或 MIT | https://github.com/BurntSushi/byteorder |
| encoding_rs | 0.8 | MIT 或 Apache-2.0 | https://github.com/hsivonen/encoding_rs |
| log | 0.4 | MIT 或 Apache-2.0 | https://github.com/rust-lang/log |
| once_cell | 1.19 | MIT 或 Apache-2.0 | https://github.com/matklad/once_cell |
| image | 0.25 | MIT 或 Apache-2.0 | https://github.com/image-rs/image |
| vek | 0.17 | MIT | https://github.com/yoanlecoq/vek |

### 传递依赖（通过 rapier3d）

| 库名 | 许可证 | 仓库地址 |
|------|--------|----------|
| parry3d | Apache-2.0 | https://github.com/dimforge/parry |
| simba | Apache-2.0 | https://github.com/dimforge/simba |

---

## 设计参考

以下项目作为架构设计参考，其代码未被包含在本项目中。

| 项目 | 许可证 | 仓库地址 | 参考内容 |
|------|--------|----------|----------|
| KAIMyEntity | MIT | https://github.com/kjkjkAIStudio/KAIMyEntity | 原始 Minecraft MMD 模组 |
| KAIMyEntity-C | MIT | https://github.com/Gengorou-C/KAIMyEntity-C | 本项目直接前身（二次开发基础） |
| Saba | MIT | https://github.com/benikabocha/saba | 物理系统设计 |
| nphysics | Apache-2.0 | https://github.com/dimforge/nphysics | 骨骼层次结构 |
| mdanceio | MIT | https://github.com/ReaNAiveD/mdanceio | 动画系统 |

