package com.shiroha.mmdskin.compat.vr;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** 文件职责：向业务层提供与具体 VR 实现解耦的追踪数据读取。 */
public final class VRDataProvider {
    private static volatile VrTrackingFacade trackingFacade = VivecraftVrTrackingFacade.INSTANCE;

    private VRDataProvider() {
    }

    static void setTrackingFacadeForTesting(VrTrackingFacade trackingFacade) {
        VRDataProvider.trackingFacade = trackingFacade != null ? trackingFacade : VivecraftVrTrackingFacade.INSTANCE;
    }

    public static boolean isVRPlayer(Player player) {
        return trackingFacade.isVrPlayer(player);
    }

    public static float[] getRenderTrackingData(Player player) {
        return trackingFacade.getTrackingData(player);
    }

    public static float getBodyYawRad(Player player, float tickDelta) {
        float vrYaw = trackingFacade.getBodyYawRadians(player);
        if (!Float.isNaN(vrYaw)) {
            return vrYaw;
        }
        return Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot) * ((float) Math.PI / 180F);
    }

    public static float getBodyYawDegrees(Player player, float tickDelta) {
        return getBodyYawRad(player, tickDelta) * (180F / (float) Math.PI);
    }

    public static Vec3 getRenderOrigin(Player player, float tickDelta) {
        Vec3 renderOrigin = trackingFacade.getLocalPlayerRenderOrigin(tickDelta);
        if (renderOrigin != null && trackingFacade.isVrPlayer(player)) {
            return renderOrigin;
        }

        return new Vec3(
                Mth.lerp(tickDelta, player.xo, player.getX()),
                Mth.lerp(tickDelta, player.yo, player.getY()),
                Mth.lerp(tickDelta, player.zo, player.getZ())
        );
    }
}
