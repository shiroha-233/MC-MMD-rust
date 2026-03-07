package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 模型独立设置界面 — 简约右侧面板风格
 * 允许用户为每个模型单独调整参数（眼球角度、物理等）
 */
public class ModelSettingsScreen extends Screen {
    private static final Logger logger = LogManager.getLogger();
    
    // 右侧面板布局（与 ModelSelectorScreen 一致）
    private static final int PANEL_WIDTH = 140;
    private static final int PANEL_MARGIN = 4;
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 20;
    
    // 统一简约配色
    private static final int COLOR_PANEL_BG = 0xC0101418;
    private static final int COLOR_PANEL_BORDER = 0xFF2A3A4A;
    private static final int COLOR_ACCENT = 0xFF60A0D0;
    private static final int COLOR_TEXT = 0xFFDDDDDD;
    private static final int COLOR_TEXT_DIM = 0xFF888888;
    private static final int COLOR_SEPARATOR = 0x30FFFFFF;
    private static final int COLOR_SLIDER_BG = 0x40FFFFFF;
    private static final int COLOR_SLIDER_FILL = 0xFF60A0D0;
    private static final int COLOR_TOGGLE_ON = 0xFF40C080;
    private static final int COLOR_TOGGLE_OFF = 0xFF505560;
    private static final int COLOR_ITEM_HOVER = 0x30FFFFFF;
    
    private static final int COLOR_SLOT_BOUND = 0xFF40C080;
    private static final int COLOR_SLOT_UNBOUND = 0xFF505560;
    
    // 设置条目布局
    private static final int ITEM_HEIGHT = 24;
    private static final int ITEM_SPACING = 2;
    private static final int SLIDER_HEIGHT = 6;
    private static final int TOGGLE_W = 20;
    private static final int TOGGLE_H = 10;
    
    private final String modelName;
    private final Screen parentScreen;
    private ModelConfigData config;
    
    // 面板区域缓存
    private int panelX, panelY, panelH;
    private int listTop, listBottom;
    
    // 拖拽状态
    private int draggingSlider = -1; // 正在拖拽的滑条索引，-1 表示无
    private int quickSlotSectionY = 0;
    
    // 设置项定义
    private static final int SETTING_EYE_TRACKING = 0;
    private static final int SETTING_EYE_MAX_ANGLE = 1;
    private static final int SETTING_MODEL_SCALE = 2;
    
    public ModelSettingsScreen(String modelName, Screen parentScreen) {
        super(Component.translatable("gui.mmdskin.model_settings.title"));
        this.modelName = modelName;
        this.parentScreen = parentScreen;
        this.config = ModelConfigManager.getConfig(modelName).copy();
    }

