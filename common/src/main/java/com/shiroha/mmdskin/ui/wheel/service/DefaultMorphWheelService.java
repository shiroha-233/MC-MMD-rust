package com.shiroha.mmdskin.ui.wheel.service;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.asset.catalog.MorphInfo;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.config.MorphWheelConfig;
import com.shiroha.mmdskin.ui.network.MorphWheelNetworkHandler;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class DefaultMorphWheelService implements MorphWheelService {
    private static final Logger logger = LogManager.getLogger();
    private static final String RESET_MORPH_NAME = "__reset__";

    private final Supplier<List<MorphWheelConfig.MorphEntry>> morphEntriesSupplier;
    private final Function<MorphWheelConfig.MorphEntry, String> fileResolver;
    private final MorphRuntimePort runtimePort;
    private final MorphSyncPort syncPort;

    public DefaultMorphWheelService() {
        this(() -> MorphWheelConfig.getInstance().getDisplayedMorphs(), DefaultMorphWheelService::resolveMorphFilePath,
                new MinecraftMorphRuntimePort(), MorphWheelNetworkHandler.getInstance());
    }

    DefaultMorphWheelService(Supplier<List<MorphWheelConfig.MorphEntry>> morphEntriesSupplier,
                             Function<MorphWheelConfig.MorphEntry, String> fileResolver,
                             MorphRuntimePort runtimePort,
                             MorphSyncPort syncPort) {
        this.morphEntriesSupplier = Objects.requireNonNull(morphEntriesSupplier, "morphEntriesSupplier");
        this.fileResolver = Objects.requireNonNull(fileResolver, "fileResolver");
        this.runtimePort = Objects.requireNonNull(runtimePort, "runtimePort");
        this.syncPort = Objects.requireNonNull(syncPort, "syncPort");
    }

    @Override
    public List<MorphOption> loadMorphs() {
        List<MorphOption> options = new ArrayList<>();
        for (MorphWheelConfig.MorphEntry entry : morphEntriesSupplier.get()) {
            options.add(new MorphOption(entry.displayName, entry.morphName, fileResolver.apply(entry), false));
        }
        options.add(new MorphOption(net.minecraft.network.chat.Component.translatable("gui.mmdskin.reset_morph").getString(),
                RESET_MORPH_NAME, null, true));
        return List.copyOf(options);
    }

    @Override
    public void selectMorph(MorphOption option) {
        if (option == null || option.morphName() == null || option.morphName().isEmpty()) {
            return;
        }

        boolean applied;
        if (option.resetAction()) {
            applied = runtimePort.resetCurrentPlayerMorphs();
        } else if (option.filePath() == null || option.filePath().isEmpty()) {
            applied = false;
        } else {
            applied = runtimePort.applyCurrentPlayerMorph(option.filePath());
        }

        if (applied) {
            syncPort.syncMorph(option.morphName());
        }
    }

    private static String resolveMorphFilePath(MorphWheelConfig.MorphEntry entry) {
        if (entry.filePath != null && !entry.filePath.isEmpty()) {
            return entry.filePath;
        }

        MorphInfo morphInfo = null;
        if (entry.catalogKey != null && !entry.catalogKey.isEmpty()) {
            morphInfo = MorphInfo.findByCatalogKey(entry.catalogKey);
        }
        if (morphInfo == null) {
            morphInfo = MorphInfo.findByMorphName(entry.morphName);
        }
        return morphInfo != null ? morphInfo.getFilePath() : null;
    }

    interface MorphRuntimePort {
        boolean resetCurrentPlayerMorphs();

        boolean applyCurrentPlayerMorph(String filePath);
    }

    private static final class MinecraftMorphRuntimePort implements MorphRuntimePort {
        @Override
        public boolean resetCurrentPlayerMorphs() {
            Long modelHandle = resolveModelHandle();
            if (modelHandle == null) {
                return false;
            }
            NativeFunc.GetInst().ResetAllMorphs(modelHandle);
            return true;
        }

        @Override
        public boolean applyCurrentPlayerMorph(String filePath) {
            Long modelHandle = resolveModelHandle();
            if (modelHandle == null) {
                return false;
            }

            int result = NativeFunc.GetInst().ApplyVpdMorph(modelHandle, filePath);
            if (result >= 0) {
                return true;
            }
            if (result == -1) {
                logger.error("VPD 文件加载失败: {}", filePath);
            } else if (result == -2) {
                logger.error("模型不存在, handle={}", modelHandle);
            }
            return false;
        }

        private Long resolveModelHandle() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return null;
            }

            String playerName = mc.player.getName().getString();
            String selectedModel = ModelSelectorConfig.getInstance().getPlayerModel(playerName);
            if (selectedModel == null || selectedModel.isEmpty() || UIConstants.DEFAULT_MODEL_NAME.equals(selectedModel)) {
                logger.warn("当前使用默认渲染，无法应用表情");
                return null;
            }

            MMDModelManager.Model model = MMDModelManager.GetModel(selectedModel, PlayerModelResolver.getCacheKey(mc.player));
            if (model == null || model.model == null) {
                logger.warn("未找到玩家模型: {}", selectedModel);
                return null;
            }
            return model.model.getModelHandle();
        }
    }
}
