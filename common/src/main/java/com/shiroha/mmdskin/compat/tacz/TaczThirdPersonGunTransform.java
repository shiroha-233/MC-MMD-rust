package com.shiroha.mmdskin.compat.tacz;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/** 文件职责：抵消 Minecraft/TaCZ 第三人称手持物品坐标相对 MMD 挂点的局部偏转。 */
public final class TaczThirdPersonGunTransform {
    static final float LOCAL_Z_CORRECTION_RADIANS = (float) Math.toRadians(45.0);

    private TaczThirdPersonGunTransform() {
    }

    public static void apply(PoseStack poseStack, InteractionHand hand, ItemStack itemStack) {
        if (!shouldApply(hand == InteractionHand.MAIN_HAND, TaczGunDetector.isGun(itemStack))) {
            return;
        }

        // Minecraft/TaCZ 的第三人称手持物品坐标约有 -45° 的局部 Z 偏转。
        // 必须紧跟 VMD 挂点矩阵后补偿 +45°，且不修改 MMD 骨骼或 VMD 动作。
        poseStack.mulPose(correction());
    }

    static boolean shouldApply(boolean mainHand, boolean taczGun) {
        return mainHand && taczGun;
    }

    static Quaternionf correction() {
        return new Quaternionf().rotateZ(LOCAL_Z_CORRECTION_RADIANS);
    }
}
