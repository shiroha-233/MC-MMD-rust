package com.shiroha.mmdskin.ui.selector.adapter;

import com.shiroha.mmdskin.model.runtime.ModelRequestKey;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.ui.network.ModelSelectorNetworkHandler;
import com.shiroha.mmdskin.ui.selector.port.ModelSelectionRuntimeGateway;
import net.minecraft.client.Minecraft;

/** 文件职责：在本地玩家切换模型后刷新对应仓储条目。 */
public class DefaultModelSelectionRuntimeGateway implements ModelSelectionRuntimeGateway {
    @Override
    public void afterLocalModelSelection(String modelName) {
        ModelSelectorNetworkHandler.getInstance().syncModelSelection(modelName);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        ClientRenderRuntime.get().modelRepository().reloadSubject(ModelRequestKey.player(minecraft.player, modelName));
    }
}
