package com.shiroha.mmdskin.stage.client.camera.port;

/** 文件职责：向相机控制器暴露玩家舞台逐帧同步与本地停播能力。 */
public interface StagePlayerSyncPort {
    void syncStageFrame(float frame);

    void stopLocalStageAnimation();
}
