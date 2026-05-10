package com.shiroha.mmdskin.player.sync;

import com.shiroha.mmdskin.ui.wheel.service.ActionSyncPort;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 文件职责：承载玩家动作轮盘的网络同步发送。 */
public final class PlayerActionSyncService implements ActionSyncPort {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final PlayerActionSyncService INSTANCE = new PlayerActionSyncService();

    private volatile Consumer<String> actionSender;
    private volatile Runnable animStopSender;

    private PlayerActionSyncService() {
    }

    public static PlayerActionSyncService getInstance() {
        return INSTANCE;
    }

    public void setActionSender(Consumer<String> sender) {
        this.actionSender = sender;
    }

    public void setAnimStopSender(Runnable sender) {
        this.animStopSender = sender;
    }

    @Override
    public void syncAction(String animId) {
        Consumer<String> sender = actionSender;
        if (sender == null) {
            return;
        }
        try {
            sender.accept(animId);
            LOGGER.debug("发送动作到服务端: {}", animId);
        } catch (Exception e) {
            LOGGER.error("发送动作失败", e);
        }
    }

    @Override
    public void syncAnimStop() {
        Runnable sender = animStopSender;
        if (sender == null) {
            return;
        }
        try {
            sender.run();
        } catch (Exception e) {
            LOGGER.error("发送动作停止失败", e);
        }
    }
}
