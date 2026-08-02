package com.shiroha.mmdskin.render.backend;

/** 文件职责：记录单个模型的第一人称预更新姿态是否仍待正式 Draw 消费。 */
final class FirstPersonPoseState {
    private boolean prepared;

    void beginPreparation() {
        prepared = false;
    }

    void markPrepared() {
        prepared = true;
    }

    boolean isPrepared() {
        return prepared;
    }

    void finishRender(boolean firstPersonDraw) {
        if (firstPersonDraw) {
            prepared = false;
        }
    }

    void discard() {
        prepared = false;
    }
}
