package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.stage.client.camera.port.StageCameraUiPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackUiPort;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/** 文件职责：适配舞台播放与相机流程到原生舞台选择界面。 */
public enum StagePlaybackUiAdapter implements StagePlaybackUiPort, StageCameraUiPort {
    INSTANCE;

    @Override
    public void showInvite(UUID hostUUID) {
        StageInvitePopup.show(hostUUID);
    }

    @Override
    public void markStageSelectionStartedAndClose() {
        Minecraft minecraft = Minecraft.getInstance();
        Runnable action = () -> {
            if (minecraft.screen instanceof StageSelectScreen screen) {
                screen.markStartedByHost();
                minecraft.setScreen(null);
            }
        };
        if (minecraft.isSameThread()) {
            action.run();
        } else {
            minecraft.execute(action);
        }
    }

    @Override
    public void openStageSelection() {
        Minecraft minecraft = Minecraft.getInstance();
        Runnable action = () -> minecraft.setScreen(new StageSelectScreen());
        if (minecraft.isSameThread()) {
            action.run();
        } else {
            minecraft.execute(action);
        }
    }

    @Override
    public void closeStageSelectionIfOpen() {
        Minecraft minecraft = Minecraft.getInstance();
        Runnable action = () -> {
            if (minecraft.screen instanceof StageSelectScreen screen) {
                screen.prepareForExternalClose();
                minecraft.setScreen(null);
            }
        };
        if (minecraft.isSameThread()) {
            action.run();
        } else {
            minecraft.execute(action);
        }
    }
}
