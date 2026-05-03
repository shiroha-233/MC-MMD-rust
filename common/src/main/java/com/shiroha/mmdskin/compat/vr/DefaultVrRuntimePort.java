package com.shiroha.mmdskin.compat.vr;

import com.shiroha.mmdskin.player.port.VrRuntimePort;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 文件职责：封装普通业务类所需的 VR 兼容与驱动细节。 */
public final class DefaultVrRuntimePort implements VrRuntimePort {
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public boolean isLocalPlayerInVr() {
        try {
            return VRArmHider.isLocalPlayerInVR();
        } catch (Exception e) {
            LOGGER.debug("Failed to query local VR state", e);
            return false;
        }
    }

    @Override
    public boolean isLocalPlayerEyePass() {
        try {
            return VivecraftReflectionBridge.isLocalPlayerEyePass();
        } catch (Exception e) {
            LOGGER.debug("Failed to query Vivecraft eye-pass state", e);
            return false;
        }
    }

    @Override
    public float getBodyYawRad(Player player, float tickDelta) {
        try {
            return VRDataProvider.getBodyYawRad(player, tickDelta);
        } catch (Exception e) {
            LOGGER.debug("Failed to query VR body yaw in radians", e);
            return Float.NaN;
        }
    }

    @Override
    public float getBodyYawDegrees(Player player, float tickDelta) {
        try {
            return VRDataProvider.getBodyYawDegrees(player, tickDelta);
        } catch (Exception e) {
            LOGGER.debug("Failed to query VR body yaw in degrees", e);
            return Float.NaN;
        }
    }

    @Override
    public Vec3 getRenderOrigin(Player player, float tickDelta) {
        try {
            return VRDataProvider.getRenderOrigin(player, tickDelta);
        } catch (Exception e) {
            LOGGER.debug("Failed to query VR render origin", e);
            return null;
        }
    }

    @Override
    public Vec3 getWorldRenderHeadPosition(Player player) {
        try {
            return VivecraftReflectionBridge.getWorldRenderHeadPosition(player);
        } catch (Exception e) {
            LOGGER.debug("Failed to query Vivecraft head position", e);
            return null;
        }
    }

    @Override
    public void applyMmdRenderState(boolean active) {
        try {
            VivecraftReflectionBridge.applyMmdRenderState(active);
        } catch (Exception e) {
            LOGGER.debug("Failed to apply Vivecraft MMD render state", e);
        }
    }

    @Override
    public void setModelVrEnabled(long modelHandle, boolean enabled) {
        try {
            VRBoneDriver.setVREnabled(modelHandle, enabled);
        } catch (Exception e) {
            LOGGER.debug("Failed to update model VR enabled state", e);
        }
    }

    @Override
    public void updateModelVr(long modelHandle, Player player, float tickDelta, float armIkStrength) {
        try {
            VRBoneDriver.setVRIKParams(modelHandle, armIkStrength);
            VRBoneDriver.driveModel(modelHandle, player, tickDelta);
        } catch (Exception e) {
            LOGGER.debug("Failed to update VR model tracking", e);
        }
    }
}
