package com.shiroha.mmdskin.player.sync;

import com.shiroha.mmdskin.player.sync.BoneSyncNetworkHandler;
import com.shiroha.mmdskin.stage.client.network.StageNetworkHandler;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** 文件职责：集中装配客户端侧的领域网络发送端口，避免平台重复绑定业务流程。 */
public final class ClientNetworkBindings {
    private ClientNetworkBindings() {
    }

    public static void bind(ClientNetworkSender sender) {
        PlayerActionSyncService.getInstance().setActionSender(animId ->
                sender.sendString(NetworkMessageType.CUSTOM_ANIM, animId));
        PlayerActionSyncService.getInstance().setAnimStopSender(() ->
                sender.sendInt(NetworkMessageType.RESET_PHYSICS, 0));

        PlayerMorphSyncService.getInstance().setMorphSender(morphToken ->
                sender.sendString(NetworkMessageType.MORPH_SYNC, morphToken));
        PlayerModelSelectionSyncService.getInstance().setModelSelectionSender(modelName ->
                sender.sendString(NetworkMessageType.MODEL_SELECT, modelName));
        PlayerModelSyncService.setNetworkBroadcaster((playerUUID, modelName) ->
                sender.sendString(playerUUID, NetworkMessageType.MODEL_SELECT, modelName));

        StageNetworkHandler.setStageMultiSender(payload ->
                sender.sendString(NetworkMessageType.STAGE_MULTI, payload));
        BoneSyncNetworkHandler.setNetworkSender(boneData ->
                sender.sendBinary(NetworkMessageType.BONE_SYNC, boneData));
    }

    public interface ClientNetworkSender {
        default void sendString(NetworkMessageType messageType, String payload) {
            sendString(null, messageType, payload);
        }

        void sendString(UUID playerUUID, NetworkMessageType messageType, String payload);

        void sendInt(NetworkMessageType messageType, int payload);

        void sendBinary(NetworkMessageType messageType, byte[] payload);
    }

    public enum NetworkMessageType {
        CUSTOM_ANIM,
        RESET_PHYSICS,
        MODEL_SELECT,
        MORPH_SYNC,
        STAGE_MULTI,
        BONE_SYNC
    }
}
