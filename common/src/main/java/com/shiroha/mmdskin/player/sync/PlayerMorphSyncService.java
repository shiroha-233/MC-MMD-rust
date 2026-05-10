package com.shiroha.mmdskin.player.sync;

import com.shiroha.mmdskin.ui.wheel.service.MorphSyncPort;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 文件职责：承载玩家表情轮盘的网络同步发送。 */
public final class PlayerMorphSyncService implements MorphSyncPort {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final PlayerMorphSyncService INSTANCE = new PlayerMorphSyncService();

    private volatile Consumer<String> morphSender;

    private PlayerMorphSyncService() {
    }

    public static PlayerMorphSyncService getInstance() {
        return INSTANCE;
    }

    public void setMorphSender(Consumer<String> sender) {
        this.morphSender = sender;
    }

    @Override
    public void syncMorph(String morphName) {
        Consumer<String> sender = morphSender;
        if (sender == null) {
            return;
        }
        try {
            sender.accept(morphName);
            LOGGER.debug("发送表情到服务端: {}", morphName);
        } catch (Exception e) {
            LOGGER.error("发送表情失败", e);
        }
    }
}
