package com.shiroha.mmdskin.compat.vr;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Vivecraft 数据适配层。
 */
public final class VRDataProvider {

    private VRDataProvider() {
    }

    public static boolean isVRPlayer(Player player) {
        return VivecraftReflectionBridge.isVRPlayer(player);
    }

    public static float[] getRenderTrackingData(Player player) {
        return VivecraftReflectionBridge.getTrackingData(player);
    }

    public static float getBodyYawRad(Player player, float tickDelta) {
        float vrYaw = VivecraftReflectionBridge.getBodyYawRadians(player);
        if (!Float.isNaN(vrYaw)) {
            return vrYaw;
        }
        return Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot) * ((float) Math.PI / 180F);
    }

    public static float getBodyYawDegrees(Player player, float tickDelta) {
        return getBodyYawRad(player, tickDelta) * (180F / (float) Math.PI);
    }
}
