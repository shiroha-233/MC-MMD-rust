package com.shiroha.mmdskin.ui.network;

import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 玩家模型同步管理器
 * 
 * 用于在联机时同步其他玩家的模型选择。
 * 与 ModelSelectorConfig（本地持久化）分离，专门处理运行时的网络同步。
 * 
 * 工作流程：
 * 1. 本地玩家选择模型 → 保存到 ModelSelectorConfig + 广播到服务器
 * 2. 服务器转发给所有其他客户端
 * 3. 其他客户端收到后存入此缓存
 * 4. 渲染时优先查此缓存，找不到再查本地配置
 */
public class PlayerModelSyncManager {
    private static final Logger logger = LogManager.getLogger();
    
    // 运行时缓存：playerUUID -> modelName
    private static final Map<UUID, String> remotePlayerModels = new ConcurrentHashMap<>();
    
    // 网络发送器（由平台特定代码设置，跨线程访问需 volatile）
    private static volatile BiConsumer<UUID, String> networkBroadcaster;
    
    /**
     * 设置网络广播器（由平台特定代码调用）
     * @param broadcaster 接收 (playerUUID, modelName) 并发送到服务器
     */
    public static void setNetworkBroadcaster(BiConsumer<UUID, String> broadcaster) {
        networkBroadcaster = broadcaster;
    }
    
    /**
     * 本地玩家选择模型后调用，广播到服务器
     */
    public static void broadcastLocalModelSelection(UUID playerUUID, String modelName) {
        if (networkBroadcaster != null) {
            networkBroadcaster.accept(playerUUID, modelName);
        } else {
        }
    }
    
    /**
     * 收到其他玩家的模型选择时调用
     */
    public static void onRemotePlayerModelReceived(UUID playerUUID, String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            remotePlayerModels.remove(playerUUID);
        } else {
            remotePlayerModels.put(playerUUID, modelName);
        }
    }
    
    /**
     * 获取玩家的模型选择（优先从运行时缓存获取）
     * 
     * @param playerUUID 玩家 UUID
     * @param playerName 玩家名（用于查询本地配置）
     * @param isLocalPlayer 是否是本地玩家
     * @return 模型名称，如果没有选择返回 null
     */
    public static String getPlayerModel(UUID playerUUID, String playerName, boolean isLocalPlayer) {
        if (isLocalPlayer) {
            // 本地玩家：从本地配置获取
            return ModelSelectorConfig.getInstance().getPlayerModel(playerName);
        } else {
            // 远程玩家：优先从运行时缓存获取
            String cachedModel = remotePlayerModels.get(playerUUID);
            if (cachedModel != null) {
                return cachedModel;
            }
            // 兼容：如果缓存没有，尝试从本地配置获取（可能是之前保存的）
            return ModelSelectorConfig.getInstance().getPlayerModel(playerName);
        }
    }
    
    /**
     * 玩家离开时清理缓存
     */
    public static void onPlayerLeave(UUID playerUUID) {
        if (remotePlayerModels.remove(playerUUID) != null) {
            logger.debug("清理离线玩家模型缓存: {}", playerUUID);
        }
    }
    
    /**
     * 断开连接时清理所有远程玩家缓存
     */
    public static void onDisconnect() {
        int count = remotePlayerModels.size();
        remotePlayerModels.clear();
    }
    
    /**
     * 获取所有远程玩家的模型选择（用于调试）
     */
    public static Map<UUID, String> getAllRemotePlayerModels() {
        return new ConcurrentHashMap<>(remotePlayerModels);
    }
}
