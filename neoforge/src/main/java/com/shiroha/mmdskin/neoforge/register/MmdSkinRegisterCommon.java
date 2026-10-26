// 负责 NeoForge 侧网络 Payload 注册、模型分发推送与玩家退出清理。
package com.shiroha.mmdskin.neoforge.register;

import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.distrib.ModelDistributionService;
import com.shiroha.mmdskin.neoforge.network.MmdSkinPayload;
import com.shiroha.mmdskin.neoforge.stage.NeoForgeStageSessionRegistry;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.network.ServerModelRegistry;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * NeoForge 网络注册
 *
 * <p>26.3 适配：NeoForge 1.21.4 起使用新的 Payload 系统；
 * 26.3 里 {@code EventBusSubscriber} 不再区分 MOD / GAME 两条总线
 * （事件自己实现 {@code IModBusEvent} 就走 mod 总线），
 * 所以这里统一用 {@code NeoForge.EVENT_BUS.addListener} 显式注册游戏侧事件。
 */
public class MmdSkinRegisterCommon {
    private static final Logger logger = LogManager.getLogger();

    /**
     * 推送可分发模型清单给玩家。
     */
    public static void sendManifestTo(ServerPlayer player) {
        if (player == null || !ModelDistributionService.isEnabled()) {
            return;
        }
        byte[] payload = ModelDistributionService.buildManifestList();
        if (payload == null || payload.length == 0) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
                MmdSkinPayload.createBinary(NetworkOpCode.DISTRIB_MANIFEST, player.getUUID(), payload));
    }

    public static void Register() {
        // 玩家进服：推送可分发模型清单
        NeoForge.EVENT_BUS.addListener(MmdSkinRegisterCommon::onPlayerLoggedIn);
        // 玩家退出：清理模型登记表、分发队列与舞台会话
        NeoForge.EVENT_BUS.addListener(MmdSkinRegisterCommon::onPlayerLoggedOut);
        // 服务端 tick：按房主配置的速率把排队中的模型分块推给客户端；
        // 房主改了配置就实时把新清单推给在线玩家（不用重连）
        NeoForge.EVENT_BUS.addListener(MmdSkinRegisterCommon::onServerTick);

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
                PacketDistributor.sendToPlayer(player,
                        MmdSkinPayload.createString(
                                NetworkOpCode.DISTRIB_COMPLETE, player.getUUID(), modelName == null ? "" : modelName));
            }

            private void sendTo(ServerPlayer player, int opCode, byte[] payload) {
                if (player == null) {
                    return;
                }
                PacketDistributor.sendToPlayer(player,
                        MmdSkinPayload.createBinary(
                                opCode, player.getUUID(), payload == null ? new byte[0] : payload));
            }
        });
    }

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MmdSkin.MOD_ID);
        registrar.playBidirectional(
            MmdSkinPayload.TYPE,
            MmdSkinPayload.CODEC,
            MmdSkinRegisterCommon::handleServerPayload,
            MmdSkinRegisterCommon::handleClientPayload
        );
    }

    /**
     * 服务端收包：处理模型选择 / 全量模型请求 / 模型分发请求 / 舞台消息，
     * 其余操作码广播给其他玩家。
     */
    private static void handleServerPayload(MmdSkinPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
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
            context.enqueueWork(() -> ServerModelRegistry.sendAllTo((modelOwnerUUID, modelName) -> {
                MmdSkinPayload reply = MmdSkinPayload.createString(NetworkOpCode.MODEL_SELECT, modelOwnerUUID, modelName);
                PacketDistributor.sendToPlayer(player, reply);
            }));
            return;
        }

        // 模型分发：客户端请求某个模型（带断点续传偏移），格式 "模型名|首个未完成的文件相对路径"
        if (opCode == NetworkOpCode.DISTRIB_REQUEST) {
            String request = payload.stringArg();
            if (request != null && !request.isEmpty()) {
                context.enqueueWork(() -> {
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
                context.enqueueWork(() ->
                        NeoForgeStageSessionRegistry.getInstance().handlePacket(player.level().getServer(), player, stagePayload));
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

        context.enqueueWork(() -> {
            MinecraftServer server = player.level().getServer();
            if (server == null) {
                return;
            }
            for (ServerPlayer serverPlayer : server.getPlayerList().getPlayers()) {
                if (!serverPlayer.equals(player)) {
                    PacketDistributor.sendToPlayer(serverPlayer, corrected);
                }
            }
        });
    }

    private static void handleClientPayload(MmdSkinPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.shiroha.mmdskin.neoforge.network.MmdSkinNetworkPack.handlePayload(payload));
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendManifestTo(player);
        }
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().getServer() != null) {
            ServerModelRegistry.onPlayerLeave(player.getUUID());
            ModelDistributionService.onPlayerLeave(player.getUUID());
            NeoForgeStageSessionRegistry.getInstance().onPlayerDisconnect(player.level().getServer(), player);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        ModelDistributionService.tickServer();
        if (!ModelDistributionService.pollConfigChange()) {
            return;
        }
        byte[] payload = ModelDistributionService.buildManifestList();
        if (payload == null || payload.length == 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(online,
                    MmdSkinPayload.createBinary(
                            NetworkOpCode.DISTRIB_MANIFEST, online.getUUID(), payload));
        }
    }
}
