package com.shiroha.mmdskin.player.sync;

import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 文件职责：维护玩家模型同步状态，并向平台网络层广播本地模型变更。 */
public final class PlayerModelSyncService {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Map<UUID, String> REMOTE_PLAYER_MODELS = new ConcurrentHashMap<>();

    private static volatile BiConsumer<UUID, String> networkBroadcaster;

    private PlayerModelSyncService() {
    }

    public static void setNetworkBroadcaster(BiConsumer<UUID, String> broadcaster) {
        networkBroadcaster = broadcaster;
    }

    public static void broadcastLocalModelSelection(UUID playerUUID, String modelName) {
        BiConsumer<UUID, String> broadcaster = networkBroadcaster;
        if (broadcaster != null) {
            broadcaster.accept(playerUUID, modelName);
        }
    }

    public static void onRemotePlayerModelReceived(UUID playerUUID, String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            REMOTE_PLAYER_MODELS.remove(playerUUID);
            return;
        }
        REMOTE_PLAYER_MODELS.put(playerUUID, modelName);
    }

    public static String getPlayerModel(UUID playerUUID, String playerName, boolean localPlayer) {
        if (localPlayer) {
            return ModelSelectorConfig.getInstance().getPlayerModel(playerName);
        }

        String cachedModel = REMOTE_PLAYER_MODELS.get(playerUUID);
        if (cachedModel != null) {
            return cachedModel;
        }
        return ModelSelectorConfig.getInstance().getPlayerModel(playerName);
    }

    public static void onPlayerLeave(UUID playerUUID) {
        if (REMOTE_PLAYER_MODELS.remove(playerUUID) != null) {
            LOGGER.debug("清理离线玩家模型缓存: {}", playerUUID);
        }
    }

    public static void onDisconnect() {
        REMOTE_PLAYER_MODELS.clear();
    }

    public static Map<UUID, String> getAllRemotePlayerModels() {
        return new ConcurrentHashMap<>(REMOTE_PLAYER_MODELS);
    }
}
