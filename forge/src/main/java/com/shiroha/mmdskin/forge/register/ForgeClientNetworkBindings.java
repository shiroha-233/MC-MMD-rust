package com.shiroha.mmdskin.forge.register;

import com.shiroha.mmdskin.forge.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.maid.MaidActionNetworkHandler;
import com.shiroha.mmdskin.maid.MaidModelNetworkHandler;
import com.shiroha.mmdskin.stage.client.network.StageNetworkHandler;
import com.shiroha.mmdskin.ui.network.ActionWheelNetworkHandler;
import com.shiroha.mmdskin.ui.network.ModelSelectorNetworkHandler;
import com.shiroha.mmdskin.ui.network.MorphWheelNetworkHandler;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
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

        ActionWheelNetworkHandler.getInstance().setNetworkSender(animId -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(NetworkOpCode.CUSTOM_ANIM, player.getUUID(), animId));
            }
        });

        ActionWheelNetworkHandler.getInstance().setAnimStopSender(() -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(NetworkOpCode.RESET_PHYSICS, player.getUUID(), 0));
            }
        });

        MorphWheelNetworkHandler.getInstance().setNetworkSender(morphName -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(NetworkOpCode.MORPH_SYNC, player.getUUID(), morphName));
            }
        });

        ModelSelectorNetworkHandler.getInstance().setNetworkSender(modelName -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(NetworkOpCode.MODEL_SELECT, player.getUUID(), modelName));
            }
        });

        PlayerModelSyncManager.setNetworkBroadcaster((playerUUID, modelName) ->
            MmdSkinRegisterCommon.channel.sendToServer(
                new MmdSkinNetworkPack(NetworkOpCode.MODEL_SELECT, playerUUID, modelName)));

        MaidModelNetworkHandler.getInstance().setNetworkSender((entityId, modelName) -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(NetworkOpCode.MAID_MODEL, player.getUUID(), entityId, modelName));
            }
        });

        MaidActionNetworkHandler.getInstance().setNetworkSender((entityId, animId) -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(NetworkOpCode.MAID_ACTION, player.getUUID(), entityId, animId));
            }
        });

        StageNetworkHandler.setStageMultiSender(data -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinRegisterCommon.channel.sendToServer(
                    new MmdSkinNetworkPack(NetworkOpCode.STAGE_MULTI, player.getUUID(), data));
            }
        });
    }
}
