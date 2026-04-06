package com.shiroha.mmdskin.renderer.integration.player;

import com.shiroha.mmdskin.config.UIConstants;
import net.minecraft.client.player.AbstractClientPlayer;

final class ModelSelectionPolicy {
    private ModelSelectionPolicy() {
    }

    static boolean shouldUseVanillaRenderer(String selectedModel, AbstractClientPlayer player) {
        return selectedModel == null
                || selectedModel.isEmpty()
                || UIConstants.DEFAULT_MODEL_NAME.equals(selectedModel)
                || player.isSpectator();
    }
}
