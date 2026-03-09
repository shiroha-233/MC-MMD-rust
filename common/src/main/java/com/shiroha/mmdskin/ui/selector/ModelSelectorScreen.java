package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.ui.selector.application.ModelSelectionApplicationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型选择界面 — 简约右侧面板风格
 * 右侧面板展示模型列表，左侧留空用于模型预览
 */
public class ModelSelectorScreen extends Screen {

    private static final int PANEL_WIDTH = 140;
    private static final int PANEL_MARGIN = 4;
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 20;
    private static final int ITEM_HEIGHT = 14;
    private static final int ITEM_SPACING = 1;

    private static final int COLOR_PANEL_BG = 0xC0101418;
    private static final int COLOR_PANEL_BORDER = 0xFF2A3A4A;
    private static final int COLOR_ITEM_HOVER = 0x30FFFFFF;
    private static final int COLOR_ITEM_SELECTED = 0x3060A0D0;
    private static final int COLOR_ACCENT = 0xFF60A0D0;
    private static final int COLOR_TEXT = 0xFFDDDDDD;
    private static final int COLOR_TEXT_DIM = 0xFF888888;
    private static final int COLOR_TEXT_SELECTED = 0xFF60A0D0;
    private static final int COLOR_SEPARATOR = 0x30FFFFFF;
    private static final int COLOR_SETTINGS_BTN = 0x80FFFFFF;
    private static final int COLOR_SETTINGS_BTN_HOVER = 0xFFFFFFFF;

    private static final int SETTINGS_BTN_SIZE = 10;
    private static final ModelSelectionApplicationService SERVICE = ModelSelectorServices.modelSelection();

    private final List<ModelCardEntry> modelCards;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private String currentModel;
    private int hoveredCardIndex = -1;
    private boolean hoveredOnSettingsBtn = false;

    private int panelX, panelY, panelH;
    private int listTop, listBottom;

    public ModelSelectorScreen() {
        super(Component.translatable("gui.mmdskin.model_selector"));
        this.modelCards = new ArrayList<>();
        this.currentModel = SERVICE.getCurrentModel();
        loadAvailableModels();
    }

    private void loadAvailableModels() {
        modelCards.clear();
        for (ModelSelectionApplicationService.ModelCard card : SERVICE.loadModelCards()) {
            modelCards.add(new ModelCardEntry(card.displayName(), card.configurable()));
        }
    }

