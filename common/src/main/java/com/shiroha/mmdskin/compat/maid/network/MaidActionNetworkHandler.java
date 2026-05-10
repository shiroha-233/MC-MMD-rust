/** 文件职责：承接女仆动作同步的客户端网络发送适配。 */
package com.shiroha.mmdskin.compat.maid.network;

import com.shiroha.mmdskin.compat.maid.service.MaidActionSyncPort;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiConsumer;

public final class MaidActionNetworkHandler implements MaidActionSyncPort {
    private static final Logger logger = LogManager.getLogger();
    private static final MaidActionNetworkHandler INSTANCE = new MaidActionNetworkHandler();

    private volatile BiConsumer<Integer, String> networkSender;

    private MaidActionNetworkHandler() {
    }

    public void setNetworkSender(BiConsumer<Integer, String> sender) {
        this.networkSender = sender;
    }

    public static MaidActionNetworkHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public void syncMaidAction(int entityId, String animId) {
        BiConsumer<Integer, String> sender = networkSender;
        if (sender != null) {
            try {
                sender.accept(entityId, animId);
                logger.debug("发送女仆动作到服务器: 实体={}, 动画={}", entityId, animId);
            } catch (Exception e) {
                logger.error("发送女仆动作失败", e);
            }
        } else {
            logger.warn("网络发送器未设置，无法发送女仆动作");
        }
    }
}
