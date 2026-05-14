/* 文件职责：验证表情轮盘服务的预设编码、VPD 选择和重置同步语义。 */
package com.shiroha.mmdskin.ui.wheel.service;

import com.shiroha.mmdskin.expression.ExpressionSelection;
import com.shiroha.mmdskin.expression.ExpressionSelectionCodec;
import com.shiroha.mmdskin.ui.config.MorphWheelConfig;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultMorphWheelServiceTest {
    @Test
    void shouldAppendResetOptionAfterConfiguredMorphs() {
        MorphWheelConfig.MorphEntry smile = MorphWheelConfig.MorphEntry.fromPreset("smile", "Smile");

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
        assertEquals(ExpressionSelectionCodec.encode(ExpressionSelection.preset("smile")), options.get(0).syncToken());
        assertEquals(true, options.get(1).resetAction());
    }

    @Test
    void shouldEncodeFileEntriesAsVpdTokens() {
        MorphWheelConfig.MorphEntry smile = new MorphWheelConfig.MorphEntry();
        smile.displayName = "Smile File";
        smile.morphName = "smile";
        smile.filePath = "morphs/smile.vpd";

        DefaultMorphWheelService service = new DefaultMorphWheelService(
                () -> List.of(smile),
                entry -> entry.filePath,
                new FakeMorphRuntimePort(),
                morphName -> {
                });

        MorphOption option = service.loadMorphs().getFirst();

        assertEquals("vpd:smile", option.syncToken());
        assertEquals("morphs/smile.vpd", option.filePath());
    }

    @Test
    void shouldSyncOnlyWhenMorphApplySucceeds() {
        AtomicReference<String> syncedMorph = new AtomicReference<>();
        DefaultMorphWheelService service = new DefaultMorphWheelService(
                List::of,
                entry -> null,
                new FakeMorphRuntimePort(false),
                syncedMorph::set);

        service.selectMorph(new MorphOption("Smile", "smile", "morphs/smile.vpd", "vpd:smile", false));
        assertEquals(null, syncedMorph.get());

        service = new DefaultMorphWheelService(
                List::of,
                entry -> null,
                new FakeMorphRuntimePort(true),
                syncedMorph::set);

        service.selectMorph(new MorphOption("Reset", "__reset__", null, "__reset__", true));
        assertEquals("__reset__", syncedMorph.get());
    }

    private record FakeMorphRuntimePort(boolean applyResult)
            implements DefaultMorphWheelService.MorphRuntimePort {
        private FakeMorphRuntimePort() {
            this(true);
        }

        @Override
        public boolean applyCurrentPlayerMorph(ExpressionSelection selection, String filePath) {
            return applyResult;
        }
    }
}
