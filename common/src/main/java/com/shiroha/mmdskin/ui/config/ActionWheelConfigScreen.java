package com.shiroha.mmdskin.ui.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/** 动作轮盘配置界面。 */
public class ActionWheelConfigScreen extends Screen {
    private static final Logger logger = LogManager.getLogger();
    private final Screen parent;

    private static final int PANEL_WIDTH = 200;
    private static final int ITEM_HEIGHT = 36;
    private static final int ITEM_SPACING = 4;
    private static final int HEADER_HEIGHT = 50;
    private static final int FOOTER_HEIGHT = 45;
    private static final int PANEL_PADDING = 10;

    private static final int COLOR_PANEL_BG = 0x80000000;
    private static final int COLOR_ITEM_BG = 0x60333333;
    private static final int COLOR_ITEM_HOVER = 0x80555555;
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFF;
    private static final int COLOR_TEXT_SECONDARY = 0xAAAAAA;
    private static final int COLOR_SOURCE_DEFAULT = 0x88AAFF;
    private static final int COLOR_SOURCE_CUSTOM = 0x88FF88;
    private static final int COLOR_SOURCE_MODEL = 0xFFAA88;

    private List<ActionWheelConfig.ActionEntry> availableActions;
    private List<ActionWheelConfig.ActionEntry> selectedActions;

    private int leftScrollOffset = 0;
    private int rightScrollOffset = 0;
    private int leftMaxScroll = 0;
    private int rightMaxScroll = 0;

    private int hoveredLeftIndex = -1;
    private int hoveredRightIndex = -1;

    public ActionWheelConfigScreen(Screen parent) {
        super(Component.translatable("gui.mmdskin.action_config"));
        this.parent = parent;
        loadData();
    }

