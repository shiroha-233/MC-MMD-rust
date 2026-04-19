package com.shiroha.mmdskin.compat.vr;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Vivecraft 运行时检测守卫
 */

public final class VRDetector {

    private static final Logger logger = LogManager.getLogger();

    private static volatile Boolean available = null;

    private VRDetector() {}

    public static boolean isAvailable() {
        if (available == null) {
            synchronized (VRDetector.class) {
                if (available == null) {
                    available = detect();
                }
            }
        }
        return available;
    }

    private static boolean detect() {
        try {
            boolean detected = VivecraftReflectionBridge.isAvailable();
            if (!detected) {
                logger.debug("Vivecraft runtime API not detected");
            }
            return detected;
        } catch (Exception e) {
            return false;
        }
    }
}
