package com.shiroha.mmdskin.voice.runtime;

import com.shiroha.mmdskin.voice.VoiceEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceCooldownBookTest {
    @Test
    void shouldUseMaxOfEventAndGlobalCooldown() {
        VoiceCooldownBook cooldownBook = new VoiceCooldownBook();

        assertTrue(cooldownBook.tryAcquire("player:1", VoiceEventType.HURT, 10, 20, 1_000L));
        assertFalse(cooldownBook.tryAcquire("player:1", VoiceEventType.HURT, 10, 20, 1_950L));
        assertTrue(cooldownBook.tryAcquire("player:1", VoiceEventType.HURT, 10, 20, 2_001L));
    }

    @Test
    void shouldEvictOnlySpeakersUnusedPastRetention() {
        VoiceCooldownBook cooldownBook = new VoiceCooldownBook();

        assertTrue(cooldownBook.tryAcquire("player:stale", VoiceEventType.HURT, 200, 0, 1_000L));
        assertTrue(cooldownBook.tryAcquire("player:fresh", VoiceEventType.HURT, 200, 0, 59_500L));

        cooldownBook.evictStaleSpeakers(61_000L, 60_000L);

        assertTrue(cooldownBook.tryAcquire("player:stale", VoiceEventType.HURT, 200, 0, 61_001L));
        assertFalse(cooldownBook.tryAcquire("player:fresh", VoiceEventType.HURT, 200, 0, 61_001L));
    }

    @Test
    void shouldKeepCooldownStateUntilLongCooldownCompletes() {
        VoiceCooldownBook cooldownBook = new VoiceCooldownBook();

        assertTrue(cooldownBook.tryAcquire("player:stage", VoiceEventType.STAGE_START, 1_400, 0, 1_000L));

        cooldownBook.evictStaleSpeakers(61_000L, 60_000L);
        assertFalse(cooldownBook.tryAcquire("player:stage", VoiceEventType.STAGE_START, 1_400, 0, 61_000L));

        cooldownBook.evictStaleSpeakers(71_000L, 60_000L);
        assertTrue(cooldownBook.tryAcquire("player:stage", VoiceEventType.STAGE_START, 1_400, 0, 71_001L));
    }
}
