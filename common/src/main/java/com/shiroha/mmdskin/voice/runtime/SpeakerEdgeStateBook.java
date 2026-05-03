package com.shiroha.mmdskin.voice.runtime;

import com.shiroha.mmdskin.voice.VoiceEventType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** 文件职责：记录说话者边沿状态，只在状态跃迁时产出事件。 */
final class SpeakerEdgeStateBook {
    private final Map<String, SpeakerEdgeState> speakerStates = new HashMap<>();

    List<VoiceEventType> update(String speakerKey, boolean hurtActive, boolean deadActive) {
        return update(speakerKey, hurtActive, deadActive, System.currentTimeMillis());
    }

    List<VoiceEventType> update(String speakerKey, boolean hurtActive, boolean deadActive, long nowMillis) {
        SpeakerEdgeState state = speakerStates.computeIfAbsent(speakerKey, key -> new SpeakerEdgeState());
        state.lastSeenMillis = nowMillis;
        List<VoiceEventType> events = new ArrayList<>(2);
        if (hurtActive && !state.hurtActive) {
            events.add(VoiceEventType.HURT);
        }
        if (deadActive && !state.deadActive) {
            events.add(VoiceEventType.DEATH);
        }
        state.hurtActive = hurtActive;
        state.deadActive = deadActive;
        return List.copyOf(events);
    }

    void evictStaleSpeakers(long nowMillis, long retentionMillis) {
        Iterator<Map.Entry<String, SpeakerEdgeState>> iterator = speakerStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SpeakerEdgeState> entry = iterator.next();
            if (nowMillis - entry.getValue().lastSeenMillis >= retentionMillis) {
                iterator.remove();
            }
        }
    }

    void clear() {
        speakerStates.clear();
    }

    private static final class SpeakerEdgeState {
        private boolean hurtActive;
        private boolean deadActive;
        private long lastSeenMillis;
    }
}
