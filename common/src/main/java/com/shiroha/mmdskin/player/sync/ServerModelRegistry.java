package com.shiroha.mmdskin.player.sync;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 文件职责：维护服务端玩家模型选择表，并向新加入玩家回放当前状态。 */
public final class ServerModelRegistry {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Map<UUID, String> PLAYER_MODELS = new ConcurrentHashMap<>();

    private ServerModelRegistry() {
    }

    public static void updateModel(UUID playerUUID, String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            PLAYER_MODELS.remove(playerUUID);
            return;
        }
        PLAYER_MODELS.put(playerUUID, modelName);
    }

    public static void onPlayerLeave(UUID playerUUID) {
        PLAYER_MODELS.remove(playerUUID);
    }

    public static void sendAllTo(BiConsumer<UUID, String> sender) {
        for (Map.Entry<UUID, String> entry : PLAYER_MODELS.entrySet()) {
            try {
                sender.accept(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                LOGGER.error("回传模型信息失败: {}", entry.getKey(), e);
            }
        }
    }

    public static void clear() {
        PLAYER_MODELS.clear();
    }
}
