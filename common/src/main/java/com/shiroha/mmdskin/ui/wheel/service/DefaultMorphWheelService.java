package com.shiroha.mmdskin.ui.wheel.service;

import com.shiroha.mmdskin.asset.catalog.MorphInfo;
import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.expression.ExpressionApplicationService;
import com.shiroha.mmdskin.expression.ExpressionSelection;
import com.shiroha.mmdskin.expression.ExpressionSelectionCodec;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelRequestKey;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.config.MorphWheelConfig;
import com.shiroha.mmdskin.ui.network.MorphWheelNetworkHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 文件职责：加载 Morph 轮盘配置并把选择应用到当前玩家模型。 */
public class DefaultMorphWheelService implements MorphWheelService {
    private static final Logger logger = LogManager.getLogger();

    private final Supplier<List<MorphWheelConfig.MorphEntry>> morphEntriesSupplier;
    private final Function<MorphWheelConfig.MorphEntry, String> fileResolver;
    private final MorphRuntimePort runtimePort;
    private final MorphSyncPort syncPort;

    public DefaultMorphWheelService() {
        this(() -> MorphWheelConfig.getInstance().getDisplayedMorphs(),
                DefaultMorphWheelService::resolveMorphFilePath,
                new MinecraftMorphRuntimePort(),
                MorphWheelNetworkHandler.getInstance());
    }

    DefaultMorphWheelService(
            Supplier<List<MorphWheelConfig.MorphEntry>> morphEntriesSupplier,
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
            String filePath = entry.isPreset() ? null : fileResolver.apply(entry);
            String syncToken = entry.isPreset()
                    ? ExpressionSelectionCodec.encode(ExpressionSelection.preset(entry.presetId))
                    : ExpressionSelectionCodec.encode(ExpressionSelection.file(entry.morphName));
            options.add(new MorphOption(entry.displayName, entry.morphName, filePath, syncToken, false));
        }
        options.add(new MorphOption(
                net.minecraft.network.chat.Component.translatable("gui.mmdskin.reset_morph").getString(),
                ExpressionSelectionCodec.RESET_TOKEN,
                null,
                ExpressionSelectionCodec.RESET_TOKEN,
                true));
        return List.copyOf(options);
    }

    @Override
    public void selectMorph(MorphOption option) {
        if (option == null || option.syncToken() == null || option.syncToken().isEmpty()) {
            return;
        }

        ExpressionSelection selection = ExpressionSelectionCodec.decode(option.syncToken());
        boolean applied = runtimePort.applyCurrentPlayerMorph(selection, option.filePath());
        if (applied) {
            syncPort.syncMorph(option.syncToken());
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
        boolean applyCurrentPlayerMorph(ExpressionSelection selection, String filePath);
    }

    private static final class MinecraftMorphRuntimePort implements MorphRuntimePort {
        @Override
        public boolean applyCurrentPlayerMorph(ExpressionSelection selection, String filePath) {
            if (selection == null) {
                return false;
            }

            ExpressionSelection resolvedSelection = selection.type() == ExpressionSelection.Type.FILE
                    ? ExpressionSelection.file(filePath)
                    : selection;
            Long modelHandle = resolveModelHandle();
            if (modelHandle == null) {
                return false;
            }
            return ExpressionApplicationService.apply(modelHandle, resolvedSelection, "local_player");
        }

        private Long resolveModelHandle() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                return null;
            }

            String playerName = minecraft.player.getName().getString();
            String selectedModel = ModelSelectorConfig.getInstance().getPlayerModel(playerName);
            if (selectedModel == null || selectedModel.isEmpty() || UIConstants.DEFAULT_MODEL_NAME.equals(selectedModel)) {
                logger.warn("Cannot apply morph while using default renderer");
                return null;
            }

            ManagedModel model = ClientRenderRuntime.get().modelRepository()
                    .acquire(ModelRequestKey.player(minecraft.player, selectedModel));
            if (model == null || model.modelInstance() == null) {
                logger.warn("Cannot resolve current player model {}", selectedModel);
                return null;
            }
            return model.modelInstance().getModelHandle();
        }
    }
}
