package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.maid.MaidMMDModelManager;
import com.shiroha.mmdskin.voice.VoiceUsageMode;
import com.shiroha.mmdskin.voice.config.VoicePackBindingsConfig;
import com.shiroha.mmdskin.voice.pack.LocalVoicePackRepository;
import com.shiroha.mmdskin.voice.pack.VoicePackDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VoicePackBindingScreen extends Screen {
    private final Screen parent;
    private final BindingTarget target;
    private final String modelName;
    private final List<Option> options = new ArrayList<>();
    private final Map<RowKind, String> values = new LinkedHashMap<>();

    public static VoicePackBindingScreen createForPlayer(Screen parent, String modelName) {
        return new VoicePackBindingScreen(parent, BindingTarget.PLAYER, normalizeModelName(modelName), null);
    }

    public static VoicePackBindingScreen createForMaid(Screen parent, UUID maidUuid) {
        return new VoicePackBindingScreen(parent, BindingTarget.MAID, normalizeModelName(MaidMMDModelManager.getBindingModelName(maidUuid)), maidUuid);
    }

    private VoicePackBindingScreen(Screen parent, BindingTarget target, String modelName, UUID maidUuid) {
        super(Component.translatable(target == BindingTarget.PLAYER
                ? "gui.mmdskin.voice.title.player"
                : "gui.mmdskin.voice.title.maid"));
        this.parent = parent;
        this.target = target;
        this.modelName = modelName;
    }

    @Override
    protected void init() {
        super.init();
        loadOptions();
        loadValues();

        int left = this.width / 2 - 150;
        int top = 56;
        int labelWidth = 120;
        int buttonWidth = 180;
        int rowHeight = 24;

        int rowIndex = 0;
        for (RowKind rowKind : visibleRows()) {
            int y = top + rowIndex * rowHeight;
            this.addRenderableWidget(Button.builder(Component.literal(buildValueLabel(rowKind)), button -> {
                        cycleValue(rowKind);
                        button.setMessage(Component.literal(buildValueLabel(rowKind)));
                    })
                    .bounds(left + labelWidth, y, buttonWidth, 20)
                    .build());
            rowIndex++;
        }

        int bottomY = Math.min(this.height - 28, top + rowIndex * rowHeight + 16);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(this.width / 2 - 102, bottomY, 100, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> Minecraft.getInstance().setScreen(parent))
                .bounds(this.width / 2 + 2, bottomY, 100, 20)
                .build());
    }

    private void loadOptions() {
        options.clear();
        options.add(new Option(null, Component.translatable("gui.mmdskin.voice.pack.none").getString()));
        for (VoicePackDefinition definition : LocalVoicePackRepository.getInstance().refresh()) {
            options.add(new Option(definition.getId(), definition.getDisplayName()));
        }
    }

    private void loadValues() {
        VoicePackBindingsConfig config = VoicePackBindingsConfig.getInstance();
        values.clear();
        if (target == BindingTarget.PLAYER) {
            values.put(RowKind.DEFAULT_PACK, config.getPlayerDefaultPackId());
            values.put(RowKind.MODEL_PACK, config.getPlayerModelPackId(modelName));
            values.put(RowKind.NORMAL_MODE, config.getPlayerUsagePackId(VoiceUsageMode.NORMAL));
            values.put(RowKind.ACTION_MODE, config.getPlayerUsagePackId(VoiceUsageMode.CUSTOM_ACTION));
            values.put(RowKind.STAGE_MODE, config.getPlayerUsagePackId(VoiceUsageMode.STAGE));
        } else {
            values.put(RowKind.DEFAULT_PACK, config.getMaidDefaultPackId());
            values.put(RowKind.MODEL_PACK, config.getMaidModelPackId(modelName));
            values.put(RowKind.NORMAL_MODE, config.getMaidUsagePackId(VoiceUsageMode.NORMAL));
            values.put(RowKind.ACTION_MODE, config.getMaidUsagePackId(VoiceUsageMode.CUSTOM_ACTION));
            values.put(RowKind.STAGE_MODE, config.getMaidUsagePackId(VoiceUsageMode.STAGE));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int left = this.width / 2 - 150;
        int top = 38;
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.mmdskin.voice.current_model", modelName == null ? UIConstants.DEFAULT_MODEL_NAME : modelName),
                left, top, 0xB8C7D9, false);

        int rowIndex = 0;
        for (RowKind rowKind : visibleRows()) {
            int y = 56 + rowIndex * 24 + 6;
            guiGraphics.drawString(this.font, Component.translatable(rowKind.translationKey()), left, y, 0xFFFFFF, false);
            rowIndex++;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<RowKind> visibleRows() {
        List<RowKind> rows = new ArrayList<>();
        rows.add(RowKind.DEFAULT_PACK);
        if (modelName != null) {
            rows.add(RowKind.MODEL_PACK);
        }
        rows.add(RowKind.NORMAL_MODE);
        rows.add(RowKind.ACTION_MODE);
        if (target == BindingTarget.PLAYER) {
            rows.add(RowKind.STAGE_MODE);
        }
        return rows;
    }

    private void cycleValue(RowKind rowKind) {
        String current = values.get(rowKind);
        int currentIndex = 0;
        for (int i = 0; i < options.size(); i++) {
            if ((options.get(i).packId == null && current == null)
                    || (options.get(i).packId != null && options.get(i).packId.equals(current))) {
                currentIndex = i;
                break;
            }
        }
        Option next = options.get((currentIndex + 1) % options.size());
        values.put(rowKind, next.packId);
        applyValue(rowKind, next.packId);
    }

    private void applyValue(RowKind rowKind, String packId) {
        VoicePackBindingsConfig config = VoicePackBindingsConfig.getInstance();
        if (target == BindingTarget.PLAYER) {
            switch (rowKind) {
                case DEFAULT_PACK -> config.setPlayerDefaultPackId(packId);
                case MODEL_PACK -> config.setPlayerModelPackId(modelName, packId);
                case NORMAL_MODE -> config.setPlayerUsagePackId(VoiceUsageMode.NORMAL, packId);
                case ACTION_MODE -> config.setPlayerUsagePackId(VoiceUsageMode.CUSTOM_ACTION, packId);
                case STAGE_MODE -> config.setPlayerUsagePackId(VoiceUsageMode.STAGE, packId);
            }
            return;
        }

        switch (rowKind) {
            case DEFAULT_PACK -> config.setMaidDefaultPackId(packId);
            case MODEL_PACK -> config.setMaidModelPackId(modelName, packId);
            case NORMAL_MODE -> config.setMaidUsagePackId(VoiceUsageMode.NORMAL, packId);
            case ACTION_MODE -> config.setMaidUsagePackId(VoiceUsageMode.CUSTOM_ACTION, packId);
            case STAGE_MODE -> config.setMaidUsagePackId(VoiceUsageMode.STAGE, packId);
        }
    }

    private String buildValueLabel(RowKind rowKind) {
        String packId = values.get(rowKind);
        for (Option option : options) {
            if ((packId == null && option.packId == null)
                    || (packId != null && packId.equals(option.packId))) {
                return option.displayName;
            }
        }
        return Component.translatable("gui.mmdskin.voice.pack.none").getString();
    }

    private static String normalizeModelName(String modelName) {
        if (modelName == null || modelName.isBlank() || UIConstants.DEFAULT_MODEL_NAME.equals(modelName)) {
            return null;
        }
        return modelName;
    }

    private enum BindingTarget {
        PLAYER,
        MAID
    }

    private enum RowKind {
        DEFAULT_PACK("gui.mmdskin.voice.row.default"),
        MODEL_PACK("gui.mmdskin.voice.row.model"),
        NORMAL_MODE("gui.mmdskin.voice.row.normal"),
        ACTION_MODE("gui.mmdskin.voice.row.action"),
        STAGE_MODE("gui.mmdskin.voice.row.stage");

        private final String translationKey;

        RowKind(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    private record Option(String packId, String displayName) {
    }
}
