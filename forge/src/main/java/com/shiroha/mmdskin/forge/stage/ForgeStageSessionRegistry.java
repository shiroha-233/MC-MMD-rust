package com.shiroha.mmdskin.forge.stage;

import com.shiroha.mmdskin.forge.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.forge.register.MmdSkinRegisterCommon;
import com.shiroha.mmdskin.stage.protocol.StagePacket;
import com.shiroha.mmdskin.stage.protocol.StagePacketCodec;
import com.shiroha.mmdskin.stage.server.application.StageServerSessionService;
import com.shiroha.mmdskin.stage.server.application.port.StageServerPlatformPort;
import com.shiroha.mmdskin.stage.server.domain.model.StageServerPlayer;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

public final class ForgeStageSessionRegistry {
    private static final ForgeStageSessionRegistry INSTANCE = new ForgeStageSessionRegistry();
    private final StageServerSessionService service = StageServerSessionService.getInstance();

    private ForgeStageSessionRegistry() {
    }

    public static ForgeStageSessionRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized void handlePacket(MinecraftServer server, ServerPlayer sender, String rawData) {
        service.handlePacket(new ForgePlatformPort(server), toStageServerPlayer(sender), rawData);
    }

    public synchronized void onPlayerDisconnect(MinecraftServer server, ServerPlayer player) {
        service.onPlayerDisconnect(new ForgePlatformPort(server), player.getUUID());
    }

    private StageServerPlayer toStageServerPlayer(ServerPlayer player) {
        return new StageServerPlayer(player.getUUID(), player.getGameProfile().getName());
    }

    private static final class ForgePlatformPort implements StageServerPlatformPort {
        private final MinecraftServer server;

        private ForgePlatformPort(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public StageServerPlayer findPlayer(UUID playerId) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                return null;
            }
            return new StageServerPlayer(player.getUUID(), player.getGameProfile().getName());
        }

        @Override
        public List<StageServerPlayer> getOnlinePlayers() {
            return server.getPlayerList().getPlayers().stream()
                    .map(player -> new StageServerPlayer(player.getUUID(), player.getGameProfile().getName()))
                    .toList();
        }

        @Override
        public void sendPacket(UUID targetPlayerId, UUID sourcePlayerId, StagePacket packet) {
            ServerPlayer target = server.getPlayerList().getPlayer(targetPlayerId);
            if (target == null) {
                return;
            }
            MmdSkinRegisterCommon.channel.send(
                    PacketDistributor.PLAYER.with(() -> target),
                    new MmdSkinNetworkPack(NetworkOpCode.STAGE_MULTI, sourcePlayerId, StagePacketCodec.encode(packet))
            );
        }
    }
}
