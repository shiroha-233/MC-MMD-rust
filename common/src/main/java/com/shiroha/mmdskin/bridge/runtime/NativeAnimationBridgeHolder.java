package com.shiroha.mmdskin.bridge.runtime;

/** 文件职责：持有统一的动画时间线 native bridge 实例。 */
public final class NativeAnimationBridgeHolder {
    private static volatile NativeAnimationPort bridge = new NativeAnimationBridge();

    private NativeAnimationBridgeHolder() {
    }

    public static NativeAnimationPort get() {
        return bridge;
    }

    public static void set(NativeAnimationPort newBridge) {
        if (newBridge == null) {
            throw new IllegalArgumentException("bridge cannot be null");
        }
        bridge = newBridge;
    }
}
