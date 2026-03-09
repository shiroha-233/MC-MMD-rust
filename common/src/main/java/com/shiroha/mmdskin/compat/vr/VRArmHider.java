package com.shiroha.mmdskin.compat.vr;

import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * VR 手臂隐藏判断（SRP：仅负责判断是否应隐藏 Vivecraft 方块手臂）
 */

public final class VRArmHider {

    private VRArmHider() {}

    public static boolean isLocalPlayerInVR() {
        try {
            if (!VRDetector.isAvailable()) return false;
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return false;
            return VRBoneDriver.isVRPlayer(player);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean shouldHideVRArms() {
        try {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return false;

            String playerName = player.getName().getString();
            String selectedModel = PlayerModelSyncManager.getPlayerModel(
                    player.getUUID(), playerName, true);

            if (selectedModel == null || selectedModel.isEmpty()
                    || selectedModel.equals("默认 (原版渲染)")) {
                return false;
            }

            if (selectedModel.equals("VanilaModel")
                    || selectedModel.equalsIgnoreCase("vanila")) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
