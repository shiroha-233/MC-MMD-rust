package com.shiroha.mmdskin.bonesync;

import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 骨骼同步总调度器
 */

public final class BoneSyncManager {

    private static final Logger logger = LogManager.getLogger();

    private static volatile boolean enabled = false;
    private static final long SAMPLE_INTERVAL_MS = 100;
    private static long lastSampleTime;

    public static void setEnabled(boolean enable) {
        enabled = enable;
    }

    public static boolean isEnabled() { return enabled; }

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

        } catch (Exception e) {
            logger.error("骨骼同步采样失败", e);
        }
    }

    public static void onDisconnect() {
        enabled = false;
        lastSampleTime = 0;
    }

}
