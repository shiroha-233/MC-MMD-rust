package com.shiroha.mmdskin.stage.client.network;

import com.shiroha.mmdskin.stage.application.StageSessionService;
import com.shiroha.mmdskin.stage.client.camera.port.StageCameraBroadcastPort;
import com.shiroha.mmdskin.stage.client.playback.StagePlaybackStartRequest;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackBroadcastPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackWatchRequest;
import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;

import java.util.Objects;
import java.util.UUID;

/** 文件职责：把舞台播放广播请求适配到网络层。 */
public final class StageNetworkPlaybackBroadcastAdapter implements StagePlaybackBroadcastPort, StageCameraBroadcastPort {
    private final StageSessionService sessionService;

    public StageNetworkPlaybackBroadcastAdapter(StageSessionService sessionService) {
        this.sessionService = Objects.requireNonNull(sessionService, "sessionService");
    }

    @Override
    public void sendStageWatch(StagePlaybackWatchRequest request) {
        if (request == null) {
            return;
        }
        StageNetworkHandler.sendStageWatch(
                request.targetUUID(),
                request.sessionId(),
                request.descriptor(),
                request.heightOffset(),
                request.startFrame()
        );
    }

    @Override
    public void sendRemoteStageStart(StageDescriptor descriptor) {
        StageNetworkHandler.sendRemoteStageStart(sessionService.getSessionId(), descriptor);
    }

    @Override
    public void sendRemoteStageStop() {
        StageNetworkHandler.sendRemoteStageStop(sessionService.getSessionId());
    }

    @Override
    public void sendFrameSync(UUID sessionId, float frame) {
        StageNetworkHandler.sendFrameSync(sessionId, frame);
    }

    @Override
    public void sendLeave(UUID hostUUID, UUID sessionId) {
        StageNetworkHandler.sendLeave(hostUUID, sessionId);
    }
}
