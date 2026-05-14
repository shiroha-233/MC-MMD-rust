/* 文件职责：提供 Fabric 生物替换模型选择界面的搜索与选择交互。 */
package com.shiroha.mmdskin.fabric.config;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.fabric.render.MobReplacementTargets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MobReplacementModelPickerScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_MARGIN = 24;
    private static final int HEADER_HEIGHT = 58;
    private static final int FOOTER_HEIGHT = 28;
    private static final int ITEM_HEIGHT = 18;
    private static final int ITEM_SPACING = 2;

    private static final int COLOR_SCREEN_OVERLAY = 0x28000000;
    private static final int COLOR_PANEL_BG = 0x2A000000;
    private static final int COLOR_PANEL_INNER = 0x20000000;
    private static final int COLOR_LIST_BG = 0x22000000;
    private static final int COLOR_ITEM_BASE = 0x24000000;
    private static final int COLOR_ITEM_HOVER = 0x38FFFFFF;
    private static final int COLOR_ITEM_SELECTED = 0x52FFFFFF;
    private static final int COLOR_TEXT_TITLE = 0xFFF1F5FB;
    private static final int COLOR_TEXT = 0xFFE9F1FA;
    private static final int COLOR_TEXT_DIM = 0xC8D5DFEC;
    private static final int COLOR_SCROLLBAR_TRACK = 0x20FFFFFF;
    private static final int COLOR_SCROLLBAR_THUMB = 0x88A8D8FF;

    private final Screen parent;
    private final MobReplacementTargets.Target target;
    private final String currentValue;
    private final Consumer<String> selectionConsumer;

    private final List<String> allModels = new ArrayList<>();
    private final List<String> filteredModels = new ArrayList<>();

    private EditBox searchBox;
    private Button chooseButton;

    private int panelX;
    private int panelY;
    private int panelHeight;
    private int listTop;
    private int listBottom;
    private int hoveredIndex = -1;
    private int scrollOffset;
    private int maxScroll;
    private String selectedModel;

    public MobReplacementModelPickerScreen(Screen parent, MobReplacementTargets.Target target,
                                           String currentValue, Consumer<String> selectionConsumer) {
        super(Component.translatable("gui.mmdskin.mod_settings.category.mob_replacement"));
        this.parent = parent;
        this.target = target;
        this.currentValue = currentValue;
        this.selectionConsumer = selectionConsumer;
        this.selectedModel = UIConstants.DEFAULT_MODEL_NAME.equals(currentValue) ? null : currentValue;
    }

    @Override
    protected void init() {
        super.init();
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = PANEL_MARGIN;
        panelHeight = this.height - PANEL_MARGIN * 2;

        int searchWidth = PANEL_WIDTH - 76;
        this.searchBox = new EditBox(this.font, panelX + 8, panelY + 30, searchWidth, 20, Component.translatable("selectWorld.search"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setResponder(value -> refreshFilter());
        this.addRenderableWidget(this.searchBox);
        this.setInitialFocus(this.searchBox);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.refresh"), button -> refreshModels())
            .bounds(panelX + PANEL_WIDTH - 60, panelY + 30, 52, 20)
            .build());

        listTop = panelY + HEADER_HEIGHT;
        listBottom = panelY + panelHeight - FOOTER_HEIGHT;

        int buttonY = listBottom + 6;
        int buttonWidth = (PANEL_WIDTH - 16) / 3;
        this.chooseButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> applySelection())
            .bounds(panelX + 4, buttonY, buttonWidth, 20)
            .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.mod_settings.mob_replacement.vanilla"), button -> applyDefault())
            .bounds(panelX + 8 + buttonWidth, buttonY, buttonWidth, 20)
            .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
            .bounds(panelX + 12 + buttonWidth * 2, buttonY, buttonWidth, 20)
            .build());

        refreshModels();
    }

    private void refreshModels() {
        String search = this.searchBox == null ? "" : this.searchBox.getValue();
        allModels.clear();
        for (String modelName : ModConfigScreen.createModelSelections()) {
            if (!UIConstants.DEFAULT_MODEL_NAME.equals(modelName) && !allModels.contains(modelName)) {
                allModels.add(modelName);
            }
        }
        if (selectedModel != null && !selectedModel.isBlank() && !allModels.contains(selectedModel)) {
            allModels.add(selectedModel);
        }
        if (!UIConstants.DEFAULT_MODEL_NAME.equals(currentValue) && !currentValue.isBlank() && !allModels.contains(currentValue)) {
            allModels.add(currentValue);
        }
        allModels.sort(Comparator.comparing(value -> value.toLowerCase(Locale.ROOT)));
        if (this.searchBox != null && !search.equals(this.searchBox.getValue())) {
            this.searchBox.setValue(search);
        }
        refreshFilter();
    }

    private void refreshFilter() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        filteredModels.clear();
        for (String modelName : allModels) {
            if (query.isEmpty() || modelName.toLowerCase(Locale.ROOT).contains(query)) {
                filteredModels.add(modelName);
            }
        }
        if (selectedModel != null && !filteredModels.contains(selectedModel)) {
            selectedModel = filteredModels.isEmpty() ? null : filteredModels.getFirst();
        }
        int contentHeight = filteredModels.size() * (ITEM_HEIGHT + ITEM_SPACING);
        int visibleHeight = listBottom - listTop;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        chooseButton.active = selectedModel != null;
    }

    private void applySelection() {
        if (selectedModel == null || selectedModel.isBlank()) {
            return;
        }
        selectionConsumer.accept(selectedModel);
        Minecraft.getInstance().setScreen(parent);
    }

    private void applyDefault() {
        selectionConsumer.accept(UIConstants.DEFAULT_MODEL_NAME);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, COLOR_SCREEN_OVERLAY);
        renderPanel(guiGraphics);
        renderWidgetBackplates(guiGraphics, mouseX, mouseY);

        guiGraphics.drawCenteredString(this.font, target.displayName(), this.width / 2, panelY + 8, COLOR_TEXT_TITLE);
        guiGraphics.drawCenteredString(
            this.font,
            ModConfigScreen.toModelSelectionComponent(currentValue),
            this.width / 2,
            panelY + 18,
            COLOR_TEXT_DIM
        );
        guiGraphics.drawString(this.font, Component.translatable("selectWorld.search"), panelX + 8, panelY + 20, COLOR_TEXT_DIM);

        renderModelList(guiGraphics, mouseX, mouseY);
        renderScrollbar(guiGraphics);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderModelList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        hoveredIndex = -1;
        guiGraphics.enableScissor(panelX + 2, listTop, panelX + PANEL_WIDTH - 2, listBottom);

        for (int i = 0; i < filteredModels.size(); i++) {
            int rowY = listTop + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;
            if (rowY + ITEM_HEIGHT < listTop || rowY > listBottom) {
                continue;
            }

            String modelName = filteredModels.get(i);
            int rowX = panelX + 6;
            int rowWidth = PANEL_WIDTH - 12;
            boolean hovered = mouseX >= rowX && mouseX <= rowX + rowWidth
                && mouseY >= Math.max(rowY, listTop) && mouseY <= Math.min(rowY + ITEM_HEIGHT, listBottom);
            boolean selected = modelName.equals(selectedModel);
            if (hovered) {
                hoveredIndex = i;
            }

            int rowColor = selected ? COLOR_ITEM_SELECTED : (hovered ? COLOR_ITEM_HOVER : COLOR_ITEM_BASE);
            guiGraphics.fill(rowX, rowY, rowX + rowWidth, rowY + ITEM_HEIGHT, rowColor);
            guiGraphics.drawString(this.font, truncate(modelName, 34), rowX + 6, rowY + 5, COLOR_TEXT, false);
        }

        guiGraphics.disableScissor();
    }

    private void renderScrollbar(GuiGraphics guiGraphics) {
        if (maxScroll <= 0) {
            return;
        }

        int barX = panelX + PANEL_WIDTH - 5;
        int barHeight = listBottom - listTop;
        guiGraphics.fill(barX, listTop, barX + 2, listBottom, COLOR_SCROLLBAR_TRACK);

        int thumbHeight = Math.max(18, barHeight * barHeight / (barHeight + maxScroll));
        int thumbY = listTop + (int) ((barHeight - thumbHeight) * ((float) scrollOffset / maxScroll));
        guiGraphics.fill(barX, thumbY, barX + 2, thumbY + thumbHeight, COLOR_SCROLLBAR_THUMB);
    }

    private void renderPanel(GuiGraphics guiGraphics) {
        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, COLOR_PANEL_BG);
        guiGraphics.fill(panelX + 1, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + panelHeight - 1, COLOR_PANEL_INNER);
        guiGraphics.fill(panelX + 6, listTop, panelX + PANEL_WIDTH - 6, listBottom, COLOR_LIST_BG);
    }

    private void renderWidgetBackplates(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (searchBox != null) {
            boolean hovered = isPointInside(mouseX, mouseY, searchBox.getX(), searchBox.getY(), searchBox.getWidth(), searchBox.getHeight());
            int color = hovered || searchBox.isFocused() ? 0x30FFFFFF : COLOR_LIST_BG;
            guiGraphics.fill(searchBox.getX(), searchBox.getY(), searchBox.getX() + searchBox.getWidth(), searchBox.getY() + searchBox.getHeight(), color);
        }

        renderButtonBackplate(guiGraphics, mouseX, mouseY, panelX + PANEL_WIDTH - 60, panelY + 30, 52, 20, true);

        int buttonY = listBottom + 6;
        int buttonWidth = (PANEL_WIDTH - 16) / 3;
        renderButtonBackplate(guiGraphics, mouseX, mouseY, panelX + 4, buttonY, buttonWidth, 20, chooseButton != null && chooseButton.active);
        renderButtonBackplate(guiGraphics, mouseX, mouseY, panelX + 8 + buttonWidth, buttonY, buttonWidth, 20, true);
        renderButtonBackplate(guiGraphics, mouseX, mouseY, panelX + 12 + buttonWidth * 2, buttonY, buttonWidth, 20, true);
    }

    private void renderButtonBackplate(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                       int x, int y, int width, int height, boolean active) {
        boolean hovered = isPointInside(mouseX, mouseY, x, y, width, height);
        int color = active ? (hovered ? 0x4AFFFFFF : 0x30000000) : 0x1A000000;
        guiGraphics.fill(x, y, x + width, y + height, color);
    }

    private static boolean isPointInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && hoveredIndex >= 0 && hoveredIndex < filteredModels.size()) {
            selectedModel = filteredModels.get(hoveredIndex);
            chooseButton.active = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= listTop && mouseY <= listBottom) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 20)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength - 2) + ".." : value;
    }
}
