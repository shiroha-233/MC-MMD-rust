package com.shiroha.mmdskin.stage.client;

import com.shiroha.mmdskin.stage.application.StageSessionService;
import com.shiroha.mmdskin.stage.client.sync.StageAnimSyncHelper;
import com.shiroha.mmdskin.stage.domain.model.StageCameraMode;
import com.shiroha.mmdskin.stage.domain.model.StageRole;
import com.shiroha.mmdskin.stage.protocol.StagePacket;
import com.shiroha.mmdskin.stage.protocol.StagePacketCodec;
import com.shiroha.mmdskin.stage.protocol.StagePacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.UUID;

/** 文件职责：解析并分发客户端收到的多人舞台协议包。 */
public final class StageClientPacketHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final StageClientPacketHandler INSTANCE = new StageClientPacketHandler();

    private final StageSessionService sessionService = StageSessionService.getInstance();
    private final StagePlaybackCoordinator playbackCoordinator = StagePlaybackCoordinator.getInstance();

    private StageClientPacketHandler() {
    }

    public static StageClientPacketHandler getInstance() {
        return INSTANCE;
    }

    public void handle(UUID senderUUID, String rawData) {
        StagePacket packet = StagePacketCodec.decode(rawData);
        if (packet == null) {
            LOGGER.warn("[多人舞台] 收到无法识别的协议数据包");
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        UUID localPlayerId = mc.player.getUUID();

        UUID targetUUID = StageClientPacketMapper.parseUUID(packet.targetPlayerId);
        if (targetUUID != null && !targetUUID.equals(localPlayerId)) {
            return;
        }

        UUID sessionId = StageClientPacketMapper.parseUUID(packet.sessionId);
        switch (packet.type) {
            case INVITE_REQUEST -> playbackCoordinator.handleInviteRequest(senderUUID, sessionId);
            case INVITE_CANCEL -> sessionService.onInviteCancelled(senderUUID, sessionId);
            case INVITE_RESPONSE -> sessionService.onInviteReply(senderUUID, sessionId, packet.inviteDecision);
            case SESSION_STATE -> sessionService.onSessionState(
                    senderUUID,
                    sessionId,
                    StageClientPacketMapper.toSessionMembers(packet.members)
            );
            case READY_UPDATE -> sessionService.onMemberReady(
                    senderUUID,
                    sessionId,
                    Boolean.TRUE.equals(packet.ready),
                    packet.cameraMode != null ? packet.cameraMode : StageCameraMode.HOST_CAMERA
            );
            case MEMBER_LEAVE -> sessionService.onMemberLeft(senderUUID, sessionId);
            case SESSION_DISSOLVE -> playbackCoordinator.handleSessionDissolve(senderUUID, sessionId);
            case PLAYBACK_START -> playbackCoordinator.handlePlaybackStart(
                    senderUUID,
                    sessionId,
                    StageClientPacketMapper.toPlaybackStartRequest(packet)
            );
            case PLAYBACK_STOP -> playbackCoordinator.handlePlaybackStop(senderUUID, sessionId);
            case FRAME_SYNC -> playbackCoordinator.handleFrameSync(senderUUID, sessionId, packet.frame);
            case REMOTE_STAGE_START -> {
                if (!shouldHandleRemoteStagePacket(localPlayerId, senderUUID, sessionId)
                        || mc.level == null
                        || packet.descriptor == null
                        || !packet.descriptor.isValid()) {
                    return;
                }
                Player target = mc.level.getPlayerByUUID(senderUUID);
                if (target != null) {
                    StageAnimSyncHelper.startStageAnim(target, packet.descriptor);
                }
            }
            case REMOTE_STAGE_STOP -> {
                if (!shouldHandleRemoteStagePacket(localPlayerId, senderUUID, sessionId)) {
                    return;
                }
                Player target = mc.level != null ? mc.level.getPlayerByUUID(senderUUID) : null;
                if (target != null) {
                    StageAnimSyncHelper.endStageAnim(target);
                } else {
                    StageAnimSyncHelper.endStageAnim(senderUUID);
                }
            }
            default -> LOGGER.warn("[多人舞台] 未处理的数据包类型 {}", packet.type);
        }
    }

    boolean shouldHandleRemoteStagePacket(UUID localPlayerId, UUID senderUUID, UUID sessionId) {
        if (localPlayerId == null || senderUUID == null || senderUUID.equals(localPlayerId)) {
            return false;
        }
        return sessionService.getLocalRole() != StageRole.NONE
                && sessionId != null
                && Objects.equals(sessionService.getSessionId(), sessionId);
    }
}