    @Override
    protected void init() {
        super.init();

        panelX = this.width - PANEL_WIDTH - PANEL_MARGIN;
        panelY = PANEL_MARGIN;
        panelH = this.height - PANEL_MARGIN * 2;

        listTop = panelY + HEADER_HEIGHT;
        listBottom = panelY + panelH - FOOTER_HEIGHT;

        int contentHeight = modelCards.size() * (ITEM_HEIGHT + ITEM_SPACING);
        int visibleHeight = listBottom - listTop;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        int btnY = listBottom + 4;
        int btnW = (PANEL_WIDTH - 12) / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> this.onClose())
            .bounds(panelX + 4, btnY, btnW, 14).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.refresh"), btn -> refreshModels())
            .bounds(panelX + 4 + btnW + 4, btnY, btnW, 14).build());
    }

    private void refreshModels() {
        SERVICE.refreshModelCatalog();
        loadAvailableModels();
        scrollOffset = 0;
        this.clearWidgets();
        this.init();
    }

    private void selectModel(ModelCardEntry card) {
        this.currentModel = card.displayName;
        SERVICE.selectModel(card.displayName);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, COLOR_PANEL_BG);

        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelH, COLOR_PANEL_BORDER);

        renderHeader(guiGraphics);

        renderModelList(guiGraphics, mouseX, mouseY);

        renderScrollbar(guiGraphics);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics guiGraphics) {
        int cx = panelX + PANEL_WIDTH / 2;

        guiGraphics.drawCenteredString(this.font, this.title, cx, panelY + 4, COLOR_ACCENT);

        String info = Component.translatable("gui.mmdskin.model_selector.stats", modelCards.size() - 1, truncate(currentModel, 10)).getString();
        guiGraphics.drawCenteredString(this.font, info, cx, panelY + 16, COLOR_TEXT_DIM);

        guiGraphics.fill(panelX + 8, listTop - 2, panelX + PANEL_WIDTH - 8, listTop - 1, COLOR_SEPARATOR);
    }

    private void renderModelList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.enableScissor(panelX, listTop, panelX + PANEL_WIDTH, listBottom);

        hoveredCardIndex = -1;
        hoveredOnSettingsBtn = false;

        for (int i = 0; i < modelCards.size(); i++) {
            ModelCardEntry card = modelCards.get(i);
            int itemY = listTop + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;

            if (itemY + ITEM_HEIGHT < listTop || itemY > listBottom) continue;

            int itemX = panelX + 6;
            int itemW = PANEL_WIDTH - 12;
            boolean isSelected = card.displayName.equals(currentModel);
            boolean isHovered = mouseX >= itemX && mouseX <= itemX + itemW
                             && mouseY >= Math.max(itemY, listTop) && mouseY <= Math.min(itemY + ITEM_HEIGHT, listBottom);

            boolean hasSettingsBtn = card.configurable;
            boolean isSettingsBtnHovered = false;
            if (isHovered && hasSettingsBtn) {
                int btnX = itemX + itemW - SETTINGS_BTN_SIZE - 2;
                int btnY = itemY + (ITEM_HEIGHT - SETTINGS_BTN_SIZE) / 2;
                int clippedBtnTop = Math.max(btnY, listTop);
                int clippedBtnBottom = Math.min(btnY + SETTINGS_BTN_SIZE, listBottom);
                isSettingsBtnHovered = mouseX >= btnX && mouseX <= btnX + SETTINGS_BTN_SIZE
                                   && mouseY >= clippedBtnTop && mouseY <= clippedBtnBottom;
            }

            if (isHovered) {
                hoveredCardIndex = i;
                hoveredOnSettingsBtn = isSettingsBtnHovered;
            }

            renderItem(guiGraphics, card, itemX, itemY, itemW, isSelected, isHovered, isSettingsBtnHovered);
        }

        guiGraphics.disableScissor();
    }

    private void renderItem(GuiGraphics guiGraphics, ModelCardEntry card, int x, int y, int w, boolean isSelected, boolean isHovered, boolean isSettingsBtnHovered) {

        if (isSelected) {
            guiGraphics.fill(x, y, x + w, y + ITEM_HEIGHT, COLOR_ITEM_SELECTED);

            guiGraphics.fill(x, y + 1, x + 2, y + ITEM_HEIGHT - 1, COLOR_ACCENT);
        } else if (isHovered) {
            guiGraphics.fill(x, y, x + w, y + ITEM_HEIGHT, COLOR_ITEM_HOVER);
        }

        int textX = x + 8;
        boolean hasSettingsBtn = card.configurable;

        int maxNameLen = hasSettingsBtn ? 12 : 16;
        String displayName = truncate(card.displayName, maxNameLen);
        int nameColor = isSelected ? COLOR_TEXT_SELECTED : COLOR_TEXT;
        guiGraphics.drawString(this.font, displayName, textX, y + 3, nameColor);

        if (hasSettingsBtn && isHovered) {

            int btnX = x + w - SETTINGS_BTN_SIZE - 2;
            int btnY = y + (ITEM_HEIGHT - SETTINGS_BTN_SIZE) / 2;
            int btnColor = isSettingsBtnHovered ? COLOR_SETTINGS_BTN_HOVER : COLOR_SETTINGS_BTN;

            if (isSettingsBtnHovered) {
                guiGraphics.fill(btnX - 1, btnY - 1, btnX + SETTINGS_BTN_SIZE + 1, btnY + SETTINGS_BTN_SIZE + 1, 0x40FFFFFF);
            }
            guiGraphics.drawString(this.font, "\u2699", btnX, btnY, btnColor);
        } else if (isSelected) {
            guiGraphics.drawString(this.font, "\u2713", x + w - 10, y + 3, COLOR_ACCENT);
        }
    }

    private void renderScrollbar(GuiGraphics guiGraphics) {
        if (maxScroll <= 0) return;

        int barX = panelX + PANEL_WIDTH - 4;
        int barH = listBottom - listTop;

        guiGraphics.fill(barX, listTop, barX + 2, listBottom, 0x20FFFFFF);

        int thumbH = Math.max(16, barH * barH / (barH + maxScroll));
        int thumbY = listTop + (int)((barH - thumbH) * ((float) scrollOffset / maxScroll));
        guiGraphics.fill(barX, thumbY, barX + 2, thumbY + thumbH, COLOR_ACCENT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredCardIndex >= 0 && hoveredCardIndex < modelCards.size()) {
            ModelCardEntry card = modelCards.get(hoveredCardIndex);

                if (hoveredOnSettingsBtn && card.configurable) {

                Minecraft.getInstance().setScreen(
                    new ModelSettingsScreen(card.displayName, this));
                return true;
            }

            selectModel(card);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {

        if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(delta * 24)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 2) + ".." : s;
    }

    private static class ModelCardEntry {
        final String displayName;
        final boolean configurable;

        ModelCardEntry(String displayName, boolean configurable) {
            this.displayName = displayName;
            this.configurable = configurable;
        }
    }
}
