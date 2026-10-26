/* 文件职责：处理 NeoForge 客户端的网络发送与接收（与 Fabric 端 MmdSkinNetworkPack 语义等价）。 */
package com.shiroha.mmdskin.neoforge.network;

import com.shiroha.mmdskin.distrib.ModelDistributionClient;
import com.shiroha.mmdskin.player.animation.PendingAnimSignalCache;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.player.sync.MorphSyncHelper;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class MmdSkinNetworkPack {
    public static void sendToServer(int opCode, UUID playerUUID, int arg0) {
        ClientPacketDistributor.sendToServer(MmdSkinPayload.createInt(opCode, playerUUID, arg0));
    }

    public static void sendBinaryToServer(int opCode, UUID playerUUID, byte[] data) {
        ClientPacketDistributor.sendToServer(MmdSkinPayload.createBinary(opCode, playerUUID, data));
    }

    public static void sendToServer(int opCode, UUID playerUUID, String animId) {
        ClientPacketDistributor.sendToServer(MmdSkinPayload.createString(opCode, playerUUID, animId));
    }

    public static void handlePayload(MmdSkinPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        int opCode = payload.opCode();
        UUID playerUUID = payload.playerUUID();

        // 模型分发的二进制载荷必须最先分派：
        // 分发消息的 playerUUID 就是收件人自己（等于本地玩家 UUID），
        // 放到后面的 handleInt/handleString 里会被"是自己发的消息"过滤掉。
        if (NetworkOpCode.isDistributionBinaryPayload(opCode)) {
            byte[] data = payload.binaryData();
            switch (opCode) {
                case NetworkOpCode.DISTRIB_MANIFEST ->
                        ModelDistributionClient.onManifest(new String(data, StandardCharsets.UTF_8));
                case NetworkOpCode.DISTRIB_OFFER -> ModelDistributionClient.onOfferPage(data);
                case NetworkOpCode.DISTRIB_CHUNK -> ModelDistributionClient.onChunk(data);
                default -> {
                }
            }
            return;
        }

        if (NetworkOpCode.isStringPayload(opCode)) {
            if (opCode == NetworkOpCode.DISTRIB_COMPLETE) {
                ModelDistributionClient.onComplete(payload.stringArg());
                return;
            }
            handleString(opCode, playerUUID, payload.stringArg());
        } else {
            handleInt(opCode, playerUUID, payload.intArg());
        }
    }

    private static void handleInt(int opCode, UUID playerUUID, int arg0) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || playerUUID.equals(mc.player.getUUID()) || mc.level == null) {
            return;
        }

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
        if (mc.player == null) {
            return;
        }
        if (opCode == NetworkOpCode.STAGE_MULTI) {
            com.shiroha.mmdskin.stage.client.StageClientPacketHandler.getInstance().handle(playerUUID, data);
            return;
        }
        if (playerUUID.equals(mc.player.getUUID()) || mc.level == null) {
            return;
        }

        Player target = mc.level.getPlayerByUUID(playerUUID);
        switch (opCode) {
            case NetworkOpCode.CUSTOM_ANIM -> {
                if (target != null) {
                    MmdSkinRendererPlayerHelper.CustomAnim(target, data);
                }
            }
            case NetworkOpCode.MODEL_SELECT -> PlayerModelSyncManager.onRemotePlayerModelReceived(playerUUID, data);
            case NetworkOpCode.MORPH_SYNC -> {
                if (target != null) {
                    MorphSyncHelper.applyRemoteMorph(target, data);
                }
            }
            default -> {
            }
        }
    }
}
