package com.shiroha.mmdskin.stage.client.camera;

/** 文件职责：集中舞台相机会话状态与迁移规则。 */
final class StageCameraSessionStateMachine {
    enum StageState {
        INACTIVE,
        INTRO,
        STANDBY,
        PLAYING,
        OUTRO,
        WATCHING
    }

    private StageState state = StageState.INACTIVE;

    StageState current() {
        return state;
    }

    void set(StageState state) {
        this.state = state;
    }

    boolean canEnterStageMode() {
        return state == StageState.INACTIVE;
    }

    boolean canStartStage() {
        return state == StageState.STANDBY || state == StageState.INTRO;
    }

    boolean isActive() {
        return state != StageState.INACTIVE;
    }

    boolean isPlaying() {
        return state == StageState.PLAYING;
    }

    boolean isWatching() {
        return state == StageState.WATCHING;
    }

    boolean isInactive() {
        return state == StageState.INACTIVE;
    }

    boolean shouldPinPlayer() {
        return state == StageState.INTRO || state == StageState.STANDBY
                || state == StageState.PLAYING || state == StageState.OUTRO;
    }

    boolean shouldBlockInput() {
        return state == StageState.PLAYING || state == StageState.WATCHING
                || state == StageState.INTRO || state == StageState.OUTRO;
    }

    void enterIntro() {
        state = StageState.INTRO;
    }

    void enterStandby() {
        state = StageState.STANDBY;
    }

    void enterPlaying() {
        state = StageState.PLAYING;
    }

    void enterOutro() {
        state = StageState.OUTRO;
    }

    void enterWatching() {
        state = StageState.WATCHING;
    }

    void enterInactive() {
        state = StageState.INACTIVE;
    }
}
