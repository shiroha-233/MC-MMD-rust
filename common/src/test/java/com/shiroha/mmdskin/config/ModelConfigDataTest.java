package com.shiroha.mmdskin.config;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ModelConfigDataTest {

    @Test
    void shouldNormalizeOutOfRangeValuesAndHiddenMaterials() {
        ModelConfigData config = new ModelConfigData();
        config.eyeMaxAngle = Float.NaN;
        config.modelScale = 100.0f;
        config.hiddenMaterials = new HashSet<>(Set.of(-2, 3));

        ModelConfigData normalized = config.normalizedCopy();

        assertEquals(ModelConfigData.DEFAULT_EYE_MAX_ANGLE, normalized.eyeMaxAngle);
        assertEquals(ModelConfigData.MAX_MODEL_SCALE, normalized.modelScale);
        assertEquals(Set.of(3), normalized.hiddenMaterials);
    }

    @Test
    void shouldCopyHiddenMaterialsDefensively() {
        ModelConfigData config = new ModelConfigData();
        config.hiddenMaterials.add(7);

        ModelConfigData copied = config.copy();
        copied.hiddenMaterials.add(9);

        assertNotSame(config.hiddenMaterials, copied.hiddenMaterials);
        assertEquals(Set.of(7), config.hiddenMaterials);
        assertEquals(Set.of(7, 9), copied.hiddenMaterials);
    }
}
