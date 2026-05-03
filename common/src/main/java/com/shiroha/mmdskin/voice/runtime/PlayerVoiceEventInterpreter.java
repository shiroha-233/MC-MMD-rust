package com.shiroha.mmdskin.voice.runtime;

import com.shiroha.mmdskin.voice.VoiceEventContext;
import com.shiroha.mmdskin.voice.VoiceEventType;
import com.shiroha.mmdskin.voice.VoiceTargetType;
import com.shiroha.mmdskin.voice.VoiceUsageMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 文件职责：根据玩家快照与状态簿解释语音事件，保持 player 事件语义稳定。 */
final class PlayerVoiceEventInterpreter {
    static final String UNKNOWN_KEY = "unknown";
    private static final int HUNGER_LOW_THRESHOLD = 14;
    private static final int HUNGER_CRITICAL_THRESHOLD = 6;
    static final int IDLE_START_TICKS = 20 * 15;
    static final int IDLE_REPEAT_TICKS = 20 * 45;

    List<VoiceEventContext> interpret(PlayerVoiceSnapshot snapshot, PlayerVoiceObserverState state) {
        state.tickCounter++;
        List<VoiceEventContext> events = new ArrayList<>();

        observeCombat(snapshot, state, events);
        observeHunger(snapshot, state, events);
        observeEffects(snapshot, state, events);
        observeWeather(snapshot, state, events);
        observeDayPhase(snapshot, state, events);
        observeBiome(snapshot, state, events);
        observeDimension(snapshot, state, events);
        observeContainer(snapshot, state, events);
        observeIdle(snapshot, state, events);
        return List.copyOf(events);
    }

    private void observeCombat(PlayerVoiceSnapshot snapshot,
                               PlayerVoiceObserverState state,
                               List<VoiceEventContext> events) {
        if (snapshot.hurt() && !state.previousHurt) {
            events.add(baseContext(snapshot, VoiceEventType.HURT, List.of()));
        }
        if (snapshot.swinging() && !state.previousSwinging) {
            events.add(baseContext(snapshot, VoiceEventType.ATTACK, List.of()));
        }
        if (snapshot.dead() && !state.previousDead) {
            events.add(baseContext(snapshot, VoiceEventType.DEATH, snapshot.deathDetailKeys()));
        }
        state.previousHurt = snapshot.hurt();
        state.previousSwinging = snapshot.swinging();
        state.previousDead = snapshot.dead();
    }

    private void observeHunger(PlayerVoiceSnapshot snapshot,
                               PlayerVoiceObserverState state,
                               List<VoiceEventContext> events) {
        HungerTier currentTier = HungerTier.fromFoodLevel(snapshot.foodLevel());
        if (state.previousFoodLevel >= 0
                && snapshot.foodLevel() < state.previousFoodLevel
                && currentTier != state.previousHungerTier
                && currentTier != HungerTier.NONE) {
            events.add(baseContext(snapshot, VoiceEventType.HUNGER, List.of(currentTier.configKey())));
        }
        state.previousFoodLevel = snapshot.foodLevel();
        state.previousHungerTier = currentTier;
    }

    private void observeEffects(PlayerVoiceSnapshot snapshot,
                                PlayerVoiceObserverState state,
                                List<VoiceEventContext> events) {
        for (Map.Entry<String, Integer> effectEntry : snapshot.effectAmplifiers().entrySet()) {
            String effectId = effectEntry.getKey();
            Integer amplifier = effectEntry.getValue();
            Integer previousAmplifier = state.activeEffectAmplifiers.get(effectId);
            if (previousAmplifier == null || !previousAmplifier.equals(amplifier)) {
                events.add(baseContext(snapshot, VoiceEventType.EFFECT_GAINED, buildEffectDetailKeys(effectId, amplifier)));
            }
        }
        state.activeEffectAmplifiers.clear();
        state.activeEffectAmplifiers.putAll(snapshot.effectAmplifiers());
    }

    private void observeWeather(PlayerVoiceSnapshot snapshot,
                                PlayerVoiceObserverState state,
                                List<VoiceEventContext> events) {
        if (!snapshot.weatherKey().equals(state.previousWeatherKey) && !UNKNOWN_KEY.equals(snapshot.weatherKey())) {
            events.add(baseContext(snapshot, VoiceEventType.WEATHER, List.of(snapshot.weatherKey())));
        }
        state.previousWeatherKey = snapshot.weatherKey();
    }

