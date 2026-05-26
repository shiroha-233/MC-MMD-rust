package com.shiroha.mmdskin.ui.network;

import com.shiroha.mmdskin.ui.wheel.service.ActionSyncPort;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;

/**
 * 动作轮盘网络通信处理器
 * 负责将动作选择和动作中断同步到服务器
 */
public final class ActionWheelNetworkHandler implements ActionSyncPort {
    private static final Logger logger = LogManager.getLogger();
    private static final ActionWheelNetworkHandler INSTANCE = new ActionWheelNetworkHandler();
    private volatile Consumer<String> networkSender;
    private volatile Runnable animStopSender;

    private ActionWheelNetworkHandler() {
    }

    public static ActionWheelNetworkHandler getInstance() {
        return INSTANCE;
    }

    public void setNetworkSender(Consumer<String> sender) {
        this.networkSender = sender;
    }

    public void setAnimStopSender(Runnable sender) {
        this.animStopSender = sender;
    }

    @Override
    public void syncAction(String animId) {
        Consumer<String> sender = networkSender;
        if (sender != null) {
            try {
                sender.accept(animId);
                logger.debug("发送动作到服务器: {}", animId);
            } catch (Exception e) {
                logger.error("发送动作失败", e);
            }
        }
    }

    @Override
    public void syncAnimStop() {
        Runnable sender = animStopSender;
        if (sender != null) {
            try {
                sender.run();
            } catch (Exception e) {
                logger.error("发送动画停止失败", e);
            }
        }
    }
}
