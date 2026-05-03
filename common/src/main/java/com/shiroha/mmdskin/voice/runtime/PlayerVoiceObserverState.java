package com.shiroha.mmdskin.voice.runtime;

import java.util.HashMap;
import java.util.Map;

/** 文件职责：保存玩家语音观察器的跨 tick 状态簿。 */
final class PlayerVoiceObserverState {
    int previousFoodLevel = -1;
    PlayerVoiceEventInterpreter.HungerTier previousHungerTier = PlayerVoiceEventInterpreter.HungerTier.NONE;
    final Map<String, Integer> activeEffectAmplifiers = new HashMap<>();
    String previousWeatherKey = PlayerVoiceEventInterpreter.UNKNOWN_KEY;
    String previousDayPhaseKey = PlayerVoiceEventInterpreter.UNKNOWN_KEY;
    String previousBiomeId;
    String previousDimensionId;
    String previousContainerType;
    boolean previousSwinging;
    boolean previousHurt;
    boolean previousDead;
    int idleTicks;
    int lastIdleEmitTick = -PlayerVoiceEventInterpreter.IDLE_REPEAT_TICKS;
    int tickCounter;

    void reset() {
        previousFoodLevel = -1;
        previousHungerTier = PlayerVoiceEventInterpreter.HungerTier.NONE;
        activeEffectAmplifiers.clear();
        previousWeatherKey = PlayerVoiceEventInterpreter.UNKNOWN_KEY;
        previousDayPhaseKey = PlayerVoiceEventInterpreter.UNKNOWN_KEY;
        previousBiomeId = null;
        previousDimensionId = null;
        previousContainerType = null;
        previousSwinging = false;
        previousHurt = false;
        previousDead = false;
        idleTicks = 0;
        lastIdleEmitTick = -PlayerVoiceEventInterpreter.IDLE_REPEAT_TICKS;
        tickCounter = 0;
    }
}
