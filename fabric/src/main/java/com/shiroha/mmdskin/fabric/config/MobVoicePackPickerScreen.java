package com.shiroha.mmdskin.fabric.config;

import com.shiroha.mmdskin.voice.pack.LocalVoicePackRepository;
import com.shiroha.mmdskin.voice.pack.VoicePackDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

public class MobVoicePackPickerScreen extends Screen {
    private final Screen parent;
    private final Component titleLabel;
    private final String currentValue;
    private final BiConsumer<String, String> selectionConsumer;
    private final List<Option> allOptions = new ArrayList<>();
    private final List<Option> filteredOptions = new ArrayList<>();

    private EditBox searchBox;
    private Button chooseButton;
    private Option selected;
    private int panelX;
    private int panelY;
    private int panelHeight;
    private int listTop;
    private int listBottom;
    private int hoveredIndex = -1;
    private int scrollOffset;
    private int maxScroll;

    public MobVoicePackPickerScreen(Screen parent, Component titleLabel, String currentValue, BiConsumer<String, String> selectionConsumer) {
        super(Component.translatable("gui.mmdskin.mod_settings.category.mob_voice_pack"));
        this.parent = parent;
        this.titleLabel = titleLabel;
        this.currentValue = currentValue;
        this.selectionConsumer = selectionConsumer;
    }

    @Override
    protected void init() {
        super.init();
        panelX = (this.width - 360) / 2;
        panelY = 24;
        panelHeight = this.height - 48;
        searchBox = new EditBox(this.font, panelX + 8, panelY + 30, 276, 20, Component.translatable("selectWorld.search"));
        searchBox.setResponder(value -> refreshFilter());
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.refresh"), button -> refreshOptions())
                .bounds(panelX + 292, panelY + 30, 60, 20)
                .build());
        listTop = panelY + 58;
        listBottom = panelY + panelHeight - 28;
        int buttonWidth = (360 - 16) / 3;
        chooseButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> applySelection())
                .bounds(panelX + 4, listBottom + 6, buttonWidth, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.voice.pack.none"), button -> applyNone())
                .bounds(panelX + 8 + buttonWidth, listBottom + 6, buttonWidth, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(panelX + 12 + buttonWidth * 2, listBottom + 6, buttonWidth, 20)
                .build());
        refreshOptions();
    }

    private void refreshOptions() {
        allOptions.clear();
        for (VoicePackDefinition definition : LocalVoicePackRepository.getInstance().refresh()) {
            allOptions.add(new Option(definition.getId(), definition.getDisplayName()));
        }
        allOptions.sort(Comparator.comparing(option -> option.label.toLowerCase(Locale.ROOT)));
        refreshFilter();
    }

    private void refreshFilter() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        filteredOptions.clear();
        for (Option option : allOptions) {
            if (query.isEmpty() || option.label.toLowerCase(Locale.ROOT).contains(query) || option.packId.toLowerCase(Locale.ROOT).contains(query)) {
                filteredOptions.add(option);
            }
        }
        if (selected == null && currentValue != null) {
            selected = filteredOptions.stream().filter(option -> option.packId.equals(currentValue)).findFirst().orElse(null);
        }
        int contentHeight = filteredOptions.size() * 20;
        maxScroll = Math.max(0, contentHeight - (listBottom - listTop));
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        chooseButton.active = selected != null;
    }

    private void applySelection() {
        if (selected == null) {
            return;
        }
        selectionConsumer.accept(selected.packId, selected.label);
        Minecraft.getInstance().setScreen(parent);
    }

    private void applyNone() {
        selectionConsumer.accept(null, null);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.fill(panelX, panelY, panelX + 360, panelY + panelHeight, 0xC0101418);
        guiGraphics.drawCenteredString(this.font, titleLabel, this.width / 2, panelY + 8, 0xFF60A0D0);
        guiGraphics.drawString(this.font, Component.translatable("selectWorld.search"), panelX + 8, panelY + 20, 0xFF888888);
        renderList(guiGraphics, mouseX, mouseY);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        hoveredIndex = -1;
        guiGraphics.enableScissor(panelX + 2, listTop, panelX + 358, listBottom);
        for (int i = 0; i < filteredOptions.size(); i++) {
            int rowY = listTop + i * 20 - scrollOffset;
            if (rowY + 18 < listTop || rowY > listBottom) {
                continue;
            }
            Option option = filteredOptions.get(i);
            boolean hovered = mouseX >= panelX + 6 && mouseX <= panelX + 354 && mouseY >= rowY && mouseY <= rowY + 18;
            if (hovered) {
                hoveredIndex = i;
            }
            boolean selectedRow = selected != null && selected.packId.equals(option.packId);
            if (selectedRow) {
                guiGraphics.fill(panelX + 6, rowY, panelX + 354, rowY + 18, 0x3060A0D0);
            } else if (hovered) {
                guiGraphics.fill(panelX + 6, rowY, panelX + 354, rowY + 18, 0x30FFFFFF);
            }
            guiGraphics.drawString(this.font, option.label, panelX + 12, rowY + 5, selectedRow ? 0xFF60A0D0 : 0xFFDDDDDD);
        }
        guiGraphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && hoveredIndex >= 0 && hoveredIndex < filteredOptions.size()) {
            selected = filteredOptions.get(hoveredIndex);
            chooseButton.active = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= panelX && mouseX <= panelX + 360 && mouseY >= listTop && mouseY <= listBottom) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (delta * 20)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Option(String packId, String label) {
    }
}