    private void loadData() {
        ActionWheelConfig config = ActionWheelConfig.getInstance();
        this.availableActions = new ArrayList<>(config.getAvailableActions());
        this.selectedActions = new ArrayList<>(config.getDisplayedActions());

        availableActions.removeIf(available ->
            selectedActions.stream().anyMatch(selected ->
                selected.matches(available)
            )
        );
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int buttonY = this.height - FOOTER_HEIGHT + 12;

        int panelHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT;
        leftMaxScroll = Math.max(0, availableActions.size() * (ITEM_HEIGHT + ITEM_SPACING) - panelHeight + PANEL_PADDING * 2);
        rightMaxScroll = Math.max(0, selectedActions.size() * (ITEM_HEIGHT + ITEM_SPACING) - panelHeight + PANEL_PADDING * 2);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.refresh"), btn -> rescan())
            .bounds(centerX - 155, buttonY, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.select_all"), btn -> selectAll())
            .bounds(centerX - 80, buttonY, 50, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.clear_all"), btn -> clearAll())
            .bounds(centerX - 25, buttonY, 50, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> saveAndClose())
            .bounds(centerX + 30, buttonY, 60, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> this.onClose())
            .bounds(centerX + 95, buttonY, 60, 20).build());
    }

    private void rescan() {
        ActionWheelConfig.getInstance().rescan();
        loadData();
        this.clearWidgets();
        this.init();
    }

    private void selectAll() {
        selectedActions.addAll(availableActions);
        availableActions.clear();
        updateScrollBounds();
    }

    private void clearAll() {
        availableActions.addAll(selectedActions);
        selectedActions.clear();

        availableActions.sort((a, b) -> a.animId.compareToIgnoreCase(b.animId));
        updateScrollBounds();
    }

    private void updateScrollBounds() {
        int panelHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT;
        leftMaxScroll = Math.max(0, availableActions.size() * (ITEM_HEIGHT + ITEM_SPACING) - panelHeight + PANEL_PADDING * 2);
        rightMaxScroll = Math.max(0, selectedActions.size() * (ITEM_HEIGHT + ITEM_SPACING) - panelHeight + PANEL_PADDING * 2);
        leftScrollOffset = Math.min(leftScrollOffset, leftMaxScroll);
        rightScrollOffset = Math.min(rightScrollOffset, rightMaxScroll);
    }

    private void saveAndClose() {
        ActionWheelConfig config = ActionWheelConfig.getInstance();
        config.setDisplayedActions(new ArrayList<>(selectedActions));
        config.save();
        this.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, COLOR_TEXT_PRIMARY);

        Component subtitle = Component.translatable("gui.mmdskin.config.stats", availableActions.size(), selectedActions.size());
        guiGraphics.drawCenteredString(this.font, subtitle, this.width / 2, 28, COLOR_TEXT_SECONDARY);

        int leftPanelX = this.width / 2 - PANEL_WIDTH - 30;
        renderPanel(guiGraphics, leftPanelX, Component.translatable("gui.mmdskin.action_config.available").getString(), availableActions, leftScrollOffset, mouseX, mouseY, true);

        int rightPanelX = this.width / 2 + 30;
        renderPanel(guiGraphics, rightPanelX, Component.translatable("gui.mmdskin.action_config.selected").getString(), selectedActions, rightScrollOffset, mouseX, mouseY, false);

        int arrowY = this.height / 2;
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.mmdskin.config.click_add"), this.width / 2, arrowY - 10, COLOR_TEXT_SECONDARY);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.mmdskin.config.click_remove"), this.width / 2, arrowY + 5, 0x888888);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderPanel(GuiGraphics guiGraphics, int x, String title,
                             List<ActionWheelConfig.ActionEntry> items, int scrollOffset,
                             int mouseX, int mouseY, boolean isLeft) {
        int y = HEADER_HEIGHT;
        int panelHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT;

        guiGraphics.fill(x, y, x + PANEL_WIDTH, y + panelHeight, COLOR_PANEL_BG);

        guiGraphics.drawCenteredString(this.font, Component.literal(title),
            x + PANEL_WIDTH / 2, y + 5, COLOR_TEXT_PRIMARY);

        int listY = y + 20;
        int listHeight = panelHeight - 25;
        guiGraphics.enableScissor(x, listY, x + PANEL_WIDTH, listY + listHeight);

        if (isLeft) hoveredLeftIndex = -1;
        else hoveredRightIndex = -1;

        for (int i = 0; i < items.size(); i++) {
            int itemY = listY + PANEL_PADDING + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;

            if (itemY + ITEM_HEIGHT < listY || itemY > listY + listHeight) continue;

            ActionWheelConfig.ActionEntry entry = items.get(i);
            boolean isHovered = mouseX >= x + 5 && mouseX <= x + PANEL_WIDTH - 5
                             && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT
                             && mouseY >= listY && mouseY <= listY + listHeight;

            if (isHovered) {
                if (isLeft) hoveredLeftIndex = i;
                else hoveredRightIndex = i;
            }

            renderItem(guiGraphics, x + 5, itemY, PANEL_WIDTH - 10, entry, isHovered);
        }

        guiGraphics.disableScissor();

        int maxScroll = isLeft ? leftMaxScroll : rightMaxScroll;
        if (maxScroll > 0) {
            int scrollbarX = x + PANEL_WIDTH - 5;
            int scrollbarHeight = listHeight;
            int thumbHeight = Math.max(20, scrollbarHeight * scrollbarHeight / (scrollbarHeight + maxScroll));
            int thumbY = listY + (int)((scrollbarHeight - thumbHeight) * ((float)scrollOffset / maxScroll));

            guiGraphics.fill(scrollbarX, listY, scrollbarX + 3, listY + scrollbarHeight, 0x40FFFFFF);
            guiGraphics.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbHeight, 0xAAFFFFFF);
        }
    }

    private void renderItem(GuiGraphics guiGraphics, int x, int y, int width,
                            ActionWheelConfig.ActionEntry entry, boolean isHovered) {

        int bgColor = isHovered ? COLOR_ITEM_HOVER : COLOR_ITEM_BG;
        guiGraphics.fill(x, y, x + width, y + ITEM_HEIGHT, bgColor);

        String name = entry.name;
        int maxWidth = width - 10;
        if (this.font.width(name) > maxWidth) {
            while (this.font.width(name + "...") > maxWidth && name.length() > 1) {
                name = name.substring(0, name.length() - 1);
            }
            name = name + "...";
        }
        guiGraphics.drawString(this.font, name, x + 5, y + 4, COLOR_TEXT_PRIMARY);

        int sourceColor = getSourceColor(entry.source);
        String sourceText = "[" + getSourceShort(entry.source) + "]";
        guiGraphics.drawString(this.font, sourceText, x + 5, y + 18, sourceColor);

        if (entry.fileSize != null && !entry.fileSize.isEmpty()) {
            guiGraphics.drawString(this.font, entry.fileSize, x + 45, y + 18, COLOR_TEXT_SECONDARY);
        }

        String animId = entry.animId;
        if (animId.length() > 28) animId = animId.substring(0, 25) + "...";
        guiGraphics.drawString(this.font, animId, x + 5, y + ITEM_HEIGHT - 10, 0x666666);
    }

    private int getSourceColor(String source) {
        if (source == null) return COLOR_TEXT_SECONDARY;
        switch (source) {
            case "DEFAULT": return COLOR_SOURCE_DEFAULT;
            case "CUSTOM": return COLOR_SOURCE_CUSTOM;
            case "MODEL": return COLOR_SOURCE_MODEL;
            default: return COLOR_TEXT_SECONDARY;
        }
    }

    private String getSourceShort(String source) {
        if (source == null) return "?";
        switch (source) {
            case "DEFAULT": return Component.translatable("gui.mmdskin.source.default").getString();
            case "CUSTOM": return Component.translatable("gui.mmdskin.source.custom").getString();
            case "MODEL": return Component.translatable("gui.mmdskin.source.model").getString();
            default: return source;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {

            if (hoveredLeftIndex >= 0 && hoveredLeftIndex < availableActions.size()) {
                ActionWheelConfig.ActionEntry entry = availableActions.remove(hoveredLeftIndex);
                selectedActions.add(entry);
                updateScrollBounds();
                return true;
            }

            if (hoveredRightIndex >= 0 && hoveredRightIndex < selectedActions.size()) {
                ActionWheelConfig.ActionEntry entry = selectedActions.remove(hoveredRightIndex);
                availableActions.add(entry);
                availableActions.sort((a, b) -> a.animId.compareToIgnoreCase(b.animId));
                updateScrollBounds();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int leftPanelX = this.width / 2 - PANEL_WIDTH - 30;
        int rightPanelX = this.width / 2 + 30;

        if (mouseX >= leftPanelX && mouseX <= leftPanelX + PANEL_WIDTH) {
            leftScrollOffset = Math.max(0, Math.min(leftMaxScroll, leftScrollOffset - (int)(delta * 25)));
            return true;
        }

        if (mouseX >= rightPanelX && mouseX <= rightPanelX + PANEL_WIDTH) {
            rightScrollOffset = Math.max(0, Math.min(rightMaxScroll, rightScrollOffset - (int)(delta * 25)));
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
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
