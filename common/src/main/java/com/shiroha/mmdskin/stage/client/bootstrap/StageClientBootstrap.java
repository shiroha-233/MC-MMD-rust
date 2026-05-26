package com.shiroha.mmdskin.stage.client.bootstrap;

import com.shiroha.mmdskin.stage.application.StageSessionService;
import com.shiroha.mmdskin.stage.client.DefaultStageCameraSessionPort;
import com.shiroha.mmdskin.stage.client.DefaultStageLocalModelBindingPort;
import com.shiroha.mmdskin.stage.client.DefaultStageLocalPlayerContext;
import com.shiroha.mmdskin.stage.client.StagePlaybackCoordinator;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import com.shiroha.mmdskin.stage.client.playback.DefaultStagePlaybackPreferencesPort;
import com.shiroha.mmdskin.stage.client.playback.DefaultStagePlaybackRuntime;
import com.shiroha.mmdskin.stage.client.playback.StageHostPlaybackService;
import com.shiroha.mmdskin.ui.network.StageNetworkPlaybackBroadcastAdapter;
import com.shiroha.mmdskin.ui.network.StageNetworkSessionOutboundAdapter;
import com.shiroha.mmdskin.ui.stage.StagePlaybackUiAdapter;

public final class StageClientBootstrap {
    private StageClientBootstrap() {
    }

    public static void initialize() {
        StageSessionService.getInstance().configureRuntimeCollaborators(
                DefaultStageLocalPlayerContext.INSTANCE,
                DefaultStagePlaybackPreferencesPort.INSTANCE,
                StageNetworkSessionOutboundAdapter.INSTANCE
        );
        StageHostPlaybackService.getInstance().configureRuntimeCollaborators(
                DefaultStagePlaybackRuntime.INSTANCE,
                StageNetworkPlaybackBroadcastAdapter.INSTANCE,
                DefaultStageCameraSessionPort.INSTANCE
        );
        DefaultStagePlaybackRuntime.INSTANCE.configureRuntimeCollaborators(
                DefaultStageLocalModelBindingPort.INSTANCE
        );
        StagePlaybackCoordinator.getInstance().configureRuntimeCollaborators(
                DefaultStagePlaybackRuntime.INSTANCE,
                StageNetworkPlaybackBroadcastAdapter.INSTANCE,
                StagePlaybackUiAdapter.INSTANCE,
                DefaultStageCameraSessionPort.INSTANCE
        );
        MMDCameraController.getInstance().configureRuntimeCollaborators(
                DefaultStageCameraSessionPort.INSTANCE,
                StageNetworkPlaybackBroadcastAdapter.INSTANCE,
                StagePlaybackUiAdapter.INSTANCE
        );
    }
}
