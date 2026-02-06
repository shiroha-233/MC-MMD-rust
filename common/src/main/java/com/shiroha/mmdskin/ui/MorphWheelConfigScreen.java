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
 * 表情轮盘配置界面
 * 双列布局：左侧显示可用表情，右侧显示已选择的表情
 * 支持自选哪些表情加入轮盘
 */
public class MorphWheelConfigScreen extends Screen {
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
    private List<MorphWheelConfig.MorphEntry> availableMorphs;
    private List<MorphWheelConfig.MorphEntry> selectedMorphs;
    
    // 滚动
    private int leftScrollOffset = 0;
    private int rightScrollOffset = 0;
    private int leftMaxScroll = 0;
    private int rightMaxScroll = 0;
    
    // 悬停
    private int hoveredLeftIndex = -1;
    private int hoveredRightIndex = -1;

    public MorphWheelConfigScreen(Screen parent) {
        super(Component.translatable("gui.mmdskin.morph_config"));
        this.parent = parent;
        loadData();
    }
    
    private void loadData() {
        MorphWheelConfig config = MorphWheelConfig.getInstance();
        this.availableMorphs = new ArrayList<>(config.getAvailableMorphs());
        this.selectedMorphs = new ArrayList<>(config.getDisplayedMorphs());
        
        // 从可用列表中移除已选择的
        availableMorphs.removeIf(available -> 
            selectedMorphs.stream().anyMatch(selected -> 
                selected.morphName.equals(available.morphName)
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
        leftMaxScroll = Math.max(0, availableMorphs.size() * (ITEM_HEIGHT + ITEM_SPACING) - panelHeight + PANEL_PADDING * 2);
        rightMaxScroll = Math.max(0, selectedMorphs.size() * (ITEM_HEIGHT + ITEM_SPACING) - panelHeight + PANEL_PADDING * 2);
        
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
        MorphWheelConfig.getInstance().scanAvailableMorphs();
        loadData();
        this.clearWidgets();
        this.init();
        logger.info("表情列表已刷新");
    }
    
    private void selectAll() {
        selectedMorphs.addAll(availableMorphs);
        availableMorphs.clear();
        updateScrollBounds();
    }
    
    private void clearAll() {
        availableMorphs.addAll(selectedMorphs);
        selectedMorphs.clear();
        // 重新排序
        availableMorphs.sort((a, b) -> a.morphName.compareToIgnoreCase(b.morphName));
        updateScrollBounds();
    }
    
    private void updateScrollBounds() {
        int panelHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT;
        leftMaxScroll = Math.max(0, availableMorphs.size() * (ITEM_HEIGHT + ITEM_SPACING) - panelHeight + PANEL_PADDING * 2);
        rightMaxScroll = Math.max(0, selectedMorphs.size() * (ITEM_HEIGHT + ITEM_SPACING) - panelHeight + PANEL_PADDING * 2);
        leftScrollOffset = Math.min(leftScrollOffset, leftMaxScroll);
        rightScrollOffset = Math.min(rightScrollOffset, rightMaxScroll);
    }
    
    private void saveAndClose() {
        MorphWheelConfig config = MorphWheelConfig.getInstance();
        config.getDisplayedMorphs().clear();
        config.getDisplayedMorphs().addAll(selectedMorphs);
        config.save();
        logger.info("已保存 {} 个表情到轮盘", selectedMorphs.size());
        this.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        
        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, COLOR_TEXT_PRIMARY);
        
        // 副标题
        String subtitle = String.format("可用: %d  |  已选: %d", availableMorphs.size(), selectedMorphs.size());
        guiGraphics.drawCenteredString(this.font, Component.literal(subtitle), this.width / 2, 28, COLOR_TEXT_SECONDARY);
        
        int panelTop = HEADER_HEIGHT;
        int panelBottom = this.height - FOOTER_HEIGHT;
        
        int leftPanelX = this.width / 2 - PANEL_WIDTH - 15;
        int rightPanelX = this.width / 2 + 15;
        
        // 绘制左侧面板背景
        guiGraphics.fill(leftPanelX, panelTop, leftPanelX + PANEL_WIDTH, panelBottom, COLOR_PANEL_BG);
        guiGraphics.drawString(this.font, "可用表情", leftPanelX + 5, panelTop - 12, COLOR_TEXT_SECONDARY);
        
        // 绘制右侧面板背景
        guiGraphics.fill(rightPanelX, panelTop, rightPanelX + PANEL_WIDTH, panelBottom, COLOR_PANEL_BG);
        guiGraphics.drawString(this.font, "已选表情", rightPanelX + 5, panelTop - 12, COLOR_TEXT_SECONDARY);
        
        // 启用裁剪
        guiGraphics.enableScissor(leftPanelX, panelTop, leftPanelX + PANEL_WIDTH, panelBottom);
        
        // 更新悬停状态
        hoveredLeftIndex = -1;
        hoveredRightIndex = -1;
        
        // 绘制左侧列表
        int y = panelTop + PANEL_PADDING - leftScrollOffset;
        for (int i = 0; i < availableMorphs.size(); i++) {
            if (y + ITEM_HEIGHT > panelTop && y < panelBottom) {
                MorphWheelConfig.MorphEntry entry = availableMorphs.get(i);
                boolean hovered = mouseX >= leftPanelX && mouseX < leftPanelX + PANEL_WIDTH
                    && mouseY >= y && mouseY < y + ITEM_HEIGHT;
                if (hovered) hoveredLeftIndex = i;
                
                renderMorphItem(guiGraphics, leftPanelX + 5, y, PANEL_WIDTH - 10, entry, hovered);
            }
            y += ITEM_HEIGHT + ITEM_SPACING;
        }
        
        guiGraphics.disableScissor();
        
        // 启用右侧裁剪
        guiGraphics.enableScissor(rightPanelX, panelTop, rightPanelX + PANEL_WIDTH, panelBottom);
        
        // 绘制右侧列表
        y = panelTop + PANEL_PADDING - rightScrollOffset;
        for (int i = 0; i < selectedMorphs.size(); i++) {
            if (y + ITEM_HEIGHT > panelTop && y < panelBottom) {
                MorphWheelConfig.MorphEntry entry = selectedMorphs.get(i);
                boolean hovered = mouseX >= rightPanelX && mouseX < rightPanelX + PANEL_WIDTH
                    && mouseY >= y && mouseY < y + ITEM_HEIGHT;
                if (hovered) hoveredRightIndex = i;
                
                renderMorphItem(guiGraphics, rightPanelX + 5, y, PANEL_WIDTH - 10, entry, hovered);
            }
            y += ITEM_HEIGHT + ITEM_SPACING;
        }
        
        guiGraphics.disableScissor();
        
        // 中间箭头提示
        int arrowY = this.height / 2;
        guiGraphics.drawCenteredString(this.font, "←点击添加", this.width / 2, arrowY - 10, COLOR_TEXT_SECONDARY);
        guiGraphics.drawCenteredString(this.font, "点击移除→", this.width / 2, arrowY + 5, COLOR_TEXT_SECONDARY);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void renderMorphItem(GuiGraphics guiGraphics, int x, int y, int width, 
                                  MorphWheelConfig.MorphEntry entry, boolean hovered) {
        int bgColor = hovered ? COLOR_ITEM_HOVER : COLOR_ITEM_BG;
        guiGraphics.fill(x, y, x + width, y + ITEM_HEIGHT, bgColor);
        
        // 表情名称
        guiGraphics.drawString(this.font, entry.displayName, x + 5, y + 4, COLOR_TEXT_PRIMARY);
        
        // 来源和大小
        int sourceColor = getSourceColor(entry.source);
        String sourceText = getSourceText(entry.source);
        guiGraphics.drawString(this.font, sourceText, x + 5, y + 16, sourceColor);
        
        if (entry.fileSize != null) {
            guiGraphics.drawString(this.font, entry.fileSize, x + width - font.width(entry.fileSize) - 5, y + 16, COLOR_TEXT_SECONDARY);
        }
        
        // 文件名
        String fileName = entry.morphName + ".vpd";
        if (font.width(fileName) > width - 10) {
            fileName = fileName.substring(0, 15) + "...";
        }
        guiGraphics.drawString(this.font, fileName, x + 5, y + 26, COLOR_TEXT_SECONDARY);
    }
    
    private int getSourceColor(String source) {
        if (source == null) return COLOR_TEXT_SECONDARY;
        return switch (source) {
            case "DEFAULT" -> COLOR_SOURCE_DEFAULT;
            case "CUSTOM" -> COLOR_SOURCE_CUSTOM;
            case "MODEL" -> COLOR_SOURCE_MODEL;
            default -> COLOR_TEXT_SECONDARY;
        };
    }
    
    private String getSourceText(String source) {
        if (source == null) return "未知";
        return switch (source) {
            case "DEFAULT" -> "默认";
            case "CUSTOM" -> "自定义";
            case "MODEL" -> "模型专属";
            default -> source;
        };
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 点击左侧项目添加到右侧
            if (hoveredLeftIndex >= 0 && hoveredLeftIndex < availableMorphs.size()) {
                MorphWheelConfig.MorphEntry entry = availableMorphs.remove(hoveredLeftIndex);
                selectedMorphs.add(entry);
                updateScrollBounds();
                return true;
            }
            
            // 点击右侧项目移回左侧
            if (hoveredRightIndex >= 0 && hoveredRightIndex < selectedMorphs.size()) {
                MorphWheelConfig.MorphEntry entry = selectedMorphs.remove(hoveredRightIndex);
                availableMorphs.add(entry);
                availableMorphs.sort((a, b) -> a.morphName.compareToIgnoreCase(b.morphName));
                updateScrollBounds();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int panelTop = HEADER_HEIGHT;
        int panelBottom = this.height - FOOTER_HEIGHT;
        int leftPanelX = this.width / 2 - PANEL_WIDTH - 15;
        int rightPanelX = this.width / 2 + 15;
        
        int scrollAmount = (int) (-scrollY * 20);
        
        // 左侧面板滚动
        if (mouseX >= leftPanelX && mouseX < leftPanelX + PANEL_WIDTH 
            && mouseY >= panelTop && mouseY < panelBottom) {
            leftScrollOffset = Math.max(0, Math.min(leftMaxScroll, leftScrollOffset + scrollAmount));
            return true;
        }
        
        // 右侧面板滚动
        if (mouseX >= rightPanelX && mouseX < rightPanelX + PANEL_WIDTH 
            && mouseY >= panelTop && mouseY < panelBottom) {
            rightScrollOffset = Math.max(0, Math.min(rightMaxScroll, rightScrollOffset + scrollAmount));
            return true;
        }
        
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
