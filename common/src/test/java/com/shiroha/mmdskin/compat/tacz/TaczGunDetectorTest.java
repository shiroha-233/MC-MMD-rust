package com.shiroha.mmdskin.compat.tacz;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaczGunDetectorTest {
    private interface GunMarker {
    }

    private interface ExtendedGunMarker extends GunMarker {
    }

    private static class BaseGun implements ExtendedGunMarker {
    }

    private static final class DerivedGun extends BaseGun {
    }

    @Test
    void shouldFindInterfaceThroughParentClassAndInterface() {
        assertTrue(TaczGunDetector.implementsInterfaceNamed(
                DerivedGun.class, GunMarker.class.getName()));
    }

    @Test
    void shouldRejectUnrelatedOrInvalidTypes() {
        assertFalse(TaczGunDetector.implementsInterfaceNamed(
                String.class, GunMarker.class.getName()));
        assertFalse(TaczGunDetector.implementsInterfaceNamed(null, GunMarker.class.getName()));
        assertFalse(TaczGunDetector.implementsInterfaceNamed(DerivedGun.class, ""));
    }
}
