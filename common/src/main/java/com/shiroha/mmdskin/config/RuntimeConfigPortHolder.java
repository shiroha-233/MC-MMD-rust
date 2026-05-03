package com.shiroha.mmdskin.config;

/** 文件职责：持有客户端运行时注入的精简配置视图。 */
public final class RuntimeConfigPortHolder {
    private static volatile RuntimeConfigPort port = RuntimeConfigPort.defaults();

    private RuntimeConfigPortHolder() {
    }

    public static RuntimeConfigPort get() {
        return port;
    }

    public static void set(RuntimeConfigPort newPort) {
        if (newPort == null) {
            throw new IllegalArgumentException("port cannot be null");
        }
        port = newPort;
    }
}
