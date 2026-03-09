package com.shiroha.mmdskin.stage.client.playback.port;

import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;

import java.util.UUID;

public interface StagePlaybackBroadcastPort {
    void sendStageWatch(UUID targetUUID, UUID sessionId, StageDescriptor descriptor,
                        float heightOffset, float startFrame);

    void sendRemoteStageStart(StageDescriptor descriptor);
}