    // MC 1.21.1: 禁用默认背景模糊效果
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }
    
    @Override
    protected void init() {
        super.init();
        
        panelX = this.width - PANEL_WIDTH - PANEL_MARGIN;
        panelY = PANEL_MARGIN;
        panelH = this.height - PANEL_MARGIN * 2;
        
        listTop = panelY + HEADER_HEIGHT;
        listBottom = panelY + panelH - FOOTER_HEIGHT;
        
        // 底部按钮
        int btnY = listBottom + 4;
        int btnW = (PANEL_WIDTH - 16) / 3;
        
        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.model_settings.save"), btn -> saveAndClose())
            .bounds(panelX + 4, btnY, btnW, 14).build());
        
        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.model_settings.reset"), btn -> resetDefaults())
            .bounds(panelX + 8 + btnW, btnY, btnW, 14).build());
        
        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.model_settings.anim_config"), btn -> openAnimConfig())
            .bounds(panelX + 12 + btnW * 2, btnY, btnW, 14).build());
    }
    
    /**
     * 打开动画映射配置界面
     */
    private void openAnimConfig() {
        Minecraft.getInstance().setScreen(new ModelAnimationScreen(modelName, this));
    }
    
    /**
     * 保存配置并关闭
     */
    private void saveAndClose() {
        ModelConfigManager.saveConfig(modelName, config);
        applyConfigToModel();
        this.onClose();
    }
    
    /**
     * 重置为默认值
     */
    private void resetDefaults() {
        config = new ModelConfigData();
    }
    
    /**
     * 将配置应用到当前加载的模型实例
     */
    private void applyConfigToModel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        String playerName = mc.player.getName().getString();
        String selectedModel = ModelSelectorConfig.getInstance().getSelectedModel();
        
        if (!modelName.equals(selectedModel)) return;
        
        MMDModelManager.Model model = MMDModelManager.GetModel(selectedModel, playerName);
        if (model == null) return;
        
        long handle = model.model.getModelHandle();
        NativeFunc nf = NativeFunc.GetInst();
        
        nf.SetEyeTrackingEnabled(handle, config.eyeTrackingEnabled);
        nf.SetEyeMaxAngle(handle, config.eyeMaxAngle);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 右侧面板背景
        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, COLOR_PANEL_BG);
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelH, COLOR_PANEL_BORDER);
        
        // 头部
        renderHeader(guiGraphics);
        
        // 设置项列表
        guiGraphics.enableScissor(panelX, listTop, panelX + PANEL_WIDTH, listBottom);
        renderSettings(guiGraphics, mouseX, mouseY);
        guiGraphics.disableScissor();
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    /**
     * 渲染头部
     */
    private void renderHeader(GuiGraphics guiGraphics) {
        int cx = panelX + PANEL_WIDTH / 2;
        guiGraphics.drawCenteredString(this.font, this.title, cx, panelY + 4, COLOR_ACCENT);
        
        String info = truncate(modelName, 18);
        guiGraphics.drawCenteredString(this.font, info, cx, panelY + 16, COLOR_TEXT_DIM);
        
        guiGraphics.fill(panelX + 8, listTop - 2, panelX + PANEL_WIDTH - 8, listTop - 1, COLOR_SEPARATOR);
    }
    
    /**
     * 渲染所有设置项
     */
    private void renderSettings(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int y = listTop + 4;
        int itemX = panelX + 6;
        int itemW = PANEL_WIDTH - 12;
        
        // 眼球追踪分组标题
        guiGraphics.drawString(this.font, Component.translatable("gui.mmdskin.model_settings.eye_tracking").getString(), itemX, y, COLOR_TEXT_DIM);
        y += 12;
        
        renderToggle(guiGraphics, Component.translatable("gui.mmdskin.model_settings.eye_tracking_enabled").getString(), config.eyeTrackingEnabled, 
                     itemX, y, itemW, mouseX, mouseY, SETTING_EYE_TRACKING);
        y += ITEM_HEIGHT + ITEM_SPACING;
        
        // 眼球最大角度滑条
        float angleDeg = (float) Math.toDegrees(config.eyeMaxAngle);
        String angleLabel = Component.translatable("gui.mmdskin.model_settings.eye_max_angle", String.format("%.0f", angleDeg)).getString();
        renderSlider(guiGraphics, angleLabel, config.eyeMaxAngle, 0.05f, 1.0f,
                     itemX, y, itemW, mouseX, mouseY, SETTING_EYE_MAX_ANGLE);
        y += ITEM_HEIGHT + ITEM_SPACING;
        
        // 分隔线
        y += 4;
        guiGraphics.fill(panelX + 12, y, panelX + PANEL_WIDTH - 12, y + 1, COLOR_SEPARATOR);
        y += 8;
        
        guiGraphics.drawString(this.font, Component.translatable("gui.mmdskin.model_settings.model_display").getString(), itemX, y, COLOR_TEXT_DIM);
        y += 12;
        
        String scaleLabel = Component.translatable("gui.mmdskin.model_settings.model_scale", String.format("%.2f", config.modelScale)).getString();
        renderSlider(guiGraphics, scaleLabel, config.modelScale, 0.5f, 2.0f,
                     itemX, y, itemW, mouseX, mouseY, SETTING_MODEL_SCALE);
        y += ITEM_HEIGHT + ITEM_SPACING;
        
        // 快捷绑定分组
        y += 4;
        guiGraphics.fill(panelX + 12, y, panelX + PANEL_WIDTH - 12, y + 1, COLOR_SEPARATOR);
        y += 8;
        guiGraphics.drawString(this.font, Component.translatable("gui.mmdskin.model_settings.quick_bind").getString(), itemX, y, COLOR_TEXT_DIM);
        y += 12;
        quickSlotSectionY = y;
        renderQuickSlots(guiGraphics, itemX, y, itemW, mouseX, mouseY);
    }
    
    private void renderQuickSlots(GuiGraphics guiGraphics, int x, int y, int w, int mouseX, int mouseY) {
        ModelSelectorConfig selectorConfig = ModelSelectorConfig.getInstance();
        int btnW = (w - 4) / 2;
        int btnH = 16;

        for (int i = 0; i < ModelSelectorConfig.QUICK_SLOT_COUNT; i++) {
            int col = i % 2;
            int row = i / 2;
            int btnX = x + col * (btnW + 4);
            int btnY = y + row * (btnH + 3);

            String boundModel = selectorConfig.getQuickSlotModel(i);
            boolean isBoundToThis = modelName.equals(boundModel);
            boolean isBoundToOther = boundModel != null && !boundModel.isEmpty() && !isBoundToThis;
            boolean isHovered = mouseX >= btnX && mouseX <= btnX + btnW
                && mouseY >= btnY && mouseY <= btnY + btnH;

            int bgColor;
            if (isBoundToThis) {
                bgColor = COLOR_SLOT_BOUND;
            } else if (isHovered) {
                bgColor = COLOR_ITEM_HOVER;
            } else {
                bgColor = COLOR_SLOT_UNBOUND;
            }
            guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, bgColor);

            String label = Component.translatable("gui.mmdskin.model_settings.slot", i + 1).getString();
            int labelColor = isBoundToThis ? 0xFFFFFFFF : COLOR_TEXT;
            int labelW = this.font.width(label);
            guiGraphics.drawString(this.font, label, btnX + (btnW - labelW) / 2, btnY + 4, labelColor);

            if (isBoundToOther && isHovered) {
                String hint = truncate(boundModel, 14);
                int hintW = this.font.width(hint);
                int hintX = btnX + (btnW - hintW) / 2;
                int hintY = btnY + btnH + 1;
                guiGraphics.fill(hintX - 2, hintY - 1, hintX + hintW + 2, hintY + 9, 0xE0182030);
                guiGraphics.drawString(this.font, hint, hintX, hintY, COLOR_TEXT_DIM);
            }
        }
    }
    
    /**
     * 渲染开关条目
     */
    private void renderToggle(GuiGraphics guiGraphics, String label, boolean value,
                               int x, int y, int w, int mouseX, int mouseY, int settingId) {
        boolean isHovered = mouseX >= x && mouseX <= x + w 
                         && mouseY >= y && mouseY <= y + ITEM_HEIGHT;
        
        if (isHovered) {
            guiGraphics.fill(x, y, x + w, y + ITEM_HEIGHT, COLOR_ITEM_HOVER);
        }
        
        // 标签
        guiGraphics.drawString(this.font, label, x + 4, y + (ITEM_HEIGHT - 8) / 2, COLOR_TEXT);
        
        // 开关
        int toggleX = x + w - TOGGLE_W - 4;
        int toggleY = y + (ITEM_HEIGHT - TOGGLE_H) / 2;
        int toggleColor = value ? COLOR_TOGGLE_ON : COLOR_TOGGLE_OFF;
        guiGraphics.fill(toggleX, toggleY, toggleX + TOGGLE_W, toggleY + TOGGLE_H, toggleColor);
        
        // 滑块圆点
        int dotX = value ? toggleX + TOGGLE_W - TOGGLE_H : toggleX;
        guiGraphics.fill(dotX + 1, toggleY + 1, dotX + TOGGLE_H - 1, toggleY + TOGGLE_H - 1, 0xFFFFFFFF);
    }
    
    /**
     * 渲染滑条条目
     */
    private void renderSlider(GuiGraphics guiGraphics, String label, float value,
                               float min, float max, int x, int y, int w,
                               int mouseX, int mouseY, int settingId) {
        boolean isHovered = mouseX >= x && mouseX <= x + w 
                         && mouseY >= y && mouseY <= y + ITEM_HEIGHT;
        
        if (isHovered) {
            guiGraphics.fill(x, y, x + w, y + ITEM_HEIGHT, COLOR_ITEM_HOVER);
        }
        
        // 标签
        guiGraphics.drawString(this.font, label, x + 4, y + 2, COLOR_TEXT);
        
        // 滑条
        int sliderX = x + 4;
        int sliderY = y + 14;
        int sliderW = w - 8;
        
        // 背景轨道
        guiGraphics.fill(sliderX, sliderY, sliderX + sliderW, sliderY + SLIDER_HEIGHT, COLOR_SLIDER_BG);
        
        // 填充
        float ratio = (value - min) / (max - min);
        ratio = Math.max(0, Math.min(1, ratio));
        int fillW = (int) (sliderW * ratio);
        guiGraphics.fill(sliderX, sliderY, sliderX + fillW, sliderY + SLIDER_HEIGHT, COLOR_SLIDER_FILL);
        
        // 滑块
        int thumbX = sliderX + fillW - 2;
        guiGraphics.fill(thumbX, sliderY - 1, thumbX + 4, sliderY + SLIDER_HEIGHT + 1, 0xFFFFFFFF);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int y = listTop + 4;
            int itemX = panelX + 6;
            int itemW = PANEL_WIDTH - 12;
            
            // 跳过眼球追踪分组标题
            y += 12;
            
            // 眼球追踪开关
            if (isInToggleArea(mouseX, mouseY, itemX, y, itemW)) {
                config.eyeTrackingEnabled = !config.eyeTrackingEnabled;
                return true;
            }
            y += ITEM_HEIGHT + ITEM_SPACING;
            
            // 眼球最大角度滑条
            if (isInSliderArea(mouseX, mouseY, itemX, y, itemW)) {
                draggingSlider = SETTING_EYE_MAX_ANGLE;
                updateSliderValue(mouseX, itemX, itemW);
                return true;
            }
            y += ITEM_HEIGHT + ITEM_SPACING;
            
            // 跳过分隔线和模型标题
            y += 4 + 8 + 12;
            
            // 模型缩放滑条
            if (isInSliderArea(mouseX, mouseY, itemX, y, itemW)) {
                draggingSlider = SETTING_MODEL_SCALE;
                updateSliderValue(mouseX, itemX, itemW);
                return true;
            }
            
            // 快捷槽位点击
            if (handleQuickSlotClick(mouseX, mouseY, itemX, itemW)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingSlider >= 0) {
            int itemX = panelX + 6;
            int itemW = PANEL_WIDTH - 12;
            updateSliderValue(mouseX, itemX, itemW);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingSlider >= 0) {
            draggingSlider = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    /**
     * 根据鼠标位置更新滑条值
     */
    private void updateSliderValue(double mouseX, int itemX, int itemW) {
        int sliderX = itemX + 4;
        int sliderW = itemW - 8;
        
        float ratio = (float) (mouseX - sliderX) / sliderW;
        ratio = Math.max(0, Math.min(1, ratio));
        
        switch (draggingSlider) {
            case SETTING_EYE_MAX_ANGLE:
                config.eyeMaxAngle = 0.05f + ratio * (1.0f - 0.05f);
                break;
            case SETTING_MODEL_SCALE:
                config.modelScale = 0.5f + ratio * (2.0f - 0.5f);
                break;
        }
    }
    
    /**
     * 检查鼠标是否在开关区域内
     */
    private boolean isInToggleArea(double mouseX, double mouseY, int x, int y, int w) {
        return mouseX >= x && mouseX <= x + w 
            && mouseY >= y && mouseY <= y + ITEM_HEIGHT;
    }
    
    /**
     * 检查鼠标是否在滑条区域内
     */
    private boolean isInSliderArea(double mouseX, double mouseY, int x, int y, int w) {
        return mouseX >= x && mouseX <= x + w 
            && mouseY >= y && mouseY <= y + ITEM_HEIGHT;
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
        Minecraft.getInstance().setScreen(parentScreen);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    private boolean handleQuickSlotClick(double mouseX, double mouseY, int x, int w) {
        int btnW = (w - 4) / 2;
        int btnH = 16;
        int y = quickSlotSectionY;

        for (int i = 0; i < ModelSelectorConfig.QUICK_SLOT_COUNT; i++) {
            int col = i % 2;
            int row = i / 2;
            int btnX = x + col * (btnW + 4);
            int btnY = y + row * (btnH + 3);
            if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                toggleQuickSlot(i);
                return true;
            }
        }
        return false;
    }
    
    private void toggleQuickSlot(int slot) {
        ModelSelectorConfig selectorConfig = ModelSelectorConfig.getInstance();
        String currentBound = selectorConfig.getQuickSlotModel(slot);
        if (modelName.equals(currentBound)) {
            selectorConfig.setQuickSlotModel(slot, null);
            logger.info("取消快捷槽位 {} 绑定", slot + 1);
        } else {
            selectorConfig.setQuickSlotModel(slot, modelName);
            logger.info("模型 {} 绑定到快捷槽位 {}", modelName, slot + 1);
        }
    }
    
    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 2) + ".." : s;
    }
}
