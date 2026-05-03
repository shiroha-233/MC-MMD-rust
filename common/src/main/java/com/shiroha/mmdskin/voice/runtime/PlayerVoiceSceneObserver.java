package com.shiroha.mmdskin.voice.runtime;

import com.shiroha.mmdskin.voice.VoiceEventContext;
import net.minecraft.client.Minecraft;

import java.util.List;

/** 文件职责：驱动玩家语音的主线程采样与事件分发门面。 */
public final class PlayerVoiceSceneObserver {
    private static final PlayerVoiceSceneObserver INSTANCE = new PlayerVoiceSceneObserver();

    private final PlayerVoiceSnapshotCollector snapshotCollector = new PlayerVoiceSnapshotCollector();
    private final PlayerVoiceEventInterpreter eventInterpreter = new PlayerVoiceEventInterpreter();
    private final PlayerVoiceObserverState observerState = new PlayerVoiceObserverState();

    private PlayerVoiceSceneObserver() {
    }

    public static PlayerVoiceSceneObserver getInstance() {
        return INSTANCE;
    }

    public void tick(Minecraft minecraft) {
        PlayerVoiceSnapshot snapshot = snapshotCollector.collect(minecraft);
        if (snapshot == null) {
            observerState.reset();
            return;
        }
        VoicePlaybackManager playbackManager = VoicePlaybackManager.getInstance();
        List<VoiceEventContext> events = eventInterpreter.interpret(snapshot, observerState);
        for (VoiceEventContext event : events) {
            playbackManager.emit(event);
        }
    }

    public void onDisconnect() {
        observerState.reset();
    }
}
