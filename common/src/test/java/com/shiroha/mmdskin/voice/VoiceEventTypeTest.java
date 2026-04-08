package com.shiroha.mmdskin.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VoiceEventTypeTest {
    @Test
    void shouldParseConfigKeys() {
        assertEquals(VoiceEventType.CUSTOM_ACTION, VoiceEventType.fromConfigKey("custom_action"));
        assertEquals(VoiceEventType.STAGE_START, VoiceEventType.fromConfigKey("stage-start"));
        assertEquals(VoiceEventType.EFFECT_GAINED, VoiceEventType.fromConfigKey("effect_gained"));
        assertNull(VoiceEventType.fromConfigKey("unknown"));
    }
}
