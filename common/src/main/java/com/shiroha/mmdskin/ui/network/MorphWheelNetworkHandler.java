package com.shiroha.mmdskin.ui.network;

import com.shiroha.mmdskin.ui.wheel.service.MorphSyncPort;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;

/**
 * 表情轮盘网络处理器
 * 负责将表情选择同步到服务器
 */
public final class MorphWheelNetworkHandler implements MorphSyncPort {
    private static final Logger logger = LogManager.getLogger();
    private static final MorphWheelNetworkHandler INSTANCE = new MorphWheelNetworkHandler();

    private volatile Consumer<String> networkSender;

    private MorphWheelNetworkHandler() {
    }

    public void setNetworkSender(Consumer<String> sender) {
        this.networkSender = sender;
    }

    public static MorphWheelNetworkHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public void syncMorph(String morphName) {
        Consumer<String> sender = networkSender;
        if (sender != null) {
            try {
                sender.accept(morphName);
                logger.debug("发送表情到服务器: {}", morphName);
            } catch (Exception e) {
                logger.error("发送表情失败", e);
            }
        }
    }
}
