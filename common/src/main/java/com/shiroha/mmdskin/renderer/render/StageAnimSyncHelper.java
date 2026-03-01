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
    
    private static final Map<UUID, PendingStageAnim> pendingAnims = new ConcurrentHashMap<>();
    private static final int MAX_RETRY_TICKS = 100;
    
    private static final class PendingStageAnim {
        final UUID playerUUID;
        final String stageData;
        int ticksWaited;
        
        PendingStageAnim(UUID playerUUID, String stageData) {
            this.playerUUID = playerUUID;
            this.stageData = stageData;
            this.ticksWaited = 0;
        }
    }
    
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
            pendingAnims.put(player.getUUID(), new PendingStageAnim(player.getUUID(), stageData));
            logger.info("[舞台同步] 远程玩家 {} 模型加载中，已加入待处理队列", player.getName().getString());
            return;
        }
        
        pendingAnims.remove(player.getUUID());
        applyStageAnim(player.getUUID(), resolved, stageData, parts);
    }
    
    private static void applyStageAnim(UUID playerUUID, PlayerModelResolver.Result resolved,
                                        String stageData, String[] parts) {
        cleanupRemoteStageAnim(playerUUID);
        
        String packName = parts[0];
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
        mwed.model.resetPhysics();
        mwed.entityData.playCustomAnim = true;
        mwed.entityData.playStageAnim = true;
        mwed.entityData.invalidateStateLayers();
        
        List<Long> tracked = new CopyOnWriteArrayList<>();
        tracked.add(mergedAnim);
        remoteStageAnims.put(playerUUID, tracked);
        remoteStageModels.put(playerUUID, modelHandle);
    }
    
    public static void tickPending() {
        if (pendingAnims.isEmpty()) return;
        
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        
        var it = pendingAnims.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            PendingStageAnim pending = entry.getValue();
            pending.ticksWaited++;
            
            if (pending.ticksWaited > MAX_RETRY_TICKS) {
                logger.warn("[舞台同步] 玩家 {} 模型加载超时，放弃重试", pending.playerUUID);
                it.remove();
                continue;
            }
            
            Player player = mc.level.getPlayerByUUID(pending.playerUUID);
            if (player == null) {
                it.remove();
                continue;
            }
            
            PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
            if (resolved != null) {
                it.remove();
                String[] parts = pending.stageData.split("\\|");
                applyStageAnim(pending.playerUUID, resolved, pending.stageData, parts);
                logger.info("[舞台同步] 玩家 {} 模型加载完成，已应用舞台动画", player.getName().getString());
            }
        }
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
            mwed.model.resetPhysics();
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
        pendingAnims.clear();
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
