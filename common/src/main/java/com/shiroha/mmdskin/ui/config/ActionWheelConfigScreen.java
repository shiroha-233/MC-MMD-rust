package com.shiroha.mmdskin.ui.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Comparator;
import java.util.List;

/** 动作轮盘配置界面，提供原生双列可选/已选迁移编辑。 */
public class ActionWheelConfigScreen extends Screen {
    private static final int WINDOW_MIN_WIDTH = 660;
    private static final int WINDOW_MIN_HEIGHT = 380;
    private static final int WINDOW_MARGIN = 16;
    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 34;
    private static final int COLUMN_GAP = 12;
    private static final int PANEL_PADDING = 10;
    private static final int LIST_PADDING = 5;
    private static final int CARD_HEIGHT = 26;
    private static final int CARD_GAP = 6;
    private static final int BUTTON_GAP = 6;

    private final Screen parent;

    private DualListSelectionState<ActionWheelConfig.ActionEntry> state;
    private float availableTargetScroll;
    private float availableAnimatedScroll;
    private float selectedTargetScroll;
    private float selectedAnimatedScroll;
    private int hoveredAvailableIndex = -1;
    private int hoveredSelectedIndex = -1;
    private ButtonTarget hoveredButton = ButtonTarget.NONE;
    private Layout layout = Layout.empty();

