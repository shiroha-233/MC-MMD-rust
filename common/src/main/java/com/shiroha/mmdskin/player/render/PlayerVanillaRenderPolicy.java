package com.shiroha.mmdskin.player.render;

import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import net.minecraft.client.Minecraft;

/** 文件职责：决定玩家是否回退到原版渲染管线。 */
final class PlayerVanillaRenderPolicy {
    private PlayerVanillaRenderPolicy() {
    }

    static PlayerRenderAction resolveTerminalAction(PlayerRenderRequest request) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean isLocalFirstPerson = request.localPlayer() && minecraft.options.getCameraType().isFirstPerson();

        if (isLocalFirstPerson && !FirstPersonManager.shouldRenderFirstPerson() && !FirstPersonManager.vrRuntime().isLocalPlayerInVr()) {
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
