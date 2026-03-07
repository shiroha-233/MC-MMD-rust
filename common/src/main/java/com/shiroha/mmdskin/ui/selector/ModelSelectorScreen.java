package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.renderer.model.ModelInfo;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.network.ModelSelectorNetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型选择界面 — 简约右侧面板风格
 * 右侧面板展示模型列表，左侧留空用于模型预览
 */
public class ModelSelectorScreen extends Screen {
    private static final Logger logger = LogManager.getLogger();
    
    // 右侧面板布局
    private static final int PANEL_WIDTH = 140;
    private static final int PANEL_MARGIN = 4;
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 20;
    private static final int ITEM_HEIGHT = 14;
    private static final int ITEM_SPACING = 1;
    
    // 统一简约配色
    private static final int COLOR_PANEL_BG = 0xC0101418;
    private static final int COLOR_PANEL_BORDER = 0xFF2A3A4A;
    private static final int COLOR_ITEM_HOVER = 0x30FFFFFF;
    private static final int COLOR_ITEM_SELECTED = 0x3060A0D0;
    private static final int COLOR_ACCENT = 0xFF60A0D0;
    private static final int COLOR_TEXT = 0xFFDDDDDD;
    private static final int COLOR_TEXT_DIM = 0xFF888888;
    private static final int COLOR_TEXT_SELECTED = 0xFF60A0D0;
    private static final int COLOR_SEPARATOR = 0x30FFFFFF;
    private static final int COLOR_SETTINGS_BTN = 0x80FFFFFF;
    private static final int COLOR_SETTINGS_BTN_HOVER = 0xFFFFFFFF;
    
    // 设置按钮尺寸
    private static final int SETTINGS_BTN_SIZE = 10;
    
    private final List<ModelCardEntry> modelCards;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private String currentModel;
    private int hoveredCardIndex = -1;
    private boolean hoveredOnSettingsBtn = false;
    
    // 面板区域缓存
    private int panelX, panelY, panelH;
    private int listTop, listBottom;

    public ModelSelectorScreen() {
        super(Component.translatable("gui.mmdskin.model_selector"));
        this.modelCards = new ArrayList<>();
        this.currentModel = ModelSelectorConfig.getInstance().getSelectedModel();
        loadAvailableModels();
    }

    // MC 1.21.1: 禁用默认背景模糊效果
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
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

    /**
     * 刷新模型列表
     */
    private void refreshModels() {
        loadAvailableModels();
        scrollOffset = 0;
        this.clearWidgets();
        this.init();
    }

    /**
     * 选择模型
     */
    private void selectModel(ModelCardEntry card) {
        this.currentModel = card.displayName;
        ModelSelectorConfig.getInstance().setSelectedModel(card.displayName);
        
        // 通知服务器模型变更
        ModelSelectorNetworkHandler.sendModelChangeToServer(card.displayName);
        
        // 仅重载本地玩家的模型（不影响其他远程玩家的模型和动画）
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            String playerName = mc.player.getName().getString();
            com.shiroha.mmdskin.renderer.model.MMDModelManager.forceReloadPlayerModels(playerName);
        }
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

    /**
     * 渲染头部区域
     */
    private void renderHeader(GuiGraphics guiGraphics) {
        int cx = panelX + PANEL_WIDTH / 2;
        
        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, cx, panelY + 4, COLOR_ACCENT);
        
        // 统计
        String info = Component.translatable("gui.mmdskin.model_selector.stats", modelCards.size() - 1, truncate(currentModel, 10)).getString();
        guiGraphics.drawCenteredString(this.font, info, cx, panelY + 16, COLOR_TEXT_DIM);
        
