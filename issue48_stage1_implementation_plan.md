# Issue #48 第一阶段实施计划

## 目标

第一阶段只处理渲染状态串场，不调整现有头颈识别算法：

- 背包、GUI、世界、阴影、VR 镜像和远端玩家不继承本地主视图的第一人称材质遮罩。
- 第一人称可见性由单次 `RenderScene` Draw 决定，不再由 Java 侧长期保持 native 第一人称状态。
- 背包与 Iris 阴影 Pass 不更新本地相机的眼骨锚点。
- 背包人物使用原版临时写入的身体与头部角度跟随光标。

## 实施方式

1. 将 `RenderScene` 继续传递到 CPU/GPU 两套底层 renderer。
2. 扩展批量子网格元数据 JNI 接口，增加 `firstPersonView` 参数。
3. native 在模型锁内临时切换视图、写出子网格可见性，并在返回前恢复原状态。
4. GPU 后端每次 Draw 单独刷新子网格可见性，动画 revision 只控制骨骼、Morph 和 Compute 上传。
5. `FirstPersonManager` 不再调用 `SetFirstPersonMode`，只维护相机与 VR 视图状态。
6. 玩家协调器在背包和 Iris 阴影 Pass 中强制使用完整模型视图，并跳过眼骨相机同步。
7. 背包模型使用临时的 `yBodyRot/yHeadRot`，避免传入实体调度器固定的零 yaw。

## 验证

- Rust 单元测试验证按视图查询后第一人称状态会恢复。
- Java 测试和编译验证接口签名与两套 renderer 调用链。
- 手动验证背包反复开关与光标跟随、第一/第三人称切换、Iris 阴影、VR 眼睛/镜像和双人互看。

## 后续阶段

第二阶段再引入按三角形生成的第一人称 EBO，解决头身共材质以及特殊动作下后颈可见的问题。
