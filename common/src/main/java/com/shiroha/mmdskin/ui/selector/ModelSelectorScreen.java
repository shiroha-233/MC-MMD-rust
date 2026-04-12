package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.ui.selector.application.ModelSelectionApplicationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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

    private final SkiaModelSelectorRenderer skiaRenderer = new SkiaModelSelectorRenderer();
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

            SkiaModelSelectorRenderer.SelectorView view = buildView();
            if (!skiaRenderer.renderSelector(this, view)) {
                renderFallback(guiGraphics, view);
            }
            flushPendingActions(minecraft);
        } catch (Throwable throwable) {
            closeAfterFailure(throwable);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (!layout.panel.contains(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!layout.listBox.contains(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        float step = 12.0f;
        targetScroll -= (float) delta * step;
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll());
        return true;
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
    public void onClose() {
        skiaRenderer.dispose();
        super.onClose();
    }

    @Override
    public void removed() {
        skiaRenderer.dispose();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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
        if (selectedCard != null
                && selectedCard.configurable()
                && layout.settingsButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.SETTINGS;
            return;
        }

        if (!layout.listBox.contains(mouseX, mouseY) || modelCards.isEmpty()) {
            return;
        }

        float listTop = layout.listBox.y + LIST_PADDING;
        float itemStride = CARD_HEIGHT + CARD_GAP;
        float localY = (float) mouseY - listTop + animatedScroll;
        if (localY < 0.0f) {
            return;
        }
        int index = (int) (localY / itemStride);
        if (index < 0 || index >= modelCards.size()) {
            return;
        }

        float offsetInItem = localY - index * itemStride;
        if (offsetInItem <= CARD_HEIGHT) {
            hoveredCard = index;
        }
    }

    private void updateScrollAnimation() {
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll());
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

    private SkiaModelSelectorRenderer.SelectorView buildView() {
        List<SkiaModelSelectorRenderer.CardView> cards = new ArrayList<>(modelCards.size());
        for (int i = 0; i < modelCards.size(); i++) {
            ModelSelectionApplicationService.ModelCard card = modelCards.get(i);
            cards.add(new SkiaModelSelectorRenderer.CardView(
                    buildCardLabel(card),
                    card.configurable(),
                    card.displayName().equals(currentModel),
                    i == hoveredCard,
                    i
            ));
        }

        return new SkiaModelSelectorRenderer.SelectorView(
                this.title.getString(),
                Component.translatable("gui.mmdskin.model_selector.stats",
                        Math.max(0, modelCards.size() - 1),
                        shorten(currentModel, 8)).getString(),
                Component.translatable("gui.done").getString(),
                Component.translatable("gui.mmdskin.refresh").getString(),
                Component.translatable("gui.mmdskin.model_settings.title").getString(),
                "No models",
                layout.panel,
                layout.header,
                layout.listBox,
                layout.doneButton,
                layout.refreshButton,
                layout.settingsButton,
                hoveredButton == ButtonTarget.DONE,
                hoveredButton == ButtonTarget.REFRESH,
                hoveredButton == ButtonTarget.SETTINGS,
                true,
                getSelectedCard() != null && getSelectedCard().configurable(),
                animatedScroll,
                cards,
                LIST_PADDING,
                CARD_HEIGHT,
                CARD_GAP
        );
    }

    private void renderFallback(GuiGraphics guiGraphics, SkiaModelSelectorRenderer.SelectorView view) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x28000000);
        guiGraphics.fill(view.panel().x, view.panel().y, view.panel().x + view.panel().w, view.panel().y + view.panel().h, 0x2A000000);
        guiGraphics.fill(view.panel().x + 1, view.panel().y + 1, view.panel().x + view.panel().w - 1, view.panel().y + view.panel().h - 1, 0x20000000);

        guiGraphics.drawString(this.font, view.title(), view.header().x, view.header().y + 1, 0xFFF1F5FB, false);
        guiGraphics.drawString(this.font, view.stats(), view.header().x, view.header().y + 10, 0xC8D5DFEC, false);
        drawFallbackButton(guiGraphics, view.doneButton(), view.doneText(), view.doneHovered(), true);
        drawFallbackButton(guiGraphics, view.refreshButton(), view.refreshText(), view.refreshHovered(), true);
        drawFallbackButton(guiGraphics, view.settingsButton(), view.settingsText(), view.settingsHovered(), view.settingsEnabled());

        UiRect list = view.listBox();
        guiGraphics.fill(list.x, list.y, list.x + list.w, list.y + list.h, 0x22000000);
        if (view.cards().isEmpty()) {
            guiGraphics.drawCenteredString(this.font, view.emptyText(), list.centerX(), list.centerY() - 4, 0xFFDDE8F8);
            return;
        }

        int y = Math.round(list.y + view.listPadding() - view.scrollOffset());
        for (SkiaModelSelectorRenderer.CardView card : view.cards()) {
            if (y + view.cardHeight() < list.y) {
                y += view.cardHeight() + view.cardGap();
                continue;
            }
            if (y > list.y + list.h) {
                break;
            }
            int bg = card.selected() ? 0x52FFFFFF : (card.hovered() ? 0x38FFFFFF : 0x24000000);
            guiGraphics.fill(list.x + 4, y, list.x + list.w - 4, y + view.cardHeight(), bg);
            guiGraphics.drawString(this.font, card.label(), list.x + 7, y + 3, 0xFFE9F1FA, false);
            y += view.cardHeight() + view.cardGap();
        }
    }

    private void drawFallbackButton(GuiGraphics guiGraphics, UiRect rect, String text, boolean hovered, boolean enabled) {
        int bg = enabled
                ? (hovered ? 0x4AFFFFFF : 0x30000000)
                : 0x1A000000;
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, bg);
        guiGraphics.drawCenteredString(this.font, text, rect.centerX(), rect.y + 4, enabled ? 0xFFF1F6FD : 0x9BB2C5D7);
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
        LOGGER.error("[ModelSelector] Skia selector failed and will close", throwable);
        skiaRenderer.dispose();
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
