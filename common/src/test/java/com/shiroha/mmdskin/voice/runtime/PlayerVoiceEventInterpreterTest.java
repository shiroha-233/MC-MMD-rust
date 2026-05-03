package com.shiroha.mmdskin.voice.runtime;

import com.shiroha.mmdskin.voice.VoiceEventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerVoiceEventInterpreterTest {
    @Test
    void shouldEmitHungerWhenFoodDropsIntoNewTier() {
        PlayerVoiceEventInterpreter interpreter = new PlayerVoiceEventInterpreter();
        PlayerVoiceObserverState state = new PlayerVoiceObserverState();

        interpreter.interpret(snapshot(20, false, false, false, Map.of(), false), state);
        var events = interpreter.interpret(snapshot(14, false, false, false, Map.of(), false), state);

        assertEquals(1, events.size());
        assertEquals(VoiceEventType.HUNGER, events.get(0).eventType());
        assertEquals(List.of("low"), events.get(0).detailKeys());
    }

    @Test
    void shouldEmitIdleAfterThresholdAndRepeatWindow() {
        PlayerVoiceEventInterpreter interpreter = new PlayerVoiceEventInterpreter();
        PlayerVoiceObserverState state = new PlayerVoiceObserverState();

        interpreter.interpret(snapshot(20, false, false, false, Map.of(), false), state);

        for (int tick = 0; tick < PlayerVoiceEventInterpreter.IDLE_START_TICKS - 1; tick++) {
            assertTrue(interpreter.interpret(snapshot(20, false, false, false, Map.of(), true), state).isEmpty());
        }

        var firstIdle = interpreter.interpret(snapshot(20, false, false, false, Map.of(), true), state);
        assertEquals(1, firstIdle.size());
        assertEquals(VoiceEventType.IDLE, firstIdle.get(0).eventType());
        assertEquals(List.of("clear", "day"), firstIdle.get(0).detailKeys());

        for (int tick = 0; tick < PlayerVoiceEventInterpreter.IDLE_REPEAT_TICKS - 1; tick++) {
            assertTrue(interpreter.interpret(snapshot(20, false, false, false, Map.of(), true), state).isEmpty());
        }

        var secondIdle = interpreter.interpret(snapshot(20, false, false, false, Map.of(), true), state);
        assertEquals(1, secondIdle.size());
        assertEquals(VoiceEventType.IDLE, secondIdle.get(0).eventType());
    }

    private static PlayerVoiceSnapshot snapshot(int foodLevel,
                                                boolean hurt,
                                                boolean swinging,
                                                boolean dead,
                                                Map<String, Integer> effects,
                                                boolean idleCandidate) {
        return new PlayerVoiceSnapshot(
                "player:test",
                "miku",
                hurt,
                swinging,
                dead,
                List.of(),
                foodLevel,
                effects,
                "clear",
                "day",
                null,
                List.of(),
                "minecraft/overworld",
                List.of("minecraft/overworld", "overworld"),
                null,
                idleCandidate
        );
    }
}
