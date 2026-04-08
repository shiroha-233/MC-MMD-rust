package com.shiroha.mmdskin.voice;

import java.util.List;

public record VoiceEventContext(
        String speakerKey,
        VoiceTargetType targetType,
        VoiceEventType eventType,
        VoiceUsageMode usageMode,
        String modelName,
        String entityTypeId,
        List<String> detailKeys
) {
    public VoiceEventContext {
        detailKeys = detailKeys == null ? List.of() : List.copyOf(detailKeys);
    }
}
