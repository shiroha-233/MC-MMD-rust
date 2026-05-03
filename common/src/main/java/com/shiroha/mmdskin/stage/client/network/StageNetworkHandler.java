package com.shiroha.mmdskin.stage.client.network;

import com.shiroha.mmdskin.stage.domain.model.StageCameraMode;
import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;
import com.shiroha.mmdskin.stage.domain.model.StageInviteDecision;
import com.shiroha.mmdskin.stage.protocol.StagePacket;
import com.shiroha.mmdskin.stage.protocol.StagePacketCodec;
import com.shiroha.mmdskin.stage.protocol.StagePacketType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 舞台网络发送入口。
 */
public final class StageNetworkHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    private static Consumer<String> stageMultiSender;

    private StageNetworkHandler() {
    }

    public static void setStageMultiSender(Consumer<String> sender) {
        stageMultiSender = sender;
    }

    public static void sendStageInvite(UUID targetUUID, UUID sessionId) {
        StagePacket packet = directedPacket(StagePacketType.INVITE_REQUEST, targetUUID, sessionId);
        sendStagePacket(packet);
    }

    public static void sendInviteCancel(UUID targetUUID, UUID sessionId) {
        StagePacket packet = directedPacket(StagePacketType.INVITE_CANCEL, targetUUID, sessionId);
        sendStagePacket(packet);
    }

    public static void sendInviteResponse(UUID hostUUID, UUID sessionId, StageInviteDecision decision) {
        StagePacket packet = directedPacket(StagePacketType.INVITE_RESPONSE, hostUUID, sessionId);
        packet.inviteDecision = decision;
        sendStagePacket(packet);
    }

    public static void sendReady(UUID hostUUID, UUID sessionId, boolean ready, boolean useHostCamera,
                                 String motionPackName, List<String> motionFiles) {
        StagePacket packet = directedPacket(StagePacketType.READY_UPDATE, hostUUID, sessionId);
        packet.ready = ready;
        packet.cameraMode = StageCameraMode.fromUseHostCamera(useHostCamera);
        packet.motionPackName = motionPackName;
        packet.motionFiles = motionFiles != null ? List.copyOf(motionFiles) : Collections.emptyList();
        sendStagePacket(packet);
    }

    public static void sendLeave(UUID hostUUID, UUID sessionId) {
        StagePacket packet = directedPacket(StagePacketType.MEMBER_LEAVE, hostUUID, sessionId);
        sendStagePacket(packet);
    }

    public static void sendSessionDissolve(UUID sessionId) {
        StagePacket packet = new StagePacket(StagePacketType.SESSION_DISSOLVE);
        if (sessionId != null) {
            packet.sessionId = sessionId.toString();
        }
        sendStagePacket(packet);
    }

    public static void sendStageWatch(UUID targetUUID, UUID sessionId, StageDescriptor descriptor,
                                      float heightOffset, float startFrame) {
        if (descriptor == null || !descriptor.isValid()) {
            LOGGER.warn("[多人舞台] 舞台描述无效，无法发送播放开始");
            return;
        }
        StagePacket packet = directedPacket(StagePacketType.PLAYBACK_START, targetUUID, sessionId);
        packet.descriptor = descriptor.copy();
        packet.heightOffset = heightOffset;
        packet.frame = startFrame;
        sendStagePacket(packet);
    }

    public static void sendRemoteStageStart(UUID sessionId, StageDescriptor descriptor) {
        if (sessionId == null) {
            LOGGER.warn("[多人舞台] 会话标识缺失，无法广播开始");
            return;
        }
        if (descriptor == null || !descriptor.isValid()) {
            LOGGER.warn("[多人舞台] 远端舞台描述无效，无法广播开始");
            return;
        }
        StagePacket packet = new StagePacket(StagePacketType.REMOTE_STAGE_START);
        packet.sessionId = sessionId.toString();
        packet.descriptor = descriptor.copy();
        sendStagePacket(packet);
    }

    public static void sendRemoteStageStop(UUID sessionId) {
        if (sessionId == null) {
            LOGGER.warn("[多人舞台] 会话标识缺失，无法广播结束");
            return;
        }
        StagePacket packet = new StagePacket(StagePacketType.REMOTE_STAGE_STOP);
        packet.sessionId = sessionId.toString();
        sendStagePacket(packet);
    }

    public static void sendStageWatchEnd(UUID targetUUID, UUID sessionId) {
        StagePacket packet = directedPacket(StagePacketType.PLAYBACK_STOP, targetUUID, sessionId);
        sendStagePacket(packet);
    }

    public static void sendFrameSync(UUID sessionId, float currentFrame) {
        StagePacket packet = new StagePacket(StagePacketType.FRAME_SYNC);
        if (sessionId != null) {
            packet.sessionId = sessionId.toString();
        }
        packet.frame = currentFrame;
        sendStagePacket(packet);
    }

    private static StagePacket directedPacket(StagePacketType type, UUID targetUUID, UUID sessionId) {
        StagePacket packet = new StagePacket(type);
        if (targetUUID != null) {
            packet.targetPlayerId = targetUUID.toString();
        }
        if (sessionId != null) {
            packet.sessionId = sessionId.toString();
        }
        return packet;
    }

    private static void sendStagePacket(StagePacket packet) {
        sendMulti(StagePacketCodec.encode(packet));
    }

    private static void sendMulti(String payload) {
        if (stageMultiSender == null) {
            LOGGER.warn("[多人舞台] stageMultiSender 未注册");
            return;
        }
        try {
            stageMultiSender.accept(payload);
        } catch (Exception e) {
            LOGGER.error("多人舞台消息发送失败", e);
        }
    }
}
