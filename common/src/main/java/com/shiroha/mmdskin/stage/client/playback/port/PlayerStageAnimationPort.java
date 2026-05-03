package com.shiroha.mmdskin.stage.client.playback.port;

/** 文件职责：抽象本地玩家舞台动画清理与恢复操作。 */
public interface PlayerStageAnimationPort {
    default void prepareLocalModelForStage(long modelHandle) {
    }

    void clearLocalStageFlags();

    void restoreLocalModelState();
}
