package com.shiroha.mmdskin.stage.client.camera;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationPort;
import com.shiroha.mmdskin.config.StageConfig;
import com.shiroha.mmdskin.stage.client.StageClientRuntime;
import com.shiroha.mmdskin.stage.client.camera.port.StageCameraBroadcastPort;
import com.shiroha.mmdskin.stage.client.camera.port.StageCameraSessionPort;
import com.shiroha.mmdskin.stage.client.camera.port.StageCameraUiPort;
import com.shiroha.mmdskin.stage.client.camera.port.StagePlayerSyncPort;
import com.shiroha.mmdskin.stage.client.playback.port.PlayerStageAnimationPort;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.UUID;

/** 文件职责：协调舞台模式下的相机、音频与观演状态。 */
public final class MMDCameraController {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final float MMD_TO_MC_SCALE = 0.09f;
    private static final float VMD_FPS = 30.0f;
    private static final long DOUBLE_ESC_WINDOW_NS = 600_000_000L;
    private static final int SYNC_INTERVAL_FRAMES = 60;
    private static final float CATCHUP_SPEED_MAX = 1.15f;
    private static final float CATCHUP_SPEED_MIN = 0.85f;
    private static final float SYNC_TOLERANCE = 2.0f;
    private static final float INTRO_DURATION = 2.0f;
    private static final float OUTRO_DURATION = 2.5f;

    private final StageCameraSessionStateMachine stateMachine = new StageCameraSessionStateMachine();
    private final StageCameraPlaybackSession playbackSession = new StageCameraPlaybackSession();
    private final StageAudioPlayer audioPlayer = new StageAudioPlayer();
    private final MMDCameraData cameraData = new MMDCameraData();
    private final StageCameraEnvironmentController environmentController = new StageCameraEnvironmentController();

    private final StageCameraSessionPort sessionPort;
    private final StageCameraBroadcastPort broadcastPort;
    private final StageCameraUiPort uiPort;
    private final StagePlayerSyncPort playerSyncPort;
    private final PlayerStageAnimationPort playerStageAnimationPort;
    private final NativeAnimationPort animationPort;

    private boolean cinematicMode;

    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private float anchorYaw;

    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private float cameraPitch;
    private float cameraYaw;
    private float cameraRoll;
    private float cameraFov = 70.0f;

    private long lastTickTimeNs;
    private boolean escWasPressed;
    private long lastEscTimeNs;

    private UUID watchingHostUUID;
    private boolean waitingForHost;

    private float introElapsed;
    private StageCameraPose introStartPose = new StageCameraPose(0.0, 0.0, 0.0, 0.0f, 0.0f, 0.0f, 70.0f);
    private StageCameraPose standbyPose = new StageCameraPose(0.0, 0.0, 0.0, 15.0f, 180.0f, 0.0f, 70.0f);
    private float outroElapsed;
    private StageCameraPose outroStartPose = new StageCameraPose(0.0, 0.0, 0.0, 0.0f, 0.0f, 0.0f, 70.0f);
    private boolean outroIsGuest;

    public MMDCameraController(StageCameraSessionPort sessionPort,
                               StageCameraBroadcastPort broadcastPort,
                               StageCameraUiPort uiPort,
                               StagePlayerSyncPort playerSyncPort,
                               PlayerStageAnimationPort playerStageAnimationPort,
                               NativeAnimationPort animationPort) {
        this.sessionPort = Objects.requireNonNull(sessionPort, "sessionPort");
        this.broadcastPort = Objects.requireNonNull(broadcastPort, "broadcastPort");
        this.uiPort = Objects.requireNonNull(uiPort, "uiPort");
        this.playerSyncPort = Objects.requireNonNull(playerSyncPort, "playerSyncPort");
        this.playerStageAnimationPort = Objects.requireNonNull(playerStageAnimationPort, "playerStageAnimationPort");
        this.animationPort = Objects.requireNonNull(animationPort, "animationPort");
        this.cameraData.setAnimationPort(animationPort);
    }

