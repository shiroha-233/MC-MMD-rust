package com.shiroha.mmdskin.renderer.integration.player;

import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.renderer.integration.entity.MobReplacementService;
import com.shiroha.mmdskin.renderer.performance.RenderPerformanceProfiler;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 文件职责：按距离和每帧预算决定玩家与世界实体的 MMD 渲染优先级。
 */
public final class PlayerPerformanceGate {
    private static final ConcurrentMap<Long, Long> lastAnimationUpdateFrameByModel = new ConcurrentHashMap<>();
    private static final Set<UUID> prioritizedVisibleEntities = new HashSet<>();
    private static final Set<UUID> prioritizedPhysicsEntities = new HashSet<>();
    private static long currentFrameKey = Long.MIN_VALUE;
    private static long currentFrameIndex;
    private static int frameSequence;

    private PlayerPerformanceGate() {
    }

    public static boolean allowsMmd(AbstractClientPlayer player) {
        beginWorldFrame();
        if (player == null) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getUUID().equals(player.getUUID())) {
            return true;
        }
        return prioritizedVisibleEntities.contains(player.getUUID());
    }

    public static boolean allowsMobReplacement(LivingEntity entity) {
        beginWorldFrame();
        if (entity == null) {
            return false;
        }
        return prioritizedVisibleEntities.contains(entity.getUUID());
    }

    public static boolean shouldUpdateAnimation(Entity entity, boolean localPlayer, long modelHandle) {
        beginWorldFrame();
        if (entity == null || modelHandle == 0L) {
            return true;
        }
        if (localPlayer) {
            lastAnimationUpdateFrameByModel.put(modelHandle, currentFrameIndex);
            return true;
        }

        double distanceSq = distanceSqToCamera(entity);
        int interval = resolveAnimationUpdateInterval(distanceSq);
        if (interval <= 1) {
            lastAnimationUpdateFrameByModel.put(modelHandle, currentFrameIndex);
            return true;
        }

        Long lastFrame = lastAnimationUpdateFrameByModel.get(modelHandle);
        if (lastFrame == null || currentFrameIndex - lastFrame >= interval) {
            lastAnimationUpdateFrameByModel.put(modelHandle, currentFrameIndex);
            return true;
        }
        return false;
    }

    public static boolean shouldEnablePhysics(Entity entity, boolean localPlayer) {
        beginWorldFrame();
        if (!ConfigManager.isPhysicsEnabled()) {
            return false;
        }
        if (entity == null || localPlayer) {
            return true;
        }
        return prioritizedPhysicsEntities.contains(entity.getUUID());
    }

    private static void beginWorldFrame() {
        long frameKey = computeFrameKey();
        if (frameKey == currentFrameKey) {
            return;
        }
        if (currentFrameKey != Long.MIN_VALUE) {
            RenderPerformanceProfiler.get().completeFrame(
                    prioritizedVisibleEntities.size(),
                    prioritizedPhysicsEntities.size());
        }
        currentFrameKey = frameKey;
        currentFrameIndex++;
        rebuildPrioritySets();
    }

    private static long computeFrameKey() {
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        return (gameTime << 32) ^ Integer.toUnsignedLong(frameSequence);
    }

    private static void rebuildPrioritySets() {
        prioritizedVisibleEntities.clear();
        prioritizedPhysicsEntities.clear();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        List<PrioritizedEntity> candidates = new ArrayList<>();
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (shouldConsiderPlayer(player)) {
                boolean localPlayer = minecraft.player != null && minecraft.player.getUUID().equals(player.getUUID());
                candidates.add(new PrioritizedEntity(player, distanceSqToCamera(player), localPlayer));
            }
        }

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && !(entity instanceof AbstractClientPlayer)) {
                if (MobReplacementService.getReplacementModelName(living) != null) {
                    candidates.add(new PrioritizedEntity(living, distanceSqToCamera(living), false));
                }
            }
        }

        candidates.sort(Comparator
                .comparing(PrioritizedEntity::localPlayer).reversed()
                .thenComparingDouble(PrioritizedEntity::distanceSq));

        int visibleCap = ConfigManager.getMaxVisibleModelsPerFrame();
        int physicsCap = ConfigManager.getMaxPhysicsModelsPerFrame();
        double physicsMaxDistance = ConfigManager.getPhysicsLodMaxDistance();
        double physicsMaxDistanceSq = physicsMaxDistance * physicsMaxDistance;

        for (PrioritizedEntity candidate : candidates) {
            UUID uuid = candidate.entity().getUUID();
            if (candidate.localPlayer() || visibleCap <= 0 || prioritizedVisibleEntities.size() < visibleCap) {
                prioritizedVisibleEntities.add(uuid);
            }

            if (!ConfigManager.isPhysicsEnabled()) {
                continue;
            }
            if (candidate.localPlayer()) {
                prioritizedPhysicsEntities.add(uuid);
                continue;
            }
            if (physicsMaxDistance > 0.0d && candidate.distanceSq() > physicsMaxDistanceSq) {
                continue;
            }
            if (physicsCap <= 0 || prioritizedPhysicsEntities.size() < physicsCap) {
                prioritizedPhysicsEntities.add(uuid);
            }
        }
    }

    private static double distanceSqToCamera(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity cameraEntity = minecraft.getCameraEntity();
        if (entity == null || cameraEntity == null) {
            return 0.0d;
        }
        return entity.distanceToSqr(cameraEntity);
    }

    private static boolean shouldConsiderPlayer(AbstractClientPlayer player) {
        if (player == null || player.isSpectator()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean localPlayer = minecraft.player != null && minecraft.player.getUUID().equals(player.getUUID());
        String selectedModel = PlayerModelSyncManager.getPlayerModel(player.getUUID(), player.getName().getString(), localPlayer);
        return selectedModel != null
                && !selectedModel.isBlank()
                && !UIConstants.DEFAULT_MODEL_NAME.equals(selectedModel);
    }

    private static int resolveAnimationUpdateInterval(double distanceSq) {
        double mediumDistance = ConfigManager.getAnimationLodMediumDistance();
        double farDistance = ConfigManager.getAnimationLodFarDistance();
        double mediumSq = mediumDistance * mediumDistance;
        double farSq = farDistance * farDistance;

        if (distanceSq <= mediumSq) {
            return 1;
        }
        if (distanceSq <= farSq) {
            return Math.max(1, ConfigManager.getAnimationLodMediumUpdateInterval());
        }
        return Math.max(1, ConfigManager.getAnimationLodFarUpdateInterval());
    }

    private static boolean isLocalPlayer(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && entity != null && minecraft.player.getUUID().equals(entity.getUUID());
    }

    private record PrioritizedEntity(Entity entity, double distanceSq, boolean localPlayer) {
    }

    public static void beginRenderFrame() {
        if (!com.shiroha.mmdskin.renderer.compat.IrisCompat.isRenderingShadows()) {
            frameSequence++;
        }
        beginWorldFrame();
    }
}
