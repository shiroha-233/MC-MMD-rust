package com.shiroha.mmdskin.renderer.render;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import com.shiroha.mmdskin.renderer.animation.MMDAnimManager;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import com.shiroha.mmdskin.renderer.camera.StageAudioPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MmdSkinRendererPlayerHelper {
    private static final Logger logger = LogManager.getLogger();
    
    // 远程玩家舞台动画句柄（用于断线时清理）
    private static final Map<UUID, List<Long>> remoteStageAnims = new ConcurrentHashMap<>();

    /**
     * 每帧更新远程玩家音频的音量衰减
     */
    public static void tick(Player player) {
        if (player == null) return;
        StageAudioPlayer.updateRemoteVolume(player);
    }

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

    MmdSkinRendererPlayerHelper() {
    }

    public static void ResetPhysics(Player player) {
        // 从同步管理器获取玩家选择的模型（支持联机）
        String playerName = player.getName().getString();
        Minecraft mc = Minecraft.getInstance();
        boolean isLocalPlayer = mc.player != null && mc.player.getUUID().equals(player.getUUID());
        String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), playerName, isLocalPlayer);
        
        // 如果是默认渲染，不处理
        if (selectedModel.equals(UIConstants.DEFAULT_MODEL_NAME) || selectedModel.isEmpty()) {
            return;
        }
        
        MMDModelManager.Model m = MMDModelManager.GetModel(selectedModel, playerName);
        if (m != null) {
            IMMDModel model = m.model;
            ((MMDModelManager.ModelWithEntityData) m).entityData.playCustomAnim = false;
            model.ChangeAnim(MMDAnimManager.GetAnimModel(model, "idle"), 0);
            model.ChangeAnim(0, 1);
            model.ChangeAnim(0, 2);
            model.ResetPhysics();
        }
    }

    public static void CustomAnim(Player player, String id) {
        // 从同步管理器获取玩家选择的模型（支持联机）
        String playerName = player.getName().getString();
        Minecraft mc = Minecraft.getInstance();
        boolean isLocalPlayer = mc.player != null && mc.player.getUUID().equals(player.getUUID());
        String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), playerName, isLocalPlayer);
        
        // 如果是默认渲染，不处理
        if (selectedModel.equals(UIConstants.DEFAULT_MODEL_NAME) || selectedModel.isEmpty()) {
            return;
        }
        
        MMDModelManager.Model m = MMDModelManager.GetModel(selectedModel, playerName);
        if (m != null) {
            MMDModelManager.ModelWithEntityData mwed = (MMDModelManager.ModelWithEntityData) m;
            IMMDModel model = m.model;
            mwed.entityData.playCustomAnim = true;
            // 直接使用文件名作为动画ID，MMDAnimManager 会自动在 CustomAnim 目录查找
            model.ChangeAnim(MMDAnimManager.GetAnimModel(model, id), 0);
            model.ChangeAnim(0, 1);
            model.ChangeAnim(0, 2);
        }
    }
    
    /**
     * 远程玩家舞台动画开始
     * 接收格式: "packName|file1.vmd|file2.vmd|..."
     */
    public static void StageAnimStart(Player player, String stageData) {
        if (player == null || stageData == null || stageData.isEmpty()) return;
        
        String playerName = player.getName().getString();
        Minecraft mc = Minecraft.getInstance();
        boolean isLocalPlayer = mc.player != null && mc.player.getUUID().equals(player.getUUID());
        String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), playerName, isLocalPlayer);
        
        if (selectedModel == null || selectedModel.equals(UIConstants.DEFAULT_MODEL_NAME) || selectedModel.isEmpty()) {
            return;
        }
        
        MMDModelManager.Model m = MMDModelManager.GetModel(selectedModel, playerName);
        if (m == null) return;
        
        NativeFunc nf = NativeFunc.GetInst();
        String[] parts = stageData.split("\\|");
        if (parts.length < 2) return;
        
        String packName = parts[0];
        
        // 加载并合并所有 VMD 动画
        long mergedAnim = 0;
        List<Long> loadedAnims = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String filePath = new File(PathConstants.getStageAnimDir(), packName + File.separator + parts[i]).getAbsolutePath();
            long tempAnim = nf.LoadAnimation(0, filePath);
            if (tempAnim != 0) {
                if (mergedAnim == 0) {
                    mergedAnim = tempAnim;
                } else {
                    nf.MergeAnimation(mergedAnim, tempAnim);
                }
                loadedAnims.add(tempAnim);
            }
        }
        
        if (mergedAnim == 0) return;
        
        // 释放临时句柄（保留 mergedAnim）
        for (int i = 1; i < loadedAnims.size(); i++) {
            nf.DeleteAnimation(loadedAnims.get(i));
        }
        
        // 应用到远程玩家模型
        MMDModelManager.ModelWithEntityData mwed = (MMDModelManager.ModelWithEntityData) m;
        long modelHandle = m.model.GetModelLong();
        nf.TransitionLayerTo(modelHandle, 0, mergedAnim, 0.3f);
        m.model.ChangeAnim(0, 1);
        m.model.ChangeAnim(0, 2);
        mwed.entityData.playCustomAnim = true;
        mwed.entityData.playStageAnim = true;
        
        // 记录句柄用于后续清理
        List<Long> tracked = new ArrayList<>();
        tracked.add(mergedAnim);
        remoteStageAnims.put(player.getUUID(), tracked);
        
        logger.info("[舞台同步] 远程玩家 {} 舞台动画已应用: {} ({}个VMD)",
                    playerName, packName, parts.length - 1);
    }
    
    /**
     * 远程玩家舞台动画结束
     */
    public static void StageAnimEnd(Player player) {
        if (player == null) return;
        
        UUID uuid = player.getUUID();
        cleanupRemoteStageAnim(uuid);
        
        // 恢复正常动画状态
        String playerName = player.getName().getString();
        Minecraft mc = Minecraft.getInstance();
        boolean isLocalPlayer = mc.player != null && mc.player.getUUID().equals(uuid);
        String selectedModel = PlayerModelSyncManager.getPlayerModel(uuid, playerName, isLocalPlayer);
        
        if (selectedModel == null || selectedModel.equals(UIConstants.DEFAULT_MODEL_NAME) || selectedModel.isEmpty()) {
            return;
        }
        
        MMDModelManager.Model m = MMDModelManager.GetModel(selectedModel, playerName);
        if (m != null) {
            MMDModelManager.ModelWithEntityData mwed = (MMDModelManager.ModelWithEntityData) m;
            mwed.entityData.playCustomAnim = false;
            mwed.entityData.playStageAnim = false;
        }
        
        logger.info("[舞台同步] 远程玩家 {} 舞台动画结束", playerName);
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
            String audioPath = new File(PathConstants.getStageAnimDir(), packName + File.separator + audioName).getAbsolutePath();
            StageAudioPlayer.playRemoteAudio(player, audioPath);
            logger.info("[舞台同步] 远程玩家 {} 舞台音频已开始: {}/{}", player.getName().getString(), packName, audioName);
        }
    }
    
    /**
     * 远程玩家表情同步
     * @param player 远程玩家
     * @param morphName 表情名称（"__reset__" 表示重置所有表情）
     */
    public static void RemoteMorph(Player player, String morphName) {
        if (player == null || morphName == null || morphName.isEmpty()) return;
        
        String playerName = player.getName().getString();
        Minecraft mc = Minecraft.getInstance();
        boolean isLocalPlayer = mc.player != null && mc.player.getUUID().equals(player.getUUID());
        String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), playerName, isLocalPlayer);
        
        if (selectedModel == null || selectedModel.equals(UIConstants.DEFAULT_MODEL_NAME) || selectedModel.isEmpty()) {
            return;
        }
        
        MMDModelManager.Model m = MMDModelManager.GetModel(selectedModel, playerName);
        if (m == null) return;
        
        long modelHandle = m.model.GetModelLong();
        NativeFunc nf = NativeFunc.GetInst();
        
        if ("__reset__".equals(morphName)) {
            nf.ResetAllMorphs(modelHandle);
            logger.info("[表情同步] 远程玩家 {} 重置所有表情", playerName);
        } else {
            String vpdPath = findVpdFile(morphName, selectedModel);
            if (vpdPath != null) {
                int result = nf.ApplyVpdMorph(modelHandle, vpdPath);
                if (result >= 0) {
                    logger.info("[表情同步] 远程玩家 {} 应用表情: {}", playerName, morphName);
                } else {
                    logger.warn("[表情同步] 远程玩家 {} 表情应用失败: {} ({})", playerName, morphName, result);
                }
            } else {
                logger.warn("[表情同步] 本地未找到表情文件: {}", morphName);
            }
        }
    }
    
    /**
     * 断线时清理所有远程玩家的舞台动画句柄
     */
    public static void onDisconnect() {
        if (remoteStageAnims.isEmpty()) return;
        NativeFunc nf = NativeFunc.GetInst();
        int count = 0;
        for (Map.Entry<UUID, List<Long>> entry : remoteStageAnims.entrySet()) {
            for (long handle : entry.getValue()) {
                if (handle != 0) {
                    nf.DeleteAnimation(handle);
                    count++;
                }
            }
        }
        remoteStageAnims.clear();
        logger.info("[舞台同步] 断线清理: 释放 {} 个远程舞台动画句柄", count);
    }
    
    /**
     * 按优先级在已知目录中查找 VPD 文件
     */
    private static String findVpdFile(String morphName, String modelName) {
        String path = PathConstants.getCustomMorphPath(morphName);
        if (new File(path).exists()) return path;
        
        path = PathConstants.getDefaultMorphPath(morphName);
        if (new File(path).exists()) return path;
        
        if (modelName != null && !modelName.isEmpty()) {
            path = PathConstants.getModelMorphPath(modelName, morphName);
            if (new File(path).exists()) return path;
        }
        
        return null;
    }
    
    /**
     * 清理远程玩家的舞台动画句柄
     */
    private static void cleanupRemoteStageAnim(UUID playerUUID) {
        List<Long> anims = remoteStageAnims.remove(playerUUID);
        if (anims != null) {
            NativeFunc nf = NativeFunc.GetInst();
            for (long handle : anims) {
                if (handle != 0) {
                    nf.DeleteAnimation(handle);
                }
            }
        }
    }
}
