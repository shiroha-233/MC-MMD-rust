package com.shiroha.mmdskin.player.sync;

import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 文件职责：承载本地玩家模型选择的网络同步发送。 */
public final class PlayerModelSelectionSyncService {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final PlayerModelSelectionSyncService INSTANCE = new PlayerModelSelectionSyncService();

    private volatile Consumer<String> modelSelectionSender;

    private PlayerModelSelectionSyncService() {
    }

    public static PlayerModelSelectionSyncService getInstance() {
        return INSTANCE;
    }

    public void setModelSelectionSender(Consumer<String> sender) {
        this.modelSelectionSender = sender;
    }

    public void syncModelSelection(String modelName) {
        Consumer<String> sender = modelSelectionSender;
        if (sender == null) {
            return;
        }
        try {
            sender.accept(modelName);
            LOGGER.debug("发送模型变更到服务端: {}", modelName);
        } catch (Exception e) {
            LOGGER.error("发送模型变更失败", e);
        }
    }
}
