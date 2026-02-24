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
 * 女仆 MMD 模型选择界面 — 简约右侧面板风格
 * 右侧面板展示模型列表，左侧留空用于模型预览
 */
public class MaidModelSelectorScreen extends Screen {
    private static final Logger logger = LogManager.getLogger();
    
    // 右侧面板布局
    private static final int PANEL_WIDTH = 140;
    private static final int PANEL_MARGIN = 4;
    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 20;
    private static final int ITEM_HEIGHT = 14;
    private static final int ITEM_SPACING = 1;
    
    // 女仆主题配色（粉紫色系）
    private static final int COLOR_PANEL_BG = 0xC0181420;
    private static final int COLOR_PANEL_BORDER = 0xFF4A2A4A;
    private static final int COLOR_ITEM_HOVER = 0x30FFFFFF;
    private static final int COLOR_ITEM_SELECTED = 0x30D060A0;
    private static final int COLOR_ACCENT = 0xFFD060A0;
    private static final int COLOR_TEXT = 0xFFDDDDDD;
    private static final int COLOR_TEXT_DIM = 0xFF888888;
    private static final int COLOR_TEXT_SELECTED = 0xFFD060A0;
    private static final int COLOR_SEPARATOR = 0x30FFFFFF;
    
    private final UUID maidUUID;
    private final int maidEntityId;
    private final String maidName;
    private final List<ModelCardEntry> modelCards;
    
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private String currentModel;
    private int hoveredCardIndex = -1;
    
    // 面板区域缓存
    private int panelX, panelY, panelH;
    private int listTop, listBottom;

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
        modelCards.add(new ModelCardEntry(UIConstants.DEFAULT_MODEL_NAME));
        
        // 使用 ModelInfo 扫描所有模型
        List<ModelInfo> models = ModelInfo.scanModels();
        for (ModelInfo info : models) {
            modelCards.add(new ModelCardEntry(info.getFolderName()));
        }
        
    }

    @Override
    protected void init() {
        super.init();
        
        // 面板位置：屏幕右侧
        panelX = this.width - PANEL_WIDTH - PANEL_MARGIN;
        panelY = PANEL_MARGIN;
        panelH = this.height - PANEL_MARGIN * 2;
        
        listTop = panelY + HEADER_HEIGHT;
        listBottom = panelY + panelH - FOOTER_HEIGHT;
        
        // 计算滚动
        int contentHeight = modelCards.size() * (ITEM_HEIGHT + ITEM_SPACING);
        int visibleHeight = listBottom - listTop;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        
        // 按钮区域（面板底部紧凑）
        int btnY = listBottom + 4;
        int btnW = (PANEL_WIDTH - 12) / 2;
        
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> this.onClose())
            .bounds(panelX + 4, btnY, btnW, 14).build());
        
        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.refresh"), btn -> refreshModels())
            .bounds(panelX + 4 + btnW + 4, btnY, btnW, 14).build());
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
        
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 不渲染全屏背景，保持左侧透明用于模型预览
        
        // 右侧面板背景
        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, COLOR_PANEL_BG);
        // 面板左边框（视觉分隔）
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelH, COLOR_PANEL_BORDER);
        
        // 头部
        renderHeader(guiGraphics);
        
        // 列表
        renderModelList(guiGraphics, mouseX, mouseY);
        
        // 滚动条
        renderScrollbar(guiGraphics);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics guiGraphics) {
        int cx = panelX + PANEL_WIDTH / 2;
        
        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, cx, panelY + 4, COLOR_ACCENT);
        
        // 女仆名称
        String maidInfo = truncate(maidName, 16);
        guiGraphics.drawCenteredString(this.font, maidInfo, cx, panelY + 16, COLOR_TEXT_DIM);
        
        // 统计
        String info = Component.translatable("gui.mmdskin.model_selector.stats", modelCards.size() - 1, truncate(currentModel, 10)).getString();
        guiGraphics.drawCenteredString(this.font, info, cx, panelY + 28, COLOR_TEXT_DIM);
        
        // 分隔线
        guiGraphics.fill(panelX + 8, listTop - 2, panelX + PANEL_WIDTH - 8, listTop - 1, COLOR_SEPARATOR);
    }

    private void renderModelList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.enableScissor(panelX, listTop, panelX + PANEL_WIDTH, listBottom);
        
        hoveredCardIndex = -1;
        
        for (int i = 0; i < modelCards.size(); i++) {
            ModelCardEntry card = modelCards.get(i);
            int itemY = listTop + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;
            
            if (itemY + ITEM_HEIGHT < listTop || itemY > listBottom) continue;
            
            int itemX = panelX + 6;
            int itemW = PANEL_WIDTH - 12;
            boolean isSelected = card.displayName.equals(currentModel);
            boolean isHovered = mouseX >= itemX && mouseX <= itemX + itemW
                             && mouseY >= Math.max(itemY, listTop) && mouseY <= Math.min(itemY + ITEM_HEIGHT, listBottom);
            
            if (isHovered) {
                hoveredCardIndex = i;
            }
            
            renderItem(guiGraphics, card, itemX, itemY, itemW, isSelected, isHovered);
        }
        
        guiGraphics.disableScissor();
    }

    private void renderItem(GuiGraphics guiGraphics, ModelCardEntry card, int x, int y, int w, boolean isSelected, boolean isHovered) {
        // 背景
        if (isSelected) {
            guiGraphics.fill(x, y, x + w, y + ITEM_HEIGHT, COLOR_ITEM_SELECTED);
            // 左侧选中指示条
            guiGraphics.fill(x, y + 1, x + 2, y + ITEM_HEIGHT - 1, COLOR_ACCENT);
        } else if (isHovered) {
            guiGraphics.fill(x, y, x + w, y + ITEM_HEIGHT, COLOR_ITEM_HOVER);
        }
        
        int textX = x + 8;
        
        // 模型名称
        String displayName = truncate(card.displayName, 16);
        int nameColor = isSelected ? COLOR_TEXT_SELECTED : COLOR_TEXT;
        guiGraphics.drawString(this.font, displayName, textX, y + 3, nameColor);
        
        // 选中标记
        if (isSelected) {
            guiGraphics.drawString(this.font, "\u2713", x + w - 10, y + 3, COLOR_ACCENT);
        }
    }

    private void renderScrollbar(GuiGraphics guiGraphics) {
        if (maxScroll <= 0) return;
        
        int barX = panelX + PANEL_WIDTH - 4;
        int barH = listBottom - listTop;
        
        // 轨道
        guiGraphics.fill(barX, listTop, barX + 2, listBottom, 0x20FFFFFF);
        
        // 滑块
        int thumbH = Math.max(16, barH * barH / (barH + maxScroll));
        int thumbY = listTop + (int)((barH - thumbH) * ((float) scrollOffset / maxScroll));
        guiGraphics.fill(barX, thumbY, barX + 2, thumbY + thumbH, COLOR_ACCENT);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 仅面板区域响应滚动
        if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(delta * 24)));
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
    public boolean isPauseScreen() {
        return false;
    }
    
    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 2) + ".." : s;
    }

    private static class ModelCardEntry {
        final String displayName;

        ModelCardEntry(String displayName) {
            this.displayName = displayName;
        }
    }
}
