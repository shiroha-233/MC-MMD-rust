package com.shiroha.mmdskin.fabric.register;

import com.shiroha.mmdskin.bonesync.BoneSyncNetworkHandler;
import com.shiroha.mmdskin.fabric.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.maid.MaidActionNetworkHandler;
import com.shiroha.mmdskin.maid.MaidModelNetworkHandler;
import com.shiroha.mmdskin.stage.client.network.StageNetworkHandler;
import com.shiroha.mmdskin.ui.network.ActionWheelNetworkHandler;
import com.shiroha.mmdskin.ui.network.ModelSelectorNetworkHandler;
import com.shiroha.mmdskin.ui.network.MorphWheelNetworkHandler;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** 文件职责：绑定 Fabric 客户端各类网络发送器。 */
@Environment(EnvType.CLIENT)
final class FabricClientNetworkBindings {
    void register(Minecraft minecraft) {
        ActionWheelNetworkHandler.getInstance().setNetworkSender(animId -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinNetworkPack.sendToServer(NetworkOpCode.CUSTOM_ANIM, player.getUUID(), animId);
            }
        });

        ActionWheelNetworkHandler.getInstance().setAnimStopSender(() -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinNetworkPack.sendToServer(NetworkOpCode.RESET_PHYSICS, player.getUUID(), 0);
            }
        });

        MorphWheelNetworkHandler.getInstance().setNetworkSender(morphName -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinNetworkPack.sendToServer(NetworkOpCode.MORPH_SYNC, player.getUUID(), morphName);
            }
        });

        ModelSelectorNetworkHandler.getInstance().setNetworkSender(modelName -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinNetworkPack.sendToServer(NetworkOpCode.MODEL_SELECT, player.getUUID(), modelName);
            }
        });

        PlayerModelSyncManager.setNetworkBroadcaster((playerUUID, modelName) ->
                MmdSkinNetworkPack.sendToServer(NetworkOpCode.MODEL_SELECT, playerUUID, modelName));

        MaidModelNetworkHandler.getInstance().setNetworkSender((entityId, modelName) -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinNetworkPack.sendToServer(NetworkOpCode.MAID_MODEL, player.getUUID(), entityId, modelName);
            }
        });

        MaidActionNetworkHandler.getInstance().setNetworkSender((entityId, animId) -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinNetworkPack.sendToServer(NetworkOpCode.MAID_ACTION, player.getUUID(), entityId, animId);
            }
        });

        StageNetworkHandler.setStageMultiSender(data -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinNetworkPack.sendToServer(NetworkOpCode.STAGE_MULTI, player.getUUID(), data);
            }
        });

        BoneSyncNetworkHandler.setNetworkSender(boneData -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinNetworkPack.sendBinaryToServer(NetworkOpCode.BONE_SYNC, player.getUUID(), boneData);
            }
        });
    }
}
