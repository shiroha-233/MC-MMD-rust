package com.shiroha.mmdskin.player.runtime;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.player.animation.AnimationStateManager;
import com.shiroha.mmdskin.player.animation.PendingAnimSignalCache;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelInstance;
import com.shiroha.mmdskin.stage.client.sync.StageAnimSyncHelper;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家动画控制辅助类。
 */
public final class MmdSkinRendererPlayerHelper {
    private static final float STAGE_TRANSITION_TIME = 0.3f;

    public static boolean isUsingMmdModel(Player player) {
        if (player == null) return false;
        String playerName = player.getName().getString();
        Minecraft mc = Minecraft.getInstance();
        boolean isLocalPlayer = mc.player != null && mc.player.getUUID().equals(player.getUUID());
        String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), playerName, isLocalPlayer);
        return selectedModel != null && !selectedModel.isEmpty() && !selectedModel.equals(UIConstants.DEFAULT_MODEL_NAME);
    }

    private MmdSkinRendererPlayerHelper() {
    }

    public static void ResetPhysics(Player player) {
        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
        if (resolved == null) return;

        resetModelAnimationState(player, resolved.model());
    }

    public static void CustomAnim(Player player, String id) {
        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
        if (resolved == null) return;

        ManagedModel managedModel = resolved.model();
        ModelInstance model = managedModel.modelInstance();
        managedModel.entityState().playCustomAnim = true;

        managedModel.entityState().invalidateStateLayers();
        model.changeAnim(managedModel.animationLibrary().animation(id), 0);
        model.setLayerLoop(1, true);
        model.changeAnim(0, 1);
        model.changeAnim(0, 2);
    }

    public static void startStageAnimation(ManagedModel modelData, long animHandle) {
        if (modelData == null || modelData.modelInstance() == null || modelData.entityState() == null || animHandle == 0) return;

        ModelInstance model = modelData.modelInstance();
        clearOverlayLayers(model);
        model.resetPhysics();
        modelData.entityState().invalidateStateLayers();
        model.transitionAnim(animHandle, 0, STAGE_TRANSITION_TIME);
        modelData.entityState().playCustomAnim = true;
        modelData.entityState().playStageAnim = true;
    }

    public static void resetModelAnimationState(ManagedModel modelData) {
        resetModelAnimationState(null, modelData);
    }

    public static void resetModelAnimationState(Player player, ManagedModel modelData) {
        if (modelData == null || modelData.modelInstance() == null || modelData.entityState() == null) return;

        ModelInstance model = modelData.modelInstance();
        modelData.entityState().playCustomAnim = false;
        modelData.entityState().playStageAnim = false;
        model.changeAnim(modelData.animationLibrary().animation("idle"), 0);
        clearOverlayLayers(model);
        model.resetPhysics();
        modelData.entityState().invalidateStateLayers();

        if (player instanceof AbstractClientPlayer clientPlayer) {
            AnimationStateManager.updateAnimationState(clientPlayer, modelData);
        }
    }

    public static void suppressDefaultAnimationState(ManagedModel modelData) {
        if (modelData == null || modelData.modelInstance() == null || modelData.entityState() == null) return;

        ModelInstance model = modelData.modelInstance();
        modelData.entityState().playCustomAnim = false;
        modelData.entityState().playStageAnim = false;
        model.changeAnim(0, 0);
        clearOverlayLayers(model);
        model.resetPhysics();
        modelData.entityState().invalidateStateLayers();
    }

    private static void clearOverlayLayers(ModelInstance model) {
        model.setLayerLoop(1, true);
        model.changeAnim(0, 1);
        model.changeAnim(0, 2);
    }

    public static void onDisconnect() {
        StageAnimSyncHelper.onDisconnect();
        PendingAnimSignalCache.onDisconnect();
    }
}
