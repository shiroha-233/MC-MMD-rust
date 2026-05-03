package com.shiroha.mmdskin.stage.client.camera;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationPort;

/** 文件职责：封装舞台相机播放与观演阶段的运行时会话数据。 */
final class StageCameraPlaybackSession {
    private static final float UNSET_SYNC_FRAME = -1.0f;

    private float currentFrame = 0.0f;
    private float maxFrame = 0.0f;
    private float playbackSpeed = 1.0f;
    private float cameraHeightOffset = 0.0f;

    private long cameraAnimHandle = 0L;
    private long motionAnimHandle = 0L;
    private long modelHandle = 0L;
    private long watchCameraAnimHandle = 0L;

    private String modelName;

    private int frameSyncCounter = 0;
    private float targetSyncFrame = UNSET_SYNC_FRAME;

    boolean configureStagePlayback(long motionAnim,
                                   long cameraAnim,
                                   long modelHandle,
                                   String modelName,
                                   float cameraHeightOffset,
                                   NativeAnimationPort animationPort) {
        this.motionAnimHandle = motionAnim;
        this.cameraAnimHandle = resolveCameraAnimationHandle(motionAnim, cameraAnim, animationPort);
        this.maxFrame = resolveMaxFrame(motionAnim, this.cameraAnimHandle, animationPort);
        this.modelHandle = modelHandle;
        this.modelName = modelName;
        this.cameraHeightOffset = cameraHeightOffset;
        this.currentFrame = 0.0f;
        this.frameSyncCounter = 0;
        this.targetSyncFrame = UNSET_SYNC_FRAME;
        return this.cameraAnimHandle != 0L;
    }

    void configureWatchCamera(long cameraAnimHandle, float cameraHeightOffset, NativeAnimationPort animationPort) {
        this.watchCameraAnimHandle = cameraAnimHandle;
        this.cameraHeightOffset = cameraHeightOffset;
        if (cameraAnimHandle != 0L && animationPort.hasCameraData(cameraAnimHandle)) {
            this.maxFrame = animationPort.getAnimationMaxFrame(cameraAnimHandle);
            this.currentFrame = 0.0f;
        }
    }

    void configureWatchMotion(long motionAnim, long modelHandle, String modelName, NativeAnimationPort animationPort) {
        this.motionAnimHandle = motionAnim;
        this.modelHandle = modelHandle;
        this.modelName = modelName;
        if (this.maxFrame <= 0.0f && motionAnim != 0L) {
            this.maxFrame = animationPort.getAnimationMaxFrame(motionAnim);
        }
    }

    StagePlaybackAdvance advance(float deltaTime,
                                 float fps,
                                 float catchupSpeedMax,
                                 float catchupSpeedMin,
                                 float syncTolerance) {
        StagePlaybackAdvance advance = StageCameraTimeline.advanceFrame(
                currentFrame,
                targetSyncFrame,
                playbackSpeed,
                deltaTime,
                maxFrame,
                fps,
                catchupSpeedMax,
                catchupSpeedMin,
                syncTolerance
        );
        this.currentFrame = advance.frame();
        this.targetSyncFrame = advance.targetSyncFrame();
        return advance;
    }

    boolean shouldBroadcastFrameSync(int syncIntervalFrames) {
        frameSyncCounter++;
        if (frameSyncCounter >= syncIntervalFrames) {
            frameSyncCounter = 0;
            return true;
        }
        return false;
    }

    void setTargetSyncFrame(float targetSyncFrame) {
        this.targetSyncFrame = targetSyncFrame;
    }

    void clearPresentationState() {
        this.cameraAnimHandle = 0L;
        this.motionAnimHandle = 0L;
        this.modelHandle = 0L;
        this.modelName = null;
        this.currentFrame = 0.0f;
        this.maxFrame = 0.0f;
        this.frameSyncCounter = 0;
        this.targetSyncFrame = UNSET_SYNC_FRAME;
    }

    void clearWatchCameraHandle() {
        this.watchCameraAnimHandle = 0L;
    }

    void clearAllState() {
        clearPresentationState();
        this.watchCameraAnimHandle = 0L;
        this.cameraHeightOffset = 0.0f;
    }

    float currentFrame() {
        return currentFrame;
    }

    float maxFrame() {
        return maxFrame;
    }

    float playbackSpeed() {
        return playbackSpeed;
    }

    void setPlaybackSpeed(float playbackSpeed) {
        this.playbackSpeed = playbackSpeed;
    }

    float cameraHeightOffset() {
        return cameraHeightOffset;
    }

    long cameraAnimHandle() {
        return cameraAnimHandle;
    }

    long motionAnimHandle() {
        return motionAnimHandle;
    }

    long modelHandle() {
        return modelHandle;
    }

    String modelName() {
        return modelName;
    }

    long watchCameraAnimHandle() {
        return watchCameraAnimHandle;
    }

    private static long resolveCameraAnimationHandle(long motionAnim, long cameraAnim, NativeAnimationPort animationPort) {
        if (cameraAnim != 0L && animationPort.hasCameraData(cameraAnim)) {
            return cameraAnim;
        }
        if (motionAnim != 0L && animationPort.hasCameraData(motionAnim)) {
            return motionAnim;
        }
        return 0L;
    }

    private static float resolveMaxFrame(long motionAnim, long resolvedCameraAnim, NativeAnimationPort animationPort) {
        if (resolvedCameraAnim != 0L) {
            return animationPort.getAnimationMaxFrame(resolvedCameraAnim);
        }
        if (motionAnim != 0L) {
            return animationPort.getAnimationMaxFrame(motionAnim);
        }
        return 0.0f;
    }
}
