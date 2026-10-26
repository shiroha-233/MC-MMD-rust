/* 文件职责：提供玩家模型选择原生界面。 */
package com.shiroha.mmdskin.ui.selector;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.distrib.ModelDistributionClient;
import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import com.shiroha.mmdskin.ui.selector.application.ModelSelectionApplicationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    /** 列表顶部"服务器模型分发"入口的哨兵卡片名（不是真的模型）。 */
    private static final String DISTRIBUTION_CARD_KEY = "__mmd_distribution_entry__";

    /** 列表顶部"本地指定玩家模型"入口的哨兵卡片名（不是真的模型）。 */
    private static final String OVERRIDE_CARD_KEY = "__mmd_player_override_entry__";

    private String currentModel;
    private boolean pendingClose;
    private String pendingSettingsModel;
    private boolean pendingDistribution;
    private boolean pendingForcedAssign;
    private int distributionCount;
    private long lastDistributionSyncMs;
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
        // 分发核心不认识 UI 类，责任条款提示页由 UI 层注入回调
        DistributionNoticeScreen.installNoticeOpener();
        reloadModelCards();
    }

    @Override
    protected void init() {
        super.init();
        updateLayout();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
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
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mouseX = event.x();
        double mouseY = event.y();
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
            String cardName = modelCards.get(hoveredCard).displayName();
            if (OVERRIDE_CARD_KEY.equals(cardName)) {
                pendingForcedAssign = true;
                return true;
            }
            if (DISTRIBUTION_CARD_KEY.equals(cardName)) {
                pendingDistribution = true;
                return true;
            }
            selectModel(cardName);
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
        if (event.isEscape()) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderScreen(GuiGraphicsExtractor guiGraphics) {
        TranslucentTrayChrome.drawOverlay(guiGraphics, this.width, this.height);
        TranslucentTrayChrome.drawPanel(guiGraphics, layout.panel.x, layout.panel.y, layout.panel.w, layout.panel.h);

        guiGraphics.text(this.font, this.title.getString(), layout.header.x, layout.header.y + 1, TranslucentTrayChrome.TITLE_TEXT, false);
        // 列表里多了一条"本地指定玩家模型"哨兵，可能还有一条"服务器分发"哨兵，
        // 计数时要把这些哨兵和"默认"那条一起扣掉
        int pseudoCards = 2 + (sentinelIndex(DISTRIBUTION_CARD_KEY) >= 0 ? 1 : 0);
        String stats = Component.translatable(
                "gui.mmdskin.model_selector.stats",
                Math.max(0, modelCards.size() - pseudoCards),
                shorten(currentModel, 8)
        ).getString();
        guiGraphics.text(this.font, stats, layout.header.x, layout.header.y + 10, TranslucentTrayChrome.SUBTITLE_TEXT, false);

        drawButton(guiGraphics, layout.doneButton, Component.translatable("gui.done").getString(), hoveredButton == ButtonTarget.DONE, true);
        drawButton(guiGraphics, layout.refreshButton, Component.translatable("gui.mmdskin.refresh").getString(), hoveredButton == ButtonTarget.REFRESH, true);
        ModelSelectionApplicationService.ModelCard selectedCard = getSelectedCard();
        boolean settingsEnabled = selectedCard != null && selectedCard.configurable();
        drawButton(guiGraphics, layout.settingsButton, Component.translatable("gui.mmdskin.model_settings.title").getString(), hoveredButton == ButtonTarget.SETTINGS, settingsEnabled);

        UiRect list = layout.listBox;
        TranslucentTrayChrome.fillListArea(guiGraphics, list.x, list.y, list.w, list.h);
        if (modelCards.isEmpty()) {
            guiGraphics.centeredText(this.font, "No models", list.centerX(), list.centerY() - 4, TranslucentTrayChrome.BODY_TEXT);
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
            guiGraphics.text(this.font, buildCardLabel(card), list.x + 7, y + 3, TranslucentTrayChrome.BODY_TEXT, false);
            y += CARD_HEIGHT + CARD_GAP;
        }
        guiGraphics.disableScissor();
        TranslucentTrayChrome.drawScrollbar(guiGraphics, list.x + list.w - 3, list.y, list.y + list.h, animatedScroll, maxScroll());
    }

    private void drawButton(GuiGraphicsExtractor guiGraphics, UiRect rect, String text, boolean hovered, boolean enabled) {
        TranslucentTrayChrome.drawButton(guiGraphics, this.font, rect.x, rect.y, rect.w, rect.h, text, hovered, enabled);
    }

    private void updateLayout() {
        syncDistributionEntry();
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
        // "服务器分发"哨兵卡片由 syncDistributionEntry 统一维护，这里先把状态清掉
        distributionCount = -1;
        currentModel = SERVICE.getCurrentModel();
    }

    /** 找哨兵卡片在列表里的下标；没有则返回 -1。 */
    private int sentinelIndex(String key) {
        for (int i = 0; i < modelCards.size(); i++) {
            if (key.equals(modelCards.get(i).displayName())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 同步列表顶部的两个入口哨兵："指定玩家模型"（恒定）与"服务器模型分发"（有可分发模型时才有）。
     *
     * <p>服务器可能在本界面打开之后才把清单推过来，所以隔一小段时间对一次账：
     * 数量变了就重挂哨兵卡片，N 不会停在旧值上。
     */
    private void syncDistributionEntry() {
        long now = System.currentTimeMillis();
        if (now - lastDistributionSyncMs < 1000L) {
            return;
        }
        lastDistributionSyncMs = now;
        int count = ModelDistributionClient.availableModels().size();
        if (count == distributionCount) {
            return;
        }
        distributionCount = count;

        // 先把旧哨兵摘掉
        int distributionIndex = sentinelIndex(DISTRIBUTION_CARD_KEY);
        if (distributionIndex >= 0) {
            modelCards.remove(distributionIndex);
        }
        int forcedIndex = sentinelIndex(OVERRIDE_CARD_KEY);
        if (forcedIndex >= 0) {
            modelCards.remove(forcedIndex);
        }

        // "本地指定玩家模型"永远在最上面
        modelCards.add(0, new ModelSelectionApplicationService.ModelCard(OVERRIDE_CARD_KEY, false));
        if (count > 0) {
            // 哨兵卡片只有在"没有同名真实模型"时才插进去，免得跟真模型撞脸
            for (ModelSelectionApplicationService.ModelCard card : modelCards) {
                if (DISTRIBUTION_CARD_KEY.equals(card.displayName())) {
                    return;
                }
            }
            modelCards.add(1, new ModelSelectionApplicationService.ModelCard(DISTRIBUTION_CARD_KEY, false));
        }
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
        if (pendingForcedAssign && minecraft.gui.screen() == this) {
            pendingForcedAssign = false;
            minecraft.gui.setScreen(new PlayerModelOverrideScreen(this));
            return;
        }
        if (pendingDistribution && minecraft.gui.screen() == this) {
            pendingDistribution = false;
            minecraft.gui.setScreen(new DistributionNoticeScreen(this,
                    ModelDistributionClient.availableModels(),
                    ModelDistributionClient.serverRateLimitKbPerSec()));
            return;
        }
        if (pendingSettingsModel != null && minecraft.gui.screen() == this) {
            String modelName = pendingSettingsModel;
            pendingSettingsModel = null;
            minecraft.gui.setScreen(new ModelSettingsScreen(modelName, this));
            return;
        }
        if (pendingClose && minecraft.gui.screen() == this) {
            pendingClose = false;
            minecraft.gui.setScreen(null);
        }
    }

    private void closeAfterFailure(Throwable throwable) {
        LOGGER.error("[ModelSelector] Native selector render failed and will close", throwable);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() == this) {
            minecraft.gui.setScreen(null);
        }
    }

    private String buildCardLabel(ModelSelectionApplicationService.ModelCard card) {
        if (OVERRIDE_CARD_KEY.equals(card.displayName())) {
            String label = Component.translatable("gui.mmdskin.override.entry").getString();
            int maxWidth = Math.max(20, layout.listBox.w - 11);
            return this.font.plainSubstrByWidth(label, maxWidth);
        }
        if (DISTRIBUTION_CARD_KEY.equals(card.displayName())) {
            String label = Component.translatable("gui.mmdskin.distribution.entry", distributionCount).getString();
            // 卡片有固定宽度，超长要截断，否则会顶出屏幕
            int maxWidth = Math.max(20, layout.listBox.w - 11);
            return this.font.plainSubstrByWidth(label, maxWidth);
        }
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
