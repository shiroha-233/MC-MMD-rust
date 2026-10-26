/* 文件职责：集中绑定 Fabric 客户端各类网络发送器。 */
package com.shiroha.mmdskin.fabric.register;

import com.shiroha.mmdskin.bonesync.BoneSyncNetworkHandler;
import com.shiroha.mmdskin.distrib.ModelDistributionClient;
import com.shiroha.mmdskin.fabric.network.MmdSkinNetworkPack;
import com.shiroha.mmdskin.player.sync.PlayerMorphSyncService;
import com.shiroha.mmdskin.ui.network.ActionWheelNetworkHandler;
import com.shiroha.mmdskin.ui.network.ModelSelectorNetworkHandler;
import com.shiroha.mmdskin.ui.network.NetworkOpCode;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import com.shiroha.mmdskin.ui.network.StageNetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

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

        PlayerMorphSyncService.getInstance().setMorphSender(morphName -> {
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

        StageNetworkHandler.setStageMultiSender(payload -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinNetworkPack.sendToServer(NetworkOpCode.STAGE_MULTI, player.getUUID(), payload);
            }
        });

        BoneSyncNetworkHandler.setNetworkSender(payload -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinNetworkPack.sendBinaryToServer(NetworkOpCode.BONE_SYNC, player.getUUID(), payload);
            }
        });

        // 模型分发：客户端向服务器请求某个模型。
        // 断点续传靠"首个未完成的文件相对路径"表达，格式 "模型名|相对路径"。
        ModelDistributionClient.setRequester((modelName, firstIncompletePath) -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                MmdSkinNetworkPack.sendToServer(NetworkOpCode.DISTRIB_REQUEST, player.getUUID(),
                        modelName + "|" + (firstIncompletePath == null ? "" : firstIncompletePath));
            }
        });
    }
}
