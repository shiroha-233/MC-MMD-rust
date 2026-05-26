/* 文件职责：加载表情轮盘配置并把预设或 VPD 选择应用到当前玩家模型。 */
package com.shiroha.mmdskin.ui.wheel.service;

import com.shiroha.mmdskin.asset.catalog.MorphInfo;
import com.shiroha.mmdskin.expression.ExpressionApplicationService;
import com.shiroha.mmdskin.expression.ExpressionSelection;
import com.shiroha.mmdskin.expression.ExpressionSelectionCodec;
import com.shiroha.mmdskin.player.sync.PlayerMorphSyncService;
import com.shiroha.mmdskin.ui.config.MorphWheelConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

public class DefaultMorphWheelService implements MorphWheelService {
    private final Supplier<List<MorphWheelConfig.MorphEntry>> morphEntriesSupplier;
    private final Function<MorphWheelConfig.MorphEntry, String> fileResolver;
    private final MorphRuntimePort runtimePort;
    private final MorphSyncPort syncPort;

    public DefaultMorphWheelService() {
        this(
                () -> MorphWheelConfig.getInstance().getDisplayedMorphs(),
                DefaultMorphWheelService::resolveMorphFilePath,
                new MinecraftMorphRuntimePort(),
                PlayerMorphSyncService.getInstance());
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
            Minecraft minecraft = Minecraft.getInstance();
            if (selection == null || minecraft.player == null) {
                return false;
            }

            ExpressionSelection resolvedSelection = selection.type() == ExpressionSelection.Type.FILE
                    ? ExpressionSelection.file(filePath)
                    : selection;
            return ExpressionApplicationService.apply(minecraft.player, resolvedSelection);
        }
    }
}
