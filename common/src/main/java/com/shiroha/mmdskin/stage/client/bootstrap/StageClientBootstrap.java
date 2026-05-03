package com.shiroha.mmdskin.stage.client.bootstrap;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationBridgeHolder;
import com.shiroha.mmdskin.bridge.runtime.NativeRuntimeBridgeHolder;
import com.shiroha.mmdskin.stage.client.StageClientRuntime;

/** 文件职责：初始化 common 客户端舞台会话、播放与相机运行时。 */
public final class StageClientBootstrap {
    private StageClientBootstrap() {
    }

    public static void initialize() {
        StageClientRuntime.initialize(NativeRuntimeBridgeHolder.get(), NativeAnimationBridgeHolder.get());
    }
}
