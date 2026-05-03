package com.shiroha.mmdskin.compat.vr;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** 文件职责：抽象 VR 追踪查询边界，隔离 Vivecraft 反射实现。 */
interface VrTrackingFacade {
    boolean isVrPlayer(Player player);

    float[] getTrackingData(Player player);

    float getBodyYawRadians(Player player);

    Vec3 getLocalPlayerRenderOrigin(float partialTick);
}
