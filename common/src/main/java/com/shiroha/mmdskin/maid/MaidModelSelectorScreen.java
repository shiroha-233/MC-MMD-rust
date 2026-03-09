package com.shiroha.mmdskin.maid;

import com.shiroha.mmdskin.maid.service.DefaultMaidModelSelectionService;
import com.shiroha.mmdskin.maid.service.MaidModelSelectionService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 女仆 MMD 模型选择界面 — 简约右侧面板风格
 */

public class MaidModelSelectorScreen extends Screen {

    private static final int PANEL_WIDTH = 140;
    private static final int PANEL_MARGIN = 4;
    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 20;
    private static final int ITEM_HEIGHT = 14;
    private static final int ITEM_SPACING = 1;

    private static final int COLOR_PANEL_BG = 0xC0181420;
    private static final int COLOR_PANEL_BORDER = 0xFF4A2A4A;
    private static final int COLOR_ITEM_HOVER = 0x30FFFFFF;
    private static final int COLOR_ITEM_SELECTED = 0x30D060A0;
    private static final int COLOR_ACCENT = 0xFFD060A0;
    private static final int COLOR_TEXT = 0xFFDDDDDD;
    private static final int COLOR_TEXT_DIM = 0xFF888888;
    private static final int COLOR_TEXT_SELECTED = 0xFFD060A0;
    private static final int COLOR_SEPARATOR = 0x30FFFFFF;

    private final UUID maidUUID;
    private final int maidEntityId;
    private final String maidName;
    private final MaidModelSelectionService maidModelSelectionService;
    private final List<ModelCardEntry> modelCards;

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private String currentModel;
    private int hoveredCardIndex = -1;

    private int panelX, panelY, panelH;
    private int listTop, listBottom;

    public MaidModelSelectorScreen(UUID maidUUID, int maidEntityId, String maidName) {
        this(maidUUID, maidEntityId, maidName, new DefaultMaidModelSelectionService());
    }

    MaidModelSelectorScreen(UUID maidUUID, int maidEntityId, String maidName,
                            MaidModelSelectionService maidModelSelectionService) {
        super(Component.translatable("gui.mmdskin.maid_model_selector"));
        this.maidUUID = maidUUID;
        this.maidEntityId = maidEntityId;
        this.maidName = maidName;
        this.maidModelSelectionService = maidModelSelectionService;
        this.modelCards = new ArrayList<>();
        this.currentModel = maidModelSelectionService.getCurrentModel(maidUUID);
        loadAvailableModels();
    }

    private void loadAvailableModels() {
        modelCards.clear();

        for (String modelName : maidModelSelectionService.loadAvailableModels()) {
            modelCards.add(new ModelCardEntry(modelName));
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
        loadAvailableModels();
        scrollOffset = 0;
        this.clearWidgets();
        this.init();
    }

    private void selectModel(ModelCardEntry card) {
        this.currentModel = card.displayName;
        maidModelSelectionService.selectModel(maidUUID, maidEntityId, card.displayName);
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

        String maidInfo = truncate(maidName, 16);
        guiGraphics.drawCenteredString(this.font, maidInfo, cx, panelY + 16, COLOR_TEXT_DIM);

        String info = Component.translatable("gui.mmdskin.model_selector.stats", modelCards.size() - 1, truncate(currentModel, 10)).getString();
        guiGraphics.drawCenteredString(this.font, info, cx, panelY + 28, COLOR_TEXT_DIM);

        guiGraphics.fill(panelX + 8, listTop - 2, panelX + PANEL_WIDTH - 8, listTop - 1, COLOR_SEPARATOR);
    }

    private void renderModelList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.enableScissor(panelX, listTop, panelX + PANEL_WIDTH, listBottom);

        hoveredCardIndex = -1;

        for (int i = 0; i < modelCards.size(); i++) {
            ModelCardEntry card = modelCards.get(i);
            int itemY = listTop + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;

            if (itemY + ITEM_HEIGHT < listTop || itemY > listBottom) continue;

            int itemX = panelX + 6;
            int itemW = PANEL_WIDTH - 12;
            boolean isSelected = card.displayName.equals(currentModel);
            boolean isHovered = mouseX >= itemX && mouseX <= itemX + itemW
                             && mouseY >= Math.max(itemY, listTop) && mouseY <= Math.min(itemY + ITEM_HEIGHT, listBottom);

            if (isHovered) {
                hoveredCardIndex = i;
            }

            renderItem(guiGraphics, card, itemX, itemY, itemW, isSelected, isHovered);
        }

        guiGraphics.disableScissor();
    }

    private void renderItem(GuiGraphics guiGraphics, ModelCardEntry card, int x, int y, int w, boolean isSelected, boolean isHovered) {

        if (isSelected) {
            guiGraphics.fill(x, y, x + w, y + ITEM_HEIGHT, COLOR_ITEM_SELECTED);

            guiGraphics.fill(x, y + 1, x + 2, y + ITEM_HEIGHT - 1, COLOR_ACCENT);
        } else if (isHovered) {
            guiGraphics.fill(x, y, x + w, y + ITEM_HEIGHT, COLOR_ITEM_HOVER);
        }

        int textX = x + 8;

        String displayName = truncate(card.displayName, 16);
        int nameColor = isSelected ? COLOR_TEXT_SELECTED : COLOR_TEXT;
        guiGraphics.drawString(this.font, displayName, textX, y + 3, nameColor);

        if (isSelected) {
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
            selectModel(modelCards.get(hoveredCardIndex));
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

        ModelCardEntry(String displayName) {
            this.displayName = displayName;
        }
    }
}
