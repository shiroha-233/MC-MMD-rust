package com.shiroha.mmdskin.fabric.register;

import com.shiroha.mmdskin.compat.maid.network.MaidActionNetworkHandler;
import com.shiroha.mmdskin.compat.maid.network.MaidModelNetworkHandler;
import com.shiroha.mmdskin.fabric.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.player.sync.ClientNetworkBindings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** 文件职责：绑定 Fabric 客户端各类网络发送器。 */
@Environment(EnvType.CLIENT)
final class FabricClientNetworkBindings {
    void register(Minecraft minecraft) {
        ClientNetworkBindings.bind(new ClientNetworkBindings.ClientNetworkSender() {
            @Override
            public void sendString(java.util.UUID playerUUID,
                                   ClientNetworkBindings.NetworkMessageType messageType,
                                   String payload) {
                LocalPlayer player = minecraft.player;
                java.util.UUID resolvedPlayerUuid = playerUUID;
                if (resolvedPlayerUuid == null) {
                    if (player == null) {
                        return;
                    }
                    resolvedPlayerUuid = player.getUUID();
                }
                MmdSkinNetworkPack.sendToServer(MmdSkinNetworkPack.toOpCode(messageType), resolvedPlayerUuid, payload);
            }

            @Override
            public void sendInt(ClientNetworkBindings.NetworkMessageType messageType, int payload) {
                LocalPlayer player = minecraft.player;
                if (player == null) {
                    return;
                }
                MmdSkinNetworkPack.sendToServer(MmdSkinNetworkPack.toOpCode(messageType), player.getUUID(), payload);
            }

            @Override
            public void sendBinary(ClientNetworkBindings.NetworkMessageType messageType, byte[] payload) {
                LocalPlayer player = minecraft.player;
                if (player == null) {
                    return;
                }
                MmdSkinNetworkPack.sendBinaryToServer(MmdSkinNetworkPack.toOpCode(messageType), player.getUUID(), payload);
            }
        });

        MaidModelNetworkHandler.getInstance().setNetworkSender((entityId, modelName) -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinNetworkPack.sendToServer(
                        com.shiroha.mmdskin.ui.network.NetworkOpCode.MAID_MODEL,
                        player.getUUID(),
                        entityId,
                        modelName);
            }
        });

        MaidActionNetworkHandler.getInstance().setNetworkSender((entityId, animId) -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinNetworkPack.sendToServer(
                        com.shiroha.mmdskin.ui.network.NetworkOpCode.MAID_ACTION,
                        player.getUUID(),
                        entityId,
                        animId);
            }
        });
    }
}
