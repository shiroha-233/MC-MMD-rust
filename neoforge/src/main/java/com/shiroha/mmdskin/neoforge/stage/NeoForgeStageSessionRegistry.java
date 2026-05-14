package com.shiroha.mmdskin.neoforge.stage;

import com.shiroha.mmdskin.neoforge.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.stage.domain.model.StageCameraMode;
import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;
import com.shiroha.mmdskin.stage.domain.model.StageMemberState;
import com.shiroha.mmdskin.stage.protocol.StageMemberSnapshot;
import com.shiroha.mmdskin.stage.protocol.StagePacket;
import com.shiroha.mmdskin.stage.protocol.StagePacketCodec;
import com.shiroha.mmdskin.stage.protocol.StagePacketType;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 文件职责：在 NeoForge 侧维护服务端舞台会话并转发多人舞台协议。 */
public final class NeoForgeStageSessionRegistry {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final NeoForgeStageSessionRegistry INSTANCE = new NeoForgeStageSessionRegistry();

    private final Map<UUID, ServerStageSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerSessions = new ConcurrentHashMap<>();

    private NeoForgeStageSessionRegistry() {
    }

    public static NeoForgeStageSessionRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized void handlePacket(MinecraftServer server, ServerPlayer sender, String rawData) {
        StagePacket packet = StagePacketCodec.decode(rawData);
        if (packet == null) {
            LOGGER.warn("[多人舞台] 服务端收到无效协议数据包");
            return;
        }

        switch (packet.type) {
            case INVITE_REQUEST -> handleInviteRequest(server, sender, packet);
            case INVITE_CANCEL -> handleInviteCancel(server, sender, packet);
            case INVITE_RESPONSE -> handleInviteResponse(server, sender, packet);
            case READY_UPDATE -> handleReadyUpdate(server, sender, packet);
            case MEMBER_LEAVE -> handleMemberLeave(server, sender, packet);
            case SESSION_DISSOLVE -> handleSessionDissolve(server, sender, packet);
            case PLAYBACK_START -> handlePlaybackStart(server, sender, packet);
            case PLAYBACK_STOP -> handlePlaybackStop(server, sender, packet);
            case FRAME_SYNC -> handleFrameSync(server, sender, packet);
            case REMOTE_STAGE_START -> handleRemoteStageStart(server, sender, packet);
            case REMOTE_STAGE_STOP -> handleRemoteStageStop(server, sender, packet);
            case SESSION_STATE -> LOGGER.warn("[多人舞台] 客户端不应主动发送 SESSION_STATE");
            default -> LOGGER.warn("[多人舞台] 未处理的数据包类型: {}", packet.type);
        }
    }

    public synchronized void onPlayerDisconnect(MinecraftServer server, ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        UUID sessionId = playerSessions.remove(playerUUID);
        if (sessionId == null) {
            return;
        }

        ServerStageSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }

        if (playerUUID.equals(session.hostId)) {
            dissolveSession(server, session, true);
            return;
        }

