package com.shiroha.mmdskin.ui.wheel.service;

import com.shiroha.mmdskin.ui.config.MorphWheelConfig;
import com.shiroha.mmdskin.expression.ExpressionSelection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultMorphWheelServiceTest {
    @Test
    void shouldAppendResetOptionAfterConfiguredMorphs() {
        MorphWheelConfig.MorphEntry smile = new MorphWheelConfig.MorphEntry();
        smile.displayName = "Smile";
        smile.morphName = "smile";

        DefaultMorphWheelService service = new DefaultMorphWheelService(
                () -> List.of(smile),
                entry -> "morphs/smile.vpd",
                new FakeMorphRuntimePort(),
                morphName -> {
                });

        List<MorphOption> options = service.loadMorphs();

        assertEquals(2, options.size());
        assertEquals("Smile", options.get(0).displayName());
        assertEquals("smile", options.get(0).morphName());
        assertEquals("vpd:smile", options.get(0).syncToken());
        assertEquals(true, options.get(1).resetAction());
    }

    @Test
    void shouldSyncOnlyWhenMorphApplySucceeds() {
        AtomicReference<String> syncedMorph = new AtomicReference<>();
        DefaultMorphWheelService service = new DefaultMorphWheelService(
                List::of,
                entry -> null,
                new FakeMorphRuntimePort(false, true),
                syncedMorph::set);

        service.selectMorph(new MorphOption("Smile", "smile", "morphs/smile.vpd", "vpd:smile", false));
        assertEquals(null, syncedMorph.get());

        service.selectMorph(new MorphOption("Reset", "__reset__", null, "__reset__", true));
        assertEquals("__reset__", syncedMorph.get());
    }

    @Test
    void shouldSyncPresetTokenWhenPresetApplySucceeds() {
        AtomicReference<String> syncedMorph = new AtomicReference<>();
        DefaultMorphWheelService service = new DefaultMorphWheelService(
                List::of,
                entry -> null,
                new FakeMorphRuntimePort(true, true),
                syncedMorph::set);

        service.selectMorph(new MorphOption("Smile", "smile", null, "preset:smile", false));

        assertEquals("preset:smile", syncedMorph.get());
    }

    private record FakeMorphRuntimePort(boolean applyResult, boolean resetResult)
            implements DefaultMorphWheelService.MorphRuntimePort {
        private FakeMorphRuntimePort() {
            this(true, true);
        }

        @Override
        public boolean applyCurrentPlayerMorph(ExpressionSelection selection, String filePath) {
            return selection.type() == ExpressionSelection.Type.RESET ? resetResult : applyResult;
        }
    }
}
