package com.shiroha.mmdskin.stage.client;

import com.shiroha.mmdskin.stage.application.StageSessionMemberSnapshot;
import com.shiroha.mmdskin.stage.client.playback.StagePlaybackStartRequest;
import com.shiroha.mmdskin.stage.domain.model.StageCameraMode;
import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;
import com.shiroha.mmdskin.stage.domain.model.StageMemberState;
import com.shiroha.mmdskin.stage.protocol.StageMemberSnapshot;
import com.shiroha.mmdskin.stage.protocol.StagePacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class StageClientPacketMapperTest {
    @Test
    void shouldFilterInvalidProtocolMembersBeforeApplicationLayer() {
        UUID memberId = UUID.randomUUID();

        List<StageSessionMemberSnapshot> members = StageClientPacketMapper.toSessionMembers(List.of(
                new StageMemberSnapshot(memberId.toString(), "Member", "READY", "LOCAL_CAMERA"),
                new StageMemberSnapshot("bad-uuid", "Ignored", "READY", "HOST_CAMERA"),
                new StageMemberSnapshot(UUID.randomUUID().toString(), "Ignored", "BAD_STATE", "HOST_CAMERA")
        ));

        assertEquals(1, members.size());
        assertEquals(memberId, members.get(0).playerId());
        assertEquals("Member", members.get(0).playerName());
        assertEquals(StageMemberState.READY, members.get(0).state());
        assertEquals(StageCameraMode.LOCAL_CAMERA, members.get(0).cameraMode());
    }

    @Test
    void shouldSanitizePlaybackRequestBeforeRuntimeLayer() {
        StagePacket packet = new StagePacket();
        packet.descriptor = new StageDescriptor("host_pack", List.of("dance.vmd"), null, null);
        packet.frame = 24.0f;
        packet.heightOffset = 1.5f;
        packet.motionPackName = "../escape";

        StagePlaybackStartRequest request = StageClientPacketMapper.toPlaybackStartRequest(packet);

        assertEquals(24.0f, request.startFrame());
        assertEquals(1.5f, request.hostHeightOffset());
        assertNull(request.motionPackName());
        assertEquals("host_pack", request.descriptor().getPackName());
        assertNotSame(packet.descriptor, request.descriptor());
    }
}
