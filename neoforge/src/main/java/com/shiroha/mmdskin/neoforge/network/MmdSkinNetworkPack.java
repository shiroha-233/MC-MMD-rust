package com.shiroha.mmdskin.neoforge.network;

import java.util.UUID;

import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.maid.MaidMMDModelManager;
import com.shiroha.mmdskin.neoforge.register.MmdSkinAttachments;
import com.shiroha.mmdskin.neoforge.stage.NeoForgeStageSessionRegistry;
import com.shiroha.mmdskin.player.animation.PendingAnimSignalCache;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.player.sync.MorphSyncHelper;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * NeoForge 网络包处理（Payload + 服务端/客户端逻辑）
 */
public record MmdSkinNetworkPack(int opCode, UUID playerUUID, String animId, int arg0, byte[] binaryData) implements CustomPacketPayload {
    private static final Logger logger = LogManager.getLogger();

    public static final Type<MmdSkinNetworkPack> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MmdSkin.MOD_ID, "network_pack"));

    public static final StreamCodec<FriendlyByteBuf, MmdSkinNetworkPack> STREAM_CODEC = StreamCodec.of(
        MmdSkinNetworkPack::encode,
        MmdSkinNetworkPack::decode
    );

    public static MmdSkinNetworkPack withAnimId(int opCode, UUID playerUUID, String animId) {
        return new MmdSkinNetworkPack(opCode, playerUUID, animId, 0, new byte[0]);
    }

    public static MmdSkinNetworkPack withArg(int opCode, UUID playerUUID, int arg0) {
        return new MmdSkinNetworkPack(opCode, playerUUID, "", arg0, new byte[0]);
    }

    public static MmdSkinNetworkPack forMaid(int opCode, UUID playerUUID, int entityId, String modelName) {
        return new MmdSkinNetworkPack(opCode, playerUUID, modelName, entityId, new byte[0]);
    }

    public static MmdSkinNetworkPack withBinary(int opCode, UUID playerUUID, byte[] data) {
        return new MmdSkinNetworkPack(opCode, playerUUID, "", 0, data);
    }

    private static void encode(FriendlyByteBuf buffer, MmdSkinNetworkPack pack) {
        buffer.writeInt(pack.opCode);
        buffer.writeUUID(pack.playerUUID);
        if (NetworkOpCode.isStringPayload(pack.opCode)) {
            buffer.writeUtf(pack.animId);
        } else if (NetworkOpCode.isEntityStringPayload(pack.opCode)) {
            buffer.writeInt(pack.arg0);
            buffer.writeUtf(pack.animId);
        } else {
            buffer.writeInt(pack.arg0);
        }
        buffer.writeByteArray(pack.binaryData);
    }

    private static MmdSkinNetworkPack decode(FriendlyByteBuf buffer) {
        int opCode = buffer.readInt();
        UUID playerUUID = buffer.readUUID();
        String animId = "";
        int arg0 = 0;

        if (NetworkOpCode.isStringPayload(opCode)) {
            animId = buffer.readUtf();
        } else if (NetworkOpCode.isEntityStringPayload(opCode)) {
            arg0 = buffer.readInt();
            animId = buffer.readUtf();
        } else {
            arg0 = buffer.readInt();
        }
        byte[] binaryData = buffer.readByteArray();
        return new MmdSkinNetworkPack(opCode, playerUUID, animId, arg0, binaryData);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MmdSkinNetworkPack pack, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sender) {
                handleServer(pack, sender);
            } else {
                pack.handleClient();
            }
        });
    }

    private static void handleServer(MmdSkinNetworkPack pack, ServerPlayer sender) {
        // UUID 鉴权
        UUID realUUID = sender.getUUID();
        if (!realUUID.equals(pack.playerUUID)) {
            logger.warn("UUID 不匹配，丢弃数据包: claimed={}, real={}", pack.playerUUID, realUUID);
            return;
        }

        // REQUEST_ALL_MODELS：回传所有已注册模型给请求者
        if (pack.opCode == NetworkOpCode.REQUEST_ALL_MODELS) {
            for (ServerPlayer player : sender.getServer().getPlayerList().getPlayers()) {
                String modelName = player.getData(MmdSkinAttachments.PLAYER_MMD_MODEL.get());
                if (modelName != null && !modelName.isEmpty()) {
                    PacketDistributor.sendToPlayer(sender,
                            MmdSkinNetworkPack.withAnimId(NetworkOpCode.MODEL_SELECT, player.getUUID(), modelName));
                }
            }
            return;
        }

        if (pack.opCode == NetworkOpCode.STAGE_MULTI) {
            NeoForgeStageSessionRegistry.getInstance().handlePacket(sender.getServer(), sender, pack.animId);
            return;
        }

        // 女仆模型绑定存入附件
        if (pack.opCode == NetworkOpCode.MAID_MODEL) {
            Entity entity = sender.level().getEntity(pack.arg0);
            if (entity != null) {
                entity.setData(MmdSkinAttachments.MAID_MMD_MODEL.get(), pack.animId);
            }
        }

        // 模型选择存入附件
        if (pack.opCode == NetworkOpCode.MODEL_SELECT) {
            sender.setData(MmdSkinAttachments.PLAYER_MMD_MODEL.get(), pack.animId);
        }

        // 用真实 UUID 重建后转发
        MmdSkinNetworkPack corrected = new MmdSkinNetworkPack(
                pack.opCode, realUUID, pack.animId, pack.arg0, pack.binaryData);
        for (ServerPlayer player : sender.getServer().getPlayerList().getPlayers()) {
            if (!player.equals(sender)) {
                PacketDistributor.sendToPlayer(player, corrected);
            }
        }
    }

    private void handleClient() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (this.opCode == NetworkOpCode.STAGE_MULTI) {
            com.shiroha.mmdskin.stage.client.StageClientPacketHandler.getInstance().handle(this.playerUUID, this.animId);
            return;
        }
        if (mc.level == null || this.playerUUID.equals(mc.player.getUUID())) return;

        Player target = mc.level.getPlayerByUUID(this.playerUUID);

        if (NetworkOpCode.isEntityStringPayload(this.opCode)) {
            Entity maidEntity = mc.level.getEntity(this.arg0);
            if (maidEntity == null) return;
            switch (this.opCode) {
                case NetworkOpCode.MAID_MODEL -> MaidMMDModelManager.bindModel(maidEntity.getUUID(), this.animId);
                case NetworkOpCode.MAID_ACTION -> MaidMMDModelManager.playAnimation(maidEntity.getUUID(), this.animId);
                default -> {}
            }
        } else if (NetworkOpCode.isStringPayload(this.opCode)) {
            switch (this.opCode) {
                case NetworkOpCode.CUSTOM_ANIM -> {
                    if (target != null) MmdSkinRendererPlayerHelper.CustomAnim(target, this.animId);
                }
                case NetworkOpCode.MODEL_SELECT -> {
                    PlayerModelSyncManager.onRemotePlayerModelReceived(this.playerUUID, this.animId);
                }
                case NetworkOpCode.MORPH_SYNC -> {
                    if (target != null) MorphSyncHelper.applyRemoteMorph(target, this.animId);
                }
                default -> {}
            }
        } else {
            if (this.opCode == NetworkOpCode.RESET_PHYSICS) {
                if (target != null) {
                    MmdSkinRendererPlayerHelper.ResetPhysics(target);
                } else {
                    PendingAnimSignalCache.put(this.playerUUID, PendingAnimSignalCache.SignalType.RESET);
                }
            }
        }
    }
}
