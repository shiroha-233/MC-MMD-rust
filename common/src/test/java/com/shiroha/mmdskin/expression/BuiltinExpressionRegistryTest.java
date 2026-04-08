package com.shiroha.mmdskin.expression;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinExpressionRegistryTest {
    @Test
    void cryShouldResolveFromKomaruAndTearsMorphs() {
        ModelMorphCatalog catalog = ModelMorphCatalog.forTesting(List.of(
                entry(0, "困る"),
                entry(1, "涙"),
                entry(2, "まばたき")
        ));

        BuiltinExpressionPreset.ResolvedPreset resolved = BuiltinExpressionRegistry.find("cry").resolve(catalog);

        assertTrue(resolved.available());
        assertEquals(0.40f, resolved.weights().get(0), 0.0001f);
        assertEquals(0.95f, resolved.weights().get(1), 0.0001f);
        assertEquals(0.30f, resolved.weights().get(2), 0.0001f);
    }

    @Test
    void surprisedShouldResolveWithSmallPupilsAndRoundMouth() {
        ModelMorphCatalog catalog = ModelMorphCatalog.forTesting(List.of(
                entry(0, "瞳小"),
                entry(1, "丸口"),
                entry(2, "口開き")
        ));

        BuiltinExpressionPreset.ResolvedPreset resolved = BuiltinExpressionRegistry.find("surprised").resolve(catalog);

        assertTrue(resolved.available());
        assertEquals(0.95f, resolved.weights().get(0), 0.0001f);
        assertEquals(0.90f, resolved.weights().get(1), 0.0001f);
        assertEquals(0.80f, resolved.weights().get(2), 0.0001f);
    }

    private static ModelMorphCatalog.MorphEntry entry(int index, String name) {
        return new ModelMorphCatalog.MorphEntry(index, name, ModelMorphCatalog.normalizeName(name), List.of());
    }
}
