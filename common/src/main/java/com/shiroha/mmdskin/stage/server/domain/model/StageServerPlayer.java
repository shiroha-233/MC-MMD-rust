package com.shiroha.mmdskin.stage.server.domain.model;

import java.util.UUID;

public final class StageServerPlayer {
    private final UUID uuid;
    private final String name;

    public StageServerPlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }
}
