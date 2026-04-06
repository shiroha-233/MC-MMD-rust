package com.shiroha.mmdskin.renderer.integration.player;

import com.shiroha.mmdskin.renderer.runtime.model.helper.MMDRenderPriorityService;
import net.minecraft.client.player.AbstractClientPlayer;

final class PlayerPerformanceGate {
    private PlayerPerformanceGate() {
    }

    static boolean allowsMmd(AbstractClientPlayer player) {
        return MMDRenderPriorityService.get().shouldUseMmdPlayer(player);
    }
}
