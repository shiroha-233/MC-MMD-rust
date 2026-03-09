package com.shiroha.mmdskin.player.runtime;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.renderer.runtime.animation.MMDAnimManager;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import com.shiroha.mmdskin.player.animation.AnimationStateManager;
import com.shiroha.mmdskin.player.animation.PendingAnimSignalCache;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.renderer.api.IMMDModel;
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

        MMDModelManager.Model mwed = resolved.model();
        IMMDModel model = mwed.model;
        mwed.entityData.playCustomAnim = true;

        mwed.entityData.invalidateStateLayers();
        model.changeAnim(MMDAnimManager.GetAnimModel(model, id), 0);
        model.setLayerLoop(1, true);
        model.changeAnim(0, 1);
        model.changeAnim(0, 2);
    }

    public static void startStageAnimation(MMDModelManager.Model modelData, long animHandle) {
        if (modelData == null || modelData.model == null || modelData.entityData == null || animHandle == 0) return;

        IMMDModel model = modelData.model;
        clearOverlayLayers(model);
        model.resetPhysics();
        modelData.entityData.invalidateStateLayers();
        model.transitionAnim(animHandle, 0, STAGE_TRANSITION_TIME);
        modelData.entityData.playCustomAnim = true;
        modelData.entityData.playStageAnim = true;
    }

    public static void resetModelAnimationState(MMDModelManager.Model modelData) {
        resetModelAnimationState(null, modelData);
    }

    public static void resetModelAnimationState(Player player, MMDModelManager.Model modelData) {
        if (modelData == null || modelData.model == null || modelData.entityData == null) return;

        IMMDModel model = modelData.model;
        modelData.entityData.playCustomAnim = false;
        modelData.entityData.playStageAnim = false;
        model.changeAnim(MMDAnimManager.GetAnimModel(model, "idle"), 0);
        clearOverlayLayers(model);
        model.resetPhysics();
        modelData.entityData.invalidateStateLayers();

        if (player instanceof AbstractClientPlayer clientPlayer) {
            AnimationStateManager.updateAnimationState(clientPlayer, modelData);
        }
    }

    private static void clearOverlayLayers(IMMDModel model) {
        model.setLayerLoop(1, true);
        model.changeAnim(0, 1);
        model.changeAnim(0, 2);
    }

    public static void onDisconnect() {
        StageAnimSyncHelper.onDisconnect();
        PendingAnimSignalCache.onDisconnect();
    }
}
