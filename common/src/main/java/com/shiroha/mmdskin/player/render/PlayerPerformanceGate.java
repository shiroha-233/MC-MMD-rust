package com.shiroha.mmdskin.player.render;

import com.shiroha.mmdskin.render.policy.RenderPriorityService;
import net.minecraft.client.player.AbstractClientPlayer;

final class PlayerPerformanceGate {
    private PlayerPerformanceGate() {
    }

    static boolean allowsMmd(AbstractClientPlayer player) {
        return RenderPriorityService.get().shouldUsePlayerModel(player);
    }
}