    public ActionWheelConfigScreen(Screen parent) {
        super(Component.translatable("gui.mmdskin.action_config"));
        this.parent = parent;
        reloadState();
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
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (!layout.panel.contains(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (layout.availableList.contains(mouseX, mouseY)) {
            availableTargetScroll = clampScroll(availableTargetScroll - (float) delta * 16.0f, maxAvailableScroll());
            return true;
        }
        if (layout.selectedList.contains(mouseX, mouseY)) {
            selectedTargetScroll = clampScroll(selectedTargetScroll - (float) delta * 16.0f, maxSelectedScroll());
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
        ActionWheelConfig config = ActionWheelConfig.getInstance();
        state = new DualListSelectionState<>(
                config.getAvailableActions(),
                config.getDisplayedActions(),
                ActionWheelConfig.ActionEntry::matches,
                Comparator.comparing(entry -> entry.animId, String.CASE_INSENSITIVE_ORDER)
        );
        availableTargetScroll = 0.0f;
        availableAnimatedScroll = 0.0f;
        selectedTargetScroll = 0.0f;
        selectedAnimatedScroll = 0.0f;
    }

    private void updateLayout() {
        int panelWidth = Math.max(WINDOW_MIN_WIDTH, Math.round(this.width * 0.72f));
        int panelHeight = Math.max(WINDOW_MIN_HEIGHT, Math.round(this.height * 0.76f));
        int panelX = (this.width - panelWidth) / 2;
        int panelY = Math.max(WINDOW_MARGIN, (this.height - panelHeight) / 2);

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
        guiGraphics.fill(0, 0, this.width, this.height, 0x30000000);
        drawPanel(guiGraphics, layout.panel);
        drawHeader(guiGraphics);
        drawColumn(guiGraphics, layout.availableColumn, layout.availableList,
                Component.translatable("gui.mmdskin.action_config.available").getString(),
                state.availableItems(), hoveredAvailableIndex, availableAnimatedScroll);
        drawColumn(guiGraphics, layout.selectedColumn, layout.selectedList,
                Component.translatable("gui.mmdskin.action_config.selected").getString(),
                state.selectedItems(), hoveredSelectedIndex, selectedAnimatedScroll);
        drawFooter(guiGraphics);
    }

    private void drawHeader(GuiGraphics guiGraphics) {
        guiGraphics.drawString(this.font, this.title, layout.header.x, layout.header.y + 2, 0xFFF1F5FB, false);
        Component stats = Component.translatable("gui.mmdskin.config.stats", state.availableCount(), state.selectedCount());
        guiGraphics.drawString(this.font, stats, layout.header.x, layout.header.y + 16, 0xC8D5DFEC, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.mmdskin.action_wheel.click_select"),
                layout.header.x, layout.header.y + 28, 0x9FB6C8D9, false);
    }

    private void drawColumn(GuiGraphics guiGraphics,
                            UiRect columnRect,
                            UiRect listRect,
                            String title,
                            List<ActionWheelConfig.ActionEntry> items,
                            int hoveredIndex,
                            float scrollOffset) {
        drawPanel(guiGraphics, columnRect);
        guiGraphics.drawString(this.font, title, columnRect.x + 6, columnRect.y + 6, 0xFFF1F5FB, false);
        guiGraphics.fill(columnRect.x + 6, columnRect.y + 16, columnRect.x + columnRect.w - 6, columnRect.y + 17, 0x22FFFFFF);
        guiGraphics.fill(listRect.x, listRect.y, listRect.x + listRect.w, listRect.y + listRect.h, 0x18000000);

        if (items.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "-", listRect.centerX(), listRect.centerY() - 4, 0xB3C4D6E6);
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
                               ActionWheelConfig.ActionEntry entry, boolean hovered) {
        int x = listRect.x + 4;
        int w = listRect.w - 12;
        guiGraphics.fill(x, y, x + w, y + CARD_HEIGHT, hovered ? 0x42FFFFFF : 0x26000000);
        guiGraphics.fill(x, y, x + 2, y + CARD_HEIGHT, hovered ? 0x90A8D8FF : 0x44A8D8FF);
        guiGraphics.drawString(this.font, buildEntryTitle(entry), x + 8, y + 4, 0xFFE9F1FA, false);
        guiGraphics.drawString(this.font, buildEntryMeta(entry), x + 8, y + 15, 0xB7CBDCEA, false);
    }

    private void drawFooter(GuiGraphics guiGraphics) {
        drawButton(guiGraphics, layout.refreshButton, Component.translatable("gui.mmdskin.refresh").getString(), hoveredButton == ButtonTarget.REFRESH);
        drawButton(guiGraphics, layout.selectAllButton, Component.translatable("gui.mmdskin.select_all").getString(), hoveredButton == ButtonTarget.SELECT_ALL);
        drawButton(guiGraphics, layout.clearAllButton, Component.translatable("gui.mmdskin.clear_all").getString(), hoveredButton == ButtonTarget.CLEAR_ALL);
        drawButton(guiGraphics, layout.saveButton, Component.translatable("gui.done").getString(), hoveredButton == ButtonTarget.SAVE);
        drawButton(guiGraphics, layout.cancelButton, Component.translatable("gui.cancel").getString(), hoveredButton == ButtonTarget.CANCEL);
    }

    private void drawButton(GuiGraphics guiGraphics, UiRect rect, String text, boolean hovered) {
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, hovered ? 0x4AFFFFFF : 0x26000000);
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + 1, 0x20FFFFFF);
        guiGraphics.drawCenteredString(this.font, text, rect.centerX(), rect.y + 12, 0xFFF1F6FD);
    }

    private void drawPanel(GuiGraphics guiGraphics, UiRect rect) {
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, 0x2A000000);
        guiGraphics.fill(rect.x + 1, rect.y + 1, rect.x + rect.w - 1, rect.y + rect.h - 1, 0x20000000);
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + 1, 0x24FFFFFF);
        guiGraphics.fill(rect.x, rect.y + rect.h - 1, rect.x + rect.w, rect.y + rect.h, 0x18000000);
    }

    private void drawScrollBar(GuiGraphics guiGraphics, UiRect listRect, int itemCount, float scrollOffset) {
        float maxScroll = Math.max(0.0f, itemCount * (CARD_HEIGHT + CARD_GAP) - CARD_GAP + LIST_PADDING * 2.0f - listRect.h);
        if (maxScroll <= 0.0f) {
            return;
        }
        int barX = listRect.x + listRect.w - 3;
        guiGraphics.fill(barX, listRect.y, barX + 2, listRect.y + listRect.h, 0x20FFFFFF);
        float visibleRatio = (float) listRect.h / (float) Math.max(listRect.h, itemCount * (CARD_HEIGHT + CARD_GAP));
        int thumbHeight = Math.max(12, Math.round(listRect.h * visibleRatio));
        int travel = Math.max(1, listRect.h - thumbHeight);
        int thumbY = listRect.y + Math.round((scrollOffset / maxScroll) * travel);
        guiGraphics.fill(barX, thumbY, barX + 2, thumbY + thumbHeight, 0x88A8D8FF);
    }

    private void rescan() {
        ActionWheelConfig.getInstance().rescan();
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
        ActionWheelConfig config = ActionWheelConfig.getInstance();
        config.setDisplayedActions(state.selectedItems());
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

    private static String buildEntryTitle(ActionWheelConfig.ActionEntry entry) {
        return shorten(entry.name, 30);
    }

    private static String buildEntryMeta(ActionWheelConfig.ActionEntry entry) {
        String source = entry.source == null ? "?" : entry.source;
        String size = entry.fileSize == null || entry.fileSize.isEmpty() ? "-" : entry.fileSize;
        return shorten(entry.animId, 32) + " | " + source + " | " + size;
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
