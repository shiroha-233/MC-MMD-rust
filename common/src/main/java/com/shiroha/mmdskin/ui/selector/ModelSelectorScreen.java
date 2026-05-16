/* 文件职责：提供玩家模型选择原生界面。 */
package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import com.shiroha.mmdskin.ui.selector.application.ModelSelectionApplicationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ModelSelectorScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ModelSelectionApplicationService SERVICE = ModelSelectorServices.modelSelection();

    private static final int WINDOW_MARGIN = 10;
    private static final int MIN_WINDOW_WIDTH = 150;
    private static final int MAX_WINDOW_WIDTH = 190;
    private static final int MIN_WINDOW_HEIGHT = 220;

    private static final int HEADER_HEIGHT = 30;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 4;
    private static final int LIST_PADDING = 5;
    private static final int CARD_HEIGHT = 14;
    private static final int CARD_GAP = 4;

    private final List<ModelSelectionApplicationService.ModelCard> modelCards = new ArrayList<>();

    private String currentModel;
    private boolean pendingClose;
    private String pendingSettingsModel;
    private float targetScroll;
    private float animatedScroll;
    private int hoveredCard = -1;
    private ButtonTarget hoveredButton = ButtonTarget.NONE;
    private Layout layout = Layout.empty();

    private enum ButtonTarget {
        NONE,
        DONE,
        REFRESH,
        SETTINGS
    }

    public ModelSelectorScreen() {
        super(Component.translatable("gui.mmdskin.model_selector"));
        reloadModelCards();
    }

    @Override
    protected void init() {
        super.init();
        updateLayout();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            updateLayout();
            updateHoverState(mouseX, mouseY);
            updateScrollAnimation();
            renderScreen(guiGraphics);
            flushPendingActions(minecraft);
        } catch (Throwable throwable) {
            closeAfterFailure(throwable);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        if (!layout.panel.contains(mouseX, mouseY)) {
            return super.mouseClicked(event, doubleClick);
        }

        if (layout.doneButton.contains(mouseX, mouseY)) {
            pendingClose = true;
            return true;
        }
        if (layout.refreshButton.contains(mouseX, mouseY)) {
            refreshModels();
            return true;
        }

        ModelSelectionApplicationService.ModelCard selectedCard = getSelectedCard();
        if (selectedCard != null
                && selectedCard.configurable()
                && layout.settingsButton.contains(mouseX, mouseY)) {
            pendingSettingsModel = selectedCard.displayName();
            return true;
        }

        if (layout.listBox.contains(mouseX, mouseY) && hoveredCard >= 0 && hoveredCard < modelCards.size()) {
            selectModel(modelCards.get(hoveredCard).displayName());
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!layout.listBox.contains(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        targetScroll = Mth.clamp(targetScroll - (float) scrollY * 12.0f, 0.0f, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderScreen(GuiGraphics guiGraphics) {
        TranslucentTrayChrome.drawOverlay(guiGraphics, this.width, this.height);
        TranslucentTrayChrome.drawPanel(guiGraphics, layout.panel.x, layout.panel.y, layout.panel.w, layout.panel.h);

        guiGraphics.drawString(this.font, this.title.getString(), layout.header.x, layout.header.y + 1, TranslucentTrayChrome.TITLE_TEXT, false);
        String stats = Component.translatable(
                "gui.mmdskin.model_selector.stats",
                Math.max(0, modelCards.size() - 1),
                shorten(currentModel, 8)
        ).getString();
        guiGraphics.drawString(this.font, stats, layout.header.x, layout.header.y + 10, TranslucentTrayChrome.SUBTITLE_TEXT, false);

        drawButton(guiGraphics, layout.doneButton, Component.translatable("gui.done").getString(), hoveredButton == ButtonTarget.DONE, true);
        drawButton(guiGraphics, layout.refreshButton, Component.translatable("gui.mmdskin.refresh").getString(), hoveredButton == ButtonTarget.REFRESH, true);
        ModelSelectionApplicationService.ModelCard selectedCard = getSelectedCard();
        boolean settingsEnabled = selectedCard != null && selectedCard.configurable();
        drawButton(guiGraphics, layout.settingsButton, Component.translatable("gui.mmdskin.model_settings.title").getString(), hoveredButton == ButtonTarget.SETTINGS, settingsEnabled);

        UiRect list = layout.listBox;
        TranslucentTrayChrome.fillListArea(guiGraphics, list.x, list.y, list.w, list.h);
        if (modelCards.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "No models", list.centerX(), list.centerY() - 4, TranslucentTrayChrome.BODY_TEXT);
            return;
        }

        guiGraphics.enableScissor(list.x, list.y, list.x + list.w, list.y + list.h);
        int y = Math.round(list.y + LIST_PADDING - animatedScroll);
        for (int i = 0; i < modelCards.size(); i++) {
            ModelSelectionApplicationService.ModelCard card = modelCards.get(i);
            if (y + CARD_HEIGHT < list.y) {
                y += CARD_HEIGHT + CARD_GAP;
                continue;
            }
            if (y > list.y + list.h) {
                break;
            }
            boolean selected = card.displayName().equals(currentModel);
            boolean hovered = i == hoveredCard;
            guiGraphics.fill(list.x + 4, y, list.x + list.w - 4, y + CARD_HEIGHT, TranslucentTrayChrome.cardBackground(selected, hovered));
            guiGraphics.drawString(this.font, buildCardLabel(card), list.x + 7, y + 3, TranslucentTrayChrome.BODY_TEXT, false);
            y += CARD_HEIGHT + CARD_GAP;
        }
        guiGraphics.disableScissor();
        TranslucentTrayChrome.drawScrollbar(guiGraphics, list.x + list.w - 3, list.y, list.y + list.h, animatedScroll, maxScroll());
    }

    private void drawButton(GuiGraphics guiGraphics, UiRect rect, String text, boolean hovered, boolean enabled) {
        TranslucentTrayChrome.drawButton(guiGraphics, this.font, rect.x, rect.y, rect.w, rect.h, text, hovered, enabled);
    }

    private void updateLayout() {
        int panelWidth = Mth.clamp(Math.round(this.width * 0.14f), MIN_WINDOW_WIDTH, MAX_WINDOW_WIDTH);
        int panelHeight = Math.max(MIN_WINDOW_HEIGHT, this.height - WINDOW_MARGIN * 2);
        int panelX = this.width - panelWidth - WINDOW_MARGIN;
        int panelY = WINDOW_MARGIN;

        UiRect panel = new UiRect(panelX, panelY, panelWidth, panelHeight);
        UiRect header = new UiRect(panelX + 8, panelY + 5, panelWidth - 16, HEADER_HEIGHT);

        int buttonY = panel.y + panel.h - BUTTON_HEIGHT - 6;
        int buttonWidth = (header.w - BUTTON_GAP * 2) / 3;
        UiRect doneButton = new UiRect(header.x, buttonY, buttonWidth, BUTTON_HEIGHT);
        UiRect refreshButton = new UiRect(header.x + buttonWidth + BUTTON_GAP, buttonY, buttonWidth, BUTTON_HEIGHT);
        UiRect settingsButton = new UiRect(header.x + (buttonWidth + BUTTON_GAP) * 2, buttonY, buttonWidth, BUTTON_HEIGHT);

        int listY = header.y + header.h + 2;
        int listBottom = buttonY - 4;
        int listHeight = Math.max(48, listBottom - listY);
        UiRect listBox = new UiRect(header.x, listY, header.w, listHeight);
        layout = new Layout(panel, header, listBox, doneButton, refreshButton, settingsButton);

        float maxScroll = maxScroll();
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll);
        animatedScroll = Mth.clamp(animatedScroll, 0.0f, maxScroll);
    }

    private void updateHoverState(int mouseX, int mouseY) {
        hoveredButton = ButtonTarget.NONE;
        hoveredCard = -1;

        if (layout.doneButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.DONE;
            return;
        }
        if (layout.refreshButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.REFRESH;
            return;
        }
        ModelSelectionApplicationService.ModelCard selectedCard = getSelectedCard();
        if (selectedCard != null && selectedCard.configurable() && layout.settingsButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.SETTINGS;
            return;
        }
        if (!layout.listBox.contains(mouseX, mouseY) || modelCards.isEmpty()) {
            return;
        }

        float localY = (float) mouseY - (layout.listBox.y + LIST_PADDING) + animatedScroll;
        if (localY < 0.0f) {
            return;
        }
        int index = (int) (localY / (CARD_HEIGHT + CARD_GAP));
        if (index < 0 || index >= modelCards.size()) {
            return;
        }
        if (localY - index * (CARD_HEIGHT + CARD_GAP) <= CARD_HEIGHT) {
            hoveredCard = index;
        }
    }

    private void updateScrollAnimation() {
        animatedScroll = Mth.lerp(0.24f, animatedScroll, targetScroll);
        if (Math.abs(animatedScroll - targetScroll) < 0.25f) {
            animatedScroll = targetScroll;
        }
    }

    private float maxScroll() {
        int count = modelCards.size();
        float contentHeight = count <= 0
                ? 0.0f
                : LIST_PADDING * 2.0f + count * CARD_HEIGHT + Math.max(0, count - 1) * CARD_GAP;
        return Math.max(0.0f, contentHeight - layout.listBox.h);
    }

    private void reloadModelCards() {
        modelCards.clear();
        modelCards.addAll(SERVICE.loadModelCards());
        currentModel = SERVICE.getCurrentModel();
    }

    private void refreshModels() {
        SERVICE.refreshModelCatalog();
        reloadModelCards();
        targetScroll = 0.0f;
        animatedScroll = 0.0f;
    }

    private void selectModel(String modelName) {
        currentModel = modelName;
        SERVICE.selectModel(modelName);
    }

    private ModelSelectionApplicationService.ModelCard getSelectedCard() {
        for (ModelSelectionApplicationService.ModelCard card : modelCards) {
            if (card.displayName().equals(currentModel)) {
                return card;
            }
        }
        return null;
    }

    private void flushPendingActions(Minecraft minecraft) {
        if (pendingSettingsModel != null && minecraft.screen == this) {
            String modelName = pendingSettingsModel;
            pendingSettingsModel = null;
            minecraft.setScreen(new ModelSettingsScreen(modelName, this));
            return;
        }
        if (pendingClose && minecraft.screen == this) {
            pendingClose = false;
            minecraft.setScreen(null);
        }
    }

    private void closeAfterFailure(Throwable throwable) {
        LOGGER.error("[ModelSelector] Native selector render failed and will close", throwable);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            minecraft.setScreen(null);
        }
    }

    private static String buildCardLabel(ModelSelectionApplicationService.ModelCard card) {
        String name = shorten(card.displayName(), 14);
        return card.configurable() ? name : name + " (Default)";
    }

    private static String shorten(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        if (maxChars <= 3) {
            return value.substring(0, Math.max(0, maxChars));
        }
        return value.substring(0, maxChars - 2) + "..";
    }

    record UiRect(int x, int y, int w, int h) {
        static UiRect empty() {
            return new UiRect(0, 0, 0, 0);
        }

        boolean contains(double px, double py) {
            return px >= x && py >= y && px <= x + w && py <= y + h;
        }

        int centerX() {
            return x + w / 2;
        }

        int centerY() {
            return y + h / 2;
        }
    }

    private record Layout(UiRect panel, UiRect header, UiRect listBox,
                          UiRect doneButton, UiRect refreshButton, UiRect settingsButton) {
        static Layout empty() {
            UiRect empty = UiRect.empty();
            return new Layout(empty, empty, empty, empty, empty, empty);
        }
    }
}
