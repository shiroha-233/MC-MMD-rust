package com.shiroha.mmdskin.forge.network;

import com.shiroha.mmdskin.forge.register.MmdSkinRegisterCommon;
import com.shiroha.mmdskin.forge.stage.ForgeStageSessionRegistry;
import com.shiroha.mmdskin.maid.MaidMMDModelManager;
import com.shiroha.mmdskin.player.animation.PendingAnimSignalCache;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.player.sync.MorphSyncHelper;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import com.shiroha.mmdskin.ui.network.ServerModelRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Forge 网络包序列化与处理
 */
public class MmdSkinNetworkPack {
    private static final Logger logger = LogManager.getLogger();

    public int opCode;
    public UUID playerUUID;
    public String animId;
    public int arg0;

    public MmdSkinNetworkPack(int opCode, UUID playerUUID, String animId) {
        this.opCode = opCode;
        this.playerUUID = playerUUID;
        this.animId = animId;
        this.arg0 = 0;
    }

    public MmdSkinNetworkPack(int opCode, UUID playerUUID, int arg0) {
        this.opCode = opCode;
        this.playerUUID = playerUUID;
        this.animId = "";
        this.arg0 = arg0;
    }

    public MmdSkinNetworkPack(int opCode, UUID playerUUID, int entityId, String modelName) {
        this.opCode = opCode;
        this.playerUUID = playerUUID;
        this.animId = modelName;
        this.arg0 = entityId;
    }

    public MmdSkinNetworkPack(FriendlyByteBuf buffer) {
        opCode = buffer.readInt();
        playerUUID = buffer.readUUID();

        if (NetworkOpCode.isStringPayload(opCode)) {
            animId = buffer.readUtf();
            arg0 = 0;
        } else if (NetworkOpCode.isEntityStringPayload(opCode)) {
            arg0 = buffer.readInt();
            animId = buffer.readUtf();
        } else {
            animId = "";
            arg0 = buffer.readInt();
        }
    }

    public void pack(FriendlyByteBuf buffer) {
        buffer.writeInt(opCode);
        buffer.writeUUID(playerUUID);

        if (NetworkOpCode.isStringPayload(opCode)) {
            buffer.writeUtf(animId);
        } else if (NetworkOpCode.isEntityStringPayload(opCode)) {
            buffer.writeInt(arg0);
            buffer.writeUtf(animId);
        } else {
            buffer.writeInt(arg0);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide() == net.minecraftforge.fml.LogicalSide.CLIENT) {
                doInClient();
            } else {
                handleOnServer(ctx.get());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private void handleOnServer(NetworkEvent.Context ctx) {
        ServerPlayer sender = ctx.getSender();
        if (sender == null) return;

        if (!sender.getUUID().equals(playerUUID)) {
            logger.warn("UUID 不匹配，丢弃数据包: claimed={}, real={}", playerUUID, sender.getUUID());
            return;
        }

        if (opCode == NetworkOpCode.MODEL_SELECT) {
            ServerModelRegistry.updateModel(playerUUID, animId);
        }

        if (opCode == NetworkOpCode.REQUEST_ALL_MODELS) {
            ServerModelRegistry.sendAllTo((modelOwnerUUID, modelName) ->
                MmdSkinRegisterCommon.channel.send(
                    PacketDistributor.PLAYER.with(() -> sender),
                    new MmdSkinNetworkPack(NetworkOpCode.MODEL_SELECT, modelOwnerUUID, modelName)));
            return;
        }

        if (opCode == NetworkOpCode.STAGE_MULTI) {
            if (sender.getServer() != null) {
                ForgeStageSessionRegistry.getInstance().handlePacket(sender.getServer(), sender, animId);
            }
            return;
        }

        MmdSkinRegisterCommon.channel.send(PacketDistributor.ALL.noArg(), this);
    }

    private void doInClient() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (opCode == NetworkOpCode.STAGE_MULTI) {
            com.shiroha.mmdskin.stage.client.StageClientPacketHandler.getInstance().handle(playerUUID, animId);
            return;
        }
        if (playerUUID.equals(mc.player.getUUID())) return;
        if (mc.level == null) return;

        Player target = mc.level.getPlayerByUUID(playerUUID);

        switch (opCode) {
            case NetworkOpCode.CUSTOM_ANIM -> {
                if (target != null) MmdSkinRendererPlayerHelper.CustomAnim(target, animId);
            }
            case NetworkOpCode.RESET_PHYSICS -> {
                if (target != null) {
                    MmdSkinRendererPlayerHelper.ResetPhysics(target);
                } else {
                    PendingAnimSignalCache.put(playerUUID, PendingAnimSignalCache.SignalType.RESET);
                }
            }
            case NetworkOpCode.MODEL_SELECT -> {
                PlayerModelSyncManager.onRemotePlayerModelReceived(playerUUID, animId);
            }
            case NetworkOpCode.MAID_MODEL -> {
                Entity maidEntity = mc.level.getEntity(arg0);
                if (maidEntity != null) MaidMMDModelManager.bindModel(maidEntity.getUUID(), animId);
            }
            case NetworkOpCode.MAID_ACTION -> {
                Entity maidEntity = mc.level.getEntity(arg0);
                if (maidEntity != null) MaidMMDModelManager.playAnimation(maidEntity.getUUID(), animId);
            }
            case NetworkOpCode.MORPH_SYNC -> {
                if (target != null) MorphSyncHelper.applyRemoteMorph(target, animId);
            }
            default -> {}
        }
    }
}

