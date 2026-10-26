package com.shiroha.mmdskin.fabric.register;

import com.shiroha.mmdskin.distrib.ModelDistributionService;
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

    /**
     * 推送可分发模型清单给玩家。
     *
     * <p>26.3 适配：1.20.1 用 {@code FriendlyByteBuf} + 通道 id 手写信封，
     * 26.3 改成了单个 {@link MmdSkinPayload} record，二进制载荷走 {@code binaryData}。
     */
    public static void sendManifestTo(ServerPlayer player) {
        if (player == null || !ModelDistributionService.isEnabled()) {
            return;
        }
        byte[] payload = ModelDistributionService.buildManifestList();
        if (payload == null || payload.length == 0) {
            return;
        }
        ServerPlayNetworking.send(player,
                MmdSkinPayload.createBinary(NetworkOpCode.DISTRIB_MANIFEST, player.getUUID(), payload));
    }

    public static void Register() {
        // 26.3 的 PayloadTypeRegistry 改成了"方向 + 阶段"命名：playC2S/playS2C →
        // serverboundPlay/clientboundPlay。
        PayloadTypeRegistry.serverboundPlay().register(MmdSkinPayload.TYPE, MmdSkinPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MmdSkinPayload.TYPE, MmdSkinPayload.CODEC);

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

            // 模型分发：客户端请求某个模型（带断点续传偏移），格式 "模型名|首个未完成的文件相对路径"
            if (opCode == NetworkOpCode.DISTRIB_REQUEST) {
                String request = payload.stringArg();
                if (request != null && !request.isEmpty()) {
                    context.server().execute(() -> {
                        int separator = request.lastIndexOf('|');
                        String modelName = separator > 0 ? request.substring(0, separator) : request;
                        String firstIncompletePath = separator > 0 ? request.substring(separator + 1) : "";
                        ModelDistributionService.enqueueModel(
                                player, player.getUUID(), modelName, firstIncompletePath);
                    });
                }
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

            // 分发相关的操作码必须早退：它们是"服务器 → 某个客户端"的定向消息，
            // 不能落到下面的广播分支被群发给所有其他玩家。
            if (NetworkOpCode.isDistribution(opCode)) {
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

        // 玩家进服：推送可分发模型清单
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> sendManifestTo(handler.getPlayer()));

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> {
                    ServerModelRegistry.onPlayerLeave(handler.getPlayer().getUUID());
                    ModelDistributionService.onPlayerLeave(handler.getPlayer().getUUID());
                    FabricStageSessionRegistry.getInstance().onPlayerDisconnect(server, handler.getPlayer());
                });

        // 服务端 tick：按房主配置的速率把排队中的模型分块推给客户端；
        // 房主改了配置就实时把新清单推给在线玩家（不用重连）
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(
                server -> {
                    ModelDistributionService.tickServer();
                    if (ModelDistributionService.pollConfigChange()) {
                        byte[] payload = ModelDistributionService.buildManifestList();
                        if (payload != null && payload.length > 0) {
                            for (ServerPlayer online : PlayerLookup.all(server)) {
                                ServerPlayNetworking.send(online,
                                        MmdSkinPayload.createBinary(
                                                NetworkOpCode.DISTRIB_MANIFEST, online.getUUID(), payload));
                            }
                        }
                    }
                });

        ModelDistributionService.setSender(new ModelDistributionService.Sender() {
            @Override
            public void sendManifest(Object handle, byte[] payload) {
                sendTo((ServerPlayer) handle, NetworkOpCode.DISTRIB_MANIFEST, payload);
            }

            @Override
            public void sendOffer(Object handle, byte[] payload) {
                sendTo((ServerPlayer) handle, NetworkOpCode.DISTRIB_OFFER, payload);
            }

            @Override
            public void sendChunk(Object handle, byte[] payload) {
                sendTo((ServerPlayer) handle, NetworkOpCode.DISTRIB_CHUNK, payload);
            }

            @Override
            public void sendComplete(Object handle, String modelName) {
                ServerPlayer player = (ServerPlayer) handle;
                if (player == null) {
                    return;
                }
                ServerPlayNetworking.send(player,
                        MmdSkinPayload.createString(
                                NetworkOpCode.DISTRIB_COMPLETE, player.getUUID(), modelName == null ? "" : modelName));
            }

            private void sendTo(ServerPlayer player, int opCode, byte[] payload) {
                if (player == null) {
                    return;
                }
                ServerPlayNetworking.send(player,
                        MmdSkinPayload.createBinary(
                                opCode, player.getUUID(), payload == null ? new byte[0] : payload));
            }
        });
    }
}
