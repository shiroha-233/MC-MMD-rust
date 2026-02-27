package com.shiroha.mmdskin.ui.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;

/**
 * 动作轮盘网络通信处理器
 * 负责将动作选择和动作中断同步到服务器
 */
public class ActionWheelNetworkHandler {
    private static final Logger logger = LogManager.getLogger();
    private static Consumer<String> networkSender;
    private static Runnable animStopSender;

    public static void setNetworkSender(Consumer<String> sender) {
        networkSender = sender;
    }
    
    public static void setAnimStopSender(Runnable sender) {
        animStopSender = sender;
    }

    public static void sendActionToServer(String animId) {
        if (networkSender != null) {
            try {
                networkSender.accept(animId);
                logger.debug("发送动作到服务器: {}", animId);
            } catch (Exception e) {
                logger.error("发送动作失败", e);
            }
        }
    }
    
    /** 通知服务器本地玩家已停止自定义动画 */
    public static void sendAnimStopToServer() {
        if (animStopSender != null) {
            try {
                animStopSender.run();
            } catch (Exception e) {
                logger.error("发送动画停止失败", e);
            }
        }
    }
}
