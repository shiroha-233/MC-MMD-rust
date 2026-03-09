package com.shiroha.mmdskin.compat.vr;

import net.blf02.vrapi.api.data.IVRData;
import net.blf02.vrapi.api.data.IVRPlayer;
import net.blf02.vrapi.common.VRAPI;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * mc-vr-api 数据适配层（SRP：将 IVRData 转换为 Rust 引擎需要的 float[21]）
 */

public final class VRDataProvider {

    private VRDataProvider() {}

    public static boolean isVRPlayer(Player player) {
        try {
            return VRAPI.VRAPIInstance.playerInVR(player);
        } catch (Exception e) {
            return false;
        }
    }

    public static float[] getRenderTrackingData(Player player) {
        try {
            if (!VRAPI.VRAPIInstance.playerInVR(player)) return null;

            IVRPlayer vrPlayer = VRAPI.VRAPIInstance.getRenderVRPlayer();
            if (vrPlayer == null) return null;

            float[] data = new float[21];
            writeTrackingPoint(vrPlayer.getHMD(), data, 0);
            writeTrackingPoint(vrPlayer.getController0(), data, 7);
            writeTrackingPoint(vrPlayer.getController1(), data, 14);
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeTrackingPoint(IVRData vrData, float[] out, int offset) {
        if (vrData == null) return;

        Vec3 pos = vrData.position();
        out[offset]     = (float) pos.x;
        out[offset + 1] = (float) pos.y;
        out[offset + 2] = (float) pos.z;

        Matrix4f rotMat = vrData.getRotationMatrix();
        Quaternionf quat = new Quaternionf();
        rotMat.getNormalizedRotation(quat);
        out[offset + 3] = quat.x;
        out[offset + 4] = quat.y;
        out[offset + 5] = quat.z;
        out[offset + 6] = quat.w;
    }
}
