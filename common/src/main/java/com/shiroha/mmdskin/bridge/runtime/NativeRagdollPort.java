/* 文件职责：定义死亡布娃娃相关的 native 能力边界。 */
package com.shiroha.mmdskin.bridge.runtime;

/**
 * 死亡布娃娃 native 能力边界。
 *
 * <p>{@link #enterRagdoll} 的 params 固定 16 float，契约（0 值段回退引擎默认，
 * 默认值见 Rust 侧 ragdoll/spec.rs 与 ragdoll/mod.rs）：
 * <ul>
 *   <li>[0] 模型局部地面高度（实体脚底相对模型原点，已除以单位换算 {@code unitToMeter}）</li>
 *   <li>[1-3] 死亡初速度（世界 m/s，引擎按 yaw 旋转换算到模型局部 Bullet 空间）</li>
 *   <li>[4] 实体 yaw（弧度，速度换算用）</li>
 *   <li>[5] 碰撞体厚度缩放（0 → 1.0）</li>
 *   <li>[6] 摩擦系数（0 → 0.55）</li>
 *   <li>[7] 恢复系数（0 → 0.06）</li>
 *   <li>[8] 线性阻尼（0 → 0.06）</li>
 *   <li>[9] 角阻尼（0 → 0.35）</li>
 *   <li>[10] 重力 Y（模型单位/s²；必须显式给 -9.81 / {@code unitToMeter}，引擎默认 -109 只在缩放 = 1 时正确）</li>
 *   <li>[11] 关节强度（1.35 = 标准手感）</li>
 *   <li>[12] 总质量 kg（0 → 50）</li>
 *   <li>[13] 手臂关节限位乘数（0 → 0.8）</li>
 *   <li>[14] 世界米 → 模型单位换算 {@code unitToMeter}（= 0.09 × 用户模型缩放；速度换算用）</li>
 *   <li>[15] 保留</li>
 * </ul>
 */
public interface NativeRagdollPort {

    /** 启用布娃娃（从当前骨骼姿势构建刚体）；模型不支持（VRM/骨骼不齐）时返回 false。 */
    boolean enterRagdoll(long modelHandle, float[] params);

    /** 拆除布娃娃，骨骼交还动画驱动。 */
    void disableRagdoll(long modelHandle);

    /** 运行中更新地面高度（模型局部单位）。 */
    void setRagdollGround(long modelHandle, float groundLocalY);

    /** 施加速度增量（世界 m/s；击退/受击预留）。 */
    void applyRagdollVelocityDelta(long modelHandle, float vx, float vy, float vz,
                                   float yawRadians, float worldToModelScale, boolean upperBodyOnly);

    boolean isRagdollActive(long modelHandle);

    /** 冻结/解冻布娃娃（冻结 = 姿势保持 + 跳过步进；性能保持期到点后调用）。 */
    void setRagdollFrozen(long modelHandle, boolean frozen);

    /** 开关该模型的 MMD 物理（发饰/裙摆随动）；尸体冻结时一并关闭。 */
    void setModelPhysicsEnabled(long modelHandle, boolean enabled);

    /** JSON 调试串。 */
    String getRagdollDebugInfo(long modelHandle);
}
