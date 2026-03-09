package com.shiroha.mmdskin.stage.server.application.port;

import com.shiroha.mmdskin.stage.protocol.StagePacket;
import com.shiroha.mmdskin.stage.server.domain.model.StageServerPlayer;

import java.util.List;
import java.util.UUID;

public interface StageServerPlatformPort {
    StageServerPlayer findPlayer(UUID playerId);

    List<StageServerPlayer> getOnlinePlayers();

    void sendPacket(UUID targetPlayerId, UUID sourcePlayerId, StagePacket packet);
}
