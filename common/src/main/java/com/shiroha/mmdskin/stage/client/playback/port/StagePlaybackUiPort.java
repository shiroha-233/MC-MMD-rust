package com.shiroha.mmdskin.stage.client.playback.port;

import com.shiroha.mmdskin.stage.client.port.StageSelectionUiPort;

import java.util.UUID;

/** 定义播放流程触达 UI 的边界。 */
public interface StagePlaybackUiPort extends StageSelectionUiPort {
    void showInvite(UUID hostUUID);

    void markStageSelectionStartedAndClose();

    void closeStageSelectionIfOpen();
}
