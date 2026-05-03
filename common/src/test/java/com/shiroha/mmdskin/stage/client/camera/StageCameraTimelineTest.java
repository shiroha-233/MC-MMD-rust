package com.shiroha.mmdskin.stage.client.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 文件职责：验证舞台相机时间线与状态迁移的纯计算行为。 */
class StageCameraTimelineTest {
    @Test
    void shouldUseCatchupSpeedWhenFrameDriftExceedsTolerance() {
        StagePlaybackAdvance advance = StageCameraTimeline.advanceFrame(
                10.0f,
                20.0f,
                1.0f,
                1.0f,
                200.0f,
                30.0f,
                1.15f,
                0.85f,
                2.0f
        );

        assertEquals(44.5f, advance.frame());
        assertEquals(1.15f, advance.effectiveSpeed());
        assertEquals(20.0f, advance.targetSyncFrame());
        assertFalse(advance.completed());
    }

    @Test
    void shouldClearSyncTargetWhenDriftFallsWithinTolerance() {
        StagePlaybackAdvance advance = StageCameraTimeline.advanceFrame(
                10.0f,
                11.0f,
                1.0f,
                0.5f,
                200.0f,
                30.0f,
                1.15f,
                0.85f,
                2.0f
        );

        assertEquals(25.0f, advance.frame());
        assertEquals(-1.0f, advance.targetSyncFrame());
        assertEquals(1.0f, advance.effectiveSpeed());
    }

    @Test
    void shouldInterpolateIntroTowardsStandbyPose() {
        StageCameraPose start = new StageCameraPose(0.0, 0.0, 0.0, 0.0f, 10.0f, 0.0f, 90.0f);
        StageCameraPose end = new StageCameraPose(10.0, 4.0, -2.0, 20.0f, 190.0f, 0.0f, 70.0f);

        StageCameraPose pose = StageCameraTimeline.interpolateIntro(start, end, 1.0f, 2.0f);

        assertTrue(pose.x() > 8.0);
        assertTrue(pose.y() > 3.0);
        assertTrue(pose.fov() < 75.0f);
        assertEquals(0.0f, pose.roll());
    }

    @Test
    void shouldExposeInputBlockingAndPinningRulesThroughStateMachine() {
        StageCameraSessionStateMachine machine = new StageCameraSessionStateMachine();

        assertTrue(machine.canEnterStageMode());
        assertFalse(machine.shouldBlockInput());

        machine.enterIntro();
        assertTrue(machine.canStartStage());
        assertTrue(machine.shouldPinPlayer());
        assertTrue(machine.shouldBlockInput());

        machine.enterStandby();
        assertTrue(machine.canStartStage());

        machine.enterWatching();
        assertFalse(machine.shouldPinPlayer());
        assertTrue(machine.shouldBlockInput());
        assertTrue(machine.isWatching());
    }
}
