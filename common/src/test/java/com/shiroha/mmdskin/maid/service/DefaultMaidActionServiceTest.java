package com.shiroha.mmdskin.maid.service;

import com.shiroha.mmdskin.ui.config.ActionWheelConfig;
import com.shiroha.mmdskin.ui.wheel.service.ActionOption;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultMaidActionServiceTest {
    @Test
    void shouldLoadConfiguredActions() {
        ActionWheelConfig.ActionEntry wave = new ActionWheelConfig.ActionEntry();
        wave.name = "Wave";
        wave.animId = "wave";

        DefaultMaidActionService service = new DefaultMaidActionService(
                () -> List.of(wave),
                (maidUUID, animId) -> {
                },
                (entityId, animId) -> {
                });

        assertEquals(List.of(new ActionOption("Wave", "wave")), service.loadActions());
    }

    @Test
    void shouldSplitRuntimeAndSyncWhenSelectingAction() {
        UUID maidUUID = UUID.randomUUID();
        AtomicReference<String> runtimeAnim = new AtomicReference<>();
        AtomicReference<String> syncedAnim = new AtomicReference<>();
        AtomicReference<Integer> syncedEntityId = new AtomicReference<>();
        DefaultMaidActionService service = new DefaultMaidActionService(
                List::of,
                (uuid, animId) -> runtimeAnim.set(uuid + ":" + animId),
                (entityId, animId) -> {
                    syncedEntityId.set(entityId);
                    syncedAnim.set(animId);
                });

        service.selectAction(maidUUID, 7, "bow");

        assertEquals(maidUUID + ":bow", runtimeAnim.get());
        assertEquals(7, syncedEntityId.get());
        assertEquals("bow", syncedAnim.get());
    }
}
