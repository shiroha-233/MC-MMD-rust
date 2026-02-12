package com.shiroha.mmdskin.renderer.render;

import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.renderer.camera.StageAudioPlayer;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import com.shiroha.mmdskin.renderer.animation.MMDAnimManager;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * 玩家动画控制辅助类 (SRP - 单一职责原则)
 * 
 * 仅负责玩家的自定义动画播放和物理重置。
 * 舞台动画同步委托给 {@link StageAnimSyncHelper}，
 * 表情同步委托给 {@link MorphSyncHelper}。
 */
public final class MmdSkinRendererPlayerHelper {

    private static final Logger logger = LogManager.getLogger();

    /**
     * 判断玩家是否正在使用 MMD 模型
     */
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

    /**
     * 重置玩家物理和动画状态
     */
    public static void ResetPhysics(Player player) {
        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
        if (resolved == null) return;
        
        MMDModelManager.Model mwed = resolved.model();
        IMMDModel model = mwed.model;
        mwed.entityData.playCustomAnim = false;
        model.changeAnim(MMDAnimManager.GetAnimModel(model, "idle"), 0);
        model.changeAnim(0, 1);
        model.changeAnim(0, 2);
        model.resetPhysics();
    }

    /**
     * 播放自定义动画
     */
    public static void CustomAnim(Player player, String id) {
        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
        if (resolved == null) return;
        
        MMDModelManager.Model mwed = resolved.model();
        IMMDModel model = mwed.model;
        mwed.entityData.playCustomAnim = true;
        model.changeAnim(MMDAnimManager.GetAnimModel(model, id), 0);
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
            logger.info("[舞台同步] 远程玩家 {} 舞台音频已开始: {}/{}", player.getName().getString(), packName, audioName);
        }
    }

    private static boolean validatePathSafety(String name) {
        return !name.contains("..") && !name.contains("/") && !name.contains("\\");
    }

    /**
     * 断线时清理所有远程同步状态
     * 由 Fabric/Forge 的 DISCONNECT 事件处理器调用
     */
    public static void onDisconnect() {
        StageAnimSyncHelper.onDisconnect();
        StageAudioPlayer.cleanupAll();
    }
}
