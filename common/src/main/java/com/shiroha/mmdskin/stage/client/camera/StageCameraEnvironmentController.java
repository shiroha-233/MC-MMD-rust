package com.shiroha.mmdskin.stage.client.camera;

import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** 文件职责：集中管理舞台相机对 Minecraft 环境的副作用。 */
final class StageCameraEnvironmentController {
    private CameraType savedCameraType;
    private boolean previousHideGui;
    private boolean mouseReleased;

    void enterStageCameraMode(Minecraft minecraft) {
        savedCameraType = minecraft.options.getCameraType();
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    void enterCinematicMode(Minecraft minecraft) {
        previousHideGui = minecraft.options.hideGui;
        minecraft.options.hideGui = true;
    }

    void leaveCinematicMode(Minecraft minecraft) {
        minecraft.options.hideGui = previousHideGui;
    }

    void restoreCameraType(Minecraft minecraft) {
        if (savedCameraType != null) {
            minecraft.options.setCameraType(savedCameraType);
            savedCameraType = null;
        }
    }

    void toggleMouseGrab(Minecraft minecraft) {
        if (mouseReleased) {
            minecraft.mouseHandler.grabMouse();
            mouseReleased = !minecraft.mouseHandler.isMouseGrabbed();
        } else {
            minecraft.mouseHandler.releaseMouse();
            mouseReleased = true;
            if (minecraft.gui != null) {
                minecraft.gui.setOverlayMessage(Component.translatable("gui.mmdskin.stage.mouse_released"), false);
            }
        }
    }

    void restoreMouseGrab(Minecraft minecraft) {
        if (mouseReleased) {
            minecraft.mouseHandler.grabMouse();
            mouseReleased = !minecraft.mouseHandler.isMouseGrabbed();
        }
    }

    boolean isMouseReleased() {
        return mouseReleased;
    }

    void resetStageInputState() {
        mouseReleased = false;
    }

    void releaseAllKeys() {
        KeyMapping.releaseAll();
    }
}
