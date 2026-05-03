package com.shiroha.mmdskin.voice.runtime;

import java.util.List;
import java.util.Map;

/** 文件职责：承载玩家主线程采样结果，供事件解释阶段串行消费。 */
record PlayerVoiceSnapshot(
        String speakerKey,
        String modelName,
        boolean hurt,
        boolean swinging,
        boolean dead,
        List<String> deathDetailKeys,
        int foodLevel,
        Map<String, Integer> effectAmplifiers,
        String weatherKey,
        String dayPhaseKey,
        String biomeId,
        List<String> biomeDetailKeys,
        String dimensionId,
        List<String> dimensionDetailKeys,
        String containerType,
        boolean idleCandidate
) {
    PlayerVoiceSnapshot {
        deathDetailKeys = deathDetailKeys == null ? List.of() : List.copyOf(deathDetailKeys);
        effectAmplifiers = effectAmplifiers == null ? Map.of() : Map.copyOf(effectAmplifiers);
        biomeDetailKeys = biomeDetailKeys == null ? List.of() : List.copyOf(biomeDetailKeys);
        dimensionDetailKeys = dimensionDetailKeys == null ? List.of() : List.copyOf(dimensionDetailKeys);
    }
}
