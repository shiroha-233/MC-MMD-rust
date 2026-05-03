package com.shiroha.mmdskin.voice.runtime;

import com.shiroha.mmdskin.voice.VoiceEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoicePlaybackControllerTest {
    @Test
    void shouldEvictCooldownStateDuringSerialTick() {
        VoiceCooldownBook cooldownBook = new VoiceCooldownBook();
        VoicePlaybackController controller = new VoicePlaybackController(
                null,
                cooldownBook,
                new ActiveVoiceRegistry(),
                () -> null,
                () -> null
        );

        assertTrue(cooldownBook.tryAcquire("mob:1", VoiceEventType.STAGE_START, 1_400, 0, 1_000L));

        controller.tick(61_000L);
        assertFalse(cooldownBook.tryAcquire("mob:1", VoiceEventType.STAGE_START, 1_400, 0, 61_000L));

        controller.tick(71_000L);
        assertTrue(cooldownBook.tryAcquire("mob:1", VoiceEventType.STAGE_START, 1_400, 0, 71_001L));
    }
}
