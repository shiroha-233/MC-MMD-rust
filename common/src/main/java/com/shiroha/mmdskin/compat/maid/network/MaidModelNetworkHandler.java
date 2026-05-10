/** 文件职责：承接女仆模型同步的客户端网络发送适配。 */
package com.shiroha.mmdskin.compat.maid.network;

import com.shiroha.mmdskin.compat.maid.service.MaidModelSyncPort;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiConsumer;

public final class MaidModelNetworkHandler implements MaidModelSyncPort {
    private static final Logger logger = LogManager.getLogger();
    private static final MaidModelNetworkHandler INSTANCE = new MaidModelNetworkHandler();

    private volatile BiConsumer<Integer, String> networkSender;

    private MaidModelNetworkHandler() {
    }

    public void setNetworkSender(BiConsumer<Integer, String> sender) {
        this.networkSender = sender;
    }

    public static MaidModelNetworkHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public void syncMaidModel(int entityId, String modelName) {
        BiConsumer<Integer, String> sender = networkSender;
        if (sender != null) {
            try {
                sender.accept(entityId, modelName);
                logger.debug("发送女仆模型变更到服务器: 实体={}, 模型={}", entityId, modelName);
            } catch (Exception e) {
                logger.error("发送女仆模型变更失败", e);
            }
        } else {
            logger.warn("网络发送器未设置，无法发送女仆模型变更");
        }
    }
}
