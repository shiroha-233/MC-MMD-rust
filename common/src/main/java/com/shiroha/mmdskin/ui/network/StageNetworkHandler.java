package com.shiroha.mmdskin.ui.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * 舞台模式网络通信
 * opCode 7: 舞台开始, 8: 舞台结束, 9: 舞台音频
 * opCode 11: 多人舞台（子操作编码在 animId 中）
 */
public class StageNetworkHandler {
    private static final Logger logger = LogManager.getLogger();

    private static Consumer<String> stageStartSender;
    private static Runnable stageEndSender;
    private static Consumer<String> stageMultiSender;

    public static void setStageStartSender(Consumer<String> sender) { stageStartSender = sender; }
    public static void setStageEndSender(Runnable sender) { stageEndSender = sender; }
    public static void setStageMultiSender(Consumer<String> sender) { stageMultiSender = sender; }

    public static void sendStageStart(String stageData) {
        if (stageStartSender != null) {
            try { stageStartSender.accept(stageData); }
            catch (Exception e) { logger.error("广播舞台开始失败", e); }
        }
    }

    public static void sendStageEnd() {
        if (stageEndSender != null) {
            try { stageEndSender.run(); }
            catch (Exception e) { logger.error("广播舞台结束失败", e); }
        }
    }

    public static void sendStageInvite(UUID targetUUID) {
        sendMulti("INVITE|" + targetUUID);
    }

    public static void sendInviteResponse(UUID hostUUID, boolean accepted) {
        sendMulti((accepted ? "ACCEPT|" : "DECLINE|") + hostUUID);
    }

    public static void sendStageWatch(UUID targetUUID, String stageData) {
        sendMulti("WATCH_START|" + targetUUID + "|" + stageData);
    }

    public static void sendStageWatchEnd(UUID targetUUID) {
        sendMulti("WATCH_END|" + targetUUID);
    }

    public static void sendFrameSync(float currentFrame) {
        sendMulti("SYNC_FRAME|" + currentFrame);
    }

    private static void sendMulti(String data) {
        if (stageMultiSender == null) {
            logger.warn("[多人舞台] stageMultiSender 未注册");
            return;
        }
        try {
            stageMultiSender.accept(data);
        } catch (Exception e) {
            logger.error("多人舞台消息发送失败", e);
        }
    }
}
