package com.shiroha.mmdskin.compat.vr;

/**
 * VR 手臂隐藏判断（SRP：仅负责判断是否应隐藏 Vivecraft 方块手臂）
 */

public final class VRArmHider {

    private VRArmHider() {}

    public static boolean isLocalPlayerInVR() {
        return false;
    }

    public static boolean shouldHideVRArms() {
        return false;
    }
}
