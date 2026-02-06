package com.shiroha.mmdskin.maid;

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
import java.util.UUID;

/**
 * 女仆 MMD 模型选择界面
 * 
 * 当玩家对着女仆按 H 键时打开此界面，
 * 允许为女仆选择 MMD 模型来替代原版渲染。
 */
public class MaidModelSelectorScreen extends Screen {
    private static final Logger logger = LogManager.getLogger();
    
    // 布局常量
    private static final int CARD_WIDTH = 280;
    private static final int CARD_HEIGHT = 50;
    private static final int CARD_SPACING = 8;
    private static final int HEADER_HEIGHT = 70;
    private static final int FOOTER_HEIGHT = 50;
    
    // 颜色常量
    private static final int COLOR_CARD_BG = 0x80000000;
    private static final int COLOR_CARD_SELECTED = 0x80006600;
    private static final int COLOR_CARD_HOVER = 0x80333333;
    private static final int COLOR_CARD_BORDER = 0xFF555555;
    private static final int COLOR_CARD_BORDER_SELECTED = 0xFF00AA00;
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFF;
    private static final int COLOR_TEXT_SECONDARY = 0xAAAAAA;
    private static final int COLOR_TEXT_ACCENT = 0x55FF55;
    private static final int COLOR_FORMAT_PMX = 0xFF6699FF;
    private static final int COLOR_FORMAT_PMD = 0xFFFF9966;
    
    private final UUID maidUUID;
    private final int maidEntityId;
    private final String maidName;
    private final List<ModelCardEntry> modelCards;
    
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private String currentModel;
    private int hoveredCardIndex = -1;

    public MaidModelSelectorScreen(UUID maidUUID, int maidEntityId, String maidName) {
        super(Component.translatable("gui.mmdskin.maid_model_selector"));
        this.maidUUID = maidUUID;
        this.maidEntityId = maidEntityId;
        this.maidName = maidName;
        this.modelCards = new ArrayList<>();
        this.currentModel = MaidMMDModelManager.getBindingModelName(maidUUID);
        if (this.currentModel == null) {
            this.currentModel = UIConstants.DEFAULT_MODEL_NAME;
        }
        loadAvailableModels();
    }

    private void loadAvailableModels() {
        modelCards.clear();
        
        // 添加默认选项（使用原版渲染）
        modelCards.add(new ModelCardEntry(UIConstants.DEFAULT_MODEL_NAME, null));
        
        // 使用 ModelInfo 扫描所有模型
        List<ModelInfo> models = ModelInfo.scanModels();
        for (ModelInfo info : models) {
            modelCards.add(new ModelCardEntry(info.getFolderName(), info));
        }
        
        logger.info("女仆模型选择: 共加载 {} 个模型选项", modelCards.size());
    }

