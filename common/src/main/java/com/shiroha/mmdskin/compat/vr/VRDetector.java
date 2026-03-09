package com.shiroha.mmdskin.compat.vr;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * mc-vr-api 运行时检测守卫
 */

public final class VRDetector {

    private static final Logger logger = LogManager.getLogger();
    private static final String VR_API_CLASS = "net.blf02.vrapi.api.IVRAPI";

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
            Class.forName(VR_API_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
