package com.shiroha.mmdskin.player.render;

import net.minecraft.client.player.AbstractClientPlayer;

record PlayerRenderRequest(
        AbstractClientPlayer player,
        String selectedModel,
        String playerCacheKey,
        boolean localPlayer,
        boolean ysmActive
) {
}
