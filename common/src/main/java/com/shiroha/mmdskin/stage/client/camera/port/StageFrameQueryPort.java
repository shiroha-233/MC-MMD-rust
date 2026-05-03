package com.shiroha.mmdskin.stage.client.camera.port;

/** 文件职责：向舞台玩家同步侧暴露当前舞台帧状态查询。 */
public interface StageFrameQueryPort {
    boolean isStagePresentationActive();

    float getCurrentFrame();
}
