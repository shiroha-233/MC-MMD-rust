package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.stage.client.camera.port.StageCameraUiPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackUiPort;
import com.shiroha.mmdskin.ui.stage.imgui.StageWorkbenchScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.UUID;

public final class StagePlaybackUiAdapter implements StagePlaybackUiPort, StageCameraUiPort {
    public static final StagePlaybackUiAdapter INSTANCE = new StagePlaybackUiAdapter();

    private StagePlaybackUiAdapter() {
    }

    @Override
    public void showInvite(UUID hostUUID) {
        StageInvitePopup.show(hostUUID);
    }

    @Override
    public void markStageSelectionStartedAndClose() {
        Minecraft mc = Minecraft.getInstance();
        Runnable action = () -> {
            if (mc.screen instanceof StageWorkbenchScreen screen) {
                screen.markStartedByHost();
                mc.setScreen(null);
            }
        };
        if (mc.isSameThread()) {
            action.run();
        } else {
            mc.execute(action);
        }
    }

    @Override
    public void openStageSelection() {
        Minecraft mc = Minecraft.getInstance();
        Runnable action = () -> {
            Screen screen = StageWorkbenchScreen.createPrimary();
            mc.setScreen(screen);
        };
        if (mc.isSameThread()) {
            action.run();
        } else {
            mc.execute(action);
        }
    }

    @Override
    public void closeStageSelectionIfOpen() {
        Minecraft mc = Minecraft.getInstance();
        Runnable action = () -> {
            if (mc.screen instanceof StageWorkbenchScreen screen) {
                screen.prepareForExternalClose();
                mc.setScreen(null);
            }
        };
        if (mc.isSameThread()) {
            action.run();
        } else {
            mc.execute(action);
        }
    }
}
