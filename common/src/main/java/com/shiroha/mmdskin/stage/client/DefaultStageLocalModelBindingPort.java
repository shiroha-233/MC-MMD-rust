package com.shiroha.mmdskin.stage.client;

import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelRequestKey;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.stage.client.playback.port.StageLocalModelBindingPort;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import net.minecraft.client.Minecraft;

/** 文件职责：把本地玩家当前选择的模型绑定到舞台播放会话。 */
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

        ManagedModel modelData = ClientRenderRuntime.get().modelRepository()
                .acquire(ModelRequestKey.player(mc.player, modelName));
        if (modelData == null) {
            return StageLocalModelBinding.empty();
        }

        long modelHandle = modelData.modelInstance().getModelHandle();
        MmdSkinRendererPlayerHelper.startStageAnimation(modelData, mergedAnim);
        return new StageLocalModelBinding(modelHandle, modelName);
    }
}
