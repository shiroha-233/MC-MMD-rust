package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.scene.client.SceneModelCatalog;
import com.shiroha.mmdskin.scene.client.SceneModelManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景模型选择界面 — 复用 ModelSelectorScreen 的面板风格
 */
public class SceneSelectorScreen extends Screen {
    private static final Logger logger = LogManager.getLogger();
    private static final SceneModelCatalog SCENE_CATALOG = SceneModelCatalog.getInstance();

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
    private static final int COLOR_CANCEL = 0xFFD06060;

    private final List<SceneCardEntry> sceneCards;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private String currentScene;
    private int hoveredCardIndex = -1;

    private int panelX, panelY, panelH;
    private int listTop, listBottom;

    public SceneSelectorScreen() {
        super(Component.translatable("gui.mmdskin.scene_selector"));
        this.sceneCards = new ArrayList<>();
        SceneModelManager mgr = SceneModelManager.getInstance();
        this.currentScene = mgr.isActive() || mgr.isLoading() ? mgr.getSceneModelName() : null;
        loadAvailableScenes();
    }

    private void loadAvailableScenes() {
        sceneCards.clear();
        List<ModelInfo> models = SCENE_CATALOG.listModels();
        for (ModelInfo info : models) {
            sceneCards.add(new SceneCardEntry(info.getFolderName(), info));
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

        int contentHeight = sceneCards.size() * (ITEM_HEIGHT + ITEM_SPACING);
        int visibleHeight = listBottom - listTop;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        int btnY = listBottom + 4;
        int btnW = (PANEL_WIDTH - 12) / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> this.onClose())
                .bounds(panelX + 4, btnY, btnW, 14).build());

        boolean hasScene = SceneModelManager.getInstance().isActive() || SceneModelManager.getInstance().isLoading();
        Component cancelText = Component.translatable("gui.mmdskin.scene_selector.cancel");
        this.addRenderableWidget(Button.builder(hasScene ? cancelText : Component.translatable("gui.mmdskin.refresh"), btn -> {
            if (SceneModelManager.getInstance().isActive() || SceneModelManager.getInstance().isLoading()) {
                SceneModelManager.getInstance().removeScene();
                this.currentScene = null;
                this.clearWidgets();
                this.init();
            } else {
                refreshScenes();
            }
        }).bounds(panelX + 4 + btnW + 4, btnY, btnW, 14).build());
    }

    private void refreshScenes() {
        SCENE_CATALOG.invalidate();
        loadAvailableScenes();
        scrollOffset = 0;
        this.clearWidgets();
        this.init();
    }

    private void selectScene(SceneCardEntry card) {
        this.currentScene = card.displayName;
        SceneModelManager.getInstance().placeScene(card.displayName);
        logger.info("放置场景模型: {}", card.displayName);
        this.clearWidgets();
        this.init();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, COLOR_PANEL_BG);
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelH, COLOR_PANEL_BORDER);

        renderHeader(guiGraphics);
        renderSceneList(guiGraphics, mouseX, mouseY);
        renderScrollbar(guiGraphics);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics guiGraphics) {
        int cx = panelX + PANEL_WIDTH / 2;
        guiGraphics.drawCenteredString(this.font, this.title, cx, panelY + 4, COLOR_ACCENT);

        String status;
        SceneModelManager mgr = SceneModelManager.getInstance();
        if (mgr.isLoading()) {
            status = Component.translatable("gui.mmdskin.scene_selector.loading").getString();
        } else if (mgr.isActive()) {
            status = Component.translatable("gui.mmdskin.scene_selector.active", truncate(currentScene, 8)).getString();
        } else {
            status = sceneCards.size() + " " + Component.translatable("gui.mmdskin.scene_selector.models").getString();
        }
        guiGraphics.drawCenteredString(this.font, status, cx, panelY + 16, COLOR_TEXT_DIM);
        guiGraphics.fill(panelX + 8, listTop - 2, panelX + PANEL_WIDTH - 8, listTop - 1, COLOR_SEPARATOR);
    }

    private void renderSceneList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.enableScissor(panelX, listTop, panelX + PANEL_WIDTH, listBottom);

        hoveredCardIndex = -1;

        for (int i = 0; i < sceneCards.size(); i++) {
            SceneCardEntry card = sceneCards.get(i);
            int itemY = listTop + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;

            if (itemY + ITEM_HEIGHT < listTop || itemY > listBottom) continue;

            int itemX = panelX + 6;
            int itemW = PANEL_WIDTH - 12;
            boolean isSelected = card.displayName.equals(currentScene);
            boolean isHovered = mouseX >= itemX && mouseX <= itemX + itemW
                    && mouseY >= Math.max(itemY, listTop) && mouseY <= Math.min(itemY + ITEM_HEIGHT, listBottom);

            if (isHovered) hoveredCardIndex = i;

            renderItem(guiGraphics, card, itemX, itemY, itemW, isSelected, isHovered);
        }

        guiGraphics.disableScissor();
    }

    private void renderItem(GuiGraphics guiGraphics, SceneCardEntry card, int x, int y, int w,
                            boolean isSelected, boolean isHovered) {
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
        int thumbY = listTop + (int) ((barH - thumbH) * ((float) scrollOffset / maxScroll));
        guiGraphics.fill(barX, thumbY, barX + 2, thumbY + thumbH, COLOR_ACCENT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredCardIndex >= 0 && hoveredCardIndex < sceneCards.size()) {
            selectScene(sceneCards.get(hoveredCardIndex));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (delta * 24)));
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
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 2) + ".." : s;
    }

    private static class SceneCardEntry {
        final String displayName;
        final ModelInfo modelInfo;

        SceneCardEntry(String displayName, ModelInfo modelInfo) {
            this.displayName = displayName;
            this.modelInfo = modelInfo;
        }
    }
}
