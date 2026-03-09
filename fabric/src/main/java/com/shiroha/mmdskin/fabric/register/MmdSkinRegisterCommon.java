package com.shiroha.mmdskin.fabric.register;

import com.shiroha.mmdskin.fabric.stage.FabricStageSessionRegistry;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.network.ServerModelRegistry;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

/**
 * Fabric 服务端网络注册
 */
public class MmdSkinRegisterCommon {
    private static final Logger logger = LogManager.getLogger();

    public static ResourceLocation SKIN_C2S = new ResourceLocation("3d-skin", "network_c2s");
    public static ResourceLocation SKIN_S2C = new ResourceLocation("3d-skin", "network_s2c");

    public static void Register() {
        ServerPlayNetworking.registerGlobalReceiver(SKIN_C2S, (server, player, handler, buf, responseSender) -> {
            int opCode = buf.readInt();
            UUID claimedUUID = buf.readUUID();

            UUID realUUID = player.getUUID();
            if (!realUUID.equals(claimedUUID)) {
                logger.warn("UUID 不匹配，丢弃数据包: claimed={}, real={}", claimedUUID, realUUID);
                return;
            }

            String strData = null;
            int entityId = 0;
            int intArg = 0;

            if (NetworkOpCode.isStringPayload(opCode)) {
                strData = buf.readUtf();
            } else if (NetworkOpCode.isEntityStringPayload(opCode)) {
                entityId = buf.readInt();
                strData = buf.readUtf();
            } else {
                intArg = buf.readInt();
            }

            if (opCode == NetworkOpCode.MODEL_SELECT && strData != null) {
                ServerModelRegistry.updateModel(realUUID, strData);
            }

            if (opCode == NetworkOpCode.REQUEST_ALL_MODELS) {
                server.execute(() -> {
                    ServerModelRegistry.sendAllTo((modelOwnerUUID, modelName) -> {
                        FriendlyByteBuf replyBuf = PacketByteBufs.create();
                        replyBuf.writeInt(NetworkOpCode.MODEL_SELECT);
                        replyBuf.writeUUID(modelOwnerUUID);
                        replyBuf.writeUtf(modelName);
                        ServerPlayNetworking.send(player, SKIN_S2C, replyBuf);
                    });
                });
                return;
            }

            if (opCode == NetworkOpCode.STAGE_MULTI && strData != null) {
                final String stagePayload = strData;
                server.execute(() -> FabricStageSessionRegistry.getInstance().handlePacket(server, player, stagePayload));
                return;
            }

            final FriendlyByteBuf packetBuf = PacketByteBufs.create();
            packetBuf.writeInt(opCode);
            packetBuf.writeUUID(realUUID);

            if (NetworkOpCode.isStringPayload(opCode)) {
                packetBuf.writeUtf(strData);
            } else if (NetworkOpCode.isEntityStringPayload(opCode)) {
                packetBuf.writeInt(entityId);
                packetBuf.writeUtf(strData);
            } else {
                packetBuf.writeInt(intArg);
            }

            server.execute(() -> {
                for (ServerPlayer serverPlayer : PlayerLookup.all(server)) {
                    if (!serverPlayer.equals(player)) {
                        FriendlyByteBuf copyBuf = PacketByteBufs.copy(packetBuf);
                        ServerPlayNetworking.send(serverPlayer, SKIN_S2C, copyBuf);
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

