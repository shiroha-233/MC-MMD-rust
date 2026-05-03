package com.shiroha.mmdskin.expression;

import com.shiroha.mmdskin.bridge.runtime.NativeModelQueryPort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 文件职责：缓存模型表情索引并提供预设匹配查询。 */
public final class ModelMorphCatalog {
    private static final Map<Long, ModelMorphCatalog> CACHE = new ConcurrentHashMap<>();
    private static final List<String> PREFIXES = List.of("morph_", "face_", "expression_");
    private static volatile NativeModelQueryPort modelQueryPort = NativeModelQueryPort.noop();

    private final long modelHandle;
    private final boolean vrmModel;
    private final List<MorphEntry> entries;
    private final Map<String, Integer> exactIndexByNormalizedName;

    private ModelMorphCatalog(long modelHandle, boolean vrmModel, List<MorphEntry> entries) {
        this.modelHandle = modelHandle;
        this.vrmModel = vrmModel;
        this.entries = entries;
        this.exactIndexByNormalizedName = new HashMap<>();
        for (MorphEntry entry : entries) {
            exactIndexByNormalizedName.putIfAbsent(entry.normalizedName(), entry.index());
        }
    }

    public static ModelMorphCatalog getOrCreate(long modelHandle) {
        return CACHE.computeIfAbsent(modelHandle, ModelMorphCatalog::load);
    }

    public static void invalidate(long modelHandle) {
        CACHE.remove(modelHandle);
    }

    public static void configureRuntimeCollaborators(NativeModelQueryPort modelQueryPort) {
        ModelMorphCatalog.modelQueryPort = modelQueryPort != null ? modelQueryPort : NativeModelQueryPort.noop();
    }

    static ModelMorphCatalog forTesting(List<MorphEntry> entries) {
        return new ModelMorphCatalog(-1L, false, List.copyOf(entries));
    }

    private static ModelMorphCatalog load(long modelHandle) {
        NativeModelQueryPort nativeBridge = modelQueryPort;
        int morphCount = nativeBridge.getMorphCount(modelHandle);
        List<MorphEntry> entries = new ArrayList<>(Math.max(morphCount, 0));
        for (int i = 0; i < morphCount; i++) {
            String originalName = nativeBridge.getMorphName(modelHandle, i);
            if (originalName == null || originalName.isEmpty()) {
                continue;
            }
            entries.add(new MorphEntry(i, originalName, normalizeName(originalName), tokenize(originalName)));
        }
        return new ModelMorphCatalog(modelHandle, nativeBridge.isVrmModel(modelHandle), entries);
    }

    public long modelHandle() {
        return modelHandle;
    }

    public boolean vrmModel() {
        return vrmModel;
    }

    public int findBest(ExpressionMatchRule rule, List<Integer> excludedIndices) {
        if (rule == null) {
            return -1;
        }

        for (String alias : rule.exactAliases()) {
            Integer exact = exactIndexByNormalizedName.get(normalizeName(alias));
            if (exact != null && !excludedIndices.contains(exact)) {
                return exact;
            }
        }

        int bestIndex = -1;
        int bestScore = Integer.MIN_VALUE;
        for (MorphEntry entry : entries) {
            if (excludedIndices.contains(entry.index())) {
                continue;
            }
            int score = rule.score(entry);
            if (score > bestScore) {
                bestScore = score;
                bestIndex = entry.index();
            }
        }
        return bestScore > 0 ? bestIndex : -1;
    }

    public MorphEntry getEntry(int index) {
        for (MorphEntry entry : entries) {
            if (entry.index() == index) {
                return entry;
            }
        }
        return null;
    }

    static String normalizeName(String name) {
        if (name == null) {
            return "";
        }

        String normalized = name.toLowerCase(Locale.ROOT).trim();
        for (String prefix : PREFIXES) {
            if (normalized.startsWith(prefix)) {
                normalized = normalized.substring(prefix.length());
                break;
            }
        }
        return normalized.replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .replace("　", "");
    }

    private static List<String> tokenize(String name) {
        String normalized = normalizeName(name);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(normalized.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]+"))
                .filter(token -> !token.isEmpty())
                .toList();
    }

    public record MorphEntry(int index, String originalName, String normalizedName, List<String> tokens) {
    }
}
