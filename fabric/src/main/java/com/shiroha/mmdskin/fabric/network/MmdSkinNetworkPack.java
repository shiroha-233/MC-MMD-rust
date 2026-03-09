package com.shiroha.mmdskin.fabric.network;

import java.util.UUID;

import com.shiroha.mmdskin.fabric.register.MmdSkinRegisterCommon;
import com.shiroha.mmdskin.maid.MaidMMDModelManager;
import com.shiroha.mmdskin.player.animation.PendingAnimSignalCache;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.player.sync.MorphSyncHelper;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Fabric 网络包发送与客户端处理
 */
public class MmdSkinNetworkPack {

    public static void sendToServer(int opCode, UUID playerUUID, int arg0) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        buffer.writeInt(opCode);
        buffer.writeUUID(playerUUID);
        buffer.writeInt(arg0);
        ClientPlayNetworking.send(MmdSkinRegisterCommon.SKIN_C2S, buffer);
    }

    public static void sendBinaryToServer(int opCode, UUID playerUUID, byte[] data) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        buffer.writeInt(opCode);
        buffer.writeUUID(playerUUID);
        buffer.writeByteArray(data);
        ClientPlayNetworking.send(MmdSkinRegisterCommon.SKIN_C2S, buffer);
    }

    public static void sendToServer(int opCode, UUID playerUUID, String animId) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        buffer.writeInt(opCode);
        buffer.writeUUID(playerUUID);
        buffer.writeUtf(animId);
        ClientPlayNetworking.send(MmdSkinRegisterCommon.SKIN_C2S, buffer);
    }

    public static void sendToServer(int opCode, UUID playerUUID, int entityId, String data) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        buffer.writeInt(opCode);
        buffer.writeUUID(playerUUID);
        buffer.writeInt(entityId);
        buffer.writeUtf(data);
        ClientPlayNetworking.send(MmdSkinRegisterCommon.SKIN_C2S, buffer);
    }

    public static void doInClient(FriendlyByteBuf buffer) {
        int opCode = buffer.readInt();
        UUID playerUUID = buffer.readUUID();

        if (NetworkOpCode.isStringPayload(opCode)) {
            String data = buffer.readUtf();
            handleString(opCode, playerUUID, data);
        } else if (NetworkOpCode.isEntityStringPayload(opCode)) {
            int entityId = buffer.readInt();
            String data = buffer.readUtf();
            handleMaid(opCode, playerUUID, entityId, data);
        } else {
            int arg0 = buffer.readInt();
            handleInt(opCode, playerUUID, arg0);
        }
    }

    private static void handleInt(int opCode, UUID playerUUID, int arg0) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || playerUUID.equals(mc.player.getUUID())) return;
        if (mc.level == null) return;

        if (opCode == NetworkOpCode.RESET_PHYSICS) {
            Player target = mc.level.getPlayerByUUID(playerUUID);
            if (target != null) {
                MmdSkinRendererPlayerHelper.ResetPhysics(target);
            } else {
                PendingAnimSignalCache.put(playerUUID, PendingAnimSignalCache.SignalType.RESET);
            }
        }
    }

    private static void handleString(int opCode, UUID playerUUID, String data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (opCode == NetworkOpCode.STAGE_MULTI) {
            com.shiroha.mmdskin.stage.client.StageClientPacketHandler.getInstance().handle(playerUUID, data);
            return;
        }
        if (playerUUID.equals(mc.player.getUUID())) return;
        if (mc.level == null) return;

        Player target = mc.level.getPlayerByUUID(playerUUID);
        switch (opCode) {
            case NetworkOpCode.CUSTOM_ANIM -> {
                if (target != null) MmdSkinRendererPlayerHelper.CustomAnim(target, data);
            }
            case NetworkOpCode.MODEL_SELECT -> {
                PlayerModelSyncManager.onRemotePlayerModelReceived(playerUUID, data);
            }
            case NetworkOpCode.MORPH_SYNC -> {
                if (target != null) MorphSyncHelper.applyRemoteMorph(target, data);
            }
            default -> {}
        }
    }

    private static void handleMaid(int opCode, UUID playerUUID, int entityId, String data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || playerUUID.equals(mc.player.getUUID())) return;
        if (mc.level == null) return;

        Entity maidEntity = mc.level.getEntity(entityId);
        if (maidEntity == null) return;

        switch (opCode) {
            case NetworkOpCode.MAID_MODEL -> MaidMMDModelManager.bindModel(maidEntity.getUUID(), data);
            case NetworkOpCode.MAID_ACTION -> MaidMMDModelManager.playAnimation(maidEntity.getUUID(), data);
            default -> {}
        }
    }
}