    private void observeDayPhase(PlayerVoiceSnapshot snapshot,
                                 PlayerVoiceObserverState state,
                                 List<VoiceEventContext> events) {
        if (!snapshot.dayPhaseKey().equals(state.previousDayPhaseKey) && !UNKNOWN_KEY.equals(snapshot.dayPhaseKey())) {
            events.add(baseContext(snapshot, VoiceEventType.DAY_PHASE, List.of(snapshot.dayPhaseKey())));
        }
        state.previousDayPhaseKey = snapshot.dayPhaseKey();
    }

    private void observeBiome(PlayerVoiceSnapshot snapshot,
                              PlayerVoiceObserverState state,
                              List<VoiceEventContext> events) {
        if (snapshot.biomeId() != null && !snapshot.biomeId().equals(state.previousBiomeId)) {
            events.add(baseContext(snapshot, VoiceEventType.BIOME_ENTER, snapshot.biomeDetailKeys()));
        }
        state.previousBiomeId = snapshot.biomeId();
    }

    private void observeDimension(PlayerVoiceSnapshot snapshot,
                                  PlayerVoiceObserverState state,
                                  List<VoiceEventContext> events) {
        if (snapshot.dimensionId() != null && !snapshot.dimensionId().equals(state.previousDimensionId)) {
            events.add(baseContext(snapshot, VoiceEventType.DIMENSION_ENTER, snapshot.dimensionDetailKeys()));
        }
        state.previousDimensionId = snapshot.dimensionId();
    }

    private void observeContainer(PlayerVoiceSnapshot snapshot,
                                  PlayerVoiceObserverState state,
                                  List<VoiceEventContext> events) {
        if (snapshot.containerType() != null && !snapshot.containerType().equals(state.previousContainerType)) {
            events.add(baseContext(snapshot, VoiceEventType.CONTAINER_OPEN, List.of(snapshot.containerType())));
        }
        state.previousContainerType = snapshot.containerType();
    }

    private void observeIdle(PlayerVoiceSnapshot snapshot,
                             PlayerVoiceObserverState state,
                             List<VoiceEventContext> events) {
        if (snapshot.idleCandidate()) {
            state.idleTicks++;
            if (state.idleTicks >= IDLE_START_TICKS && state.tickCounter - state.lastIdleEmitTick >= IDLE_REPEAT_TICKS) {
                events.add(baseContext(snapshot, VoiceEventType.IDLE, List.of(snapshot.weatherKey(), snapshot.dayPhaseKey())));
                state.lastIdleEmitTick = state.tickCounter;
            }
            return;
        }
        state.idleTicks = 0;
    }

    private static List<String> buildEffectDetailKeys(String effectId, int amplifier) {
        var location = net.minecraft.resources.ResourceLocation.tryParse(effectId);
        if (location == null) {
            return List.of();
        }
        return VoiceRuntimeSupport.normalizeDetailKeys(List.of(
                VoiceRuntimeSupport.toDetailPath(location),
                location.getPath(),
                VoiceRuntimeSupport.toDetailPath(location) + "/amp_" + amplifier
        ));
    }

    private static VoiceEventContext baseContext(PlayerVoiceSnapshot snapshot,
                                                 VoiceEventType eventType,
                                                 List<String> detailKeys) {
        return new VoiceEventContext(
                snapshot.speakerKey(),
                VoiceTargetType.PLAYER,
                eventType,
                VoiceUsageMode.NORMAL,
                snapshot.modelName(),
                null,
                VoiceRuntimeSupport.normalizeDetailKeys(detailKeys)
        );
    }

    enum HungerTier {
        NONE,
        LOW,
        CRITICAL;

        private static HungerTier fromFoodLevel(int foodLevel) {
            if (foodLevel <= HUNGER_CRITICAL_THRESHOLD) {
                return CRITICAL;
            }
            if (foodLevel <= HUNGER_LOW_THRESHOLD) {
                return LOW;
            }
            return NONE;
        }

        private String configKey() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
