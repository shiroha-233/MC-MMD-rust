package com.shiroha.mmdskin.ui.selector.adapter;

import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import com.shiroha.mmdskin.ui.network.ModelSelectorNetworkHandler;
import com.shiroha.mmdskin.ui.selector.port.ModelSelectionRuntimeGateway;
import net.minecraft.client.Minecraft;

public class DefaultModelSelectionRuntimeGateway implements ModelSelectionRuntimeGateway {
    @Override
    public void afterLocalModelSelection(String modelName) {
        ModelSelectorNetworkHandler.getInstance().syncModelSelection(modelName);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        MMDModelManager.forceReloadPlayerModels(PlayerModelResolver.getCacheKey(minecraft.player));
    }
}
