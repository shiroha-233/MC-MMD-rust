package com.shiroha.mmdskin.stage.client.camera;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 文件职责：验证舞台相机播放会话提取后的纯运行时行为。 */
class StageCameraPlaybackSessionTest {
    @Test
    void shouldPreferDedicatedCameraAnimationWhenAvailable() {
        StageCameraPlaybackSession session = new StageCameraPlaybackSession();

        boolean hasCameraData = session.configureStagePlayback(
                10L,
                20L,
                30L,
                "miku",
                1.25f,
                new FakeAnimationPort(true, true, 120.0f, 240.0f)
        );

        assertTrue(hasCameraData);
        assertEquals(20L, session.cameraAnimHandle());
        assertEquals(10L, session.motionAnimHandle());
        assertEquals(30L, session.modelHandle());
        assertEquals("miku", session.modelName());
        assertEquals(240.0f, session.maxFrame());
        assertEquals(1.25f, session.cameraHeightOffset());
    }

    @Test
    void shouldFallbackToMotionFrameRangeWhenNoCameraTrackExists() {
        StageCameraPlaybackSession session = new StageCameraPlaybackSession();

        boolean hasCameraData = session.configureStagePlayback(
                10L,
                20L,
                0L,
                "rin",
                0.5f,
                new FakeAnimationPort(false, false, 96.0f, 240.0f)
        );

        assertFalse(hasCameraData);
        assertEquals(0L, session.cameraAnimHandle());
        assertEquals(96.0f, session.maxFrame());
        assertEquals("rin", session.modelName());
    }

    @Test
    void shouldAdvanceFramesAndResetSyncCounterAfterBroadcast() {
        StageCameraPlaybackSession session = new StageCameraPlaybackSession();
        session.configureStagePlayback(
                10L,
                20L,
                30L,
                "miku",
                0.0f,
                new FakeAnimationPort(true, true, 100.0f, 150.0f)
        );
        session.setPlaybackSpeed(1.0f);
        session.setTargetSyncFrame(25.0f);

        StagePlaybackAdvance advance = session.advance(1.0f, 30.0f, 1.15f, 0.85f, 2.0f);

        assertEquals(34.5f, advance.frame());
        assertEquals(34.5f, session.currentFrame());
        assertTrue(session.shouldBroadcastFrameSync(1));
        assertFalse(session.shouldBroadcastFrameSync(2));
        assertTrue(session.shouldBroadcastFrameSync(2));
    }

    private static final class FakeAnimationPort implements NativeAnimationPort {
        private final boolean motionHasCamera;
        private final boolean cameraHasCamera;
        private final float motionMaxFrame;
        private final float cameraMaxFrame;

        private FakeAnimationPort(boolean motionHasCamera, boolean cameraHasCamera, float motionMaxFrame, float cameraMaxFrame) {
            this.motionHasCamera = motionHasCamera;
            this.cameraHasCamera = cameraHasCamera;
            this.motionMaxFrame = motionMaxFrame;
            this.cameraMaxFrame = cameraMaxFrame;
        }

        @Override
        public long loadAnimation(long modelHandle, String animationPath) {
            return 0L;
        }

        @Override
        public void deleteAnimation(long animationHandle) {
        }

        @Override
        public void mergeAnimation(long mergedAnimationHandle, long sourceAnimationHandle) {
        }

        @Override
        public boolean hasCameraData(long animationHandle) {
            if (animationHandle == 20L) {
                return cameraHasCamera;
            }
            if (animationHandle == 10L) {
                return motionHasCamera;
            }
            return false;
        }

        @Override
        public boolean hasBoneData(long animationHandle) {
            return false;
        }

        @Override
        public boolean hasMorphData(long animationHandle) {
            return false;
        }

        @Override
        public float getAnimationMaxFrame(long animationHandle) {
            if (animationHandle == 20L) {
                return cameraMaxFrame;
            }
            if (animationHandle == 10L) {
                return motionMaxFrame;
            }
            return 0.0f;
        }

        @Override
        public void seekLayer(long modelHandle, long layer, float frame) {
        }

        @Override
        public void getCameraTransform(long animationHandle, float frame, java.nio.ByteBuffer targetBuffer) {
        }
    }
}
