package com.shiroha.mmdskin.compat.vr;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VRDataProviderTest {
    @AfterEach
    void tearDown() {
        VRDataProvider.setTrackingFacadeForTesting(null);
    }

    @Test
    void shouldDelegateVrPlayerCheckToFacade() {
        VRDataProvider.setTrackingFacadeForTesting(new VrTrackingFacade() {
            @Override
            public boolean isVrPlayer(Player player) {
                return player != null;
            }

            @Override
            public float[] getTrackingData(Player player) {
                return null;
            }

            @Override
            public float getBodyYawRadians(Player player) {
                return Float.NaN;
            }

            @Override
            public Vec3 getLocalPlayerRenderOrigin(float partialTick) {
                return null;
            }
        });

        assertFalse(VRDataProvider.isVRPlayer(null));
    }

    @Test
    void shouldExposeFacadeBodyYawForVrPlayers() {
        VRDataProvider.setTrackingFacadeForTesting(new VrTrackingFacade() {
            @Override
            public boolean isVrPlayer(Player player) {
                return true;
            }

            @Override
            public float[] getTrackingData(Player player) {
                return null;
            }

            @Override
            public float getBodyYawRadians(Player player) {
                return 0.5f;
            }

            @Override
            public Vec3 getLocalPlayerRenderOrigin(float partialTick) {
                return new Vec3(4.0d, 5.0d, 6.0d);
            }
        });

        assertEquals(0.5f * (180.0f / (float) Math.PI), VRDataProvider.getBodyYawDegrees(null, 0.0f));
    }
}
