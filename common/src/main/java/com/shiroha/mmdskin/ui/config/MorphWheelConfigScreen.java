/* 文件职责：提供表情轮盘可选/已选双列配置界面，并维护平滑滚动与迁移交互。 */
package com.shiroha.mmdskin.ui.config;

import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Comparator;
import java.util.List;

public class MorphWheelConfigScreen extends Screen {
    private static final int WINDOW_MIN_WIDTH = 600;
    private static final int WINDOW_MIN_HEIGHT = 320;
    private static final int WINDOW_MARGIN = 16;
    private static final float WINDOW_WIDTH_RATIO = 0.68f;
    private static final float WINDOW_HEIGHT_RATIO = 0.70f;
    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 34;
    private static final int COLUMN_GAP = 12;
    private static final int PANEL_PADDING = 10;
    private static final int LIST_PADDING = 5;
    private static final int CARD_HEIGHT = 26;
    private static final int CARD_GAP = 6;
    private static final int BUTTON_GAP = 6;

    private final Screen parent;

    private DualListSelectionState<MorphWheelConfig.MorphEntry> state;
    private float availableTargetScroll;
    private float availableAnimatedScroll;
    private float selectedTargetScroll;
    private float selectedAnimatedScroll;
    private int hoveredAvailableIndex = -1;
    private int hoveredSelectedIndex = -1;
    private ButtonTarget hoveredButton = ButtonTarget.NONE;
    private Layout layout = Layout.empty();

    public MorphWheelConfigScreen(Screen parent) {
        super(Component.translatable("gui.mmdskin.morph_config"));
        this.parent = parent;
        reloadState();
    }

