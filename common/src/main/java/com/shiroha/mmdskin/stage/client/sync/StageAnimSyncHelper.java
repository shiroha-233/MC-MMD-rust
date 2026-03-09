package com.shiroha.mmdskin.stage.client.sync;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;
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

public final class StageAnimSyncHelper {

    private static final Logger logger = LogManager.getLogger();

    private static final Map<UUID, List<Long>> remoteStageAnims = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> remoteStageModels = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingStageAnim> pendingAnims = new ConcurrentHashMap<>();
    private static final int MAX_RETRY_TICKS = 100;

    private record PendingStageAnim(UUID playerUUID, StageDescriptor descriptor, int ticksWaited) {
        private PendingStageAnim nextTick() {
            return new PendingStageAnim(playerUUID, descriptor, ticksWaited + 1);
        }
    }

    private StageAnimSyncHelper() {
    }

    public static void startStageAnim(Player player, StageDescriptor descriptor) {
        if (player == null || descriptor == null || !descriptor.isValid()) {
            return;
        }

        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
        if (resolved == null) {
            pendingAnims.put(player.getUUID(), new PendingStageAnim(player.getUUID(), descriptor.copy(), 0));
            logger.info("[舞台同步] 远程玩家 {} 模型加载中，已加入待处理队列", player.getName().getString());
            return;
        }

        pendingAnims.remove(player.getUUID());
        applyStageAnim(player.getUUID(), resolved, descriptor);
    }

    public static void endStageAnim(Player player) {
        if (player == null) {
            return;
        }
        endStageAnim(player.getUUID());

        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
        if (resolved != null) {
            MmdSkinRendererPlayerHelper.resetModelAnimationState(player, resolved.model());
        }
    }

    public static void endStageAnim(UUID playerUUID) {
        if (playerUUID == null) {
            return;
        }
        pendingAnims.remove(playerUUID);
        cleanupRemoteStageAnim(playerUUID);
    }

    public static void tickPending() {
        if (pendingAnims.isEmpty()) {
            return;
        }

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        var it = pendingAnims.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            PendingStageAnim pending = entry.getValue();
            PendingStageAnim next = pending.nextTick();
            if (next.ticksWaited() > MAX_RETRY_TICKS) {
                logger.warn("[舞台同步] 玩家 {} 模型加载超时，放弃重试", pending.playerUUID());
                it.remove();
                continue;
            }

            Player player = mc.level.getPlayerByUUID(pending.playerUUID());
            if (player == null) {
                it.remove();
                continue;
            }

            PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
            if (resolved != null) {
                it.remove();
                applyStageAnim(pending.playerUUID(), resolved, pending.descriptor());
                logger.info("[舞台同步] 玩家 {} 模型加载完成，已应用舞台动画", player.getName().getString());
            } else {
                pendingAnims.put(pending.playerUUID(), next);
            }
        }
    }

    public static void syncAllRemoteStageFrame(float frame) {
        if (remoteStageModels.isEmpty()) {
            return;
        }
        NativeFunc nf = NativeFunc.GetInst();
        for (Long modelHandle : remoteStageModels.values()) {
            if (modelHandle != 0) {
                nf.SeekLayer(modelHandle, 0, frame);
            }
        }
    }

    public static void syncLocalStageFrame(float frame) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(mc.player);
        if (resolved == null || !resolved.model().entityData.playStageAnim) {
            return;
        }
        long modelHandle = resolved.model().model.getModelHandle();
        if (modelHandle != 0) {
            NativeFunc.GetInst().SeekLayer(modelHandle, 0, frame);
        }
    }

    public static void onDisconnect() {
        pendingAnims.clear();
        remoteStageModels.clear();
        if (remoteStageAnims.isEmpty()) {
            return;
        }
        NativeFunc nf = NativeFunc.GetInst();
        for (List<Long> handles : remoteStageAnims.values()) {
            for (long handle : handles) {
                if (handle != 0) {
                    nf.DeleteAnimation(handle);
                }
            }
        }
        remoteStageAnims.clear();
    }

    private static void applyStageAnim(UUID playerUUID, PlayerModelResolver.Result resolved, StageDescriptor descriptor) {
        cleanupRemoteStageAnim(playerUUID);

        File stageDir = new File(PathConstants.getStageAnimDir(), descriptor.getPackName());
        if (!stageDir.exists() || !stageDir.isDirectory()) {
            logger.warn("[舞台同步] 本地没有舞台包: {}", descriptor.getPackName());
            return;
        }

        long mergedAnim = loadAndMergeAnimations(stageDir, descriptor.getMotionFiles());
        if (mergedAnim == 0) {
            return;
        }

        MMDModelManager.Model modelData = resolved.model();
        NativeFunc nf = NativeFunc.GetInst();
        long modelHandle = modelData.model.getModelHandle();
        MmdSkinRendererPlayerHelper.startStageAnimation(modelData, mergedAnim);

        List<Long> tracked = new CopyOnWriteArrayList<>();
        tracked.add(mergedAnim);
        remoteStageAnims.put(playerUUID, tracked);
        remoteStageModels.put(playerUUID, modelHandle);

        MMDCameraController controller = MMDCameraController.getInstance();
        if (controller.isActive()) {
            nf.SeekLayer(modelHandle, 0, controller.getCurrentFrame());
        }
    }

    private static long loadAndMergeAnimations(File stageDir, List<String> motionFiles) {
        if (motionFiles == null || motionFiles.isEmpty()) {
            return 0;
        }

        NativeFunc nf = NativeFunc.GetInst();
        List<Long> loadedAnims = new ArrayList<>();

        String firstFile = new File(stageDir, motionFiles.get(0)).getAbsolutePath();
        long mergedAnim = nf.LoadAnimation(0, firstFile);
        if (mergedAnim == 0) {
            logger.warn("[舞台同步] VMD 加载失败: {}", firstFile);
            return 0;
        }
        loadedAnims.add(mergedAnim);

        for (int i = 1; i < motionFiles.size(); i++) {
            String filePath = new File(stageDir, motionFiles.get(i)).getAbsolutePath();
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
