# 玩家模型正视面HUD - 始终面向镜头功能

## 功能说明

此功能实现了MC-MMD模组中左上角玩家模型预览的"始终面向镜头"特性，参考了Ayame-PaperDoll模组的RotationMode.LOCK实现。

## 实现原理

### 核心思想

在Ayame-PaperDoll中，通过设置`RotationMode.LOCK`模式，**临时修改player实体的yBodyRot和yHeadRot属性**，使纸娃娃始终面向观察者，不受玩家实际朝向影响。

**关键发现**：MC-MMD的渲染系统会直接从player实体读取yBodyRot，而不是使用传入的yaw参数！

我们在MC-MMD的PlayerFrontViewHud中应用了完全相同的原理：

```java
// 关键代码：参考Ayame-PaperDoll第248-255行
// 1. 保存原始旋转角度
float originalYBodyRot = player.yBodyRot;
float originalYBodyRotO = player.yBodyRotO;
float originalYHeadRot = player.yHeadRot;
float originalYHeadRotO = player.yHeadRotO;

try {
    // 2. 锁定旋转角度（与Ayame-PaperDoll完全一致）
    player.yBodyRot = player.yBodyRotO = 180.0f;
    player.yHeadRot = player.yHeadRotO = 180.0f;
    player.setXRot(0.0f);
    
    // 3. 渲染模型（此时player的旋转已被锁定）
    modelData.model.render(player, fixedYaw, fixedPitch, ...);
} finally {
    // 4. 恢复原始旋转角度（不影响实际游戏）
    player.yBodyRot = originalYBodyRot;
    player.yBodyRotO = originalYBodyRotO;
    player.yHeadRot = originalYHeadRot;
    player.yHeadRotO = originalYHeadRotO;
}
```

### 与Ayame-PaperDoll的对比

| 特性 | Ayame-PaperDoll | MC-MMD (本实现) |
|------|----------------|----------------|
| 旋转控制 | `CONFIGS.rotationMode.getValue()` | 直接锁定 |
| 锁定模式 | `RotationMode.LOCK` | 硬编码180度 |
| Yaw设置 | `targetEntity.yBodyRot = 180 - bodyClamp` | `player.yBodyRot = 180.0f` |
| 头部Yaw设置 | `targetEntity.yHeadRot = 180 - headClamp` | `player.yHeadRot = 180.0f` |
| 俯仰角设置 | `targetEntity.setXRot(pitchClamp)` | `player.setXRot(0.0f)` |
| 渲染调用 | `entityRenderDispatcher.render(..., rotationYaw, ...)` | `model.render(..., fixedYaw, ...)` |
| **关键技巧** | **修改实体旋转属性** | **修改实体旋转属性** |

## 文件位置

- **实现文件**: `common/src/main/java/com/shiroha/mmdskin/debug/client/PlayerFrontViewHud.java`
- **注册位置**: 
  - Fabric: `fabric/src/main/java/com/shiroha/mmdskin/fabric/register/FabricClientRuntimeHooks.java` (第51行)
  - NeoForge: `neoforge/src/main/java/com/shiroha/mmdskin/neoforge/register/NeoForgeClientRuntimeHooks.java` (第127行)

## 配置选项

在`ConfigData.java`中可以调整以下参数：

```java
public boolean playerFrontViewEnabled = true;  // 是否启用
public float playerFrontViewScale = 1.5f;      // 缩放比例
public int playerFrontViewOffsetX = 10;        // X偏移（左上角）
public int playerFrontViewOffsetY = 10;        // Y偏移（左上角）
```

## 技术细节

### 1. 固定Yaw值的作用

- **正常情况**: 模型使用`player.yBodyRot`，会跟随玩家在游戏中转动
- **锁定后**: 模型始终使用`180.0f`，无论玩家如何转动，模型都保持正面朝向

### 2. 为什么是180度？

在Minecraft的坐标系统中：
- 0度 = 南方（Z轴正方向）
- 90度 = 西方（X轴负方向）
- 180度 = 北方（Z轴负方向）← **面向观察者**
- 270度 = 东方（X轴正方向）

### 3. 第一人称头部显示问题

**问题原因**：
- MC-MMD在第一人称模式下会隐藏头部模型（防止遮挡玩家视野）
- 这是通过`set_first_person_mode(true)`和材质可见性控制实现的
- 但HUD预览需要显示完整模型，包括头部