        session.members.remove(playerUUID);
        broadcastSessionState(server, session);
        cleanupIfEmpty(session);
    }

    private void handleInviteRequest(MinecraftServer server, ServerPlayer sender, StagePacket packet) {
        UUID sessionId = parseUUID(packet.sessionId);
        UUID targetUUID = parseUUID(packet.targetPlayerId);
        if (sessionId == null || targetUUID == null) {
            return;
        }

        ServerStageSession session = ensureHostSession(server, sender, sessionId);
        if (session == null) {
            return;
        }

        ServerPlayer target = server.getPlayerList().getPlayer(targetUUID);
        if (target == null) {
            return;
        }

        session.members.put(targetUUID, new ServerStageMember(
                targetUUID,
                target.getGameProfile().getName(),
                StageMemberState.INVITED,
                StageCameraMode.HOST_CAMERA
        ));
        sendPacket(target, sender.getUUID(), packet);
        broadcastSessionState(server, session);
    }

    private void handleInviteCancel(MinecraftServer server, ServerPlayer sender, StagePacket packet) {
        ServerStageSession session = requireHostSession(sender, parseUUID(packet.sessionId));
        UUID targetUUID = parseUUID(packet.targetPlayerId);
        if (session == null || targetUUID == null) {
            return;
        }

        ServerStageMember member = session.members.get(targetUUID);
        if (member == null || member.state != StageMemberState.INVITED) {
            return;
        }

        session.members.remove(targetUUID);
        ServerPlayer target = server.getPlayerList().getPlayer(targetUUID);
        if (target != null) {
            sendPacket(target, sender.getUUID(), packet);
        }
        broadcastSessionState(server, session);
    }

    private void handleInviteResponse(MinecraftServer server, ServerPlayer sender, StagePacket packet) {
        UUID sessionId = parseUUID(packet.sessionId);
        UUID hostUUID = parseUUID(packet.targetPlayerId);
        if (sessionId == null || hostUUID == null || packet.inviteDecision == null) {
            return;
        }

        ServerStageSession session = sessions.get(sessionId);
        if (session == null || !session.hostId.equals(hostUUID)) {
            return;
        }

        ServerStageMember member = session.members.get(sender.getUUID());
        if (member == null) {
            member = new ServerStageMember(sender.getUUID(), sender.getGameProfile().getName(),
                    StageMemberState.INVITED, StageCameraMode.HOST_CAMERA);
            session.members.put(sender.getUUID(), member);
        }

        switch (packet.inviteDecision) {
            case ACCEPT -> {
                member.state = StageMemberState.ACCEPTED;
                member.cameraMode = StageCameraMode.HOST_CAMERA;
                playerSessions.put(sender.getUUID(), sessionId);
            }
            case DECLINE -> {
                member.state = StageMemberState.DECLINED;
                playerSessions.remove(sender.getUUID());
            }
            case BUSY -> {
                member.state = StageMemberState.BUSY;
                playerSessions.remove(sender.getUUID());
            }
        }

        broadcastSessionState(server, session);
    }

    private void handleReadyUpdate(MinecraftServer server, ServerPlayer sender, StagePacket packet) {
        UUID sessionId = parseUUID(packet.sessionId);
        ServerStageSession session = requireMemberSession(sender, sessionId);
        if (session == null) {
            return;
        }

        ServerStageMember member = session.members.get(sender.getUUID());
        if (member == null || !member.state.isAcceptedState()) {
            return;
        }

        member.cameraMode = packet.cameraMode != null ? packet.cameraMode : StageCameraMode.HOST_CAMERA;
        member.motionPackName = isSafeName(packet.motionPackName) ? packet.motionPackName : null;
        member.motionFiles = sanitizeMotionFiles(packet.motionFiles);
        member.state = Boolean.TRUE.equals(packet.ready) ? StageMemberState.READY : StageMemberState.ACCEPTED;
        broadcastSessionState(server, session);
    }

    private void handleMemberLeave(MinecraftServer server, ServerPlayer sender, StagePacket packet) {
        UUID sessionId = parseUUID(packet.sessionId);
        ServerStageSession session = requireMemberSession(sender, sessionId);
        if (session == null) {
            return;
        }

        session.members.remove(sender.getUUID());
        playerSessions.remove(sender.getUUID());
        broadcastSessionState(server, session);
        cleanupIfEmpty(session);
    }

    private void handleSessionDissolve(MinecraftServer server, ServerPlayer sender, StagePacket packet) {
        ServerStageSession session = requireHostSession(sender, parseUUID(packet.sessionId));
        if (session == null) {
            return;
        }
        dissolveSession(server, session, true);
    }

    private void handlePlaybackStart(MinecraftServer server, ServerPlayer sender, StagePacket packet) {
        ServerStageSession session = requireHostSession(sender, parseUUID(packet.sessionId));
        if (session == null || packet.descriptor == null || !packet.descriptor.isValid()) {
            return;
        }

        UUID targetUUID = parseUUID(packet.targetPlayerId);
        if (targetUUID != null) {
            ServerStageMember member = session.members.get(targetUUID);
            if (member == null || !member.state.isAcceptedState()) {
                return;
            }
            ServerPlayer target = server.getPlayerList().getPlayer(targetUUID);
            if (target != null) {
                sendPacket(target, sender.getUUID(), resolveMemberPlaybackPacket(packet, member));
            }
            return;
        }

        for (ServerStageMember member : session.members.values()) {
            if (member.uuid.equals(session.hostId) || !member.state.isAcceptedState()) {
                continue;
            }
            ServerPlayer target = server.getPlayerList().getPlayer(member.uuid);
            if (target != null) {
                sendPacket(target, sender.getUUID(), resolveMemberPlaybackPacket(packet, member));
            }
        }
    }

    private StagePacket resolveMemberPlaybackPacket(StagePacket packet, ServerStageMember member) {
        if (packet.descriptor == null || member.motionFiles.isEmpty()) {
            return packet;
        }

        StagePacket resolvedPacket = copyPacket(packet);
        StageDescriptor descriptor = packet.descriptor.copy();
        descriptor.setMotionFiles(member.motionFiles);
        resolvedPacket.descriptor = descriptor;
        resolvedPacket.motionPackName = member.motionPackName;
        return resolvedPacket;
    }

    private StagePacket copyPacket(StagePacket source) {
        StagePacket copy = new StagePacket(source.type);
        copy.version = source.version;
        copy.sessionId = source.sessionId;
        copy.targetPlayerId = source.targetPlayerId;
        copy.inviteDecision = source.inviteDecision;
        copy.ready = source.ready;
        copy.cameraMode = source.cameraMode;
        copy.frame = source.frame;
        copy.heightOffset = source.heightOffset;
        copy.descriptor = source.descriptor != null ? source.descriptor.copy() : null;
        copy.motionPackName = source.motionPackName;
        copy.motionFiles = source.motionFiles != null ? List.copyOf(source.motionFiles) : List.of();
        copy.members = source.members != null ? List.copyOf(source.members) : List.of();
        return copy;
    }

    private List<String> sanitizeMotionFiles(List<String> motionFiles) {
        if (motionFiles == null || motionFiles.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        for (String motionFile : motionFiles) {
            if (isSafeName(motionFile)) {
                sanitized.add(motionFile);
            }
        }
        return sanitized.isEmpty() ? List.of() : List.copyOf(sanitized);
    }

    private boolean isSafeName(String value) {
        return value != null && !value.isEmpty()
                && !value.contains("..")
                && !value.contains("/")
                && !value.contains("\\");
    }

    private void handlePlaybackStop(MinecraftServer server, ServerPlayer sender, StagePacket packet) {
        ServerStageSession session = requireHostSession(sender, parseUUID(packet.sessionId));
        if (session == null) {
            return;
        }

        UUID targetUUID = parseUUID(packet.targetPlayerId);
        if (targetUUID != null) {
            ServerPlayer target = server.getPlayerList().getPlayer(targetUUID);
            if (target != null) {
                sendPacket(target, sender.getUUID(), packet);
            }
            return;
        }

        for (ServerStageMember member : session.members.values()) {
            if (member.uuid.equals(session.hostId) || !member.state.isAcceptedState()) {
                continue;
            }
            ServerPlayer target = server.getPlayerList().getPlayer(member.uuid);
            if (target != null) {
                sendPacket(target, sender.getUUID(), packet);
            }
        }
    }

    private void handleRemoteStageStart(MinecraftServer server, ServerPlayer sender, StagePacket packet) {
        ServerStageSession session = requireMemberSession(sender, parseUUID(packet.sessionId));
        if (session == null || packet.descriptor == null || !packet.descriptor.isValid()) {
            return;
        }
        broadcastRemotePacket(server, sender, session, packet);
    }

    private void handleRemoteStageStop(MinecraftServer server, ServerPlayer sender, StagePacket packet) {
        ServerStageSession session = requireMemberSession(sender, parseUUID(packet.sessionId));
        if (session == null) {
            return;
        }
        broadcastRemotePacket(server, sender, session, packet);
    }

    private void handleFrameSync(MinecraftServer server, ServerPlayer sender, StagePacket packet) {
        ServerStageSession session = requireHostSession(sender, parseUUID(packet.sessionId));
        if (session == null || packet.frame == null) {
            return;
        }

        for (ServerStageMember member : session.members.values()) {
            if (member.uuid.equals(session.hostId) || !member.state.isAcceptedState()) {
                continue;
            }
            ServerPlayer target = server.getPlayerList().getPlayer(member.uuid);
            if (target != null) {
                sendPacket(target, sender.getUUID(), packet);
            }
        }
    }

    private ServerStageSession ensureHostSession(MinecraftServer server, ServerPlayer sender, UUID sessionId) {
        UUID senderUUID = sender.getUUID();
        UUID existingSessionId = playerSessions.get(senderUUID);
        if (existingSessionId != null && !existingSessionId.equals(sessionId)) {
            ServerStageSession existingSession = sessions.get(existingSessionId);
            if (existingSession != null && senderUUID.equals(existingSession.hostId)) {
                dissolveSession(server, existingSession, true);
            } else {
                return null;
            }
        }

        ServerStageSession session = sessions.computeIfAbsent(sessionId, id -> new ServerStageSession(id, senderUUID));
        if (!session.hostId.equals(senderUUID)) {
            return null;
        }

        playerSessions.put(senderUUID, sessionId);
        session.members.put(senderUUID, new ServerStageMember(
                senderUUID,
                sender.getGameProfile().getName(),
                StageMemberState.HOST,
                StageCameraMode.HOST_CAMERA
        ));
        return session;
    }

    private ServerStageSession requireHostSession(ServerPlayer sender, UUID sessionId) {
        if (sessionId == null) {
            return null;
        }
        ServerStageSession session = sessions.get(sessionId);
        if (session == null || !session.hostId.equals(sender.getUUID())) {
            return null;
        }
        return session;
    }

    private ServerStageSession requireMemberSession(ServerPlayer sender, UUID sessionId) {
        if (sessionId == null) {
            return null;
        }
        UUID indexedSession = playerSessions.get(sender.getUUID());
        if (!Objects.equals(indexedSession, sessionId)) {
            return null;
        }
        return sessions.get(sessionId);
    }

    private void broadcastSessionState(MinecraftServer server, ServerStageSession session) {
        StagePacket packet = new StagePacket(StagePacketType.SESSION_STATE);
        packet.sessionId = session.sessionId.toString();
        packet.members = buildSnapshots(session);

        ServerPlayer host = server.getPlayerList().getPlayer(session.hostId);
        if (host != null) {
            sendPacket(host, session.hostId, packet);
        }

        for (ServerStageMember member : session.members.values()) {
            if (member.uuid.equals(session.hostId) || !member.state.isAcceptedState()) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(member.uuid);
            if (player != null) {
                sendPacket(player, session.hostId, packet);
            }
        }
    }

    private List<StageMemberSnapshot> buildSnapshots(ServerStageSession session) {
        List<ServerStageMember> ordered = new ArrayList<>(session.members.values());
        ordered.sort(Comparator
                .comparingInt((ServerStageMember member) -> member.state == StageMemberState.HOST ? 0 : 1)
                .thenComparing(member -> member.name, String.CASE_INSENSITIVE_ORDER));

        List<StageMemberSnapshot> snapshots = new ArrayList<>();
        for (ServerStageMember member : ordered) {
            snapshots.add(new StageMemberSnapshot(
                    member.uuid.toString(),
                    member.name,
                    member.state.name(),
                    member.cameraMode.name()
            ));
        }
        return snapshots;
    }

    private void dissolveSession(MinecraftServer server, ServerStageSession session, boolean notifyMembers) {
        if (notifyMembers) {
            StagePacket packet = new StagePacket(StagePacketType.SESSION_DISSOLVE);
            packet.sessionId = session.sessionId.toString();
            for (ServerStageMember member : session.members.values()) {
                if (member.uuid.equals(session.hostId)) {
                    continue;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(member.uuid);
                if (player != null) {
                    sendPacket(player, session.hostId, packet);
                }
            }
        }

        for (ServerStageMember member : session.members.values()) {
            playerSessions.remove(member.uuid);
        }
        sessions.remove(session.sessionId);
    }

    private void cleanupIfEmpty(ServerStageSession session) {
        boolean hasAnyGuest = session.members.values().stream().anyMatch(member -> !member.uuid.equals(session.hostId));
        if (!hasAnyGuest) {
            sessions.remove(session.sessionId);
            playerSessions.remove(session.hostId);
        }
    }

    private void sendPacket(ServerPlayer target, UUID sourceUUID, StagePacket packet) {
        PacketDistributor.sendToPlayer(
                target,
                MmdSkinNetworkPack.withAnimId(NetworkOpCode.STAGE_MULTI, sourceUUID, StagePacketCodec.encode(packet))
        );
    }

    private void broadcastRemotePacket(MinecraftServer server, ServerPlayer sender,
                                       ServerStageSession session, StagePacket packet) {
        StagePacket resolvedPacket = copyPacket(packet);
        resolvedPacket.sessionId = session.sessionId.toString();
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            if (!target.getUUID().equals(sender.getUUID())) {
                sendPacket(target, sender.getUUID(), resolvedPacket);
            }
        }
    }

    private UUID parseUUID(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static final class ServerStageSession {
        private final UUID sessionId;
        private final UUID hostId;
        private final Map<UUID, ServerStageMember> members = new LinkedHashMap<>();

        private ServerStageSession(UUID sessionId, UUID hostId) {
            this.sessionId = sessionId;
            this.hostId = hostId;
        }
    }

    private static final class ServerStageMember {
        private final UUID uuid;
        private final String name;
        private StageMemberState state;
        private StageCameraMode cameraMode;
        private String motionPackName;
        private List<String> motionFiles = List.of();

        private ServerStageMember(UUID uuid, String name, StageMemberState state, StageCameraMode cameraMode) {
            this.uuid = uuid;
            this.name = name;
            this.state = state;
            this.cameraMode = cameraMode;
        }
    }
}
