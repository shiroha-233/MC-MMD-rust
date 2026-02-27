package com.shiroha.mmdskin.renderer.render;

import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.renderer.camera.StageAudioPlayer;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import com.shiroha.mmdskin.renderer.animation.MMDAnimManager;
import com.shiroha.mmdskin.renderer.animation.PendingAnimSignalCache;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * 玩家动画控制辅助类
 * 负责自定义动画播放和物理重置
 */
public final class MmdSkinRendererPlayerHelper {

    private static final Logger logger = LogManager.getLogger();

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
        
        MMDModelManager.Model mwed = resolved.model();
        IMMDModel model = mwed.model;
        mwed.entityData.playCustomAnim = false;
        mwed.entityData.playStageAnim = false;
        model.changeAnim(MMDAnimManager.GetAnimModel(model, "idle"), 0);
        model.setLayerLoop(1, true);
        model.changeAnim(0, 1);
        model.changeAnim(0, 2);
        model.resetPhysics();
        mwed.entityData.invalidateStateLayers();
    }

    public static void CustomAnim(Player player, String id) {
        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
        if (resolved == null) return;
        
        MMDModelManager.Model mwed = resolved.model();
        IMMDModel model = mwed.model;
        mwed.entityData.playCustomAnim = true;
        // 标记脏状态，确保动画中断后 changeAnimationOnce 不会因状态相同而跳过
        mwed.entityData.invalidateStateLayers();
        model.changeAnim(MMDAnimManager.GetAnimModel(model, id), 0);
        model.setLayerLoop(1, true);
        model.changeAnim(0, 1);
        model.changeAnim(0, 2);
    }
    
    /**
     * 远程玩家舞台音频同步
     */
    public static void StageAudioPlay(Player player, String audioData) {
        if (player == null || audioData == null || audioData.isEmpty()) return;

        String[] parts = audioData.split("\\|");
        if (parts.length >= 2) {
            String packName = parts[0];
            String audioName = parts[1];
            // 路径安全校验（防止路径遍历攻击）
            if (!validatePathSafety(packName) || !validatePathSafety(audioName)) {
                logger.warn("[舞台同步] 不安全的音频路径: {}/{}", packName, audioName);
                return;
            }
            String audioPath = new File(PathConstants.getStageAnimDir(), packName + File.separator + audioName).getAbsolutePath();
            StageAudioPlayer.playRemoteAudio(player, audioPath);
        }
    }

    private static boolean validatePathSafety(String name) {
        return !name.contains("..") && !name.contains("/") && !name.contains("\\");
    }

    public static void onDisconnect() {
        StageAnimSyncHelper.onDisconnect();
        StageAudioPlayer.cleanupAll();
        PendingAnimSignalCache.onDisconnect();
    }
}
