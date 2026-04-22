package com.shiroha.mmdskin.bridge.runtime;

/** 文件职责：抽象平台能力探测，避免渲染核心直接依赖 NativeFunc。 */
public interface PlatformCapabilityPort {

    boolean isAndroid();
}
