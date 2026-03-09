package com.shiroha.mmdskin.stage.client;

import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import com.shiroha.mmdskin.stage.client.playback.port.StageLocalModelBindingPort;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import net.minecraft.client.Minecraft;

public final class DefaultStageLocalModelBindingPort implements StageLocalModelBindingPort {
    public static final DefaultStageLocalModelBindingPort INSTANCE = new DefaultStageLocalModelBindingPort();

    private DefaultStageLocalModelBindingPort() {
    }

    @Override
    public StageLocalModelBinding bindLocalModel(long mergedAnim) {
        Minecraft mc = StageClientContext.minecraft();
        if (mc.player == null) {
            return StageLocalModelBinding.empty();
        }

        String modelName = ModelSelectorConfig.getInstance().getSelectedModel();
        if (modelName == null || modelName.isEmpty()) {
            return StageLocalModelBinding.empty();
        }

        MMDModelManager.Model modelData = MMDModelManager.GetModel(
                modelName,
                PlayerModelResolver.getCacheKey(mc.player)
        );
        if (modelData == null) {
            return StageLocalModelBinding.empty();
        }

        long modelHandle = modelData.model.getModelHandle();
        MmdSkinRendererPlayerHelper.startStageAnimation(modelData, mergedAnim);
        return new StageLocalModelBinding(modelHandle, modelName);
    }
}
