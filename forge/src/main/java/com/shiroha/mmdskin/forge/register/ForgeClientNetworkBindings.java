package com.shiroha.mmdskin.forge.register;

import com.shiroha.mmdskin.compat.maid.network.MaidActionNetworkHandler;
import com.shiroha.mmdskin.compat.maid.network.MaidModelNetworkHandler;
import com.shiroha.mmdskin.forge.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.player.sync.ClientNetworkBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** 文件职责：绑定 Forge 客户端各类网络发送器。 */
@OnlyIn(Dist.CLIENT)
final class ForgeClientNetworkBindings {
    private boolean registered;

    void register() {
        if (registered) {
            return;
        }
        registered = true;

        Minecraft minecraft = Minecraft.getInstance();

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
                MmdSkinRegisterCommon.channel.sendToServer(
                        new MmdSkinNetworkPack(MmdSkinNetworkPack.toOpCode(messageType), resolvedPlayerUuid, payload));
            }

            @Override
            public void sendInt(ClientNetworkBindings.NetworkMessageType messageType, int payload) {
                LocalPlayer player = minecraft.player;
                if (player == null) {
                    return;
                }
                MmdSkinRegisterCommon.channel.sendToServer(
                        new MmdSkinNetworkPack(MmdSkinNetworkPack.toOpCode(messageType), player.getUUID(), payload));
            }

            @Override
            public void sendBinary(ClientNetworkBindings.NetworkMessageType messageType, byte[] payload) {
                LocalPlayer player = minecraft.player;
                if (player == null) {
                    return;
                }
                MmdSkinRegisterCommon.channel.sendToServer(
                        new MmdSkinNetworkPack(MmdSkinNetworkPack.toOpCode(messageType), player.getUUID(), payload));
            }
        });

        MaidModelNetworkHandler.getInstance().setNetworkSender((entityId, modelName) -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(com.shiroha.mmdskin.ui.network.NetworkOpCode.MAID_MODEL, player.getUUID(), entityId, modelName));
            }
        });

        MaidActionNetworkHandler.getInstance().setNetworkSender((entityId, animId) -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(com.shiroha.mmdskin.ui.network.NetworkOpCode.MAID_ACTION, player.getUUID(), entityId, animId));
            }
        });
    }
}
