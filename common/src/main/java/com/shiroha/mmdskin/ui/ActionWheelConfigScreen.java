package com.shiroha.mmdskin.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 动作轮盘配置界面
 * 双列布局：左侧显示可用动画，右侧显示已选择的动画
 * 支持自选哪些动画加入轮盘
 */
public class ActionWheelConfigScreen extends Screen {
    private static final Logger logger = LogManager.getLogger();
    private final Screen parent;
    
    // 布局常量
    private static final int PANEL_WIDTH = 200;
    private static final int ITEM_HEIGHT = 36;
    private static final int ITEM_SPACING = 4;
    private static final int HEADER_HEIGHT = 50;
    private static final int FOOTER_HEIGHT = 45;
    private static final int PANEL_PADDING = 10;
    
    // 颜色常量
    private static final int COLOR_PANEL_BG = 0x80000000;
    private static final int COLOR_ITEM_BG = 0x60333333;
    private static final int COLOR_ITEM_HOVER = 0x80555555;
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFF;
    private static final int COLOR_TEXT_SECONDARY = 0xAAAAAA;
    private static final int COLOR_SOURCE_DEFAULT = 0x88AAFF;
    private static final int COLOR_SOURCE_CUSTOM = 0x88FF88;
    private static final int COLOR_SOURCE_MODEL = 0xFFAA88;
    
    // 数据
    private List<ActionWheelConfig.ActionEntry> availableActions;
    private List<ActionWheelConfig.ActionEntry> selectedActions;
    
    // 滚动
    private int leftScrollOffset = 0;
    private int rightScrollOffset = 0;
    private int leftMaxScroll = 0;
    private int rightMaxScroll = 0;
    
    // 悬停
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
        
