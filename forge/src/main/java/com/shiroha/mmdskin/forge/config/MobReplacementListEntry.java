package com.shiroha.mmdskin.forge.config;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.render.entity.MobReplacementTargets;
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

final class MobReplacementListEntry extends AbstractConfigListEntry<String> {
    private static final int ENTRY_HEIGHT = 24;
    private static final int CHOOSE_BUTTON_WIDTH = 52;
    private static final int RESET_BUTTON_WIDTH = 44;
    private static final int BUTTON_GAP = 4;
    private static final int COLOR_LABEL = 0xFFFFFF;
    private static final int COLOR_VALUE = 0xA0A0A0;

    private final MobReplacementTargets.Target target;
    private final Consumer<String> saveConsumer;
    private final Button chooseButton;
    private final Button resetButton;

    private String originalValue;
    private String value;

    MobReplacementListEntry(MobReplacementTargets.Target target, String value, Consumer<String> saveConsumer) {
        super(target.displayName(), false);
        this.target = target;
        this.saveConsumer = saveConsumer;
        this.originalValue = normalize(value);
        this.value = this.originalValue;
        this.chooseButton = Button.builder(
                Component.translatable("gui.mmdskin.mod_settings.mob_replacement.choose"),
                button -> openPicker())
            .bounds(0, 0, CHOOSE_BUTTON_WIDTH, 20)
            .build();
        this.resetButton = Button.builder(
                Component.translatable("gui.mmdskin.mod_settings.mob_replacement.reset"),
                button -> setValue(UIConstants.DEFAULT_MODEL_NAME))
            .bounds(0, 0, RESET_BUTTON_WIDTH, 20)
            .build();
        updateButtons();
    }

    private void openPicker() {
        Screen parent = Minecraft.getInstance().screen;
        if (parent == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new MobReplacementModelPickerScreen(parent, target, value, this::setValue));
    }

    private void setValue(String value) {
        this.value = normalize(value);
        updateButtons();
    }

    private void updateButtons() {
        this.resetButton.active = !UIConstants.DEFAULT_MODEL_NAME.equals(this.value);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return UIConstants.DEFAULT_MODEL_NAME;
        }
        return value;
    }

    private static String trimToWidth(String value, int maxWidth) {
        if (value == null || value.isEmpty() || maxWidth <= 0) {
            return "";
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.font.width(value) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        int ellipsisWidth = minecraft.font.width(ellipsis);
        String trimmed = value;
        while (!trimmed.isEmpty() && minecraft.font.width(trimmed) + ellipsisWidth > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? ellipsis : trimmed + ellipsis;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public Optional<String> getDefaultValue() {
        return Optional.of(UIConstants.DEFAULT_MODEL_NAME);
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
    public void render(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight,
                       int mouseX, int mouseY, boolean isHovered, float delta) {
        int resetX = x + entryWidth - RESET_BUTTON_WIDTH;
        int chooseX = resetX - BUTTON_GAP - CHOOSE_BUTTON_WIDTH;
        int buttonY = y + 2;

        chooseButton.setX(chooseX);
        chooseButton.setY(buttonY);
        resetButton.setX(resetX);
        resetButton.setY(buttonY);

        Minecraft minecraft = Minecraft.getInstance();
        guiGraphics.drawString(minecraft.font, target.displayName(), x, y + 6, COLOR_LABEL, false);

        String summary = trimToWidth(
            ModConfigScreen.toModelSelectionComponent(value).getString(),
            Math.max(40, chooseX - x - 12)
        );
        int summaryWidth = minecraft.font.width(summary);
        guiGraphics.drawString(minecraft.font, summary, chooseX - 8 - summaryWidth, y + 6, COLOR_VALUE, false);

        chooseButton.render(guiGraphics, mouseX, mouseY, delta);
        resetButton.render(guiGraphics, mouseX, mouseY, delta);
    }
}
