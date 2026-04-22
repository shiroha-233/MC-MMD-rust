package com.shiroha.mmdskin.bridge.runtime;

/** 文件职责：持有统一的运行时 native bridge 实例。 */
public final class NativeRuntimeBridgeHolder {
    private static volatile NativeRuntimePort bridge = new NativeRuntimeBridge();

    private NativeRuntimeBridgeHolder() {
    }

    public static NativeRuntimePort get() {
        return bridge;
    }

    public static void set(NativeRuntimePort newBridge) {
        if (newBridge == null) {
            throw new IllegalArgumentException("bridge cannot be null");
        }
        bridge = newBridge;
    }
}
