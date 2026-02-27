package com.shiroha.mmdskin.bonesync;

import com.shiroha.mmdskin.renderer.render.PlayerModelResolver;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 骨骼同步总调度器
 * 控制采样频率、优先级判断、本地发送与远程接收
 */
public final class BoneSyncManager {

    private static final Logger logger = LogManager.getLogger();

    private static volatile boolean enabled = false;
    private static final long SAMPLE_INTERVAL_MS = 100; // 10fps
    private static long lastSampleTime;

    public static void setEnabled(boolean enable) {
        enabled = enable;
    }

    public static boolean isEnabled() { return enabled; }

    /**
     * 每帧调用：本地玩家采样 + 发送
     */
    public static void tickLocal() {
        if (!enabled) return;

        long now = System.currentTimeMillis();
        if (now - lastSampleTime < SAMPLE_INTERVAL_MS) return;
        lastSampleTime = now;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(mc.player);
            if (resolved == null) return;

            // TODO: 骨骼快照采样与网络发送
        } catch (Exception e) {
            logger.error("骨骼同步采样失败", e);
        }
    }

    public static void onDisconnect() {
        enabled = false;
        lastSampleTime = 0;
    }

}
