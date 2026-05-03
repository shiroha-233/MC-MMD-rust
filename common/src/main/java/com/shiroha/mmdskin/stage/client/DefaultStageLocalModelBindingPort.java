package com.shiroha.mmdskin.stage.client;

import com.shiroha.mmdskin.bridge.runtime.NativeScenePort;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelRequestKey;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.stage.client.playback.port.PlayerStageAnimationPort;
import com.shiroha.mmdskin.stage.client.playback.port.StageLocalModelBindingPort;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;

import java.util.Objects;

/** 文件职责：把本地玩家当前选择的模型绑定到舞台播放会话。 */
public final class DefaultStageLocalModelBindingPort implements StageLocalModelBindingPort, PlayerStageAnimationPort {
    private final NativeScenePort scenePort;

    public DefaultStageLocalModelBindingPort(NativeScenePort scenePort) {
        this.scenePort = Objects.requireNonNull(scenePort, "scenePort");
    }

    @Override
    public StageLocalModelBinding bindLocalModel(long mergedAnim) {
        var minecraft = StageClientContext.minecraft();
        if (minecraft.player == null) {
            return StageLocalModelBinding.empty();
        }

        String modelName = ModelSelectorConfig.getInstance().getSelectedModel();
        if (modelName == null || modelName.isEmpty()) {
            return StageLocalModelBinding.empty();
        }

        ManagedModel modelData = ClientRenderRuntime.get().modelRepository()
                .acquire(ModelRequestKey.player(minecraft.player, modelName));
        if (modelData == null) {
            return StageLocalModelBinding.empty();
        }

        long modelHandle = modelData.modelInstance().getModelHandle();
        MmdSkinRendererPlayerHelper.startStageAnimation(modelData, mergedAnim);
        return new StageLocalModelBinding(modelHandle, modelName);
    }

    @Override
    public void prepareLocalModelForStage(long modelHandle) {
        if (modelHandle == 0L) {
            return;
        }
        scenePort.setAutoBlinkEnabled(modelHandle, false);
        scenePort.setEyeTrackingEnabled(modelHandle, false);
    }

    @Override
    public void clearLocalStageFlags() {
        var minecraft = StageClientContext.minecraft();
        if (minecraft.player == null) {
            return;
        }

        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(minecraft.player);
        if (resolved != null) {
            resolved.model().entityState().playCustomAnim = false;
            resolved.model().entityState().playStageAnim = false;
        }
    }

    @Override
    public void restoreLocalModelState() {
        var minecraft = StageClientContext.minecraft();
        if (minecraft.player == null) {
            return;
        }

        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(minecraft.player);
        if (resolved != null) {
            ManagedModel modelData = resolved.model();
            long handle = modelData.modelInstance().getModelHandle();
            if (handle != 0L) {
                scenePort.setAutoBlinkEnabled(handle, true);
                scenePort.setEyeTrackingEnabled(handle, true);
            }
            MmdSkinRendererPlayerHelper.resetModelAnimationState(minecraft.player, modelData);
        }
    }
}
