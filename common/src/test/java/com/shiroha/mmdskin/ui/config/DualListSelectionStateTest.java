package com.shiroha.mmdskin.ui.config;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DualListSelectionStateTest {
    @Test
    void shouldPreferCatalogKeyDuringActionMigration() {
        ActionWheelConfig.ActionEntry available = new ActionWheelConfig.ActionEntry(
                "Wave New", "wave_new", "CUSTOM", null, "4 KB", "catalog:wave");
        ActionWheelConfig.ActionEntry selected = new ActionWheelConfig.ActionEntry(
                "Wave Old", "wave_old", "DEFAULT", "Legacy", "1 KB", "catalog:wave");
        ActionWheelConfig.ActionEntry other = new ActionWheelConfig.ActionEntry(
                "Bow", "bow", "CUSTOM", null, "3 KB", "catalog:bow");

        DualListSelectionState<ActionWheelConfig.ActionEntry> state = new DualListSelectionState<>(
                List.of(available, other),
                List.of(selected),
                ActionWheelConfig.ActionEntry::matches,
                Comparator.comparing(entry -> entry.animId, String.CASE_INSENSITIVE_ORDER)
        );

        assertEquals(List.of(other), state.availableItems());
        assertEquals(List.of(selected), state.selectedItems());
    }

    @Test
    void shouldRestoreAvailableActionOrderingWhenClearingSelected() {
        ActionWheelConfig.ActionEntry wave = new ActionWheelConfig.ActionEntry("Wave", "wave");
        ActionWheelConfig.ActionEntry clap = new ActionWheelConfig.ActionEntry("Clap", "clap");
        ActionWheelConfig.ActionEntry bow = new ActionWheelConfig.ActionEntry("Bow", "bow");

        DualListSelectionState<ActionWheelConfig.ActionEntry> state = new DualListSelectionState<>(
                List.of(wave, clap),
                List.of(bow),
                ActionWheelConfig.ActionEntry::matches,
                Comparator.comparing(entry -> entry.animId, String.CASE_INSENSITIVE_ORDER)
        );

        state.moveAllToAvailable();

        assertEquals(List.of("bow", "clap", "wave"),
                state.availableItems().stream().map(entry -> entry.animId).toList());
        assertEquals(List.of(), state.selectedItems());
    }

    @Test
    void shouldPreferCatalogKeyDuringMorphMigration() {
        MorphWheelConfig.MorphEntry available = new MorphWheelConfig.MorphEntry(
                "Smile New", "smile_new", "MODEL", "Maid", "2 KB", "morphs/smile.vpd", "preset:smile");
        MorphWheelConfig.MorphEntry selected = MorphWheelConfig.MorphEntry.fromPreset("smile", "Smile");
        MorphWheelConfig.MorphEntry other = MorphWheelConfig.MorphEntry.fromPreset("wink", "Wink");

        DualListSelectionState<MorphWheelConfig.MorphEntry> state = new DualListSelectionState<>(
                List.of(available, other),
                List.of(selected),
                MorphWheelConfig.MorphEntry::matches,
                Comparator.comparing(entry -> entry.morphName, String.CASE_INSENSITIVE_ORDER)
        );

        assertEquals(List.of(other), state.availableItems());
        assertEquals(List.of(selected), state.selectedItems());
    }

    @Test
    void shouldRestoreAvailableMorphOrderingWhenReturningSelection() {
        MorphWheelConfig.MorphEntry smile = MorphWheelConfig.MorphEntry.fromPreset("smile", "Smile");
        MorphWheelConfig.MorphEntry angry = MorphWheelConfig.MorphEntry.fromPreset("angry", "Angry");
        MorphWheelConfig.MorphEntry wink = MorphWheelConfig.MorphEntry.fromPreset("wink", "Wink");

        DualListSelectionState<MorphWheelConfig.MorphEntry> state = new DualListSelectionState<>(
                List.of(smile, angry),
                List.of(wink),
                MorphWheelConfig.MorphEntry::matches,
                Comparator.comparing(entry -> entry.morphName, String.CASE_INSENSITIVE_ORDER)
        );

        state.moveAllToAvailable();

        assertEquals(List.of("angry", "smile", "wink"),
                state.availableItems().stream().map(entry -> entry.morphName).toList());
        assertEquals(List.of(), state.selectedItems());
    }
}
