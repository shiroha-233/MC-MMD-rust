package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StagePack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StageSelectStateTest {

    @Test
    void shouldClampAudioVolumeAndCameraHeight() {
        StageSelectState state = new StageSelectState(
                new StageWorkbenchFacade.WorkbenchPreferences("", true, 9.0f, -2.0f),
                List.of()
        );

        assertEquals(2.0f, state.cameraHeightOffset());
        assertEquals(0.0f, state.audioVolume());

        state.setCameraHeightOffset(-5.0f);
        state.setAudioVolume(4.0f);

        assertEquals(-2.0f, state.cameraHeightOffset());
        assertEquals(1.0f, state.audioVolume());
    }

    @Test
    void shouldResetSelectedMotionWhenPackChanges() {
        StagePack firstPack = new StagePack(
                "a",
                "a",
                List.of(new StagePack.VmdFileInfo("dance_a.vmd", "a/dance_a.vmd", false, true, false)),
                List.of()
        );
        StagePack secondPack = new StagePack(
                "b",
                "b",
                List.of(new StagePack.VmdFileInfo("dance_b.vmd", "b/dance_b.vmd", false, true, false)),
                List.of()
        );
        StageSelectState state = new StageSelectState(
                new StageWorkbenchFacade.WorkbenchPreferences("a", true, 0.0f, 1.0f),
                List.of(firstPack, secondPack)
        );

        state.toggleSelectedHostMotion("dance_a.vmd");
        state.selectPack(1);

        assertNull(state.selectedHostMotionFileName());
    }
}
