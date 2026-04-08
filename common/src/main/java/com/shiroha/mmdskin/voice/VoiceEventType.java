package com.shiroha.mmdskin.voice;

import java.util.Locale;

public enum VoiceEventType {
    ATTACK,
    HURT,
    DEATH,
    HUNGER,
    EFFECT_GAINED,
    IDLE,
    WEATHER,
    DAY_PHASE,
    BIOME_ENTER,
    DIMENSION_ENTER,
    CONTAINER_OPEN,
    MODEL_SWITCH,
    CUSTOM_ACTION,
    STAGE_START,
    STAGE_END;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static VoiceEventType fromConfigKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        normalized = normalized.replace('-', '_');
        try {
            return VoiceEventType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
