/* 文件职责：检测可用 VR 运行时兼容链是否存在。 */
package com.shiroha.mmdskin.compat.vr;

/**
 * 文件职责：检测当前客户端是否存在可用的 Vivecraft 兼容链。
 */
public final class VRDetector {
    private static volatile Boolean available;

    private VRDetector() {
    }

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
        return VivecraftReflectionBridge.isAvailable();
    }
}
