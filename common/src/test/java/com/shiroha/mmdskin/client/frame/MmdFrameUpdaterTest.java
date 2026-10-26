// 负责验证模型在同一帧只推进一次且新帧恢复推进。
package com.shiroha.mmdskin.client.frame;

import com.shiroha.mmdskin.client.animation.MmdAnimationIntent;
import com.shiroha.mmdskin.client.model.ModelKey;
import com.shiroha.mmdskin.config.ConfigManager;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MmdFrameUpdaterTest {
    @Test
    void shouldUpdateEachTargetAtMostOncePerFrame() {
        MmdFrameUpdater updater = new MmdFrameUpdater();
        AtomicInteger updates = new AtomicInteger();
        FrameUpdateTarget target = (snapshot, deltaSeconds) -> updates.incrementAndGet();
        MmdRenderSnapshot snapshot = snapshot(2.0D);

        assertTrue(updater.updateOnce(10L, target, snapshot, 0.05F));
        assertFalse(updater.updateOnce(10L, target, snapshot, 0.05F));
        assertTrue(updater.updateOnce(11L, target, snapshot, 0.05F));
        assertEquals(2, updates.get());
    }

    @Test
    void shouldPrepareAnimationAndMotionBeforeNativeUpdateOnlyOnce() {
        List<String> calls = new ArrayList<>();
        MmdFrameUpdater updater = new MmdFrameUpdater(
                (target, snapshot) -> calls.add("prepare"));
        FrameUpdateTarget target = (snapshot, deltaSeconds) -> calls.add("update");
        MmdRenderSnapshot snapshot = snapshot(1.0D);

        updater.updateOnce(7L, target, snapshot, 0.05F);
        updater.updateOnce(7L, target, snapshot, 0.05F);

        assertEquals(List.of("prepare", "update"), calls);
    }

    /**
     * 远处模型降频推进，但跳过的帧必须把 delta 一次性补上：
     * 动画要"降频"而不是"变慢"（远处动作明显滞后是最容易踩的坑）。
     */
    @Test
    void shouldSkipDistantFramesButAccumulateDelta() {
        int interval = Math.max(1, ConfigManager.getAnimationLodFarUpdateInterval());
        double distanceSquared = Math.pow(Math.max(ConfigManager.getAnimationLodFarDistance(),
                ConfigManager.getAnimationLodMediumDistance()) + 10.0D, 2.0D);
        MmdFrameUpdater updater = new MmdFrameUpdater();
        List<Float> deltas = new ArrayList<>();
        FrameUpdateTarget target = (snapshot, deltaSeconds) -> deltas.add(deltaSeconds);
        MmdRenderSnapshot snapshot = snapshot(distanceSquared);

        for (int frame = 1; frame < interval; frame++) {
            assertFalse(updater.updateOnce(frame, target, snapshot, 0.05F, true));
        }
        assertTrue(updater.updateOnce(interval, target, snapshot, 0.05F, true));

        assertEquals(1, deltas.size());
        assertEquals(0.05F * interval, deltas.get(0), 1.0E-5F);
    }

    @Test
    void shouldAdvanceNearModelEveryFrame() {
        MmdFrameUpdater updater = new MmdFrameUpdater();
        List<Float> deltas = new ArrayList<>();
        FrameUpdateTarget target = (snapshot, deltaSeconds) -> deltas.add(deltaSeconds);
        // 距离 0 的模型必然落在最近档（animationLodMediumDistance 以内）。
        MmdRenderSnapshot snapshot = snapshot(0.0D);

        assertTrue(updater.updateOnce(1L, target, snapshot, 0.05F, true));
        assertTrue(updater.updateOnce(2L, target, snapshot, 0.05F, true));

        assertEquals(List.of(0.05F, 0.05F), deltas);
    }

    public static MmdRenderSnapshot snapshot(double distanceSquared) {
        return new MmdRenderSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new ModelKey("test", "owner", ModelKey.Usage.ENTITY),
                MmdEntityPose.standing(),
                MmdAnimationIntent.none(),
                MmdModelMotion.stationary(),
                ModelTransform.identity(),
                MmdRenderSnapshot.Visibility.OPAQUE,
                false,
                0,
                distanceSquared,
                MmdRenderSnapshot.Context.WORLD);
    }
}
