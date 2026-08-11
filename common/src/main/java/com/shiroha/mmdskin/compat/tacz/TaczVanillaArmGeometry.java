package com.shiroha.mmdskin.compat.tacz;

import org.joml.Matrix4f;

/** 文件职责：把 TaCZ 捕获的原版手臂渲染入口矩阵转换为原版腕端矩阵。 */
final class TaczVanillaArmGeometry {
    private static final float PIXEL_SCALE = 1.0f / 16.0f;
    private static final float ARM_LENGTH_PIXELS = 12.0f;
    private static final float ARM_PIVOT_X_PIXELS = 5.0f;
    private static final float NORMAL_ARM_PIVOT_Y_PIXELS = 2.0f;
    private static final float SLIM_ARM_PIVOT_Y_PIXELS = 2.5f;

    private TaczVanillaArmGeometry() {
    }

    /**
     * PlayerRenderer.renderHand 会在入口矩阵后应用手臂 pivot；第一人称静态 setupAnim 下手臂
     * 旋转为零，因此腕端等于 pivot 再沿局部 +Y 前进完整臂长。
     */
    static Matrix4f toWrist(Matrix4f armRenderEntry, TaczFirstPersonFrameSnapshot.Hand hand,
                            boolean slimArms) {
        float pivotX = hand == TaczFirstPersonFrameSnapshot.Hand.LEFT
                ? ARM_PIVOT_X_PIXELS : -ARM_PIVOT_X_PIXELS;
        float pivotY = slimArms ? SLIM_ARM_PIVOT_Y_PIXELS : NORMAL_ARM_PIVOT_Y_PIXELS;
        return new Matrix4f(armRenderEntry).translate(
                pivotX * PIXEL_SCALE,
                (pivotY + ARM_LENGTH_PIXELS) * PIXEL_SCALE,
                0.0f);
    }
}