    public static MMDCameraController getInstance() {
        return StageClientRuntime.get().cameraController();
    }

    public void enterStageMode() {
        if (!stateMachine.canEnterStageMode()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        environmentController.enterStageCameraMode(minecraft);

        if (minecraft.player != null) {
            anchorX = minecraft.player.getX();
            anchorY = minecraft.player.getY();
            anchorZ = minecraft.player.getZ();
            anchorYaw = minecraft.player.getYRot();
            minecraft.player.setXRot(0.0f);
            minecraft.player.setYRot(anchorYaw);
            minecraft.player.yHeadRot = anchorYaw;
            minecraft.player.yBodyRot = anchorYaw;
        }

        computeIntroAndStandby(minecraft);
        introElapsed = 0.0f;
        lastTickTimeNs = System.nanoTime();
        escWasPressed = false;
        lastEscTimeNs = 0L;
        environmentController.resetStageInputState();
        if (sessionPort.getHostPlayerId() == null) {
            waitingForHost = false;
        }
        stateMachine.enterIntro();
        applyPose(introStartPose);
    }

    public boolean startStage(long motionAnim, long cameraAnim, boolean cinematic,
                              long modelHandle, String modelName, String audioPath, float heightOffset) {
        if (!stateMachine.canStartStage()) {
            return false;
        }

        boolean hasCameraData = playbackSession.configureStagePlayback(
                motionAnim,
                cameraAnim,
                modelHandle,
                modelName,
                heightOffset,
                animationPort
        );
        if (!hasCameraData) {
            LOGGER.warn("[舞台模式] 没有可用的相机数据，将以无相机模式继续");
        } else {
            cameraData.setAnimHandle(playbackSession.cameraAnimHandle());
        }

        cinematicMode = cinematic;
        if (cinematic) {
            environmentController.enterCinematicMode(Minecraft.getInstance());
        }

        if (modelHandle != 0L) {
            playerStageAnimationPort.prepareLocalModelForStage(modelHandle);
        }

        if (audioPath != null && !audioPath.isEmpty()) {
            if (audioPlayer.load(audioPath)) {
                audioPlayer.play();
            } else {
                LOGGER.warn("[舞台模式] 音频加载失败: {}", audioPath);
            }
        }

        stateMachine.enterPlaying();
        lastTickTimeNs = System.nanoTime();
        escWasPressed = false;
        lastEscTimeNs = 0L;
        environmentController.resetStageInputState();
        return true;
    }

    public void exitStageMode() {
        if (stateMachine.isInactive()) {
            return;
        }
        if (stateMachine.isWatching()) {
            exitWatchMode();
            return;
        }

        boolean wasPlaying = stateMachine.isPlaying();
        if (wasPlaying) {
            cleanupPlayingResources();
        }

        restoreMouseGrab();
        Minecraft minecraft = Minecraft.getInstance();
        environmentController.restoreCameraType(minecraft);

        stateMachine.enterInactive();
        waitingForHost = false;
        playbackSession.clearAllState();

        if (wasPlaying) {
            broadcastPort.sendRemoteStageStop();
            if (sessionPort.isWatchingStage()) {
                playerSyncPort.stopLocalStageAnimation();
                waitingForHost = true;
                uiPort.openStageSelection();
            } else {
                sessionPort.closeHostedSession();
            }
        }

        environmentController.releaseAllKeys();
    }

    public void updateCamera() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && stateMachine.shouldPinPlayer()) {
            minecraft.player.setPos(anchorX, anchorY, anchorZ);
            minecraft.player.setDeltaMovement(0, 0, 0);
        }

