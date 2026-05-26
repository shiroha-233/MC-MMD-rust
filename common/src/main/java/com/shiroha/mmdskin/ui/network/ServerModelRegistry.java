package com.shiroha.mmdskin.ui.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/** 服务端玩家模型注册表。 */
public final class ServerModelRegistry {
    private static final Logger logger = LogManager.getLogger();

    private static final Map<UUID, String> playerModels = new ConcurrentHashMap<>();

    public static void updateModel(UUID playerUUID, String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            playerModels.remove(playerUUID);
        } else {
            playerModels.put(playerUUID, modelName);
        }
    }

    public static void onPlayerLeave(UUID playerUUID) {
        playerModels.remove(playerUUID);
    }

    
    public static void sendAllTo(BiConsumer<UUID, String> sender) {
        for (Map.Entry<UUID, String> entry : playerModels.entrySet()) {
            try {
                sender.accept(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                logger.error("回传模型信息失败: {}", entry.getKey(), e);
            }
        }
    }

    public static void clear() {
        playerModels.clear();
    }

    private ServerModelRegistry() {}
}
