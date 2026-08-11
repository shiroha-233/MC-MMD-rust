package com.shiroha.mmdskin.bridge.runtime;

/** TaCZ 一次性双臂模型局部空间目标的 native 能力边界。 */
public interface NativeTaczArmTargetPort {
    /**
     * 提交同一动画帧的手部目标。数组必须恰有 32 项，validMask 位 0 为左手、位 1 为右手。
     * native 拒绝非法矩阵，并同时丢弃该模型尚未消费的旧目标。
     */
    boolean setTaczArmTargets(long modelHandle, float[] columnMajorMatrices, int validMask);

    /** 清除尚未消费的目标；用于武器状态离开或本帧准备失败。 */
    void clearTaczArmTargets(long modelHandle);

    /** 读取并清除最近一次 native 更新的目标接收与 IK 应用位。 */
    int getLastTaczArmApplyResult(long modelHandle);

    /** 读取并清除求解后的目标、手首、挂点、误差和臂长诊断。 */
    boolean getLastTaczArmDiagnostics(long modelHandle, float[] output);

}
