package com.shiroha.mmdskin.renderer.runtime.bridge;

/**
 * 模型运行时桥接持有者。
 */
public final class ModelRuntimeBridgeHolder {

    private static volatile ModelRuntimeBridge bridge = new NativeModelRuntimeBridge();

    private ModelRuntimeBridgeHolder() {
    }

    public static ModelRuntimeBridge get() {
        return bridge;
    }

    public static void set(ModelRuntimeBridge newBridge) {
        if (newBridge == null) {
            throw new IllegalArgumentException("bridge cannot be null");
        }
        bridge = newBridge;
    }
}
