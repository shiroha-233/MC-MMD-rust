package com.shiroha.mmdskin.bonesync;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;

/**
 * 骨骼同步网络通信桥接，平台侧注册实际发送实现
 */

public final class BoneSyncNetworkHandler {

    private static final Logger logger = LogManager.getLogger();
    private static volatile Consumer<byte[]> networkSender;

    private BoneSyncNetworkHandler() {}

    public static void setNetworkSender(Consumer<byte[]> sender) {
        networkSender = sender;
    }

    public static void send(byte[] boneData) {
        Consumer<byte[]> sender = networkSender;
        if (sender == null) return;
        try {
            sender.accept(boneData);
        } catch (Exception e) {
            logger.error("骨骼同步数据发送失败", e);
        }
    }
}
