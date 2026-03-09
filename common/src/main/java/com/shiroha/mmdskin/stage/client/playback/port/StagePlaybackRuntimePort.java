package com.shiroha.mmdskin.stage.client.playback.port;

import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.stage.client.playback.StagePlaybackStartRequest;
import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;

import java.util.UUID;

public interface StagePlaybackRuntimePort {
    record HostStartResult(boolean started, StageDescriptor sessionDescriptor, StageDescriptor remoteDescriptor) {
        public static HostStartResult success(StageDescriptor sessionDescriptor, StageDescriptor remoteDescriptor) {
            return new HostStartResult(true, sessionDescriptor, remoteDescriptor);
        }

        public static HostStartResult failed() {
            return new HostStartResult(false, null, null);
        }
    }

    record GuestStartResult(boolean started, StageDescriptor sessionDescriptor, StageDescriptor remoteDescriptor) {
        public static GuestStartResult success(StageDescriptor sessionDescriptor, StageDescriptor remoteDescriptor) {
            return new GuestStartResult(true, sessionDescriptor, remoteDescriptor);
        }

        public static GuestStartResult failed() {
            return new GuestStartResult(false, null, null);
        }
    }

    void enterStageSelection(boolean waitingForHost);

    void setWaitingForHost(boolean waitingForHost);

    void exitStageSelection();

    void stopActivePlaybackForRemoteEnd();

    void applyFrameSync(float frame);

    void applyInitialFrameSync(float frame);

    HostStartResult startHostPlayback(StagePack pack, boolean cinematicMode,
                                      float cameraHeightOffset, String selectedMotionFileName);

    GuestStartResult startGuestPlayback(UUID hostUUID, StagePlaybackStartRequest request, boolean useHostCamera);
}
