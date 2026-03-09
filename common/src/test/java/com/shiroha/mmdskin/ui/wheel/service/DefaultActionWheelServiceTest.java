package com.shiroha.mmdskin.ui.wheel.service;

import com.shiroha.mmdskin.ui.config.ActionWheelConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultActionWheelServiceTest {
    @Test
    void shouldMapConfiguredActionsIntoOptions() {
        ActionWheelConfig.ActionEntry wave = new ActionWheelConfig.ActionEntry();
        wave.name = "Wave";
        wave.animId = "wave";
        ActionWheelConfig.ActionEntry bow = new ActionWheelConfig.ActionEntry();
        bow.name = "Bow";
        bow.animId = "bow";

        DefaultActionWheelService service = new DefaultActionWheelService(
                () -> List.of(wave, bow),
                animId -> true,
                new RecordingActionSyncPort());

        List<ActionOption> options = service.loadActions();

        assertEquals(List.of(new ActionOption("Wave", "wave"), new ActionOption("Bow", "bow")), options);
    }

    @Test
    void shouldSyncOnlyWhenLocalActionSucceeds() {
        AtomicReference<String> syncedAnimId = new AtomicReference<>();
        DefaultActionWheelService service = new DefaultActionWheelService(
                List::of,
                animId -> false,
                new RecordingActionSyncPort(syncedAnimId));

        service.selectAction("wave");

        assertEquals(null, syncedAnimId.get());

        DefaultActionWheelService successfulService = new DefaultActionWheelService(
                List::of,
                animId -> true,
                new RecordingActionSyncPort(syncedAnimId));

        successfulService.selectAction("bow");

        assertEquals("bow", syncedAnimId.get());
    }

    private record RecordingActionSyncPort(AtomicReference<String> syncedAnimId) implements ActionSyncPort {
        private RecordingActionSyncPort() {
            this(new AtomicReference<>());
        }

        @Override
        public void syncAction(String animId) {
            syncedAnimId.set(animId);
        }

        @Override
        public void syncAnimStop() {
            assertTrue(true);
        }
    }
}
