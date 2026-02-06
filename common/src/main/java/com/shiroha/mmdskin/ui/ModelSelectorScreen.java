package com.shiroha.mmdskin.ui;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.renderer.model.ModelInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型选择界面
 * 按 Alt+H 打开，显示所有可用的玩家模型
 * 
 * 重构说明：
 * - 使用卡片式布局，显示更多模型信息
 * - 支持任意名称的 PMX/PMD 文件
 * - 更现代化的 UI 设计
 */
public class ModelSelectorScreen extends Screen {
    private static final Logger logger = LogManager.getLogger();
    
    // 布局常量
    private static final int CARD_WIDTH = 280;
    private static final int CARD_HEIGHT = 50;
    private static final int CARD_SPACING = 8;
    private static final int HEADER_HEIGHT = 60;
    private static final int FOOTER_HEIGHT = 50;
    
    // 颜色常量
    private static final int COLOR_CARD_BG = 0x80000000;           // 半透明黑色
    private static final int COLOR_CARD_SELECTED = 0x80006600;     // 半透明绿色
    private static final int COLOR_CARD_HOVER = 0x80333333;        // 半透明灰色
    private static final int COLOR_CARD_BORDER = 0xFF555555;       // 边框灰色
    private static final int COLOR_CARD_BORDER_SELECTED = 0xFF00AA00; // 选中边框绿色
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFF;        // 主文本白色
    private static final int COLOR_TEXT_SECONDARY = 0xAAAAAA;      // 次级文本灰色
    private static final int COLOR_TEXT_ACCENT = 0x55FF55;         // 强调文本绿色
    private static final int COLOR_FORMAT_PMX = 0xFF6699FF;        // PMX 蓝色
    private static final int COLOR_FORMAT_PMD = 0xFFFF9966;        // PMD 橙色
    
    private final List<ModelCardEntry> modelCards;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private String currentModel;
    private int hoveredCardIndex = -1;

    public ModelSelectorScreen() {
        super(Component.translatable("gui.mmdskin.model_selector"));
        this.modelCards = new ArrayList<>();
        this.currentModel = ModelSelectorConfig.getInstance().getSelectedModel();
        loadAvailableModels();
    }

    /**
     * 加载所有可用的模型（使用 ModelInfo 扫描）
     */
    private void loadAvailableModels() {
        modelCards.clear();
        
        // 添加默认选项（使用原版渲染）
        modelCards.add(new ModelCardEntry(UIConstants.DEFAULT_MODEL_NAME, null));
        
        // 使用 ModelInfo 扫描所有模型
        List<ModelInfo> models = ModelInfo.scanModels();
        for (ModelInfo info : models) {
            modelCards.add(new ModelCardEntry(info.getFolderName(), info));
        }
        
        logger.info("共加载 {} 个模型选项", modelCards.size());
    }

