package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.stage.client.camera.port.StageCameraUiPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackUiPort;
import net.minecraft.client.Minecraft;

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
            if (mc.screen instanceof StageSelectScreen screen) {
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
        Runnable action = () -> mc.setScreen(new StageSelectScreen());
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
            if (mc.screen instanceof StageSelectScreen screen) {
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
