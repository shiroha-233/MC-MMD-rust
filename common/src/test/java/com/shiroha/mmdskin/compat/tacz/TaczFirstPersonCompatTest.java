package com.shiroha.mmdskin.compat.tacz;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaczFirstPersonCompatTest {
    @Test
    void shouldClampAdsProgressToValidRange() {
        assertEquals(0.0f, TaczFirstPersonCompat.sanitizeProgress(-0.5f));
        assertEquals(0.4f, TaczFirstPersonCompat.sanitizeProgress(0.4f));
        assertEquals(1.0f, TaczFirstPersonCompat.sanitizeProgress(1.5f));
    }

    @Test
    void shouldRejectNonFiniteAdsProgress() {
        assertEquals(0.0f, TaczFirstPersonCompat.sanitizeProgress(Float.NaN));
        assertEquals(0.0f, TaczFirstPersonCompat.sanitizeProgress(Float.POSITIVE_INFINITY));
        assertEquals(0.0f, TaczFirstPersonCompat.sanitizeProgress(Float.NEGATIVE_INFINITY));
    }
}