    @Override
    protected void init() {
        super.init();
        
        // 计算最大滚动距离
        int contentHeight = modelCards.size() * (CARD_HEIGHT + CARD_SPACING);
        int visibleHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
        
        // 确保滚动偏移在有效范围内
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        
        // 底部按钮区域
        int centerX = this.width / 2;
        int buttonY = this.height - FOOTER_HEIGHT + 15;
        
        // 完成按钮
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> this.onClose())
            .bounds(centerX - 100, buttonY, 95, 20)
            .build());
        
        // 刷新按钮
        this.addRenderableWidget(Button.builder(Component.literal("刷新"), btn -> refreshModels())
            .bounds(centerX + 5, buttonY, 95, 20)
            .build());
    }

    /**
     * 刷新模型列表
     */
    private void refreshModels() {
        loadAvailableModels();
        scrollOffset = 0;
        this.clearWidgets();
        this.init();
        logger.info("模型列表已刷新");
    }

    /**
     * 选择模型
     */
    private void selectModel(ModelCardEntry card) {
        this.currentModel = card.displayName;
        ModelSelectorConfig.getInstance().setSelectedModel(card.displayName);
        ModelSelectorConfig.getInstance().save();
        
        // 通知服务器模型变更
        ModelSelectorNetworkHandler.sendModelChangeToServer(card.displayName);
        
        // 强制重载所有模型（立即释放旧模型，确保 CPU/GPU 模式都生效）
        com.shiroha.mmdskin.renderer.model.MMDModelManager.forceReloadAllModels();
        
        logger.info("玩家选择模型: {}", card.displayName);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染半透明背景
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        
        // 渲染头部
        renderHeader(guiGraphics);
        
        // 渲染模型卡片列表
        renderModelCards(guiGraphics, mouseX, mouseY);
        
        // 渲染滚动条
        renderScrollbar(guiGraphics);
        
        // 渲染底部按钮（由 super.render 处理）
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /**
     * 渲染头部区域
     */
    private void renderHeader(GuiGraphics guiGraphics) {
        int centerX = this.width / 2;
        
        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 15, COLOR_TEXT_PRIMARY);
        
        // 副标题
        String subtitle = String.format("§7共 §f%d §7个模型可用  |  当前: §a%s", 
            modelCards.size() - 1, currentModel);
        guiGraphics.drawCenteredString(this.font, Component.literal(subtitle), centerX, 32, COLOR_TEXT_SECONDARY);
        
        // 分隔线
        guiGraphics.fill(centerX - 140, HEADER_HEIGHT - 5, centerX + 140, HEADER_HEIGHT - 4, 0x40FFFFFF);
    }

    /**
     * 渲染模型卡片列表
     */
    private void renderModelCards(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int centerX = this.width / 2;
        int startY = HEADER_HEIGHT;
        int endY = this.height - FOOTER_HEIGHT;
        
        // 设置裁剪区域
        guiGraphics.enableScissor(0, startY, this.width, endY);
        
        hoveredCardIndex = -1;
        
        for (int i = 0; i < modelCards.size(); i++) {
            ModelCardEntry card = modelCards.get(i);
            int cardY = startY + i * (CARD_HEIGHT + CARD_SPACING) - scrollOffset + CARD_SPACING;
            
            // 跳过不可见的卡片
            if (cardY + CARD_HEIGHT < startY || cardY > endY) {
                continue;
            }
            
            int cardX = centerX - CARD_WIDTH / 2;
            boolean isSelected = card.displayName.equals(currentModel);
            boolean isHovered = mouseX >= cardX && mouseX <= cardX + CARD_WIDTH 
                             && mouseY >= cardY && mouseY <= cardY + CARD_HEIGHT
                             && mouseY >= startY && mouseY <= endY;
            
            if (isHovered) {
                hoveredCardIndex = i;
            }
            
            renderCard(guiGraphics, card, cardX, cardY, isSelected, isHovered);
        }
        
        guiGraphics.disableScissor();
    }

    /**
     * 渲染单个模型卡片
     */
    private void renderCard(GuiGraphics guiGraphics, ModelCardEntry card, int x, int y, boolean isSelected, boolean isHovered) {
        // 背景颜色
        int bgColor = isSelected ? COLOR_CARD_SELECTED : (isHovered ? COLOR_CARD_HOVER : COLOR_CARD_BG);
        int borderColor = isSelected ? COLOR_CARD_BORDER_SELECTED : COLOR_CARD_BORDER;
        
        // 绘制卡片背景
        guiGraphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, bgColor);
        
        // 绘制边框
        guiGraphics.fill(x, y, x + CARD_WIDTH, y + 1, borderColor);                    // 上
        guiGraphics.fill(x, y + CARD_HEIGHT - 1, x + CARD_WIDTH, y + CARD_HEIGHT, borderColor); // 下
        guiGraphics.fill(x, y, x + 1, y + CARD_HEIGHT, borderColor);                   // 左
        guiGraphics.fill(x + CARD_WIDTH - 1, y, x + CARD_WIDTH, y + CARD_HEIGHT, borderColor); // 右
        
        // 文字内容
        int textX = x + 12;
        int textY = y + 10;
        
        // 模型名称（主标题）
        String displayName = card.displayName;
        if (displayName.length() > 28) {
            displayName = displayName.substring(0, 25) + "...";
        }
        guiGraphics.drawString(this.font, displayName, textX, textY, COLOR_TEXT_PRIMARY);
        
        // 选中标识
        if (isSelected) {
            guiGraphics.drawString(this.font, "✓", x + CARD_WIDTH - 20, textY, COLOR_TEXT_ACCENT);
        }
        
        // 模型详情（副标题）
        int detailY = textY + 14;
        if (card.modelInfo != null) {
            // 格式标签
            String format = card.modelInfo.getFormatDescription();
            int formatColor = card.modelInfo.isPMD() ? COLOR_FORMAT_PMD : COLOR_FORMAT_PMX;
            guiGraphics.drawString(this.font, "[" + format + "]", textX, detailY, formatColor);
            
            // 文件名和大小
            String fileName = card.modelInfo.getModelFileName();
            if (fileName.length() > 20) {
                fileName = fileName.substring(0, 17) + "...";
            }
            String details = "  " + fileName + "  (" + card.modelInfo.getFormattedSize() + ")";
            guiGraphics.drawString(this.font, details, textX + 32, detailY, COLOR_TEXT_SECONDARY);
        } else {
            // 默认选项
            guiGraphics.drawString(this.font, "使用原版玩家皮肤渲染", textX, detailY, COLOR_TEXT_SECONDARY);
        }
    }

    /**
     * 渲染滚动条
     */
    private void renderScrollbar(GuiGraphics guiGraphics) {
        if (maxScroll <= 0) return;
        
        int scrollbarX = this.width / 2 + CARD_WIDTH / 2 + 8;
        int scrollbarY = HEADER_HEIGHT;
        int scrollbarHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT;
        
        // 滚动条轨道
        guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight, 0x40FFFFFF);
        
        // 滚动条滑块
        int thumbHeight = Math.max(20, scrollbarHeight * scrollbarHeight / (scrollbarHeight + maxScroll));
        int thumbY = scrollbarY + (int)((scrollbarHeight - thumbHeight) * ((float)scrollOffset / maxScroll));
        guiGraphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredCardIndex >= 0 && hoveredCardIndex < modelCards.size()) {
            selectModel(modelCards.get(hoveredCardIndex));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * 30)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape 键关闭
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

    /**
     * 模型卡片条目
     */
    private static class ModelCardEntry {
        final String displayName;
        final ModelInfo modelInfo;

        ModelCardEntry(String displayName, ModelInfo modelInfo) {
            this.displayName = displayName;
            this.modelInfo = modelInfo;
        }
    }
}
