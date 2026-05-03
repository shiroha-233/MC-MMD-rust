package com.shiroha.mmdskin.stage.client.sync;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationPort;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.stage.client.StageClientRuntime;
import com.shiroha.mmdskin.stage.client.camera.port.StageFrameQueryPort;
import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 文件职责：同步远端舞台动画到本地玩家模型并管理临时动画句柄。 */
public final class StageAnimSyncHelper {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int MAX_RETRY_TICKS = 100;

    private final Map<UUID, List<Long>> remoteStageAnims = new ConcurrentHashMap<>();
    private final Map<UUID, Long> remoteStageModels = new ConcurrentHashMap<>();
    private final Map<UUID, PendingStageAnim> pendingAnims = new ConcurrentHashMap<>();
    private final StageFrameQueryPort frameQueryPort;
    private final NativeAnimationPort animationPort;

    private record PendingStageAnim(UUID playerUUID, StageDescriptor descriptor, int ticksWaited) {
        private PendingStageAnim nextTick() {
            return new PendingStageAnim(playerUUID, descriptor, ticksWaited + 1);
        }
    }

    public StageAnimSyncHelper(StageFrameQueryPort frameQueryPort, NativeAnimationPort animationPort) {
        this.frameQueryPort = Objects.requireNonNull(frameQueryPort, "frameQueryPort");
        this.animationPort = Objects.requireNonNull(animationPort, "animationPort");
    }

    public static StageAnimSyncHelper getInstance() {
        return StageClientRuntime.get().animSyncHelper();
    }

    public static void startStageAnim(Player player, StageDescriptor descriptor) {
        getInstance().start(player, descriptor);
    }

    public static void endStageAnim(Player player) {
        getInstance().end(player);
    }

    public static void endStageAnim(UUID playerUUID) {
        getInstance().end(playerUUID);
    }

    public static void syncAllRemoteStageFrame(float frame) {
        getInstance().syncRemoteFrame(frame);
    }

    public static void syncLocalStageFrame(float frame) {
        getInstance().syncLocalFrame(frame);
    }

    public static void onDisconnect() {
        getInstance().disconnect();
    }

    public void start(Player player, StageDescriptor descriptor) {
        if (player == null || descriptor == null || !descriptor.isValid()) {
            return;
        }

        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
        if (resolved == null) {
            pendingAnims.put(player.getUUID(), new PendingStageAnim(player.getUUID(), descriptor.copy(), 0));
            LOGGER.info("[StageSync] Player model still loading for {}", player.getName().getString());
            return;
        }

        pendingAnims.remove(player.getUUID());
        applyStageAnim(player.getUUID(), resolved, descriptor);
    }

    public void end(Player player) {
        if (player == null) {
            return;
        }
        end(player.getUUID());

        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
        if (resolved != null) {
            MmdSkinRendererPlayerHelper.resetModelAnimationState(player, resolved.model());
        }
    }

    public void end(UUID playerUUID) {
        if (playerUUID == null) {
            return;
        }
        pendingAnims.remove(playerUUID);
        cleanupRemoteStageAnim(playerUUID);
    }

    public void tickPending() {
        if (pendingAnims.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        var iterator = pendingAnims.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            PendingStageAnim pending = entry.getValue();
            PendingStageAnim next = pending.nextTick();
            if (next.ticksWaited() > MAX_RETRY_TICKS) {
                LOGGER.warn("[StageSync] Timed out waiting for player model {}", pending.playerUUID());
                iterator.remove();
                continue;
            }

            Player player = minecraft.level.getPlayerByUUID(pending.playerUUID());
            if (player == null) {
                iterator.remove();
                continue;
            }

            PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
            if (resolved != null) {
                iterator.remove();
                applyStageAnim(pending.playerUUID(), resolved, pending.descriptor());
            } else {
                pendingAnims.put(pending.playerUUID(), next);
            }
        }
    }

    public void syncRemoteFrame(float frame) {
        if (remoteStageModels.isEmpty()) {
            return;
        }
        for (Long modelHandle : remoteStageModels.values()) {
            if (modelHandle != null && modelHandle != 0L) {
                animationPort.seekLayer(modelHandle, 0, frame);
            }
        }
    }

    public void syncLocalFrame(float frame) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(minecraft.player);
        if (resolved == null || !resolved.model().entityState().playStageAnim) {
            return;
        }
        long modelHandle = resolved.model().modelInstance().getModelHandle();
        if (modelHandle != 0L) {
            animationPort.seekLayer(modelHandle, 0, frame);
        }
    }

    public void disconnect() {
        pendingAnims.clear();
        remoteStageModels.clear();
        if (remoteStageAnims.isEmpty()) {
            return;
        }
        for (List<Long> handles : remoteStageAnims.values()) {
            for (long handle : handles) {
                if (handle != 0L) {
                    animationPort.deleteAnimation(handle);
                }
            }
        }
        remoteStageAnims.clear();
    }

    private void applyStageAnim(UUID playerUUID, PlayerModelResolver.Result resolved, StageDescriptor descriptor) {
        cleanupRemoteStageAnim(playerUUID);

        File stageDir = new File(PathConstants.getStageAnimDir(), descriptor.getPackName());
        if (!stageDir.exists() || !stageDir.isDirectory()) {
            LOGGER.warn("[StageSync] Missing local stage pack {}", descriptor.getPackName());
            return;
        }

        long mergedAnim = loadAndMergeAnimations(stageDir, descriptor.getMotionFiles());
        if (mergedAnim == 0L) {
            return;
        }

        ManagedModel modelData = resolved.model();
        long modelHandle = modelData.modelInstance().getModelHandle();
        MmdSkinRendererPlayerHelper.startStageAnimation(modelData, mergedAnim);

        List<Long> tracked = new CopyOnWriteArrayList<>();
        tracked.add(mergedAnim);
        remoteStageAnims.put(playerUUID, tracked);
        remoteStageModels.put(playerUUID, modelHandle);

        if (frameQueryPort.isStagePresentationActive()) {
            animationPort.seekLayer(modelHandle, 0, frameQueryPort.getCurrentFrame());
        }
    }

    private long loadAndMergeAnimations(File stageDir, List<String> motionFiles) {
        if (motionFiles == null || motionFiles.isEmpty()) {
            return 0L;
        }

        List<Long> loadedAnimations = new ArrayList<>();
        String firstFile = new File(stageDir, motionFiles.get(0)).getAbsolutePath();
        long mergedAnimation = animationPort.loadAnimation(0, firstFile);
        if (mergedAnimation == 0L) {
            LOGGER.warn("[StageSync] Failed to load motion {}", firstFile);
            return 0L;
        }
        loadedAnimations.add(mergedAnimation);

        for (int i = 1; i < motionFiles.size(); i++) {
            String filePath = new File(stageDir, motionFiles.get(i)).getAbsolutePath();
            long tempAnimation = animationPort.loadAnimation(0, filePath);
            if (tempAnimation != 0L) {
                animationPort.mergeAnimation(mergedAnimation, tempAnimation);
                loadedAnimations.add(tempAnimation);
            }
        }

        for (int i = 1; i < loadedAnimations.size(); i++) {
            animationPort.deleteAnimation(loadedAnimations.get(i));
        }

        return mergedAnimation;
    }

    private void cleanupRemoteStageAnim(UUID playerUUID) {
        remoteStageModels.remove(playerUUID);
        List<Long> animations = remoteStageAnims.remove(playerUUID);
        if (animations == null) {
            return;
        }

        for (long handle : animations) {
            if (handle != 0L) {
                animationPort.deleteAnimation(handle);
            }
        }
    }
}