    @Override
    protected void init() {
        super.init();
        
        // 计算最大滚动距离
        int contentHeight = modelCards.size() * (CARD_HEIGHT + CARD_SPACING);
        int visibleHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        
        // 底部按钮
        int centerX = this.width / 2;
        int buttonY = this.height - FOOTER_HEIGHT + 15;
        
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> this.onClose())
            .bounds(centerX - 100, buttonY, 95, 20)
            .build());
        
        this.addRenderableWidget(Button.builder(Component.literal("刷新"), btn -> refreshModels())
            .bounds(centerX + 5, buttonY, 95, 20)
            .build());
    }

    private void refreshModels() {
        loadAvailableModels();
        scrollOffset = 0;
        this.clearWidgets();
        this.init();
    }

    private void selectModel(ModelCardEntry card) {
        this.currentModel = card.displayName;
        
        // 更新绑定
        MaidMMDModelManager.bindModel(maidUUID, card.displayName);
        
        // 发送网络包同步到服务器
        MaidModelNetworkHandler.sendMaidModelChange(maidEntityId, card.displayName);
        
        // 触发模型切换事件，1分钟后清理未使用的缓存
        com.shiroha.mmdskin.renderer.model.MMDModelManager.onModelSwitch();
        
        logger.info("女仆 {} 选择模型: {}", maidName, card.displayName);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        renderHeader(guiGraphics);
        renderModelCards(guiGraphics, mouseX, mouseY);
        renderScrollbar(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics guiGraphics) {
        int centerX = this.width / 2;
        
        // 标题
        guiGraphics.drawCenteredString(this.font, 
            Component.literal("§6女仆 MMD 模型选择"), centerX, 12, COLOR_TEXT_PRIMARY);
        
        // 女仆名称
        guiGraphics.drawCenteredString(this.font, 
            Component.literal("§7目标女仆: §f" + maidName), centerX, 28, COLOR_TEXT_SECONDARY);
        
        // 当前模型
        String subtitle = String.format("§7共 §f%d §7个模型可用  |  当前: §a%s", 
            modelCards.size() - 1, currentModel);
        guiGraphics.drawCenteredString(this.font, Component.literal(subtitle), centerX, 44, COLOR_TEXT_SECONDARY);
        
        // 分隔线
        guiGraphics.fill(centerX - 140, HEADER_HEIGHT - 5, centerX + 140, HEADER_HEIGHT - 4, 0x40FFFFFF);
    }

    private void renderModelCards(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int centerX = this.width / 2;
        int startY = HEADER_HEIGHT;
        int endY = this.height - FOOTER_HEIGHT;
        
        guiGraphics.enableScissor(0, startY, this.width, endY);
        
        hoveredCardIndex = -1;
        
        for (int i = 0; i < modelCards.size(); i++) {
            ModelCardEntry card = modelCards.get(i);
            int cardY = startY + i * (CARD_HEIGHT + CARD_SPACING) - scrollOffset + CARD_SPACING;
            
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

    private void renderCard(GuiGraphics guiGraphics, ModelCardEntry card, int x, int y, 
                           boolean isSelected, boolean isHovered) {
        int bgColor = isSelected ? COLOR_CARD_SELECTED : (isHovered ? COLOR_CARD_HOVER : COLOR_CARD_BG);
        int borderColor = isSelected ? COLOR_CARD_BORDER_SELECTED : COLOR_CARD_BORDER;
        
        // 背景
        guiGraphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, bgColor);
        
        // 边框
        guiGraphics.fill(x, y, x + CARD_WIDTH, y + 1, borderColor);
        guiGraphics.fill(x, y + CARD_HEIGHT - 1, x + CARD_WIDTH, y + CARD_HEIGHT, borderColor);
        guiGraphics.fill(x, y, x + 1, y + CARD_HEIGHT, borderColor);
        guiGraphics.fill(x + CARD_WIDTH - 1, y, x + CARD_WIDTH, y + CARD_HEIGHT, borderColor);
        
        int textX = x + 12;
        int textY = y + 10;
        
        // 模型名称
        String displayName = card.displayName;
        if (displayName.length() > 28) {
            displayName = displayName.substring(0, 25) + "...";
        }
        guiGraphics.drawString(this.font, displayName, textX, textY, COLOR_TEXT_PRIMARY);
        
        // 选中标识
        if (isSelected) {
            guiGraphics.drawString(this.font, "✓", x + CARD_WIDTH - 20, textY, COLOR_TEXT_ACCENT);
        }
        
        // 模型详情
        int detailY = textY + 14;
        if (card.modelInfo != null) {
            String format = card.modelInfo.getFormatDescription();
            int formatColor = card.modelInfo.isPMD() ? COLOR_FORMAT_PMD : COLOR_FORMAT_PMX;
            guiGraphics.drawString(this.font, "[" + format + "]", textX, detailY, formatColor);
            
            String fileName = card.modelInfo.getModelFileName();
            if (fileName.length() > 20) {
                fileName = fileName.substring(0, 17) + "...";
            }
            String details = "  " + fileName + "  (" + card.modelInfo.getFormattedSize() + ")";
            guiGraphics.drawString(this.font, details, textX + 32, detailY, COLOR_TEXT_SECONDARY);
        } else {
            guiGraphics.drawString(this.font, "使用女仆模组原版渲染", textX, detailY, COLOR_TEXT_SECONDARY);
        }
    }

    private void renderScrollbar(GuiGraphics guiGraphics) {
        if (maxScroll <= 0) return;
        
        int scrollbarX = this.width / 2 + CARD_WIDTH / 2 + 8;
        int scrollbarY = HEADER_HEIGHT;
        int scrollbarHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT;
        
        guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight, 0x40FFFFFF);
        
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

    private static class ModelCardEntry {
        final String displayName;
        final ModelInfo modelInfo;

        ModelCardEntry(String displayName, ModelInfo modelInfo) {
            this.displayName = displayName;
            this.modelInfo = modelInfo;
        }
    }
}
