package com.shiroha.mmdskin.ui.network;

import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/** 玩家模型运行时同步管理器。 */
public class PlayerModelSyncManager {
    private static final Logger logger = LogManager.getLogger();

    private static final Map<UUID, String> remotePlayerModels = new ConcurrentHashMap<>();

    private static volatile BiConsumer<UUID, String> networkBroadcaster;

    public static void setNetworkBroadcaster(BiConsumer<UUID, String> broadcaster) {
        networkBroadcaster = broadcaster;
    }

    public static void broadcastLocalModelSelection(UUID playerUUID, String modelName) {
        if (networkBroadcaster != null) {
            networkBroadcaster.accept(playerUUID, modelName);
        } else {
        }
    }

    public static void onRemotePlayerModelReceived(UUID playerUUID, String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            remotePlayerModels.remove(playerUUID);
        } else {
            remotePlayerModels.put(playerUUID, modelName);
        }
    }

    public static String getPlayerModel(UUID playerUUID, String playerName, boolean isLocalPlayer) {
        if (isLocalPlayer) {

            return ModelSelectorConfig.getInstance().getPlayerModel(playerName);
        } else {

            String cachedModel = remotePlayerModels.get(playerUUID);
            if (cachedModel != null) {
                return cachedModel;
            }

            return ModelSelectorConfig.getInstance().getPlayerModel(playerName);
        }
    }

    public static void onPlayerLeave(UUID playerUUID) {
        if (remotePlayerModels.remove(playerUUID) != null) {
            logger.debug("清理离线玩家模型缓存: {}", playerUUID);
        }
    }

    public static void onDisconnect() {
        int count = remotePlayerModels.size();
        remotePlayerModels.clear();
    }

    public static Map<UUID, String> getAllRemotePlayerModels() {
        return new ConcurrentHashMap<>(remotePlayerModels);
    }
}
