package com.shiroha.mmdskin.fabric.config;

import com.shiroha.mmdskin.renderer.integration.entity.MobReplacementTargets;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

final class MobVoicePackListEntry extends AbstractConfigListEntry<String> {
    private static final int ENTRY_HEIGHT = 24;
    private static final int CHOOSE_BUTTON_WIDTH = 52;
    private static final int RESET_BUTTON_WIDTH = 44;
    private static final int BUTTON_GAP = 4;

    private final Component label;
    private final Consumer<String> saveConsumer;
    private final Button chooseButton;
    private final Button resetButton;
    private String originalValue;
    private String value;
    private String displayLabel;

    MobVoicePackListEntry(MobReplacementTargets.Target target, String value, Consumer<String> saveConsumer) {
        this(target.displayName(), value, saveConsumer);
    }

    MobVoicePackListEntry(Component label, String value, Consumer<String> saveConsumer) {
        super(label, false);
        this.label = label;
        this.saveConsumer = saveConsumer;
        this.originalValue = normalize(value);
        this.value = this.originalValue;
        this.displayLabel = this.originalValue;
        this.chooseButton = Button.builder(Component.translatable("gui.mmdskin.mod_settings.mob_replacement.choose"), button -> openPicker())
                .bounds(0, 0, CHOOSE_BUTTON_WIDTH, 20)
                .build();
        this.resetButton = Button.builder(Component.translatable("gui.mmdskin.mod_settings.mob_replacement.reset"), button -> setValue(null, null))
                .bounds(0, 0, RESET_BUTTON_WIDTH, 20)
                .build();
        updateButtons();
    }

    private void openPicker() {
        Screen parent = Minecraft.getInstance().screen;
        if (parent != null) {
            Minecraft.getInstance().setScreen(new MobVoicePackPickerScreen(parent, label, value, this::setValue));
        }
    }

    private void setValue(String packId, String label) {
        this.value = normalize(packId);
        this.displayLabel = label == null || label.isBlank() ? this.value : label;
        updateButtons();
    }

    private void updateButtons() {
        this.resetButton.active = value != null && !value.isBlank();
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public Optional<String> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public void save() {
        saveConsumer.accept(value);
        originalValue = value;
        updateButtons();
    }

    @Override
    public boolean isEdited() {
        return !Objects.equals(originalValue, value);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(chooseButton, resetButton);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(chooseButton, resetButton);
    }

    @Override
    public int getItemHeight() {
        return ENTRY_HEIGHT;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
        int resetX = x + entryWidth - RESET_BUTTON_WIDTH;
        int chooseX = resetX - BUTTON_GAP - CHOOSE_BUTTON_WIDTH;
        int buttonY = y + 2;
        chooseButton.setX(chooseX);
        chooseButton.setY(buttonY);
        resetButton.setX(resetX);
        resetButton.setY(buttonY);

        Minecraft minecraft = Minecraft.getInstance();
        guiGraphics.drawString(minecraft.font, label, x, y + 6, 0xFFFFFF, false);
        String summary = ModConfigScreen.toVoicePackSelectionComponent(displayLabel).getString();
        int summaryWidth = minecraft.font.width(summary);
        guiGraphics.drawString(minecraft.font, summary, Math.max(x + 8, chooseX - 8 - summaryWidth), y + 6, 0xA0A0A0, false);

        chooseButton.render(guiGraphics, mouseX, mouseY, delta);
        resetButton.render(guiGraphics, mouseX, mouseY, delta);
    }
}
