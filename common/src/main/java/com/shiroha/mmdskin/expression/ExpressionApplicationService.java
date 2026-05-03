package com.shiroha.mmdskin.expression;

import com.shiroha.mmdskin.bridge.runtime.NativeMorphPort;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 文件职责：把预设表情或 VPD 表情应用到模型实例上。 */
public final class ExpressionApplicationService {
    private static final Logger logger = LogManager.getLogger();
    private static final ConcurrentHashMap<Long, Set<Integer>> PRESET_STATE = new ConcurrentHashMap<>();
    private static final NativeMorphPort NOOP_MORPH_PORT = new NativeMorphPort() {
        @Override
        public void resetAllMorphs(long modelHandle) {
        }

        @Override
        public void setMorphWeight(long modelHandle, int morphIndex, float weight) {
        }

        @Override
        public void syncGpuMorphWeights(long modelHandle) {
        }

        @Override
        public int applyVpdMorph(long modelHandle, String filePath) {
            return -1;
        }
    };

    private static volatile NativeMorphPort morphPort = NOOP_MORPH_PORT;

    private ExpressionApplicationService() {
    }

    public static void configureRuntimeCollaborators(NativeMorphPort morphPort) {
        ExpressionApplicationService.morphPort = morphPort != null ? morphPort : NOOP_MORPH_PORT;
    }

    public static boolean apply(Player player, ExpressionSelection selection) {
        if (player == null || selection == null) {
            return false;
        }

        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
        if (resolved == null || resolved.model() == null || resolved.model().modelInstance() == null) {
            return false;
        }
        return apply(resolved.model().modelInstance().getModelHandle(), selection, resolved.playerName());
    }

    public static boolean apply(long modelHandle, ExpressionSelection selection, String playerName) {
        NativeMorphPort nativeBridge = morphPort;
        return switch (selection.type()) {
            case RESET -> {
                nativeBridge.resetAllMorphs(modelHandle);
                PRESET_STATE.remove(modelHandle);
                yield true;
            }
            case PRESET -> applyPreset(modelHandle, selection.value(), playerName);
            case FILE -> applyVpd(modelHandle, selection.value(), playerName);
        };
    }

    private static boolean applyPreset(long modelHandle, String presetId, String playerName) {
        BuiltinExpressionPreset preset = BuiltinExpressionRegistry.find(presetId);
        if (preset == null) {
            logger.warn("[BuiltinExpression] Unknown preset: {}", presetId);
            return false;
        }

        ModelMorphCatalog catalog = ModelMorphCatalog.getOrCreate(modelHandle);
        BuiltinExpressionPreset.ResolvedPreset resolvedPreset = preset.resolve(catalog);
        clearPresetMorphs(modelHandle);
        if (!resolvedPreset.available()) {
            logger.warn("[BuiltinExpression] Model for {} is missing required morphs for preset {}", playerName, presetId);
            return false;
        }

        NativeMorphPort nativeBridge = morphPort;
        for (var entry : resolvedPreset.weights().entrySet()) {
            nativeBridge.setMorphWeight(modelHandle, entry.getKey(), entry.getValue());
        }
        nativeBridge.syncGpuMorphWeights(modelHandle);
        PRESET_STATE.put(modelHandle, ConcurrentHashMap.newKeySet(resolvedPreset.weights().size()));
        PRESET_STATE.get(modelHandle).addAll(resolvedPreset.weights().keySet());
        return true;
    }

    private static boolean applyVpd(long modelHandle, String filePath, String playerName) {
        clearPresetMorphs(modelHandle);
        if (filePath == null || filePath.isEmpty() || !new File(filePath).exists()) {
            logger.warn("[Expression] VPD file does not exist for {}: {}", playerName, filePath);
            return false;
        }

        int result = morphPort.applyVpdMorph(modelHandle, filePath);
        if (result < 0) {
            logger.warn("[Expression] Failed to apply VPD for {}: {} ({})", playerName, filePath, result);
            return false;
        }
        return true;
    }

    private static void clearPresetMorphs(long modelHandle) {
        NativeMorphPort nativeBridge = morphPort;
        Set<Integer> indices = PRESET_STATE.getOrDefault(modelHandle, Collections.emptySet());
        for (Integer index : indices) {
            nativeBridge.setMorphWeight(modelHandle, index, 0.0f);
        }
        if (!indices.isEmpty()) {
            nativeBridge.syncGpuMorphWeights(modelHandle);
        }
        PRESET_STATE.remove(modelHandle);
    }
}
