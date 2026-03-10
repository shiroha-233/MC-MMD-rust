package com.shiroha.mmdskin.renderer.integration.player;

import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

final class PlayerRenderSelectionResolver {

    private static final String DEFAULT_RENDER_LABEL = "默认 (原版渲染)";

    private PlayerRenderSelectionResolver() {
    }

    static PlayerRenderSelection resolve(AbstractClientPlayer player, boolean isYsmActive) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean isLocalPlayer = minecraft.player != null && minecraft.player.getUUID().equals(player.getUUID());
        String playerName = player.getName().getString();
        String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), playerName, isLocalPlayer);
        boolean isLocalFirstPerson = isLocalPlayer && minecraft.options.getCameraType().isFirstPerson();

        if (isLocalFirstPerson && !FirstPersonManager.shouldRenderFirstPerson() && !VRArmHider.isLocalPlayerInVR()) {
            return PlayerRenderSelection.terminal(PlayerMixinDelegate.RenderAction.FALLTHROUGH);
        }

        if (isLocalPlayer && FirstPersonManager.shouldRenderFirstPerson()) {
            if (isYsmActive) {
                return PlayerRenderSelection.terminal(PlayerMixinDelegate.RenderAction.CANCEL);
            }
            if (shouldUseVanillaRenderer(selectedModel, player)) {
                return PlayerRenderSelection.terminal(PlayerMixinDelegate.RenderAction.CANCEL);
            }
        }

        if (shouldUseVanillaRenderer(selectedModel, player) || isYsmActive) {
            return PlayerRenderSelection.terminal(PlayerMixinDelegate.RenderAction.FALLTHROUGH);
        }

        return PlayerRenderSelection.render(selectedModel, PlayerModelResolver.getCacheKey(player), isLocalPlayer);
    }

    private static boolean shouldUseVanillaRenderer(String selectedModel, AbstractClientPlayer player) {
        return selectedModel == null
                || selectedModel.isEmpty()
                || DEFAULT_RENDER_LABEL.equals(selectedModel)
                || player.isSpectator();
    }
}
