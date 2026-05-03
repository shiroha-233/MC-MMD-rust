package com.shiroha.mmdskin.stage.client.camera;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationPort;
import com.shiroha.mmdskin.stage.client.DefaultStageFrameQueryPort;
import com.shiroha.mmdskin.stage.client.camera.port.StageCameraBroadcastPort;
import com.shiroha.mmdskin.stage.client.camera.port.StageCameraSessionPort;
import com.shiroha.mmdskin.stage.client.camera.port.StageCameraUiPort;
import com.shiroha.mmdskin.stage.client.camera.port.StagePlayerSyncPort;
import com.shiroha.mmdskin.stage.client.playback.port.PlayerStageAnimationPort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 文件职责：验证舞台相机控制器经由新 port 委托玩家同步与帧查询。 */
class MMDCameraControllerPortTest {
    @Test
    void shouldDelegateStagePresentationSyncToPlayerSyncPort() throws Exception {
        FakePlayerSyncPort playerSyncPort = new FakePlayerSyncPort();
        MMDCameraController controller = createController(playerSyncPort, NoopPlayerStageAnimationPort.INSTANCE, NoopAnimationPort.INSTANCE);

        invokePrivate(controller, "syncStagePresentation", 42.5f);

        assertEquals(42.5f, playerSyncPort.lastFrame);
        assertEquals(1, playerSyncPort.syncCalls);
    }

    @Test
    void shouldUsePlayerStageAnimationPortDuringForceCleanup() throws Exception {
        FakePlayerStageAnimationPort playerStageAnimationPort = new FakePlayerStageAnimationPort();
        MMDCameraController controller = createController(NoopStagePlayerSyncPort.INSTANCE, playerStageAnimationPort, NoopAnimationPort.INSTANCE);
        controller.setStateForTesting(StageCameraSessionStateMachine.StageState.PLAYING);

        invokePrivate(controller, "forceCleanupForWatch");

        assertEquals(1, playerStageAnimationPort.clearCalls);
        assertEquals(1, playerStageAnimationPort.restoreCalls);
    }

    @Test
    void shouldExposeControllerFrameThroughFrameQueryPort() throws Exception {
        MMDCameraController controller = createController(NoopStagePlayerSyncPort.INSTANCE, NoopPlayerStageAnimationPort.INSTANCE, NoopAnimationPort.INSTANCE);
        DefaultStageFrameQueryPort queryPort = new DefaultStageFrameQueryPort(() -> controller);

        controller.setStateForTesting(StageCameraSessionStateMachine.StageState.PLAYING);
        setPlaybackSessionField(controller, "currentFrame", 18.75f);

        assertTrue(queryPort.isStagePresentationActive());
        assertEquals(18.75f, queryPort.getCurrentFrame());

        controller.setStateForTesting(StageCameraSessionStateMachine.StageState.INACTIVE);
        assertFalse(queryPort.isStagePresentationActive());
    }

    @Test
    void shouldPrepareLocalModelThroughPlayerStageAnimationPortWhenWatchingMotion() {
        FakePlayerStageAnimationPort playerStageAnimationPort = new FakePlayerStageAnimationPort();
        MMDCameraController controller = createController(NoopStagePlayerSyncPort.INSTANCE, playerStageAnimationPort, new FakeAnimationPort(96.0f));
        controller.setStateForTesting(StageCameraSessionStateMachine.StageState.WATCHING);

        controller.setWatchMotion(77L, 42L, "miku");

        assertEquals(42L, playerStageAnimationPort.lastPreparedModelHandle);
        assertEquals(1, playerStageAnimationPort.prepareCalls);
        assertEquals(96.0f, controller.getMaxFrame());
    }

    private MMDCameraController createController(StagePlayerSyncPort playerSyncPort,
                                                 PlayerStageAnimationPort playerStageAnimationPort,
                                                 NativeAnimationPort animationPort) {
        return new MMDCameraController(
                NoopStageCameraSessionPort.INSTANCE,
                NoopStageCameraBroadcastPort.INSTANCE,
                NoopStageCameraUiPort.INSTANCE,
                playerSyncPort,
                playerStageAnimationPort,
                animationPort
        );
    }

