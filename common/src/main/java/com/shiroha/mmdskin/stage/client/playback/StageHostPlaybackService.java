package com.shiroha.mmdskin.stage.client.playback;

import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackBroadcastPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackRuntimePort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackSessionPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackWatchRequest;
import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;

import java.util.Objects;
import java.util.UUID;

/** 文件职责：协调主机侧舞台开播与成员广播。 */
public final class StageHostPlaybackService {
    private final StagePlaybackRuntimePort runtime;
    private final StagePlaybackBroadcastPort broadcast;
    private final StagePlaybackSessionPort sessionPort;

    public StageHostPlaybackService(StagePlaybackRuntimePort runtime,
                                    StagePlaybackBroadcastPort broadcast,
                                    StagePlaybackSessionPort sessionPort) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.broadcast = Objects.requireNonNull(broadcast, "broadcast");
        this.sessionPort = Objects.requireNonNull(sessionPort, "sessionPort");
    }

    public boolean startPack(StagePack pack, boolean cinematicMode, float cameraHeightOffset,
                             String selectedMotionFileName) {
        StagePlaybackRuntimePort.HostStartResult result = runtime.startHostPlayback(
                pack,
                cinematicMode,
                cameraHeightOffset,
                selectedMotionFileName
        );
        if (!result.started()) {
            return false;
        }

        notifyMembers(result.sessionDescriptor(), cameraHeightOffset);
        if (result.remoteDescriptor() != null) {
            broadcast.sendRemoteStageStart(result.remoteDescriptor());
        }
        return true;
    }

    private void notifyMembers(StageDescriptor descriptor, float heightOffset) {
        UUID sessionId = sessionPort.getSessionId();
        if (sessionId == null || descriptor == null || !descriptor.isValid()) {
            return;
        }
        for (UUID memberUUID : sessionPort.getAcceptedMembers()) {
            broadcast.sendStageWatch(new StagePlaybackWatchRequest(memberUUID, sessionId, descriptor, heightOffset, 0.0f));
        }
    }
}
