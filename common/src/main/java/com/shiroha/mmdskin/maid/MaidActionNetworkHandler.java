package com.shiroha.mmdskin.maid;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiConsumer;

/**
 * 女仆动作网络通信处理器
 * 用于在客户端和服务器之间同步女仆的动作
 */
public class MaidActionNetworkHandler {
    private static final Logger logger = LogManager.getLogger();
    
    // 网络发送器：(实体ID, 动画ID) -> 发送到服务器
    private static BiConsumer<Integer, String> networkSender;

    /**
     * 设置网络发送器（由平台特定代码调用）
     */
    public static void setNetworkSender(BiConsumer<Integer, String> sender) {
        networkSender = sender;
    }

    /**
     * 发送女仆动作到服务器
     */
    public static void sendMaidAction(int entityId, String animId) {
        if (networkSender != null) {
            try {
                networkSender.accept(entityId, animId);
                logger.debug("发送女仆动作到服务器: 实体={}, 动画={}", entityId, animId);
            } catch (Exception e) {
                logger.error("发送女仆动作失败", e);
            }
        } else {
            logger.warn("网络发送器未设置，无法发送女仆动作");
        }
    }
}
