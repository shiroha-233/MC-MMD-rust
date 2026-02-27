package com.shiroha.mmdskin.ui.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 服务端玩家模型注册表
 * 
 * 在服务端维护所有在线玩家的模型选择，
 * 当新玩家加入（opCode 10）时将已有数据回传。
 */
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

    /**
     * 将所有已注册的模型信息逐条回传给请求者
     * @param sender 回传函数：(playerUUID, modelName) → 发送 S2C 包
     */
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
