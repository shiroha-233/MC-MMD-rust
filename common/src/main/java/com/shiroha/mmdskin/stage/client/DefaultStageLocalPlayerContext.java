package com.shiroha.mmdskin.stage.client;

import com.shiroha.mmdskin.stage.application.port.StageLocalPlayerContextPort;

import java.util.UUID;

public final class DefaultStageLocalPlayerContext implements StageLocalPlayerContextPort {
    public static final DefaultStageLocalPlayerContext INSTANCE = new DefaultStageLocalPlayerContext();

    private DefaultStageLocalPlayerContext() {
    }

    @Override
    public UUID getLocalPlayerUUID() {
        return StageClientContext.getLocalPlayerUUID();
    }

    @Override
    public String resolvePlayerName(UUID uuid) {
        return StageClientContext.resolvePlayerName(uuid);
    }
}
