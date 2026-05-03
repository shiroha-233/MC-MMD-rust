package com.shiroha.mmdskin.compat.vr;

import com.shiroha.mmdskin.config.RuntimeConfigPortHolder;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * VR 手臂隐藏判断。
 */
public final class VRArmHider {

    private VRArmHider() {}

    public static boolean isLocalPlayerInVR() {
        if (!RuntimeConfigPortHolder.get().isVrEnabled() || !VRDetector.isAvailable()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return false;
        }

        return VRDataProvider.isVRPlayer(player);
    }

    public static boolean shouldHideVRArms() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return false;
        }

        return minecraft.options.getCameraType().isFirstPerson()
                && isLocalPlayerInVR()
                && MmdSkinRendererPlayerHelper.isUsingMmdModel(player);
    }
}
