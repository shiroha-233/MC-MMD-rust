package com.shiroha.mmdskin.maid;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiConsumer;

/**
 * 女仆模型网络通信处理器
 * 
 * 用于在客户端和服务器之间同步女仆的 MMD 模型选择。
 * 平台特定的网络实现由 Fabric/Forge 模块提供。
 */
public class MaidModelNetworkHandler {
    private static final Logger logger = LogManager.getLogger();
    
    // 网络发送器：(实体ID, 模型名称) -> 发送到服务器
    private static BiConsumer<Integer, String> networkSender;

    /**
     * 设置网络发送器（由平台特定代码调用）
     * 
     * @param sender 发送器函数，接收 (entityId, modelName)
     */
    public static void setNetworkSender(BiConsumer<Integer, String> sender) {
        networkSender = sender;
    }

    /**
     * 发送女仆模型变更到服务器
     * 
     * @param entityId 女仆实体 ID
     * @param modelName 模型名称
     */
    public static void sendMaidModelChange(int entityId, String modelName) {
        if (networkSender != null) {
            try {
                networkSender.accept(entityId, modelName);
                logger.debug("发送女仆模型变更到服务器: 实体={}, 模型={}", entityId, modelName);
            } catch (Exception e) {
                logger.error("发送女仆模型变更失败", e);
            }
        } else {
            logger.warn("网络发送器未设置，无法发送女仆模型变更");
        }
    }
}
