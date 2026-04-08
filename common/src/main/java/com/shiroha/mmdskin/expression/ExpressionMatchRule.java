package com.shiroha.mmdskin.expression;

import java.util.List;

public record ExpressionMatchRule(List<String> exactAliases, List<List<String>> tokenGroups) {
    public ExpressionMatchRule {
        exactAliases = exactAliases == null ? List.of() : List.copyOf(exactAliases);
        tokenGroups = tokenGroups == null ? List.of() : tokenGroups.stream().map(List::copyOf).toList();
    }

    public static ExpressionMatchRule of(List<String> exactAliases, List<List<String>> tokenGroups) {
        return new ExpressionMatchRule(exactAliases, tokenGroups);
    }

    public int score(ModelMorphCatalog.MorphEntry entry) {
        if (entry == null) {
            return Integer.MIN_VALUE;
        }

        for (String alias : exactAliases) {
            if (entry.normalizedName().equals(ModelMorphCatalog.normalizeName(alias))) {
                return 10_000;
            }
        }

        int bestTokenScore = Integer.MIN_VALUE;
        for (List<String> group : tokenGroups) {
            boolean matched = true;
            for (String token : group) {
                if (!entry.normalizedName().contains(ModelMorphCatalog.normalizeName(token))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                bestTokenScore = Math.max(bestTokenScore, 1_000 - group.size());
            }
        }
        return bestTokenScore;
    }
}
