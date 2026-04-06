package com.shiroha.mmdskin.renderer.runtime.model.helper;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * 世界场景下的模型更新与物理策略。
 */
public final class MMDWorldRenderPolicy {
    private static final MMDWorldRenderPolicy INSTANCE = new MMDWorldRenderPolicy();

    private MMDWorldRenderPolicy() {
    }

    public static MMDWorldRenderPolicy get() {
        return INSTANCE;
    }

    public Decision resolve(long modelHandle, Entity entity) {
        MMDRenderPriorityService priorityService = MMDRenderPriorityService.get();
        priorityService.beginWorldFrame();

        boolean localPlayer = isLocalPlayer(entity);
        double distanceSq = priorityService.distanceSqToCamera(entity, localPlayer);
        boolean shouldUpdate = priorityService.shouldUpdateAnimation(modelHandle, distanceSq, localPlayer);
        boolean physicsEnabled = shouldUpdate && priorityService.shouldEnablePhysics(entity, localPlayer);
        return new Decision(true, shouldUpdate, physicsEnabled);
    }

    private boolean isLocalPlayer(Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getUUID().equals(player.getUUID());
    }

    public record Decision(boolean shouldRender, boolean shouldUpdate, boolean physicsEnabled) {
    }
}