        // 从可用列表中移除已选择的
        availableActions.removeIf(available -> 
            selectedActions.stream().anyMatch(selected -> 
                selected.animId.equals(available.animId)
            )
        );
    }

    @Override
    protected void init() {
        super.init();
        
        int centerX = this.width / 2;
        int buttonY = this.height - FOOTER_HEIGHT + 12;
        
        // 计算滚动范围
        int panelHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT;
        leftMaxScroll = Math.max(0, availableActions.size() * (ITEM_HEIGHT + ITEM_SPACING) - panelHeight + PANEL_PADDING * 2);
        rightMaxScroll = Math.max(0, selectedActions.size() * (ITEM_HEIGHT + ITEM_SPACING) - panelHeight + PANEL_PADDING * 2);
        
        // 底部按钮
        this.addRenderableWidget(Button.builder(Component.literal("刷新"), btn -> rescan())
            .bounds(centerX - 155, buttonY, 70, 20).build());
        
        this.addRenderableWidget(Button.builder(Component.literal("全选"), btn -> selectAll())
            .bounds(centerX - 80, buttonY, 50, 20).build());
        
        this.addRenderableWidget(Button.builder(Component.literal("清空"), btn -> clearAll())
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
        logger.info("动画列表已刷新");
    }
    
    private void selectAll() {
        selectedActions.addAll(availableActions);
        availableActions.clear();
        updateScrollBounds();
    }
    
    private void clearAll() {
        availableActions.addAll(selectedActions);
        selectedActions.clear();
        // 重新排序
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
        logger.info("已保存 {} 个动画到轮盘", selectedActions.size());
        this.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        
        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, COLOR_TEXT_PRIMARY);
        
        // 副标题
        String subtitle = String.format("可用: %d  |  已选: %d", availableActions.size(), selectedActions.size());
        guiGraphics.drawCenteredString(this.font, Component.literal(subtitle), this.width / 2, 28, COLOR_TEXT_SECONDARY);
        
        // 左侧面板 - 可用动画
        int leftPanelX = this.width / 2 - PANEL_WIDTH - 30;
        renderPanel(guiGraphics, leftPanelX, "可用动画", availableActions, leftScrollOffset, mouseX, mouseY, true);
        
        // 右侧面板 - 已选动画
        int rightPanelX = this.width / 2 + 30;
        renderPanel(guiGraphics, rightPanelX, "轮盘动画", selectedActions, rightScrollOffset, mouseX, mouseY, false);
        
        // 中间箭头提示
        int arrowY = this.height / 2;
        guiGraphics.drawCenteredString(this.font, Component.literal("←→"), this.width / 2, arrowY - 10, COLOR_TEXT_SECONDARY);
        guiGraphics.drawCenteredString(this.font, Component.literal("点击添加/移除"), this.width / 2, arrowY + 5, 0x888888);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void renderPanel(GuiGraphics guiGraphics, int x, String title, 
                             List<ActionWheelConfig.ActionEntry> items, int scrollOffset,
                             int mouseX, int mouseY, boolean isLeft) {
        int y = HEADER_HEIGHT;
        int panelHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT;
        
        // 面板背景
        guiGraphics.fill(x, y, x + PANEL_WIDTH, y + panelHeight, COLOR_PANEL_BG);
        
        // 面板标题
        guiGraphics.drawCenteredString(this.font, Component.literal(title), 
            x + PANEL_WIDTH / 2, y + 5, COLOR_TEXT_PRIMARY);
        
        // 裁剪区域
        int listY = y + 20;
        int listHeight = panelHeight - 25;
        guiGraphics.enableScissor(x, listY, x + PANEL_WIDTH, listY + listHeight);
        
        // 重置悬停状态
        if (isLeft) hoveredLeftIndex = -1;
        else hoveredRightIndex = -1;
        
        // 渲染项目
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
        
        // 滚动条
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
        // 背景
        int bgColor = isHovered ? COLOR_ITEM_HOVER : COLOR_ITEM_BG;
        guiGraphics.fill(x, y, x + width, y + ITEM_HEIGHT, bgColor);
        
        // 名称
        String name = entry.name;
        if (name.length() > 22) name = name.substring(0, 19) + "...";
        guiGraphics.drawString(this.font, name, x + 5, y + 4, COLOR_TEXT_PRIMARY);
        
        // 来源和大小
        int sourceColor = getSourceColor(entry.source);
        String sourceText = "[" + getSourceShort(entry.source) + "]";
        guiGraphics.drawString(this.font, sourceText, x + 5, y + 18, sourceColor);
        
        if (entry.fileSize != null && !entry.fileSize.isEmpty()) {
            guiGraphics.drawString(this.font, entry.fileSize, x + 45, y + 18, COLOR_TEXT_SECONDARY);
        }
        
        // animId（较小的字体颜色）
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
            case "DEFAULT": return "默认";
            case "CUSTOM": return "自定义";
            case "MODEL": return "模型";
            default: return source;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 左侧面板点击 -> 添加到右侧
            if (hoveredLeftIndex >= 0 && hoveredLeftIndex < availableActions.size()) {
                ActionWheelConfig.ActionEntry entry = availableActions.remove(hoveredLeftIndex);
                selectedActions.add(entry);
                updateScrollBounds();
                return true;
            }
            
            // 右侧面板点击 -> 移回左侧
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int leftPanelX = this.width / 2 - PANEL_WIDTH - 30;
        int rightPanelX = this.width / 2 + 30;
        
        // 左侧面板滚动
        if (mouseX >= leftPanelX && mouseX <= leftPanelX + PANEL_WIDTH) {
            leftScrollOffset = Math.max(0, Math.min(leftMaxScroll, leftScrollOffset - (int)(scrollY * 25)));
            return true;
        }
        
        // 右侧面板滚动
        if (mouseX >= rightPanelX && mouseX <= rightPanelX + PANEL_WIDTH) {
            rightScrollOffset = Math.max(0, Math.min(rightMaxScroll, rightScrollOffset - (int)(scrollY * 25)));
            return true;
        }
        
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
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
