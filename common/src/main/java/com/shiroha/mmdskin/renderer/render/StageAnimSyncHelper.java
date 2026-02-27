package com.shiroha.mmdskin.renderer.render;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 舞台动画远程同步
 */
public final class StageAnimSyncHelper {
    
    private static final Logger logger = LogManager.getLogger();
    
    private static final Map<UUID, List<Long>> remoteStageAnims = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> remoteStageModels = new ConcurrentHashMap<>();
    
    private StageAnimSyncHelper() {
    }
    
    public static void startStageAnim(Player player, String stageData) {
        if (stageData == null || stageData.isEmpty()) return;
        
        String[] parts = stageData.split("\\|");
        if (parts.length < 2) {
            logger.warn("[舞台同步] 无效的舞台数据: {}", stageData);
            return;
        }
        
        String packName = parts[0];
        
        if (!validatePathSafety(packName)) return;
        for (int i = 1; i < parts.length; i++) {
            if (!validatePathSafety(parts[i])) return;
        }
        
        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
        if (resolved == null) {
            logger.warn("[舞台同步] 远程玩家 {} 没有 MMD 模型", player.getName().getString());
            return;
        }
        
        cleanupRemoteStageAnim(player.getUUID());
        
        File stageDir = new File(PathConstants.getStageAnimDir(), packName);
        if (!stageDir.exists() || !stageDir.isDirectory()) {
            logger.warn("[舞台同步] 本地没有舞台包: {}", packName);
            return;
        }
        
        long mergedAnim = loadAndMergeAnimations(stageDir, parts);
        if (mergedAnim == 0) return;
        
        MMDModelManager.Model mwed = resolved.model();
        NativeFunc nf = NativeFunc.GetInst();
        long modelHandle = mwed.model.getModelHandle();
        nf.TransitionLayerTo(modelHandle, 0, mergedAnim, 0.3f);
        mwed.model.setLayerLoop(1, true);
        mwed.model.changeAnim(0, 1);
        mwed.model.changeAnim(0, 2);
        mwed.entityData.playCustomAnim = true;
        mwed.entityData.playStageAnim = true;
        mwed.entityData.invalidateStateLayers();
        
        List<Long> tracked = new CopyOnWriteArrayList<>();
        tracked.add(mergedAnim);
        remoteStageAnims.put(player.getUUID(), tracked);
        remoteStageModels.put(player.getUUID(), modelHandle);
    }
    
    public static void endStageAnim(Player player) {
        if (player == null) return;
        
        UUID uuid = player.getUUID();
        cleanupRemoteStageAnim(uuid);
        
        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
        if (resolved != null) {
            MMDModelManager.Model mwed = resolved.model();
            mwed.entityData.playCustomAnim = false;
            mwed.entityData.playStageAnim = false;
            mwed.model.changeAnim(
                com.shiroha.mmdskin.renderer.animation.MMDAnimManager.GetAnimModel(mwed.model, "idle"), 0);
            mwed.model.setLayerLoop(1, true);
            mwed.model.changeAnim(0, 1);
            mwed.model.changeAnim(0, 2);
            mwed.entityData.invalidateStateLayers();
        }
    }
    
    public static void syncAllRemoteStageFrame(float frame) {
        if (remoteStageModels.isEmpty()) return;
        NativeFunc nf = NativeFunc.GetInst();
        for (Long modelHandle : remoteStageModels.values()) {
            if (modelHandle != 0) {
                nf.SeekLayer(modelHandle, 0, frame);
            }
        }
    }

    public static void syncLocalStageFrame(float frame) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(mc.player);
        if (resolved == null || !resolved.model().entityData.playStageAnim) return;
        long modelHandle = resolved.model().model.getModelHandle();
        if (modelHandle != 0) {
            NativeFunc.GetInst().SeekLayer(modelHandle, 0, frame);
        }
    }

    public static void onDisconnect() {
        remoteStageModels.clear();
        if (remoteStageAnims.isEmpty()) return;
        NativeFunc nf = NativeFunc.GetInst();
        for (List<Long> handles : remoteStageAnims.values()) {
            for (long handle : handles) {
                if (handle != 0) nf.DeleteAnimation(handle);
            }
        }
        remoteStageAnims.clear();
    }
    
    private static boolean validatePathSafety(String name) {
        return !name.contains("..") && !name.contains("/") && !name.contains("\\");
    }
    
    private static long loadAndMergeAnimations(File stageDir, String[] parts) {
        NativeFunc nf = NativeFunc.GetInst();
        List<Long> loadedAnims = new ArrayList<>();
        
        String firstFile = new File(stageDir, parts[1]).getAbsolutePath();
        long mergedAnim = nf.LoadAnimation(0, firstFile);
        if (mergedAnim == 0) {
            logger.warn("[舞台同步] VMD 加载失败: {}", firstFile);
            return 0;
        }
        loadedAnims.add(mergedAnim);
        
        for (int i = 2; i < parts.length; i++) {
            String filePath = new File(stageDir, parts[i]).getAbsolutePath();
            long tempAnim = nf.LoadAnimation(0, filePath);
            if (tempAnim != 0) {
                nf.MergeAnimation(mergedAnim, tempAnim);
                loadedAnims.add(tempAnim);
            }
        }
        
        for (int i = 1; i < loadedAnims.size(); i++) {
            nf.DeleteAnimation(loadedAnims.get(i));
        }
        
        return mergedAnim;
    }
    
    private static void cleanupRemoteStageAnim(UUID playerUUID) {
        remoteStageModels.remove(playerUUID);
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