    private void invokePrivate(MMDCameraController controller, String methodName, Object... args) throws Exception {
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = args[i] instanceof Float ? float.class : args[i].getClass();
        }
        Method method = MMDCameraController.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        method.invoke(controller, args);
    }

    private void setPlaybackSessionField(MMDCameraController controller, String name, Object value) throws Exception {
        Field sessionField = MMDCameraController.class.getDeclaredField("playbackSession");
        sessionField.setAccessible(true);
        Object session = sessionField.get(controller);

        Field field = session.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(session, value);
    }

    private enum NoopStageCameraSessionPort implements StageCameraSessionPort {
        INSTANCE;

        @Override
        public boolean isWatchingStage() {
            return false;
        }

        @Override
        public boolean isSessionHost() {
            return false;
        }

        @Override
        public UUID getSessionId() {
            return null;
        }

        @Override
        public UUID getHostPlayerId() {
            return null;
        }

        @Override
        public void stopWatching() {
        }

        @Override
        public void stopWatchingStageOnly() {
        }

        @Override
        public void notifyMembersStageEnd() {
        }

        @Override
        public void closeHostedSession() {
        }
    }

    private enum NoopStageCameraBroadcastPort implements StageCameraBroadcastPort {
        INSTANCE;

        @Override
        public void sendRemoteStageStop() {
        }

        @Override
        public void sendFrameSync(UUID sessionId, float frame) {
        }

        @Override
        public void sendLeave(UUID hostUUID, UUID sessionId) {
        }
    }

    private enum NoopStageCameraUiPort implements StageCameraUiPort {
        INSTANCE;

        @Override
        public void openStageSelection() {
        }
    }

    private enum NoopStagePlayerSyncPort implements StagePlayerSyncPort {
        INSTANCE;

        @Override
        public void syncStageFrame(float frame) {
        }

        @Override
        public void stopLocalStageAnimation() {
        }
    }

    private enum NoopPlayerStageAnimationPort implements PlayerStageAnimationPort {
        INSTANCE;

        @Override
        public void clearLocalStageFlags() {
        }

        @Override
        public void restoreLocalModelState() {
        }
    }

    private enum NoopAnimationPort implements NativeAnimationPort {
        INSTANCE;

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
            return 0.0f;
        }

        @Override
        public void seekLayer(long modelHandle, long layer, float frame) {
        }

        @Override
        public void getCameraTransform(long animationHandle, float frame, java.nio.ByteBuffer targetBuffer) {
        }
    }

    private static final class FakePlayerSyncPort implements StagePlayerSyncPort {
        private float lastFrame = Float.NaN;
        private int syncCalls;

        @Override
        public void syncStageFrame(float frame) {
            lastFrame = frame;
            syncCalls++;
        }

        @Override
        public void stopLocalStageAnimation() {
        }
    }

    private static final class FakePlayerStageAnimationPort implements PlayerStageAnimationPort {
        private long lastPreparedModelHandle;
        private int prepareCalls;
        private int clearCalls;
        private int restoreCalls;

        @Override
        public void prepareLocalModelForStage(long modelHandle) {
            lastPreparedModelHandle = modelHandle;
            prepareCalls++;
        }

        @Override
        public void clearLocalStageFlags() {
            clearCalls++;
        }

        @Override
        public void restoreLocalModelState() {
            restoreCalls++;
        }
    }

    private static final class FakeAnimationPort implements NativeAnimationPort {
        private final float maxFrame;

        private FakeAnimationPort(float maxFrame) {
            this.maxFrame = maxFrame;
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
            return maxFrame;
        }

        @Override
        public void seekLayer(long modelHandle, long layer, float frame) {
        }

        @Override
        public void getCameraTransform(long animationHandle, float frame, java.nio.ByteBuffer targetBuffer) {
        }
    }
}
