package com.shiroha.mmdskin.stage.client.playback.port;

import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;

import java.util.UUID;

/** 舞台播放开始广播请求。 */
public record StagePlaybackWatchRequest(UUID targetUUID, UUID sessionId, StageDescriptor descriptor,
                                        float heightOffset, float startFrame) {
    public StagePlaybackWatchRequest {
        descriptor = descriptor != null ? descriptor.copy() : null;
    }
}