**解决方案**：
```java
// 渲染前：临时禁用第一人称模式
long modelHandle = modelData.model.getModelHandle();
boolean wasFirstPersonEnabled = FirstPersonManager.isActive();
if (wasFirstPersonEnabled) {
    NativeFunc.GetInst().SetFirstPersonMode(modelHandle, false);
}

try {
    // 渲染模型（此时头部会显示）
    modelData.model.render(...);
} finally {
    // 渲染后：恢复第一人称模式状态
    if (wasFirstPersonEnabled) {
        NativeFunc.GetInst().SetFirstPersonMode(modelHandle, true);
    }
}
```

这样确保了：
- ✅ HUD中显示完整头部模型
- ✅ 不影响实际游戏中的第一人称视角
- ✅ 自动恢复原有状态，不会造成状态泄漏

### 4. 模型朝向锁定问题（关键修复）

**问题原因**：
- MC-MMD的渲染系统会**直接从player实体读取yBodyRot**，而不是使用传入的yaw参数
- 仅仅传入固定yaw值是不够的，底层渲染时仍然会使用player的真实朝向
- 这就是为什么之前的实现中模型会跟随玩家转动

**解决方案（参考Ayame-PaperDoll）**：
```java
// 1. 保存玩家原始旋转角度
float originalYBodyRot = player.yBodyRot;
float originalYBodyRotO = player.yBodyRotO;
float originalYHeadRot = player.yHeadRot;
float originalYHeadRotO = player.yHeadRotO;

try {
    // 2. 锁定旋转角度（与Ayame-PaperDoll第248-255行完全一致）
    player.yBodyRot = player.yBodyRotO = 180.0f;  // 身体朝向
    player.yHeadRot = player.yHeadRotO = 180.0f;  // 头部朝向
    player.setXRot(0.0f);  // 俯仰角
    
    // 3. 渲染模型（此时player的旋转已被锁定）
    modelData.model.render(player, fixedYaw, ...);
} finally {
    // 4. 恢复原始旋转角度（不影响实际游戏）
    player.yBodyRot = originalYBodyRot;
    player.yBodyRotO = originalYBodyRotO;
    player.yHeadRot = originalYHeadRot;
    player.yHeadRotO = originalYHeadRotO;
}
```

这样确保了：
- ✅ HUD模型始终面向镜头，不随玩家转动
- ✅ 不影响实际游戏中的玩家朝向
- ✅ 使用try-finally确保状态一定恢复
- ✅ 与Ayame-PaperDoll的实现原理完全一致

### 渲染流程

```
1. 检查配置是否启用
2. 获取本地玩家实例
3. 解析玩家当前使用的MMD模型
4. 在屏幕左上角设置位置（无背景和文字）
5. 设置3D变换矩阵：
   - 缩放到指定大小
   - **Z轴旋转180度修正头脚颠倒** ← 关键修正
6. **保存玩家原始旋转角度** ← 新增
7. **锁定玩家旋转属性** ← 关键：临时修改yBodyRot/yHeadRot
8. **临时禁用第一人称模式** ← 确保头部显示
9. 调用模型渲染API，传入固定yaw=180度
10. 完整渲染模型（包括头部）
11. **恢复第一人称模式状态**
12. **恢复玩家原始旋转角度** ← 关键：不影响实际游戏
```

## 测试方法

1. 启动游戏并加载MMD模型
2. 确保设置中启用了“玩家模型正视面”
3. 在游戏中转动视角（按A/D键或移动鼠标）
4. **观察左上角的HUD**：模型应该始终保持**正面朝向**，不会随玩家转动
5. 在第一人称模式下，HUD中应显示**完整的头部模型**
6. 低头/抬头看自己的身体：实际游戏中头部应该**正常隐藏**（不遮挡视野）
7. 切换回第三人称：HUD模型仍应**始终面向镜头**

## 扩展建议

如果需要更灵活的旋转控制，可以参考Ayame-PaperDoll添加配置选项：

```java
// 可选：添加旋转模式配置
public enum RotationMode {
    LOCK,      // 始终面向镜头
    FOLLOW     // 跟随玩家朝向
}

public RotationMode playerFrontViewRotationMode = RotationMode.LOCK;
```

然后在渲染时根据模式选择yaw值：

```java
float yaw = (rotationMode == RotationMode.LOCK) 
    ? 180.0f 
    : player.yBodyRot;
```

## 注意事项

- 此功能仅在玩家已加载MMD模型时生效
- HUD会在隐藏GUI（F1）或显示调试屏幕（F3）时自动隐藏
- 模型渲染使用固定亮度（0xF000F0），确保在任何光照条件下都清晰可见
- 默认情况下此功能是启用的

## 参考资料

- Ayame-PaperDoll源码: `Ayame-PaperDoll-1.21.1/common/src/main/java/org/ayamemc/ayamepaperdoll/hud/PaperDollRenderer.java`
- 关键代码段: 第248-255行（头部和身体锁定逻辑）
