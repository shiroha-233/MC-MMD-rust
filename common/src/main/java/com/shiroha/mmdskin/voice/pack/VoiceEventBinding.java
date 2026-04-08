package com.shiroha.mmdskin.voice.pack;

import java.util.Map;
import java.util.List;

public record VoiceEventBinding(
        String folder,
        int cooldownTicks,
        int priority,
        String mode,
        List<VoiceClipDefinition> clips,
        Map<String, List<VoiceClipDefinition>> detailClips
) {
}
