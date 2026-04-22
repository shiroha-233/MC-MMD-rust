package com.shiroha.mmdskin.bridge.runtime;

/** 文件职责：组合客户端运行时需要的全部 native 能力边界。 */
public interface NativeRuntimePort extends
        NativeRenderBackendPort,
        NativeMorphPort,
        NativeMatrixPort,
        PlatformCapabilityPort {
}
