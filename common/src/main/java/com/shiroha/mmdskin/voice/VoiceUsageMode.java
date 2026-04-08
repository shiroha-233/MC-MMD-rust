package com.shiroha.mmdskin.voice;

import java.util.Locale;

public enum VoiceUsageMode {
    NORMAL,
    CUSTOM_ACTION,
    STAGE;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
