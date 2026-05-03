package com.shiroha.mmdskin.voice.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 文件职责：维护当前活跃语音播放器并负责生命周期清理。 */
final class ActiveVoiceRegistry {
    private final Map<String, ActiveVoice> activeVoices = new HashMap<>();

    ActiveVoice get(String speakerKey) {
        return activeVoices.get(speakerKey);
    }

    void replace(String speakerKey, VoiceAudioPlayer player, int priority) {
        ActiveVoice previous = activeVoices.put(speakerKey, new ActiveVoice(player, priority));
        if (previous != null) {
            previous.player().cleanup();
        }
    }

    void removeAndCleanup(String speakerKey) {
        ActiveVoice previous = activeVoices.remove(speakerKey);
        if (previous != null) {
            previous.player().cleanup();
        }
    }

    void cleanupFinished() {
        List<String> finished = new ArrayList<>();
        for (Map.Entry<String, ActiveVoice> entry : activeVoices.entrySet()) {
            if (!entry.getValue().player().isPlaying()) {
                entry.getValue().player().cleanup();
                finished.add(entry.getKey());
            }
        }
        for (String key : finished) {
            activeVoices.remove(key);
        }
    }

    void stopAll() {
        for (ActiveVoice activeVoice : activeVoices.values()) {
            activeVoice.player().cleanup();
        }
        activeVoices.clear();
    }

    record ActiveVoice(VoiceAudioPlayer player, int priority) {
    }
}
