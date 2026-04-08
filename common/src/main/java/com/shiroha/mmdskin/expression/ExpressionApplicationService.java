package com.shiroha.mmdskin.expression;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ExpressionApplicationService {
    private static final Logger logger = LogManager.getLogger();
    private static final ConcurrentHashMap<Long, Set<Integer>> PRESET_STATE = new ConcurrentHashMap<>();

    private ExpressionApplicationService() {
    }

    public static boolean apply(Player player, ExpressionSelection selection) {
        if (player == null || selection == null) {
            return false;
        }

        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
        if (resolved == null || resolved.model() == null || resolved.model().model == null) {
            return false;
        }
        return apply(resolved.model().model.getModelHandle(), selection, resolved.playerName());
    }

    public static boolean apply(long modelHandle, ExpressionSelection selection, String playerName) {
        NativeFunc nativeFunc = NativeFunc.GetInst();
        return switch (selection.type()) {
            case RESET -> {
                nativeFunc.ResetAllMorphs(modelHandle);
                PRESET_STATE.remove(modelHandle);
                yield true;
            }
            case PRESET -> applyPreset(nativeFunc, modelHandle, selection.value(), playerName);
            case FILE -> applyVpd(nativeFunc, modelHandle, selection.value(), playerName);
        };
    }

    private static boolean applyPreset(NativeFunc nativeFunc, long modelHandle, String presetId, String playerName) {
        BuiltinExpressionPreset preset = BuiltinExpressionRegistry.find(presetId);
        if (preset == null) {
            logger.warn("[内置表情] 未知预设: {}", presetId);
            return false;
        }

        ModelMorphCatalog catalog = ModelMorphCatalog.getOrCreate(modelHandle);
        BuiltinExpressionPreset.ResolvedPreset resolvedPreset = preset.resolve(catalog);
        clearPresetMorphs(nativeFunc, modelHandle);
        if (!resolvedPreset.available()) {
            logger.warn("[内置表情] 玩家 {} 的模型缺少预设 {} 所需的核心 morph", playerName, presetId);
            return false;
        }

        for (var entry : resolvedPreset.weights().entrySet()) {
            nativeFunc.SetMorphWeight(modelHandle, entry.getKey(), entry.getValue());
        }
        nativeFunc.SyncGpuMorphWeights(modelHandle);
        PRESET_STATE.put(modelHandle, ConcurrentHashMap.newKeySet(resolvedPreset.weights().size()));
        PRESET_STATE.get(modelHandle).addAll(resolvedPreset.weights().keySet());
        return true;
    }

    private static boolean applyVpd(NativeFunc nativeFunc, long modelHandle, String filePath, String playerName) {
        clearPresetMorphs(nativeFunc, modelHandle);
        if (filePath == null || filePath.isEmpty() || !new File(filePath).exists()) {
            logger.warn("[表情] 玩家 {} 的 VPD 文件不存在: {}", playerName, filePath);
            return false;
        }

        int result = nativeFunc.ApplyVpdMorph(modelHandle, filePath);
        if (result < 0) {
            logger.warn("[表情] 玩家 {} 的 VPD 应用失败: {} ({})", playerName, filePath, result);
            return false;
        }
        return true;
    }

    private static void clearPresetMorphs(NativeFunc nativeFunc, long modelHandle) {
        Set<Integer> indices = PRESET_STATE.getOrDefault(modelHandle, Collections.emptySet());
        for (Integer index : indices) {
            nativeFunc.SetMorphWeight(modelHandle, index, 0.0f);
        }
        if (!indices.isEmpty()) {
            nativeFunc.SyncGpuMorphWeights(modelHandle);
        }
        PRESET_STATE.remove(modelHandle);
    }
}
