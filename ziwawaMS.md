# 纸娃娃模式优化说明

## 优化内容

本次更新针对纸娃娃模式进行了三项重要优化：

### 1. 解决视角抖动问题 ✅

**问题描述**: 
玩家在纸娃娃模式下移动时，相机视角会出现轻微抖动，影响观看体验。

**解决方案**:
在 `MMDCameraController.updatePuppetMode()` 方法中实现了平滑相机跟随算法：

```java
// 使用平滑插值来减少抖动
float smoothFactor = 0.8f;  // 平滑因子，越高越跟随，越低越平滑
cameraX = lerp(cameraX, playerX, smoothFactor);
cameraY = lerp(cameraY, playerY, smoothFactor);
cameraZ = lerp(cameraZ, playerZ, smoothFactor);

// 角度也需要平滑处理
cameraYaw = lerpAngle(cameraYaw, playerYaw, smoothFactor);
cameraPitch = lerpAngle(cameraPitch, playerPitch, smoothFactor);
```

**技术细节**:
- 使用线性插值 (lerp) 对相机位置和角度进行平滑过渡
- 平滑因子设为 0.8，平衡了跟随性和流畅性
- 同时处理位置 (XYZ) 和角度 (Yaw/Pitch) 的平滑

### 2. 实现动画循环播放 ✅

**问题描述**:
纸娃娃模式下，动画播放到结尾会自动停止并退出模式，无法持续观看舞蹈。

**解决方案**:
修改动画帧更新逻辑，实现无缝循环：

```java
// 实现动画循环播放：当动画结束时自动重置到开头
if (currentFrame >= maxFrame) {
    currentFrame = 0.0f;  // 循环回到开头而不是结束播放
}
```

**技术细节**:
- 移除了原有的 `endPlayback()` 调用
- 动画到达最大帧数时自动重置为 0
- 支持无限循环播放，适合长时间观看舞蹈表演

### 3. 完全隐藏玩家MMD模型以优化性能 ✅

**问题描述**:
纸娃娃模式下，玩家的MMD模型仍在世界中渲染，造成不必要的性能开销。

**解决方案**:
在 `PlayerRenderSelectionResolver.resolve()` 中已实现模型隐藏：

```java
// 纸娃娃模式下，本地玩家不渲染MMD模型（只渲染默认实体）
if (isLocalPlayer) {
    com.shiroha.mmdskin.stage.client.camera.MMDCameraController controller = 
        com.shiroha.mmdskin.stage.client.camera.MMDCameraController.getInstance();
    if (controller.isPuppetMode()) {
        return PlayerRenderSelection.terminal(PlayerMixinDelegate.RenderAction.CANCEL);
    }
}
```

**技术细节**:
- 检测到纸娃娃模式时，直接取消MMD模型渲染
- 返回 `CANCEL` 动作，阻止后续渲染流程
- 玩家实体本身仍然可见（原版渲染），但MMD模型被隐藏
- 显著降低了GPU渲染负担，提升帧率

## 文件修改清单

### 主要修改
- `common/src/main/java/com/shiroha/mmdskin/stage/client/camera/MMDCameraController.java`
  - 修改 `updatePuppetMode()` 方法
  - 添加相机平滑跟随逻辑
  - 实现动画循环播放

### 已有功能（无需修改）
- `common/src/main/java/com/shiroha/mmdskin/renderer/integration/player/PlayerRenderSelectionResolver.java`
  - 已实现纸娃娃模式下的模型隐藏

## 使用说明

### 启动纸娃娃模式
1. 打开舞台选择界面（按住 Alt → 舞台模式）
2. 选择一个包含舞蹈动画的舞台包
3. 点击底部的「纸娃娃模式」按钮
4. 进入第一人称视角，可以自由移动观察跳舞的MMD模型

### 效果预期
- ✅ 相机跟随平滑，无明显抖动
- ✅ 舞蹈动画会持续循环播放
- ✅ 玩家自己的MMD模型不可见（性能优化）
- ✅ 可以自由走动、跳跃，从不同角度观察

## 性能优化说明

通过隐藏玩家MMD模型，纸娃娃模式获得了以下性能提升：

1. **减少渲染调用**: 不再渲染玩家的MMD模型网格
2. **降低GPU负载**: 减少了顶点着色器和片段着色器的计算
3. **节省内存带宽**: 不需要传输蒙皮数据到GPU
4. **提升帧率**: 特别是在复杂场景中效果明显

## 技术架构

```
纸娃娃模式工作流程:
┌─────────────────────────────────────┐
│ 1. 用户点击"纸娃娃模式"              │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ 2. DefaultStagePlaybackRuntime       │
│    加载VMD动画并合并                  │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ 3. MMDCameraController               │
│    - 设置 PUPPET_MODE 状态           │
│    - 切换到第一人称视角               │
│    - 绑定动画到模型句柄               │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ 4. 每帧更新 (updatePuppetMode)       │
│    - 更新动画帧（循环）               │
│    - 同步模型动画                    │
│    - 平滑跟随玩家相机                │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ 5. 渲染阶段                          │
│    - PlayerRenderSelectionResolver   │
│      检测到纸娃娃模式                 │
│    - 取消玩家MMD模型渲染             │
│    - 仅渲染舞台上的MMD模型           │
└─────────────────────────────────────┘
```

## 注意事项

1. **平滑因子调整**: 如果希望相机更紧密地跟随玩家，可以增大 `smoothFactor`（最大1.0）；如果希望更平滑但延迟更大，可以减小该值（最小0.0）。

2. **动画循环**: 当前实现是简单循环，如果需要更复杂的循环逻辑（如淡入淡出），可以在 `syncStagePresentation()` 中添加过渡效果。

3. **多人模式**: 纸娃娃模式目前是单机模式，不会影响其他玩家的渲染。

## 未来改进方向

- [ ] 添加可配置的平滑因子（在配置文件中）
- [ ] 支持自定义循环次数（而非无限循环）
- [ ] 添加动画切换时的淡入淡出效果
- [ ] 支持在纸娃娃模式下切换不同的舞蹈动画

---

**更新日期**: 2026-05-25  
**版本**: MC-MMD-rust 1.0.5  
**作者**:YNXLDSMJT
