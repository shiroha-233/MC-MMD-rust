package com.shiroha.mmdskin.bridge.runtime;

/** 文件职责：持有统一的纹理 native bridge 实例。 */
public final class NativeTextureBridgeHolder {
    private static volatile NativeTexturePort bridge = new NativeTextureBridge();

    private NativeTextureBridgeHolder() {
    }

    public static NativeTexturePort get() {
        return bridge;
    }

    public static void set(NativeTexturePort newBridge) {
        if (newBridge == null) {
            throw new IllegalArgumentException("bridge cannot be null");
        }
        bridge = newBridge;
    }
}
