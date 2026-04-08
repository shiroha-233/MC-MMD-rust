package com.shiroha.mmdskin.expression;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record BuiltinExpressionPreset(String id, String translationKey, List<MorphTarget> coreTargets,
                                      List<MorphTarget> optionalTargets) {
    public BuiltinExpressionPreset {
        coreTargets = List.copyOf(coreTargets);
        optionalTargets = List.copyOf(optionalTargets);
    }

    public String displayName() {
        return Component.translatable(translationKey).getString();
    }

    public ResolvedPreset resolve(ModelMorphCatalog catalog) {
        Map<Integer, Float> weights = new LinkedHashMap<>();
        List<Integer> used = new ArrayList<>();

        for (MorphTarget target : coreTargets) {
            int index = catalog.findBest(target.rule(), used);
            if (index < 0) {
                return ResolvedPreset.unavailable();
            }
            used.add(index);
            weights.merge(index, target.weight(), Math::max);
        }

        for (MorphTarget target : optionalTargets) {
            int index = catalog.findBest(target.rule(), used);
            if (index < 0) {
                continue;
            }
            used.add(index);
            weights.merge(index, target.weight(), Math::max);
        }

        return ResolvedPreset.available(weights);
    }

    public record MorphTarget(ExpressionMatchRule rule, float weight) {
    }

    public record ResolvedPreset(boolean available, Map<Integer, Float> weights) {
        public static ResolvedPreset unavailable() {
            return new ResolvedPreset(false, Map.of());
        }

        public static ResolvedPreset available(Map<Integer, Float> weights) {
            return new ResolvedPreset(true, Map.copyOf(weights));
        }
    }
}
