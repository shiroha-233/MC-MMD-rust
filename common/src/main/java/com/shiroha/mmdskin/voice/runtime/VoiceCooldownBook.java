package com.shiroha.mmdskin.voice.runtime;

import com.shiroha.mmdskin.voice.VoiceEventType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** 文件职责：维护说话者事件冷却簿，避免重复触发播放。 */
final class VoiceCooldownBook {
    private final Map<String, SpeakerCooldownState> cooldowns = new HashMap<>();

    boolean tryAcquire(String speakerKey, VoiceEventType eventType, int eventCooldownTicks, int globalCooldownTicks) {
        return tryAcquire(speakerKey, eventType, eventCooldownTicks, globalCooldownTicks, System.currentTimeMillis());
    }

    boolean tryAcquire(String speakerKey,
                       VoiceEventType eventType,
                       int eventCooldownTicks,
                       int globalCooldownTicks,
                       long nowMillis) {
        SpeakerCooldownState state = cooldowns.computeIfAbsent(speakerKey, key -> new SpeakerCooldownState());
        state.lastUsedMillis = nowMillis;
        long cooldownMillis = Math.max(eventCooldownTicks, globalCooldownTicks) * 50L;
        state.retainUntilMillis = Math.max(state.retainUntilMillis, nowMillis + cooldownMillis);
        Long lastPlayed = state.lastPlayedMillis.get(eventType);
        if (lastPlayed != null && nowMillis - lastPlayed < cooldownMillis) {
            return false;
        }
        state.lastPlayedMillis.put(eventType, nowMillis);
        return true;
    }

    void evictStaleSpeakers(long nowMillis, long retentionMillis) {
        Iterator<Map.Entry<String, SpeakerCooldownState>> iterator = cooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SpeakerCooldownState> entry = iterator.next();
            SpeakerCooldownState state = entry.getValue();
            if (nowMillis - state.lastUsedMillis >= retentionMillis && nowMillis >= state.retainUntilMillis) {
                iterator.remove();
            }
        }
    }

    void clear() {
        cooldowns.clear();
    }

    private static final class SpeakerCooldownState {
        private final Map<VoiceEventType, Long> lastPlayedMillis = new EnumMap<>(VoiceEventType.class);
        private long lastUsedMillis;
        private long retainUntilMillis;
    }
}
