package com.shiroha.mmdskin.fabric.stage;

import com.shiroha.mmdskin.fabric.register.MmdSkinRegisterCommon;
import com.shiroha.mmdskin.stage.protocol.StagePacket;
import com.shiroha.mmdskin.stage.protocol.StagePacketCodec;
import com.shiroha.mmdskin.stage.server.application.StageServerSessionService;
import com.shiroha.mmdskin.stage.server.application.port.StageServerPlatformPort;
import com.shiroha.mmdskin.stage.server.domain.model.StageServerPlayer;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public final class FabricStageSessionRegistry {
    private static final FabricStageSessionRegistry INSTANCE = new FabricStageSessionRegistry();
    private final StageServerSessionService service = StageServerSessionService.getInstance();

    private FabricStageSessionRegistry() {
    }

    public static FabricStageSessionRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized void handlePacket(MinecraftServer server, ServerPlayer sender, String rawData) {
        service.handlePacket(new FabricPlatformPort(server), toStageServerPlayer(sender), rawData);
    }

    public synchronized void onPlayerDisconnect(MinecraftServer server, ServerPlayer player) {
        service.onPlayerDisconnect(new FabricPlatformPort(server), player.getUUID());
    }

    private StageServerPlayer toStageServerPlayer(ServerPlayer player) {
        return new StageServerPlayer(player.getUUID(), player.getGameProfile().getName());
    }

    private static final class FabricPlatformPort implements StageServerPlatformPort {
        private final MinecraftServer server;

        private FabricPlatformPort(MinecraftServer server) {
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
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeInt(NetworkOpCode.STAGE_MULTI);
            buf.writeUUID(sourcePlayerId);
            buf.writeUtf(StagePacketCodec.encode(packet));
            ServerPlayNetworking.send(target, MmdSkinRegisterCommon.SKIN_S2C, buf);
        }
    }
}
