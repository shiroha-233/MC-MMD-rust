package com.shiroha.mmdskin.stage.client;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationPort;
import com.shiroha.mmdskin.bridge.runtime.NativeRuntimePort;
import com.shiroha.mmdskin.bridge.runtime.NativeScenePort;
import com.shiroha.mmdskin.stage.application.StageSessionService;
import com.shiroha.mmdskin.stage.client.asset.LocalStagePackRepository;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import com.shiroha.mmdskin.stage.client.camera.port.StageFrameQueryPort;
import com.shiroha.mmdskin.stage.client.network.StageNetworkPlaybackBroadcastAdapter;
import com.shiroha.mmdskin.stage.client.network.StageNetworkSessionOutboundAdapter;
import com.shiroha.mmdskin.stage.client.playback.DefaultStagePlaybackPreferencesPort;
import com.shiroha.mmdskin.stage.client.playback.DefaultStagePlaybackRuntime;
import com.shiroha.mmdskin.stage.client.playback.StageHostPlaybackService;
import com.shiroha.mmdskin.stage.client.playback.StageLocalPlaybackPreferences;
import com.shiroha.mmdskin.stage.client.sync.StageAnimSyncHelper;
import com.shiroha.mmdskin.stage.client.viewmodel.StageLobbyViewModel;
import com.shiroha.mmdskin.ui.stage.StagePlaybackUiAdapter;

import java.util.Objects;
import java.util.function.Supplier;

/** 文件职责：组装并持有舞台客户端运行时对象图。 */
public final class StageClientRuntime {
    private static volatile StageClientRuntime instance;

    private final StageSessionService sessionService;
    private final StagePlaybackCoordinator playbackCoordinator;
    private final StageHostPlaybackService hostPlaybackService;
    private final DefaultStagePlaybackRuntime playbackRuntime;
    private final MMDCameraController cameraController;
    private final StageClientPacketHandler packetHandler;
    private final StageLobbyViewModel lobbyViewModel;
    private final LocalStagePackRepository stagePackRepository;
    private final StageAnimSyncHelper animSyncHelper;

    private StageClientRuntime(NativeRuntimePort runtimeBridge, NativeAnimationPort animationBridge) {
        Objects.requireNonNull(runtimeBridge, "runtimeBridge");
        Objects.requireNonNull(animationBridge, "animationBridge");

        StageLocalPlaybackPreferences playbackPreferences = StageLocalPlaybackPreferences.getInstance();
        this.sessionService = new StageSessionService(
                DefaultStageLocalPlayerContext.INSTANCE,
                new DefaultStagePlaybackPreferencesPort(playbackPreferences),
                StageNetworkSessionOutboundAdapter.INSTANCE
        );

        DefaultStageCameraSessionPort sessionPort = new DefaultStageCameraSessionPort(sessionService);
        StageNetworkPlaybackBroadcastAdapter broadcastAdapter = new StageNetworkPlaybackBroadcastAdapter(sessionService);
        DefaultStageLocalModelBindingPort localModelBindingPort =
                new DefaultStageLocalModelBindingPort((NativeScenePort) runtimeBridge);

        Supplier<MMDCameraController> cameraSupplier = () -> {
            MMDCameraController controller = instance != null ? instance.cameraController : null;
            if (controller == null) {
                throw new IllegalStateException("StageClientRuntime has not been fully initialized");
            }
            return controller;
        };
        StageFrameQueryPort frameQueryPort = new DefaultStageFrameQueryPort(cameraSupplier);
        this.animSyncHelper = new StageAnimSyncHelper(frameQueryPort, animationBridge);
        DefaultStagePlayerSyncPort playerSyncPort = new DefaultStagePlayerSyncPort(animSyncHelper);

        this.cameraController = new MMDCameraController(
                sessionPort,
                broadcastAdapter,
                StagePlaybackUiAdapter.INSTANCE,
                playerSyncPort,
                localModelBindingPort,
                animationBridge
        );
        this.playbackRuntime = new DefaultStagePlaybackRuntime(localModelBindingPort, animationBridge, cameraController);
        this.playbackCoordinator = new StagePlaybackCoordinator(
                playbackRuntime,
                broadcastAdapter,
                StagePlaybackUiAdapter.INSTANCE,
                sessionPort
        );
        this.hostPlaybackService = new StageHostPlaybackService(playbackRuntime, broadcastAdapter, sessionPort);
        this.packetHandler = new StageClientPacketHandler(sessionService, playbackCoordinator, animSyncHelper);
        this.lobbyViewModel = new StageLobbyViewModel(sessionService, playbackPreferences);
        this.stagePackRepository = new LocalStagePackRepository(animationBridge);
    }

    public static synchronized void initialize(NativeRuntimePort runtimeBridge, NativeAnimationPort animationBridge) {
        if (instance == null) {
            instance = new StageClientRuntime(runtimeBridge, animationBridge);
        }
    }

    public static StageClientRuntime get() {
        StageClientRuntime runtime = instance;
        if (runtime == null) {
            throw new IllegalStateException("StageClientRuntime has not been initialized");
        }
        return runtime;
    }

    public StageSessionService sessionService() {
        return sessionService;
    }

    public StagePlaybackCoordinator playbackCoordinator() {
        return playbackCoordinator;
    }

    public StageHostPlaybackService hostPlaybackService() {
        return hostPlaybackService;
    }

    public DefaultStagePlaybackRuntime playbackRuntime() {
        return playbackRuntime;
    }

    public MMDCameraController cameraController() {
        return cameraController;
    }

    public StageClientPacketHandler packetHandler() {
        return packetHandler;
    }

    public StageLobbyViewModel lobbyViewModel() {
        return lobbyViewModel;
    }

    public LocalStagePackRepository stagePackRepository() {
        return stagePackRepository;
    }

    public StageAnimSyncHelper animSyncHelper() {
        return animSyncHelper;
    }
}