        switch (stateMachine.current()) {
            case INTRO -> updateIntro();
            case PLAYING -> updatePlaying();
            case OUTRO -> updateOutro();
            case WATCHING -> updateWatching();
            default -> {
            }
        }
    }

    public void checkEscapeKey() {
        if (stateMachine.isInactive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PauseScreen) {
            minecraft.setScreen(null);
            return;
        }
        if (minecraft.screen != null) {
            return;
        }

        long window = minecraft.getWindow().getWindow();
        boolean escNow = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (escNow && !escWasPressed) {
            if (stateMachine.isPlaying()) {
                long now = System.nanoTime();
                if (lastEscTimeNs != 0L && now - lastEscTimeNs < DOUBLE_ESC_WINDOW_NS) {
                    lastEscTimeNs = 0L;
                    endPlayback();
                } else {
                    lastEscTimeNs = now;
                    if (cinematicMode) {
                        environmentController.leaveCinematicMode(minecraft);
                    }
                    if (minecraft.gui != null) {
                        minecraft.gui.setOverlayMessage(Component.translatable("gui.mmdskin.stage.esc_hint"), false);
                    }
                }
            } else if (stateMachine.isWatching()) {
                exitWatchMode();
            } else {
                exitStageMode();
            }
        }
        escWasPressed = escNow;
    }

    public void toggleMouseGrab() {
        environmentController.toggleMouseGrab(Minecraft.getInstance());
    }

    public boolean isMouseReleased() {
        return environmentController.isMouseReleased();
    }

    public boolean isActive() {
        return stateMachine.isActive();
    }

    public boolean isPlaying() {
        return stateMachine.isPlaying();
    }

    public boolean isInStageMode() {
        return stateMachine.isActive();
    }

    public boolean isStagePlayingModel(long handle) {
        return stateMachine.isPlaying() && playbackSession.modelHandle() != 0L && playbackSession.modelHandle() == handle;
    }

    public float getAnchorYaw() {
        return anchorYaw;
    }

    public boolean isCinematicMode() {
        return cinematicMode;
    }

    public float getCurrentFrame() {
        return playbackSession.currentFrame();
    }

    public float getMaxFrame() {
        return playbackSession.maxFrame();
    }

    public float getProgress() {
        return playbackSession.maxFrame() > 0 ? playbackSession.currentFrame() / playbackSession.maxFrame() : 0.0f;
    }

    public double getCameraX() {
        return cameraX;
    }

    public double getCameraY() {
        return cameraY;
    }

    public double getCameraZ() {
        return cameraZ;
    }

    public float getCameraPitch() {
        return cameraPitch;
    }

    public float getCameraYaw() {
        return cameraYaw;
    }

    public float getCameraRoll() {
        return cameraRoll;
    }

    public float getCameraFov() {
        return cameraFov;
    }

    public void setPlaybackSpeed(float speed) {
        playbackSession.setPlaybackSpeed(speed);
    }

    public float getPlaybackSpeed() {
        return playbackSession.playbackSpeed();
    }

    public String getActiveStageModelName() {
        return playbackSession.modelName();
    }

    public boolean isWaitingForHost() {
        return waitingForHost;
    }

    public void setWaitingForHost(boolean waiting) {
        this.waitingForHost = waiting;
    }

    public UUID getWatchingHostUUID() {
        return watchingHostUUID;
    }

    public void enterWatchMode(UUID hostUUID) {
        if (stateMachine.isWatching()) {
            return;
        }
        if (!stateMachine.isInactive()) {
            forceCleanupForWatch();
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        var host = minecraft.level.getPlayerByUUID(hostUUID);
        if (host == null) {
            return;
        }

        environmentController.enterStageCameraMode(minecraft);

        anchorX = host.getX();
        anchorY = host.getY();
        anchorZ = host.getZ();
        anchorYaw = host.getYRot();
        watchingHostUUID = hostUUID;

        cinematicMode = StageConfig.getInstance().cinematicMode;
        if (cinematicMode) {
            environmentController.enterCinematicMode(minecraft);
        }

        lastTickTimeNs = System.nanoTime();
        escWasPressed = false;
        environmentController.resetStageInputState();
        playbackSession.clearPresentationState();
        stateMachine.enterWatching();
    }

    public void setWatchCamera(long cameraAnimHandle, float heightOffset) {
        if (!stateMachine.isWatching()) {
            return;
        }
        if (playbackSession.watchCameraAnimHandle() != 0L) {
            animationPort.deleteAnimation(playbackSession.watchCameraAnimHandle());
        }
        playbackSession.configureWatchCamera(cameraAnimHandle, heightOffset, animationPort);
        if (cameraAnimHandle != 0L && animationPort.hasCameraData(cameraAnimHandle)) {
            cameraData.setAnimHandle(cameraAnimHandle);
        }
    }

    public void setWatchMotion(long motionAnim, long modelHandle, String modelName) {
        if (!stateMachine.isWatching()) {
            return;
        }
        playbackSession.configureWatchMotion(motionAnim, modelHandle, modelName, animationPort);
        if (modelHandle != 0L) {
            playerStageAnimationPort.prepareLocalModelForStage(modelHandle);
        }
    }

    public void loadWatchAudio(String audioPath) {
        if (!stateMachine.isWatching()) {
            return;
        }
        if (audioPlayer.load(audioPath)) {
            audioPlayer.play();
        } else {
            LOGGER.warn("[WATCHING] 音频加载失败: {}", audioPath);
        }
    }

    public void syncAudioPosition(float seconds) {
        if (!audioPlayer.isLoaded()) {
            return;
        }
        float current = audioPlayer.getPlaybackPosition();
        if (Math.abs(current - seconds) > 0.15f) {
            audioPlayer.setPlaybackPosition(seconds);
        }
    }

    public void exitWatchMode() {
        exitWatchMode(true);
    }

    public void exitWatchMode(boolean sendLeave) {
        if (!stateMachine.isWatching()) {
            return;
        }

        audioPlayer.cleanup();
        if (cinematicMode) {
            environmentController.leaveCinematicMode(Minecraft.getInstance());
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            playerSyncPort.stopLocalStageAnimation();
            broadcastPort.sendRemoteStageStop();
            UUID hostPlayerId = sessionPort.getHostPlayerId();
            UUID sessionId = sessionPort.getSessionId();
            if (sendLeave && hostPlayerId != null && sessionId != null) {
                broadcastPort.sendLeave(hostPlayerId, sessionId);
            }

            anchorX = minecraft.player.getX();
            anchorY = minecraft.player.getY();
            anchorZ = minecraft.player.getZ();
            anchorYaw = minecraft.player.getYRot();
        }

        computeIntroAndStandby(minecraft);
        playerStageAnimationPort.clearLocalStageFlags();
        playerStageAnimationPort.restoreLocalModelState();
        if (playbackSession.motionAnimHandle() != 0L) {
            animationPort.deleteAnimation(playbackSession.motionAnimHandle());
        }
        if (playbackSession.watchCameraAnimHandle() != 0L) {
            animationPort.deleteAnimation(playbackSession.watchCameraAnimHandle());
            playbackSession.clearWatchCameraHandle();
        }

        playbackSession.clearPresentationState();
        outroStartPose = currentPose();
        outroElapsed = 0.0f;
        lastTickTimeNs = System.nanoTime();
        outroIsGuest = true;
        restoreMouseGrab();

        if (sendLeave) {
            sessionPort.stopWatching();
            watchingHostUUID = null;
        } else {
            sessionPort.stopWatchingStageOnly();
            waitingForHost = true;
        }
        stateMachine.enterOutro();
    }

    public void onFrameSync(float hostFrame) {
        if (!stateMachine.isWatching() && !stateMachine.isPlaying()) {
            return;
        }
        playbackSession.setTargetSyncFrame(hostFrame);
    }

    public boolean isWatching() {
        return stateMachine.isWatching();
    }

    public boolean shouldBlockInput() {
        return stateMachine.shouldBlockInput();
    }

    void setStateForTesting(StageCameraSessionStateMachine.StageState state) {
        stateMachine.set(state);
    }

    StageCameraSessionStateMachine.StageState getStateForTesting() {
        return stateMachine.current();
    }

    private void computeIntroAndStandby(Minecraft minecraft) {
        if (minecraft.gameRenderer != null && minecraft.gameRenderer.getMainCamera() != null) {
            var camera = minecraft.gameRenderer.getMainCamera();
            introStartPose = new StageCameraPose(
                    camera.getPosition().x,
                    camera.getPosition().y,
                    camera.getPosition().z,
                    camera.getXRot(),
                    camera.getYRot(),
                    0.0f,
                    (float) minecraft.options.fov().get()
            );
        } else if (minecraft.player != null) {
            introStartPose = new StageCameraPose(
                    minecraft.player.getX(),
                    minecraft.player.getEyeY(),
                    minecraft.player.getZ(),
                    minecraft.player.getXRot(),
                    minecraft.player.getYRot(),
                    0.0f,
                    (float) minecraft.options.fov().get()
            );
        }
        standbyPose = StageCameraTimeline.standbyPose(anchorX, anchorY, anchorZ, anchorYaw);
    }

    private void updateIntro() {
        long now = System.nanoTime();
        float deltaTime = StageCameraTimeline.cappedDeltaSeconds(now, lastTickTimeNs);
        lastTickTimeNs = now;
        introElapsed += deltaTime;
        applyPose(StageCameraTimeline.interpolateIntro(introStartPose, standbyPose, introElapsed, INTRO_DURATION));
        if (introElapsed >= INTRO_DURATION) {
            applyPose(standbyPose);
            stateMachine.enterStandby();
        }
    }

    private void updatePlaying() {
        long now = System.nanoTime();
        float deltaTime = StageCameraTimeline.cappedDeltaSeconds(now, lastTickTimeNs);
        lastTickTimeNs = now;

        if (cinematicMode && lastEscTimeNs != 0L && now - lastEscTimeNs >= DOUBLE_ESC_WINDOW_NS) {
            Minecraft.getInstance().options.hideGui = true;
            lastEscTimeNs = 0L;
        }

        StagePlaybackAdvance advance = playbackSession.advance(
                deltaTime,
                VMD_FPS,
                CATCHUP_SPEED_MAX,
                CATCHUP_SPEED_MIN,
                SYNC_TOLERANCE
        );
        if (advance.completed()) {
            endPlayback();
            return;
        }

        syncStagePresentation(playbackSession.currentFrame());
        if (sessionPort.isSessionHost() && sessionPort.getSessionId() != null
                && playbackSession.shouldBroadcastFrameSync(SYNC_INTERVAL_FRAMES)) {
            broadcastPort.sendFrameSync(sessionPort.getSessionId(), playbackSession.currentFrame());
        }

        if (playbackSession.cameraAnimHandle() != 0L) {
            cameraData.update(playbackSession.currentFrame());
            applyPose(StageCameraTimeline.playbackPose(
                    cameraData,
                    anchorX,
                    anchorY,
                    anchorZ,
                    anchorYaw,
                    playbackSession.cameraHeightOffset(),
                    MMD_TO_MC_SCALE
            ));
        }
    }

    private void updateOutro() {
        long now = System.nanoTime();
        float deltaTime = StageCameraTimeline.cappedDeltaSeconds(now, lastTickTimeNs);
        lastTickTimeNs = now;
        outroElapsed += deltaTime;
        applyPose(StageCameraTimeline.interpolateOutro(outroStartPose, standbyPose, outroElapsed, OUTRO_DURATION));
        if (outroElapsed >= OUTRO_DURATION) {
            applyPose(standbyPose);
            stateMachine.enterStandby();
            uiPort.openStageSelection();
        }
    }

    private void updateWatching() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && watchingHostUUID != null) {
            var host = minecraft.level.getPlayerByUUID(watchingHostUUID);
            if (host != null) {
                anchorX = host.getX();
                anchorY = host.getY();
                anchorZ = host.getZ();
                anchorYaw = host.getYRot();
            }
        }

        long now = System.nanoTime();
        float deltaTime = StageCameraTimeline.cappedDeltaSeconds(now, lastTickTimeNs);
        lastTickTimeNs = now;

        StagePlaybackAdvance advance = playbackSession.advance(
                deltaTime,
                VMD_FPS,
                CATCHUP_SPEED_MAX,
                CATCHUP_SPEED_MIN,
                SYNC_TOLERANCE
        );
        if (advance.completed()) {
            exitWatchMode(true);
            return;
        }

        syncStagePresentation(playbackSession.currentFrame());
        if (playbackSession.watchCameraAnimHandle() == 0L) {
            applyPose(StageCameraTimeline.standbyPose(anchorX, anchorY, anchorZ, anchorYaw));
            return;
        }

        cameraData.update(playbackSession.currentFrame());
        applyPose(StageCameraTimeline.playbackPose(
                cameraData,
                anchorX,
                anchorY,
                anchorZ,
                anchorYaw,
                playbackSession.cameraHeightOffset(),
                MMD_TO_MC_SCALE
        ));
    }

    private void cleanupPlayingResources() {
        audioPlayer.cleanup();
        if (cinematicMode) {
            environmentController.leaveCinematicMode(Minecraft.getInstance());
        }
        playerStageAnimationPort.clearLocalStageFlags();
        playerStageAnimationPort.restoreLocalModelState();
        if (playbackSession.motionAnimHandle() != 0L) {
            animationPort.deleteAnimation(playbackSession.motionAnimHandle());
        }
        if (playbackSession.cameraAnimHandle() != 0L
                && playbackSession.cameraAnimHandle() != playbackSession.motionAnimHandle()) {
            animationPort.deleteAnimation(playbackSession.cameraAnimHandle());
        }
    }

    private void endPlayback() {
        restoreMouseGrab();
        cleanupPlayingResources();
        playbackSession.clearPresentationState();
        outroStartPose = currentPose();
        outroElapsed = 0.0f;
        lastTickTimeNs = System.nanoTime();
        outroIsGuest = sessionPort.isWatchingStage();
        stateMachine.enterOutro();

        if (outroIsGuest) {
            playerSyncPort.stopLocalStageAnimation();
            broadcastPort.sendRemoteStageStop();
            sessionPort.stopWatchingStageOnly();
        } else {
            broadcastPort.sendRemoteStageStop();
            sessionPort.notifyMembersStageEnd();
        }
    }

    private void forceCleanupForWatch() {
        if (stateMachine.isPlaying()) {
            cleanupPlayingResources();
        }
        if (playbackSession.watchCameraAnimHandle() != 0L) {
            animationPort.deleteAnimation(playbackSession.watchCameraAnimHandle());
            playbackSession.clearWatchCameraHandle();
        }
        restoreMouseGrab();
        playbackSession.clearAllState();
        waitingForHost = false;
        stateMachine.enterInactive();
    }

    private void restoreMouseGrab() {
        environmentController.restoreMouseGrab(Minecraft.getInstance());
        escWasPressed = false;
        lastEscTimeNs = 0L;
    }

    private void syncStagePresentation(float frame) {
        playerSyncPort.syncStageFrame(frame);
        syncAudioPosition(frame / VMD_FPS);
    }

    private StageCameraPose currentPose() {
        return new StageCameraPose(cameraX, cameraY, cameraZ, cameraPitch, cameraYaw, cameraRoll, cameraFov);
    }

    private void applyPose(StageCameraPose pose) {
        cameraX = pose.x();
        cameraY = pose.y();
        cameraZ = pose.z();
        cameraPitch = pose.pitch();
        cameraYaw = pose.yaw();
        cameraRoll = pose.roll();
        cameraFov = pose.fov();
    }
}
