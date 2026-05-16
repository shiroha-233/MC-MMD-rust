/* 文件职责：提供女仆模型选择界面与交互状态管理。 */
package com.shiroha.mmdskin.maid;

import com.shiroha.mmdskin.maid.service.DefaultMaidModelSelectionService;
import com.shiroha.mmdskin.maid.service.MaidModelSelectionService;
import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MaidModelSelectorScreen extends Screen {
    private static final int WINDOW_MARGIN = 10;
    private static final int MIN_WINDOW_WIDTH = 228;
    private static final int MAX_WINDOW_WIDTH = 332;
    private static final int MIN_WINDOW_HEIGHT = 232;
    private static final int HEADER_HEIGHT = 40;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_GAP = 4;
    private static final int LIST_PADDING = 5;
    private static final int CARD_HEIGHT = 16;
    private static final int CARD_GAP = 4;

    private final UUID maidUUID;
    private final int maidEntityId;
    private final String maidName;
    private final MaidModelSelectionService maidModelSelectionService;
    private final List<String> modelCards = new ArrayList<>();

    private String currentModel;
    private boolean pendingClose;
    private float targetScroll;
    private float animatedScroll;
    private int hoveredCard = -1;
    private ButtonTarget hoveredButton = ButtonTarget.NONE;
    private Layout layout = Layout.empty();

    private enum ButtonTarget {
        NONE,
        DONE,
        REFRESH
    }

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
        reloadModelCards();
    }

    @Override
    protected void init() {
        super.init();
        updateLayout();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateLayout();
        updateHoverState(mouseX, mouseY);
        updateScrollAnimation();
        renderScreen(guiGraphics);
        flushPendingActions(Minecraft.getInstance());
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
            reloadModelCards();
            targetScroll = 0.0f;
            animatedScroll = 0.0f;
            return true;
        }
        if (layout.listBox.contains(mouseX, mouseY) && hoveredCard >= 0 && hoveredCard < modelCards.size()) {
            selectModel(modelCards.get(hoveredCard));
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
        drawPanel(guiGraphics, layout.panel);
        drawHeader(guiGraphics);
        drawButtons(guiGraphics);
        drawModelList(guiGraphics);
    }

    private void drawHeader(GuiGraphics guiGraphics) {
        guiGraphics.drawString(this.font, this.title, layout.header.x, layout.header.y + 1, TranslucentTrayChrome.TITLE_TEXT, false);
        guiGraphics.drawString(this.font, shorten(maidName, 24), layout.header.x, layout.header.y + 11, TranslucentTrayChrome.SUBTITLE_TEXT, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.mmdskin.model_selector.stats",
                        Math.max(0, modelCards.size() - 1),
                        shorten(currentModel, 14)),
                layout.header.x, layout.header.y + 21, TranslucentTrayChrome.DETAIL_TEXT, false);
    }

    private void drawButtons(GuiGraphics guiGraphics) {
        drawButton(guiGraphics, layout.doneButton, Component.translatable("gui.done").getString(), hoveredButton == ButtonTarget.DONE);
        drawButton(guiGraphics, layout.refreshButton, Component.translatable("gui.mmdskin.refresh").getString(), hoveredButton == ButtonTarget.REFRESH);
    }

    private void drawModelList(GuiGraphics guiGraphics) {
        UiRect list = layout.listBox;
        TranslucentTrayChrome.fillListArea(guiGraphics, list.x, list.y, list.w, list.h);
        if (modelCards.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "No models", list.centerX(), list.centerY() - 4, TranslucentTrayChrome.BODY_TEXT);
            return;
        }

        guiGraphics.enableScissor(list.x, list.y, list.x + list.w, list.y + list.h);
        int y = Math.round(list.y + LIST_PADDING - animatedScroll);
        for (int i = 0; i < modelCards.size(); i++) {
            if (y + CARD_HEIGHT < list.y) {
                y += CARD_HEIGHT + CARD_GAP;
                continue;
            }
            if (y > list.y + list.h) {
                break;
            }
            drawModelCard(guiGraphics, list, y, modelCards.get(i), i == hoveredCard);
            y += CARD_HEIGHT + CARD_GAP;
        }
        guiGraphics.disableScissor();
        drawScrollBar(guiGraphics, list);
    }

    private void drawModelCard(GuiGraphics guiGraphics, UiRect list, int y, String modelName, boolean hovered) {
        boolean selected = modelName.equals(currentModel);
        int bg = TranslucentTrayChrome.cardBackground(selected, hovered);
        guiGraphics.fill(list.x + 4, y, list.x + list.w - 4, y + CARD_HEIGHT, bg);
        guiGraphics.fill(list.x + 4, y, list.x + 6, y + CARD_HEIGHT, selected ? TranslucentTrayChrome.ACCENT_STRIP_ACTIVE : TranslucentTrayChrome.ACCENT_STRIP);
        guiGraphics.drawString(this.font, shorten(modelName, 24), list.x + 10, y + 4, TranslucentTrayChrome.BODY_TEXT, false);
    }

    private void drawScrollBar(GuiGraphics guiGraphics, UiRect list) {
        float maxScroll = maxScroll();
        if (maxScroll <= 0.0f) {
            return;
        }
        int barX = list.x + list.w - 3;
        guiGraphics.fill(barX, list.y, barX + 2, list.y + list.h, TranslucentTrayChrome.SCROLL_TRACK);
        float contentHeight = LIST_PADDING * 2.0f + modelCards.size() * CARD_HEIGHT + Math.max(0, modelCards.size() - 1) * CARD_GAP;
        float visibleRatio = list.h / Math.max((float) list.h, contentHeight);
        int thumbHeight = Math.max(12, Math.round(list.h * visibleRatio));
        int travel = Math.max(1, list.h - thumbHeight);
        int thumbY = list.y + Math.round((animatedScroll / maxScroll) * travel);
        guiGraphics.fill(barX, thumbY, barX + 2, thumbY + thumbHeight, TranslucentTrayChrome.SCROLL_THUMB);
    }

    private void drawButton(GuiGraphics guiGraphics, UiRect rect, String text, boolean hovered) {
        TranslucentTrayChrome.drawButton(guiGraphics, this.font, rect.x, rect.y, rect.w, rect.h, text, hovered, true);
    }

    private void drawPanel(GuiGraphics guiGraphics, UiRect rect) {
        TranslucentTrayChrome.drawPanel(guiGraphics, rect.x, rect.y, rect.w, rect.h);
    }

    private void updateLayout() {
        int panelWidth = Mth.clamp(Math.round(this.width * 0.18f), MIN_WINDOW_WIDTH, MAX_WINDOW_WIDTH);
        int panelHeight = Math.max(MIN_WINDOW_HEIGHT, this.height - WINDOW_MARGIN * 2);
        int panelX = this.width - panelWidth - WINDOW_MARGIN;
        int panelY = WINDOW_MARGIN;

        UiRect panel = new UiRect(panelX, panelY, panelWidth, panelHeight);
        UiRect header = new UiRect(panelX + 8, panelY + 6, panelWidth - 16, HEADER_HEIGHT);

        int buttonY = panel.y + panel.h - BUTTON_HEIGHT - 6;
        int buttonWidth = (header.w - BUTTON_GAP) / 2;
        UiRect doneButton = new UiRect(header.x, buttonY, buttonWidth, BUTTON_HEIGHT);
        UiRect refreshButton = new UiRect(header.x + buttonWidth + BUTTON_GAP, buttonY, buttonWidth, BUTTON_HEIGHT);

        int listY = header.y + header.h + 4;
        int listHeight = Math.max(72, buttonY - listY - 6);
        UiRect listBox = new UiRect(header.x, listY, header.w, listHeight);
        layout = new Layout(panel, header, listBox, doneButton, refreshButton);

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
        float contentHeight = modelCards.isEmpty()
                ? 0.0f
                : LIST_PADDING * 2.0f + modelCards.size() * CARD_HEIGHT + Math.max(0, modelCards.size() - 1) * CARD_GAP;
        return Math.max(0.0f, contentHeight - layout.listBox.h);
    }

    private void reloadModelCards() {
        modelCards.clear();
        modelCards.addAll(maidModelSelectionService.loadAvailableModels());
        currentModel = maidModelSelectionService.getCurrentModel(maidUUID);
    }

    private void selectModel(String modelName) {
        currentModel = modelName;
        maidModelSelectionService.selectModel(maidUUID, maidEntityId, modelName);
    }

    private void flushPendingActions(Minecraft minecraft) {
        if (pendingClose && minecraft.screen == this) {
            pendingClose = false;
            minecraft.setScreen(null);
        }
    }

    private static String shorten(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        if (maxChars <= 3) {
            return value.substring(0, Math.max(0, maxChars));
        }
        return value.substring(0, maxChars - 3) + "...";
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

    private record Layout(UiRect panel, UiRect header, UiRect listBox, UiRect doneButton, UiRect refreshButton) {
        static Layout empty() {
            UiRect empty = UiRect.empty();
            return new Layout(empty, empty, empty, empty, empty);
        }
    }
}
