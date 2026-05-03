package com.shiroha.mmdskin.stage.client.camera;

import org.joml.Vector3f;

/** 文件职责：集中舞台相机时间线、插值与轨迹换算。 */
final class StageCameraTimeline {
    private StageCameraTimeline() {
    }

    static float cappedDeltaSeconds(long now, long lastTickTimeNs) {
        return Math.min((now - lastTickTimeNs) / 1_000_000_000.0f, 0.1f);
    }

    static StageCameraPose standbyPose(double anchorX, double anchorY, double anchorZ, float anchorYaw) {
        float yawRad = (float) Math.toRadians(anchorYaw);
        return new StageCameraPose(
                anchorX - Math.sin(yawRad) * 3.5,
                anchorY + 1.8,
                anchorZ + Math.cos(yawRad) * 3.5,
                15.0f,
                anchorYaw + 180.0f,
                0.0f,
                70.0f
        );
    }

    static StageCameraPose interpolateIntro(StageCameraPose start, StageCameraPose end, float elapsed, float duration) {
        return interpolate(start, end, easeOutCubic(elapsed / duration));
    }

    static StageCameraPose interpolateOutro(StageCameraPose start, StageCameraPose end, float elapsed, float duration) {
        return interpolate(start, end, easeInOutQuart(elapsed / duration));
    }

    static StagePlaybackAdvance advanceFrame(float currentFrame,
                                             float targetSyncFrame,
                                             float playbackSpeed,
                                             float deltaTime,
                                             float maxFrame,
                                             float fps,
                                             float catchupSpeedMax,
                                             float catchupSpeedMin,
                                             float syncTolerance) {
        float effectiveSpeed = playbackSpeed;
        float nextTargetSyncFrame = targetSyncFrame;
        if (targetSyncFrame >= 0) {
            float drift = targetSyncFrame - currentFrame;
            if (Math.abs(drift) > syncTolerance) {
                effectiveSpeed = drift > 0 ? catchupSpeedMax : catchupSpeedMin;
            } else {
                nextTargetSyncFrame = -1.0f;
            }
        }

        float nextFrame = currentFrame + deltaTime * fps * effectiveSpeed;
        boolean completed = nextFrame >= maxFrame;
        if (completed) {
            nextFrame = maxFrame;
        }
        return new StagePlaybackAdvance(nextFrame, nextTargetSyncFrame, effectiveSpeed, completed);
    }

    static StageCameraPose playbackPose(MMDCameraData cameraData,
                                        double anchorX,
                                        double anchorY,
                                        double anchorZ,
                                        float anchorYaw,
                                        float cameraHeightOffset,
                                        float scale) {
        Vector3f mmdPos = cameraData.getPosition();
        float sx = mmdPos.x * scale;
        float sy = mmdPos.y * scale;
        float sz = mmdPos.z * scale;

        float yawRad = (float) Math.toRadians(anchorYaw);
        float cos = (float) Math.cos(yawRad);
        float sin = (float) Math.sin(yawRad);
        return new StageCameraPose(
                anchorX + sx * cos - sz * sin,
                anchorY + sy + cameraHeightOffset,
                anchorZ + sx * sin + sz * cos,
                (float) Math.toDegrees(cameraData.getPitch()),
                (float) Math.toDegrees(cameraData.getYaw()) + anchorYaw,
                (float) Math.toDegrees(cameraData.getRoll()),
                cameraData.getFov()
        );
    }

    private static StageCameraPose interpolate(StageCameraPose start, StageCameraPose end, float t) {
        return new StageCameraPose(
                lerp(start.x(), end.x(), t),
                lerp(start.y(), end.y(), t),
                lerp(start.z(), end.z(), t),
                lerp(start.pitch(), end.pitch(), t),
                lerpAngle(start.yaw(), end.yaw(), t),
                lerp(start.roll(), end.roll(), t),
                lerp(start.fov(), end.fov(), t)
        );
    }

    private static float easeOutCubic(float t) {
        float clamped = clamp01(t);
        float f = 1 - clamped;
        return 1 - f * f * f;
    }

    private static float easeInOutQuart(float t) {
        float clamped = clamp01(t);
        return clamped < 0.5f ? 8 * clamped * clamped * clamped * clamped
                : 1 - (float) Math.pow(-2 * clamped + 2, 4) / 2;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float lerpAngle(float a, float b, float t) {
        float diff = ((b - a) % 360 + 540) % 360 - 180;
        return a + diff * t;
    }
}
