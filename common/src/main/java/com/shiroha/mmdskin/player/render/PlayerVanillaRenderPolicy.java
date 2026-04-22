package com.shiroha.mmdskin.player.render;

import com.shiroha.mmdskin.compat.vr.VRArmHider;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import net.minecraft.client.Minecraft;

final class PlayerVanillaRenderPolicy {
    private PlayerVanillaRenderPolicy() {
    }

    static PlayerRenderAction resolveTerminalAction(PlayerRenderRequest request) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean isLocalFirstPerson = request.localPlayer() && minecraft.options.getCameraType().isFirstPerson();

        if (isLocalFirstPerson && !FirstPersonManager.shouldRenderFirstPerson() && !VRArmHider.isLocalPlayerInVR()) {
            return PlayerRenderAction.FALLTHROUGH;
        }

        if (request.localPlayer() && FirstPersonManager.shouldRenderFirstPerson()) {
            if (request.ysmActive()) {
                return PlayerRenderAction.CANCEL;
            }
            if (shouldUseVanillaRenderer(request.selectedModel(), request.player())) {
                return PlayerRenderAction.CANCEL;
            }
        }

        if (shouldUseVanillaRenderer(request.selectedModel(), request.player()) || request.ysmActive()) {
            return PlayerRenderAction.FALLTHROUGH;
        }

        return null;
    }

    private static boolean shouldUseVanillaRenderer(String selectedModel, net.minecraft.client.player.AbstractClientPlayer player) {
        return ModelSelectionPolicy.shouldUseVanillaRenderer(selectedModel, player);
    }
}