    @Override
    protected void init() {
        super.init();
        updateLayout();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateLayout();
        updateHoverState(mouseX, mouseY);
        updateScrollAnimation();
        renderScreen(guiGraphics);
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

        if (layout.refreshButton.contains(mouseX, mouseY)) {
            rescan();
            return true;
        }
        if (layout.selectAllButton.contains(mouseX, mouseY)) {
            selectAll();
            return true;
        }
        if (layout.clearAllButton.contains(mouseX, mouseY)) {
            clearAll();
            return true;
        }
        if (layout.saveButton.contains(mouseX, mouseY)) {
            saveAndClose();
            return true;
        }
        if (layout.cancelButton.contains(mouseX, mouseY)) {
            this.onClose();
            return true;
        }
        if (layout.availableList.contains(mouseX, mouseY) && hoveredAvailableIndex >= 0) {
            state.moveAvailableToSelected(hoveredAvailableIndex);
            clampScrollOffsets();
            return true;
        }
        if (layout.selectedList.contains(mouseX, mouseY) && hoveredSelectedIndex >= 0) {
            state.moveSelectedToAvailable(hoveredSelectedIndex);
            clampScrollOffsets();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout.availableList.contains(mouseX, mouseY)) {
            availableTargetScroll = clampScroll(availableTargetScroll - (float) scrollY * 16.0f, maxAvailableScroll());
            return true;
        }
        if (layout.selectedList.contains(mouseX, mouseY)) {
            selectedTargetScroll = clampScroll(selectedTargetScroll - (float) scrollY * 16.0f, maxSelectedScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
    public void onClose() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            minecraft.setScreen(parent);
            return;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void reloadState() {
        MorphWheelConfig config = MorphWheelConfig.getInstance();
        state = new DualListSelectionState<>(
                config.getAvailableMorphs(),
                config.getDisplayedMorphs(),
                MorphWheelConfig.MorphEntry::matches,
                Comparator.comparing(entry -> entry.morphName, String.CASE_INSENSITIVE_ORDER)
        );
        availableTargetScroll = 0.0f;
        availableAnimatedScroll = 0.0f;
        selectedTargetScroll = 0.0f;
        selectedAnimatedScroll = 0.0f;
    }

    private void updateLayout() {
        int maxPanelWidth = Math.max(1, this.width - WINDOW_MARGIN * 2);
        int maxPanelHeight = Math.max(1, this.height - WINDOW_MARGIN * 2);
        int minPanelWidth = Math.min(WINDOW_MIN_WIDTH, maxPanelWidth);
        int minPanelHeight = Math.min(WINDOW_MIN_HEIGHT, maxPanelHeight);

        int panelWidth = Mth.clamp(Math.round(this.width * WINDOW_WIDTH_RATIO), minPanelWidth, maxPanelWidth);
        int panelHeight = Mth.clamp(Math.round(this.height * WINDOW_HEIGHT_RATIO), minPanelHeight, maxPanelHeight);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        UiRect panel = new UiRect(panelX, panelY, panelWidth, panelHeight);
        UiRect header = new UiRect(panelX + PANEL_PADDING, panelY + PANEL_PADDING, panelWidth - PANEL_PADDING * 2, HEADER_HEIGHT);
        UiRect footer = new UiRect(panelX + PANEL_PADDING, panelY + panelHeight - FOOTER_HEIGHT - PANEL_PADDING, panelWidth - PANEL_PADDING * 2, FOOTER_HEIGHT);

        int contentTop = header.y + header.h + 8;
        int contentHeight = Math.max(80, footer.y - contentTop - 8);
        int columnWidth = (header.w - COLUMN_GAP) / 2;
        UiRect availableColumn = new UiRect(header.x, contentTop, columnWidth, contentHeight);
        UiRect selectedColumn = new UiRect(header.x + columnWidth + COLUMN_GAP, contentTop, columnWidth, contentHeight);
        UiRect availableList = new UiRect(availableColumn.x + 6, availableColumn.y + 20, availableColumn.w - 12, availableColumn.h - 26);
        UiRect selectedList = new UiRect(selectedColumn.x + 6, selectedColumn.y + 20, selectedColumn.w - 12, selectedColumn.h - 26);

        int buttonWidth = (footer.w - BUTTON_GAP * 4) / 5;
        UiRect refreshButton = new UiRect(footer.x, footer.y, buttonWidth, FOOTER_HEIGHT);
        UiRect selectAllButton = new UiRect(refreshButton.x + buttonWidth + BUTTON_GAP, footer.y, buttonWidth, FOOTER_HEIGHT);
        UiRect clearAllButton = new UiRect(selectAllButton.x + buttonWidth + BUTTON_GAP, footer.y, buttonWidth, FOOTER_HEIGHT);
        UiRect saveButton = new UiRect(clearAllButton.x + buttonWidth + BUTTON_GAP, footer.y, buttonWidth, FOOTER_HEIGHT);
        UiRect cancelButton = new UiRect(saveButton.x + buttonWidth + BUTTON_GAP, footer.y, buttonWidth, FOOTER_HEIGHT);

        layout = new Layout(panel, header, footer, availableColumn, selectedColumn, availableList, selectedList,
                refreshButton, selectAllButton, clearAllButton, saveButton, cancelButton);
        clampScrollOffsets();
    }

    private void updateHoverState(int mouseX, int mouseY) {
        hoveredButton = ButtonTarget.NONE;
        hoveredAvailableIndex = -1;
        hoveredSelectedIndex = -1;

        if (layout.refreshButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.REFRESH;
            return;
        }
        if (layout.selectAllButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.SELECT_ALL;
            return;
        }
        if (layout.clearAllButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.CLEAR_ALL;
            return;
        }
        if (layout.saveButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.SAVE;
            return;
        }
        if (layout.cancelButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.CANCEL;
            return;
        }

        hoveredAvailableIndex = resolveHoveredIndex(mouseX, mouseY, layout.availableList, availableAnimatedScroll, state.availableCount());
        hoveredSelectedIndex = resolveHoveredIndex(mouseX, mouseY, layout.selectedList, selectedAnimatedScroll, state.selectedCount());
    }

    private int resolveHoveredIndex(int mouseX, int mouseY, UiRect listRect, float scroll, int size) {
        if (!listRect.contains(mouseX, mouseY) || size <= 0) {
            return -1;
        }
        float localY = (float) mouseY - (listRect.y + LIST_PADDING) + scroll;
        if (localY < 0.0f) {
            return -1;
        }
        int stride = CARD_HEIGHT + CARD_GAP;
        int index = (int) (localY / stride);
        if (index < 0 || index >= size) {
            return -1;
        }
        return localY - index * stride <= CARD_HEIGHT ? index : -1;
    }

    private void updateScrollAnimation() {
        availableAnimatedScroll = animateScroll(availableAnimatedScroll, availableTargetScroll);
        selectedAnimatedScroll = animateScroll(selectedAnimatedScroll, selectedTargetScroll);
    }

    private float animateScroll(float current, float target) {
        float next = Mth.lerp(0.24f, current, target);
        return Math.abs(next - target) < 0.25f ? target : next;
    }

    private void renderScreen(GuiGraphics guiGraphics) {
        TranslucentTrayChrome.drawOverlay(guiGraphics, this.width, this.height);
        drawPanel(guiGraphics, layout.panel);
        drawHeader(guiGraphics);
        drawColumn(guiGraphics, layout.availableColumn, layout.availableList,
                Component.translatable("gui.mmdskin.morph_config.available").getString(),
                state.availableItems(), hoveredAvailableIndex, availableAnimatedScroll);
        drawColumn(guiGraphics, layout.selectedColumn, layout.selectedList,
                Component.translatable("gui.mmdskin.morph_config.selected").getString(),
                state.selectedItems(), hoveredSelectedIndex, selectedAnimatedScroll);
        drawFooter(guiGraphics);
    }

    private void drawHeader(GuiGraphics guiGraphics) {
        guiGraphics.drawString(this.font, this.title, layout.header.x, layout.header.y + 2, TranslucentTrayChrome.TITLE_TEXT, false);
        Component stats = Component.translatable("gui.mmdskin.config.stats", state.availableCount(), state.selectedCount());
        guiGraphics.drawString(this.font, stats, layout.header.x, layout.header.y + 16, TranslucentTrayChrome.SUBTITLE_TEXT, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.mmdskin.action_wheel.click_select"),
                layout.header.x, layout.header.y + 28, TranslucentTrayChrome.MUTED_TEXT, false);
    }

    private void drawColumn(GuiGraphics guiGraphics,
                            UiRect columnRect,
                            UiRect listRect,
                            String title,
                            List<MorphWheelConfig.MorphEntry> items,
                            int hoveredIndex,
                            float scrollOffset) {
        drawPanel(guiGraphics, columnRect);
        guiGraphics.drawString(this.font, title, columnRect.x + 6, columnRect.y + 6, TranslucentTrayChrome.TITLE_TEXT, false);
        TranslucentTrayChrome.drawSeparator(guiGraphics, columnRect.x + 6, columnRect.y + 16, columnRect.w - 12);
        TranslucentTrayChrome.fillListArea(guiGraphics, listRect.x, listRect.y, listRect.w, listRect.h);

        if (items.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "-", listRect.centerX(), listRect.centerY() - 4, TranslucentTrayChrome.DIM_TEXT);
            return;
        }

        guiGraphics.enableScissor(listRect.x, listRect.y, listRect.x + listRect.w, listRect.y + listRect.h);
        int y = Math.round(listRect.y + LIST_PADDING - scrollOffset);
        for (int i = 0; i < items.size(); i++) {
            if (y + CARD_HEIGHT < listRect.y) {
                y += CARD_HEIGHT + CARD_GAP;
                continue;
            }
            if (y > listRect.y + listRect.h) {
                break;
            }
            drawEntryCard(guiGraphics, listRect, y, items.get(i), i == hoveredIndex);
            y += CARD_HEIGHT + CARD_GAP;
        }
        guiGraphics.disableScissor();
        drawScrollBar(guiGraphics, listRect, items.size(), scrollOffset);
    }

    private void drawEntryCard(GuiGraphics guiGraphics, UiRect listRect, int y,
                               MorphWheelConfig.MorphEntry entry, boolean hovered) {
        int x = listRect.x + 4;
        int width = listRect.w - 12;
        guiGraphics.fill(x, y, x + width, y + CARD_HEIGHT, TranslucentTrayChrome.cardBackground(false, hovered));
        guiGraphics.fill(x, y, x + 2, y + CARD_HEIGHT, hovered ? TranslucentTrayChrome.ACCENT_STRIP_ACTIVE : TranslucentTrayChrome.ACCENT_STRIP);
        guiGraphics.drawString(this.font, buildMorphTitle(entry), x + 8, y + 4, TranslucentTrayChrome.BODY_TEXT, false);
        guiGraphics.drawString(this.font, buildMorphMeta(entry), x + 8, y + 15, TranslucentTrayChrome.DETAIL_TEXT, false);
    }

    private void drawFooter(GuiGraphics guiGraphics) {
        drawButton(guiGraphics, layout.refreshButton, Component.translatable("gui.mmdskin.refresh").getString(), hoveredButton == ButtonTarget.REFRESH);
        drawButton(guiGraphics, layout.selectAllButton, Component.translatable("gui.mmdskin.select_all").getString(), hoveredButton == ButtonTarget.SELECT_ALL);
        drawButton(guiGraphics, layout.clearAllButton, Component.translatable("gui.mmdskin.clear_all").getString(), hoveredButton == ButtonTarget.CLEAR_ALL);
        drawButton(guiGraphics, layout.saveButton, Component.translatable("gui.done").getString(), hoveredButton == ButtonTarget.SAVE);
        drawButton(guiGraphics, layout.cancelButton, Component.translatable("gui.cancel").getString(), hoveredButton == ButtonTarget.CANCEL);
    }

    private void drawButton(GuiGraphics guiGraphics, UiRect rect, String text, boolean hovered) {
        TranslucentTrayChrome.drawButton(guiGraphics, this.font, rect.x, rect.y, rect.w, rect.h, text, hovered, true);
    }

    private void drawPanel(GuiGraphics guiGraphics, UiRect rect) {
        TranslucentTrayChrome.drawPanel(guiGraphics, rect.x, rect.y, rect.w, rect.h);
    }

    private void drawScrollBar(GuiGraphics guiGraphics, UiRect listRect, int itemCount, float scrollOffset) {
        float maxScroll = Math.max(0.0f, itemCount * (CARD_HEIGHT + CARD_GAP) - CARD_GAP + LIST_PADDING * 2.0f - listRect.h);
        if (maxScroll <= 0.0f) {
            return;
        }
        int barX = listRect.x + listRect.w - 3;
        TranslucentTrayChrome.drawScrollbar(guiGraphics, barX, listRect.y, listRect.y + listRect.h, scrollOffset, maxScroll);
    }

    private void rescan() {
        MorphWheelConfig.getInstance().scanAvailableMorphs();
        reloadState();
    }

    private void selectAll() {
        state.moveAllToSelected();
        clampScrollOffsets();
    }

    private void clearAll() {
        state.moveAllToAvailable();
        clampScrollOffsets();
    }

    private void saveAndClose() {
        MorphWheelConfig config = MorphWheelConfig.getInstance();
        config.setDisplayedMorphs(state.selectedItems());
        config.save();
        this.onClose();
    }

    private void clampScrollOffsets() {
        availableTargetScroll = clampScroll(availableTargetScroll, maxAvailableScroll());
        availableAnimatedScroll = clampScroll(availableAnimatedScroll, maxAvailableScroll());
        selectedTargetScroll = clampScroll(selectedTargetScroll, maxSelectedScroll());
        selectedAnimatedScroll = clampScroll(selectedAnimatedScroll, maxSelectedScroll());
    }

    private float maxAvailableScroll() {
        return maxScroll(state.availableCount(), layout.availableList.h);
    }

    private float maxSelectedScroll() {
        return maxScroll(state.selectedCount(), layout.selectedList.h);
    }

    private float maxScroll(int itemCount, int listHeight) {
        float contentHeight = itemCount <= 0 ? 0.0f : LIST_PADDING * 2.0f + itemCount * CARD_HEIGHT + Math.max(0, itemCount - 1) * CARD_GAP;
        return Math.max(0.0f, contentHeight - listHeight);
    }

    private float clampScroll(float scroll, float maxScroll) {
        return Mth.clamp(scroll, 0.0f, maxScroll);
    }

    private static String buildMorphTitle(MorphWheelConfig.MorphEntry entry) {
        return shorten(entry.displayName, 30);
    }

    private static String buildMorphMeta(MorphWheelConfig.MorphEntry entry) {
        String source = entry.source == null ? "?" : entry.source;
        String size = entry.fileSize == null || entry.fileSize.isEmpty() ? "-" : entry.fileSize;
        return shorten(entry.morphName, 32) + " | " + source + " | " + size;
    }

    private static String shorten(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        if (maxChars <= 3) {
            return text.substring(0, Math.max(0, maxChars));
        }
        return text.substring(0, maxChars - 3) + "...";
    }

    private enum ButtonTarget {
        NONE,
        REFRESH,
        SELECT_ALL,
        CLEAR_ALL,
        SAVE,
        CANCEL
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

    private record Layout(UiRect panel, UiRect header, UiRect footer,
                          UiRect availableColumn, UiRect selectedColumn,
                          UiRect availableList, UiRect selectedList,
                          UiRect refreshButton, UiRect selectAllButton, UiRect clearAllButton,
                          UiRect saveButton, UiRect cancelButton) {
        static Layout empty() {
            UiRect empty = UiRect.empty();
            return new Layout(empty, empty, empty, empty, empty, empty, empty, empty, empty, empty, empty, empty);
        }
    }
}
