package com.shiroha.mmdskin.stage.client.playback.port;

import java.util.UUID;

public interface StagePlaybackUiPort {
    void showInvite(UUID hostUUID);

    void markStageSelectionStartedAndClose();

    void openStageSelection();

    void closeStageSelectionIfOpen();
}
