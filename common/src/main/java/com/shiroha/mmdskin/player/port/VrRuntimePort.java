package com.shiroha.mmdskin.player.port;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** 文件职责：向普通业务类暴露最小化的 VR 运行时能力边界。 */
public interface VrRuntimePort {
    VrRuntimePort NOOP = new VrRuntimePort() {
        @Override
        public boolean isLocalPlayerInVr() {
            return false;
        }

        @Override
        public boolean isLocalPlayerEyePass() {
            return false;
        }

        @Override
        public float getBodyYawRad(Player player, float tickDelta) {
            return Float.NaN;
        }

        @Override
        public float getBodyYawDegrees(Player player, float tickDelta) {
            return Float.NaN;
        }

        @Override
        public Vec3 getRenderOrigin(Player player, float tickDelta) {
            return null;
        }

        @Override
        public Vec3 getWorldRenderHeadPosition(Player player) {
            return null;
        }

        @Override
        public void applyMmdRenderState(boolean active) {
        }

        @Override
        public void setModelVrEnabled(long modelHandle, boolean enabled) {
        }

        @Override
        public void updateModelVr(long modelHandle, Player player, float tickDelta, float armIkStrength) {
        }
    };

    static VrRuntimePort noop() {
        return NOOP;
    }

    boolean isLocalPlayerInVr();

    boolean isLocalPlayerEyePass();

    float getBodyYawRad(Player player, float tickDelta);

    float getBodyYawDegrees(Player player, float tickDelta);

    Vec3 getRenderOrigin(Player player, float tickDelta);

    Vec3 getWorldRenderHeadPosition(Player player);

    void applyMmdRenderState(boolean active);

    void setModelVrEnabled(long modelHandle, boolean enabled);

    void updateModelVr(long modelHandle, Player player, float tickDelta, float armIkStrength);
}
