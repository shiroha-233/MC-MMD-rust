package com.shiroha.mmdskin.neoforge.stage;

import com.shiroha.mmdskin.neoforge.network.MmdSkinPayload;
import com.shiroha.mmdskin.stage.protocol.StagePacket;
import com.shiroha.mmdskin.stage.protocol.StagePacketCodec;
import com.shiroha.mmdskin.stage.server.application.StageServerSessionService;
import com.shiroha.mmdskin.stage.server.application.port.StageServerPlatformPort;
import com.shiroha.mmdskin.stage.server.domain.model.StageServerPlayer;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public final class NeoForgeStageSessionRegistry {
    private static final NeoForgeStageSessionRegistry INSTANCE = new NeoForgeStageSessionRegistry();
    private final StageServerSessionService service = StageServerSessionService.getInstance();

    private NeoForgeStageSessionRegistry() {
    }

    public static NeoForgeStageSessionRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized void handlePacket(MinecraftServer server, ServerPlayer sender, String rawData) {
        service.handlePacket(new NeoForgePlatformPort(server), toStageServerPlayer(sender), rawData);
    }

    public synchronized void onPlayerDisconnect(MinecraftServer server, ServerPlayer player) {
        service.onPlayerDisconnect(new NeoForgePlatformPort(server), player.getUUID());
    }

    private StageServerPlayer toStageServerPlayer(ServerPlayer player) {
        return new StageServerPlayer(player.getUUID(), player.getGameProfile().name());
    }

    private static final class NeoForgePlatformPort implements StageServerPlatformPort {
        private final MinecraftServer server;

        private NeoForgePlatformPort(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public StageServerPlayer findPlayer(UUID playerId) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                return null;
            }
            return new StageServerPlayer(player.getUUID(), player.getGameProfile().name());
        }

        @Override
        public List<StageServerPlayer> getOnlinePlayers() {
            return server.getPlayerList().getPlayers().stream()
                    .map(player -> new StageServerPlayer(player.getUUID(), player.getGameProfile().name()))
                    .toList();
        }

        @Override
        public void sendPacket(UUID targetPlayerId, UUID sourcePlayerId, StagePacket packet) {
            ServerPlayer target = server.getPlayerList().getPlayer(targetPlayerId);
            if (target == null) {
                return;
            }
            PacketDistributor.sendToPlayer(target,
                MmdSkinPayload.createString(NetworkOpCode.STAGE_MULTI, sourcePlayerId, StagePacketCodec.encode(packet)));
        }
    }
}