        // 分隔线
        guiGraphics.fill(panelX + 8, listTop - 2, panelX + PANEL_WIDTH - 8, listTop - 1, COLOR_SEPARATOR);
    }

    /**
     * 渲染模型列表
     */
    private void renderModelList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.enableScissor(panelX, listTop, panelX + PANEL_WIDTH, listBottom);
        
        hoveredCardIndex = -1;
        hoveredOnSettingsBtn = false;
        
        for (int i = 0; i < modelCards.size(); i++) {
            ModelCardEntry card = modelCards.get(i);
            int itemY = listTop + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;
            
            if (itemY + ITEM_HEIGHT < listTop || itemY > listBottom) continue;
            
            int itemX = panelX + 6;
            int itemW = PANEL_WIDTH - 12;
            boolean isSelected = card.displayName.equals(currentModel);
            boolean isHovered = mouseX >= itemX && mouseX <= itemX + itemW
                             && mouseY >= Math.max(itemY, listTop) && mouseY <= Math.min(itemY + ITEM_HEIGHT, listBottom);
            
            // 判断鼠标是否悬停在设置按钮区域
            boolean hasSettingsBtn = card.modelInfo != null;
            boolean isSettingsBtnHovered = false;
            if (isHovered && hasSettingsBtn) {
                int btnX = itemX + itemW - SETTINGS_BTN_SIZE - 2;
                int btnY = itemY + (ITEM_HEIGHT - SETTINGS_BTN_SIZE) / 2;
                int clippedBtnTop = Math.max(btnY, listTop);
                int clippedBtnBottom = Math.min(btnY + SETTINGS_BTN_SIZE, listBottom);
                isSettingsBtnHovered = mouseX >= btnX && mouseX <= btnX + SETTINGS_BTN_SIZE
                                   && mouseY >= clippedBtnTop && mouseY <= clippedBtnBottom;
            }
            
            if (isHovered) {
                hoveredCardIndex = i;
                hoveredOnSettingsBtn = isSettingsBtnHovered;
            }
            
            renderItem(guiGraphics, card, itemX, itemY, itemW, isSelected, isHovered, isSettingsBtnHovered);
        }
        
        guiGraphics.disableScissor();
    }

    /**
     * 渲染单个模型条目 — 简约单行/双行风格
     */
    private void renderItem(GuiGraphics guiGraphics, ModelCardEntry card, int x, int y, int w, boolean isSelected, boolean isHovered, boolean isSettingsBtnHovered) {
        // 背景
        if (isSelected) {
            guiGraphics.fill(x, y, x + w, y + ITEM_HEIGHT, COLOR_ITEM_SELECTED);
            // 左侧选中指示条
            guiGraphics.fill(x, y + 1, x + 2, y + ITEM_HEIGHT - 1, COLOR_ACCENT);
        } else if (isHovered) {
            guiGraphics.fill(x, y, x + w, y + ITEM_HEIGHT, COLOR_ITEM_HOVER);
        }
        
        int textX = x + 8;
        boolean hasSettingsBtn = card.modelInfo != null;
        
        // 模型名称（为设置按钮预留空间）
        int maxNameLen = hasSettingsBtn ? 12 : 16;
        String displayName = truncate(card.displayName, maxNameLen);
        int nameColor = isSelected ? COLOR_TEXT_SELECTED : COLOR_TEXT;
        guiGraphics.drawString(this.font, displayName, textX, y + 3, nameColor);
        
        // 右侧区域：设置按钮 或 选中标记
        if (hasSettingsBtn && isHovered) {
            // 设置按钮（齿轮图标），仅鼠标悬停时显示
            int btnX = x + w - SETTINGS_BTN_SIZE - 2;
            int btnY = y + (ITEM_HEIGHT - SETTINGS_BTN_SIZE) / 2;
            int btnColor = isSettingsBtnHovered ? COLOR_SETTINGS_BTN_HOVER : COLOR_SETTINGS_BTN;
            
            // 齿轮图标背景
            if (isSettingsBtnHovered) {
                guiGraphics.fill(btnX - 1, btnY - 1, btnX + SETTINGS_BTN_SIZE + 1, btnY + SETTINGS_BTN_SIZE + 1, 0x40FFFFFF);
            }
            guiGraphics.drawString(this.font, "\u2699", btnX, btnY, btnColor);
        } else if (isSelected) {
            guiGraphics.drawString(this.font, "\u2713", x + w - 10, y + 3, COLOR_ACCENT);
        }
    }

    /**
     * 渲染滚动条
     */
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
            ModelCardEntry card = modelCards.get(hoveredCardIndex);
            
            if (hoveredOnSettingsBtn && card.modelInfo != null) {
                // 点击设置按钮 -> 打开模型独立设置界面
                Minecraft.getInstance().setScreen(
                    new ModelSettingsScreen(card.displayName, this));
                return true;
            }
            
            // 点击条目主体 -> 选择模型
            selectModel(card);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 仅面板区域响应滚动
        if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * 24)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
