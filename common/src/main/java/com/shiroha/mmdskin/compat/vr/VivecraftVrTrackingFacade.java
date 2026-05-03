package com.shiroha.mmdskin.compat.vr;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** 文件职责：把 Vivecraft 反射桥接适配为 VR 追踪查询门面。 */
final class VivecraftVrTrackingFacade implements VrTrackingFacade {
    static final VivecraftVrTrackingFacade INSTANCE = new VivecraftVrTrackingFacade();

    private VivecraftVrTrackingFacade() {
    }

    @Override
    public boolean isVrPlayer(Player player) {
        return VivecraftReflectionBridge.isVRPlayer(player);
    }

    @Override
    public float[] getTrackingData(Player player) {
        return VivecraftReflectionBridge.getTrackingData(player);
    }

    @Override
    public float getBodyYawRadians(Player player) {
        return VivecraftReflectionBridge.getBodyYawRadians(player);
    }

    @Override
    public Vec3 getLocalPlayerRenderOrigin(float partialTick) {
        return VivecraftReflectionBridge.getLocalPlayerRenderOrigin(partialTick);
    }
}
