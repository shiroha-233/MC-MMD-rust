package com.shiroha.mmdskin.fabric.register;

import com.shiroha.mmdskin.fabric.network.MmdSkinPayload;
import com.shiroha.mmdskin.fabric.stage.FabricStageSessionRegistry;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.network.ServerModelRegistry;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

/**
 * Fabric 服务端网络注册
 */
public class MmdSkinRegisterCommon {
    private static final Logger logger = LogManager.getLogger();

    public static void Register() {
        PayloadTypeRegistry.playC2S().register(MmdSkinPayload.TYPE, MmdSkinPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MmdSkinPayload.TYPE, MmdSkinPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(MmdSkinPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            UUID realUUID = player.getUUID();

            if (!realUUID.equals(payload.playerUUID())) {
                logger.warn("UUID 不匹配，丢弃数据包: claimed={}, real={}", payload.playerUUID(), realUUID);
                return;
            }

            int opCode = payload.opCode();

            if (opCode == NetworkOpCode.MODEL_SELECT) {
                ServerModelRegistry.updateModel(realUUID, payload.stringArg());
            }

            if (opCode == NetworkOpCode.REQUEST_ALL_MODELS) {
                context.server().execute(() -> {
                    ServerModelRegistry.sendAllTo((modelOwnerUUID, modelName) -> {
                        MmdSkinPayload reply = MmdSkinPayload.createString(NetworkOpCode.MODEL_SELECT, modelOwnerUUID, modelName);
                        ServerPlayNetworking.send(player, reply);
                    });
                });
                return;
            }

            if (opCode == NetworkOpCode.STAGE_MULTI) {
                String stagePayload = payload.stringArg();
                if (stagePayload != null && !stagePayload.isEmpty()) {
                    context.server().execute(() ->
                        FabricStageSessionRegistry.getInstance().handlePacket(context.server(), player, stagePayload));
                }
                return;
            }

            MmdSkinPayload corrected = new MmdSkinPayload(
                opCode, realUUID, payload.intArg(), payload.entityId(), payload.stringArg(), payload.binaryData());

            context.server().execute(() -> {
                for (ServerPlayer serverPlayer : PlayerLookup.all(context.server())) {
                    if (!serverPlayer.equals(player)) {
                        ServerPlayNetworking.send(serverPlayer, corrected);
                    }
                }
            });
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> {
                    ServerModelRegistry.onPlayerLeave(handler.getPlayer().getUUID());
                    FabricStageSessionRegistry.getInstance().onPlayerDisconnect(server, handler.getPlayer());
                });
    }
}

