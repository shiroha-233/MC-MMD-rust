package com.shiroha.mmdskin.ui.network;

import com.shiroha.mmdskin.stage.client.camera.port.StageCameraBroadcastPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackBroadcastPort;
import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;

import java.util.UUID;

public final class StageNetworkPlaybackBroadcastAdapter implements StagePlaybackBroadcastPort, StageCameraBroadcastPort {
    public static final StageNetworkPlaybackBroadcastAdapter INSTANCE = new StageNetworkPlaybackBroadcastAdapter();

    private StageNetworkPlaybackBroadcastAdapter() {
    }

    @Override
    public void sendStageWatch(UUID targetUUID, UUID sessionId, StageDescriptor descriptor,
                               float heightOffset, float startFrame) {
        StageNetworkHandler.sendStageWatch(targetUUID, sessionId, descriptor, heightOffset, startFrame);
    }

    @Override
    public void sendRemoteStageStart(StageDescriptor descriptor) {
        StageNetworkHandler.sendRemoteStageStart(descriptor);
    }

    @Override
    public void sendRemoteStageStop() {
        StageNetworkHandler.sendRemoteStageStop();
    }

    @Override
    public void sendFrameSync(UUID sessionId, float frame) {
        StageNetworkHandler.sendFrameSync(sessionId, frame);
    }

    @Override
    public void sendLeave(UUID hostUUID) {
        StageNetworkHandler.sendLeave(hostUUID);
    }
}
