package com.shiroha.mmdskin.ui.network;

import com.shiroha.mmdskin.stage.protocol.StagePacket;
import com.shiroha.mmdskin.stage.protocol.StagePacketCodec;
import com.shiroha.mmdskin.stage.protocol.StagePacketType;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StageNetworkPlaybackBroadcastAdapterTest {

    @AfterEach
    void tearDown() {
        StageNetworkHandler.setStageMultiSender(null);
    }

    @Test
    void shouldForwardLeaveWithExplicitSessionId() {
        AtomicReference<String> payloadRef = new AtomicReference<>();
        StageNetworkHandler.setStageMultiSender(payloadRef::set);

        UUID hostId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        StageNetworkPlaybackBroadcastAdapter.INSTANCE.sendLeave(hostId, sessionId);

        StagePacket packet = StagePacketCodec.decode(payloadRef.get());
        assertNotNull(packet);
        assertEquals(StagePacketType.MEMBER_LEAVE, packet.type);
        assertEquals(hostId.toString(), packet.targetPlayerId);
        assertEquals(sessionId.toString(), packet.sessionId);
    }
}
