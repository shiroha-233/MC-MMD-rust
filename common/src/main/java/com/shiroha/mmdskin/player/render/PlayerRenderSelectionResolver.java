package com.shiroha.mmdskin.player.render;

import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

final class PlayerRenderSelectionResolver {

    private PlayerRenderSelectionResolver() {
    }

    static PlayerRenderSelection resolve(AbstractClientPlayer player, boolean isYsmActive) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean isLocalPlayer = minecraft.player != null && minecraft.player.getUUID().equals(player.getUUID());
        String playerName = player.getName().getString();
        String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), playerName, isLocalPlayer);
        PlayerRenderRequest request = new PlayerRenderRequest(
                player,
                selectedModel,
                PlayerModelResolver.getCacheKey(player),
                isLocalPlayer,
                isYsmActive);

        PlayerRenderAction terminalAction = PlayerVanillaRenderPolicy.resolveTerminalAction(request);
        if (terminalAction != null) {
            return PlayerRenderSelection.terminal(terminalAction);
        }

        if (!PlayerPerformanceGate.allowsMmd(request.player())) {
            return PlayerRenderSelection.terminal(PlayerRenderAction.FALLTHROUGH);
        }

        return PlayerRenderSelection.render(request.selectedModel(), request.playerCacheKey(), request.localPlayer());
    }
}
