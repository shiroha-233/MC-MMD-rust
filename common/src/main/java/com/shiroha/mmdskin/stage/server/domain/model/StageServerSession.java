package com.shiroha.mmdskin.stage.server.domain.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class StageServerSession {
    private final UUID sessionId;
    private final UUID hostId;
    private final Map<UUID, StageServerSessionMember> members = new LinkedHashMap<>();

    public StageServerSession(UUID sessionId, UUID hostId) {
        this.sessionId = sessionId;
        this.hostId = hostId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getHostId() {
        return hostId;
    }

    public Map<UUID, StageServerSessionMember> getMembers() {
        return members;
    }
}
