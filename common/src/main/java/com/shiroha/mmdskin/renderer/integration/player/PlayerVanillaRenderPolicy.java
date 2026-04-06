package com.shiroha.mmdskin.renderer.integration.player;

import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import net.minecraft.client.Minecraft;

final class PlayerVanillaRenderPolicy {
    private PlayerVanillaRenderPolicy() {
    }

    static PlayerRenderSelection resolveTerminalSelection(PlayerRenderRequest request) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean isLocalFirstPerson = request.localPlayer() && minecraft.options.getCameraType().isFirstPerson();

        if (isLocalFirstPerson && !FirstPersonManager.shouldRenderFirstPerson() && !VRArmHider.isLocalPlayerInVR()) {
            FirstPersonManager.reset();
            return PlayerRenderSelection.terminal(PlayerRenderAction.FALLTHROUGH, true);
        }

        if (request.localPlayer() && FirstPersonManager.shouldRenderFirstPerson()) {
            if (request.ysmActive()) {
                return PlayerRenderSelection.terminal(PlayerRenderAction.CANCEL);
            }
            if (shouldUseVanillaRenderer(request.selectedModel(), request.player())) {
                return PlayerRenderSelection.terminal(PlayerRenderAction.CANCEL);
            }
        }

        if (shouldUseVanillaRenderer(request.selectedModel(), request.player()) || request.ysmActive()) {
            return PlayerRenderSelection.terminal(PlayerRenderAction.FALLTHROUGH);
        }

        return null;
    }

    private static boolean shouldUseVanillaRenderer(String selectedModel, net.minecraft.client.player.AbstractClientPlayer player) {
        return ModelSelectionPolicy.shouldUseVanillaRenderer(selectedModel, player);
    }
}
