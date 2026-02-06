package com.shiroha.mmdskin.ui;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
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
 * 材质可见性控制界面
 * 用于控制 MMD 模型的材质显示/隐藏（如脱外套功能）
 * 
 * 按 Alt+M 打开
 */
public class MaterialVisibilityScreen extends Screen {
    private static final Logger logger = LogManager.getLogger();
    
    // 布局常量
    private static final int ITEM_HEIGHT = 36;
    private static final int ITEM_SPACING = 4;
    private static final int HEADER_HEIGHT = 70;
    private static final int FOOTER_HEIGHT = 55;
    private static final int PANEL_WIDTH = 320;
    private static final int TOGGLE_WIDTH = 50;
    private static final int TOGGLE_HEIGHT = 22;
    
    // 颜色常量 - 精致的配色方案
    private static final int COLOR_BG_PANEL = 0xE0101820;           // 深蓝黑背景
    private static final int COLOR_ITEM_BG = 0x80182028;            // 条目背景
    private static final int COLOR_ITEM_HOVER = 0x80304050;         // 悬停背景
    private static final int COLOR_BORDER = 0xFF3A4A5A;             // 边框颜色
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFF;         // 主文本
    private static final int COLOR_TEXT_SECONDARY = 0xFF8899AA;     // 次级文本
    private static final int COLOR_TEXT_DIM = 0xFF556677;           // 暗淡文本
    private static final int COLOR_TOGGLE_ON = 0xFF40C080;          // 开关开启
    private static final int COLOR_TOGGLE_OFF = 0xFF505560;         // 开关关闭
    private static final int COLOR_ACCENT = 0xFF60A0D0;             // 强调色
    
    // 模型和材质数据
    private final long modelHandle;
    private final String modelName;
    private final List<MaterialEntry> materials;
    
    // UI 状态
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int hoveredIndex = -1;
    private int visibleCount = 0;
    private int totalCount = 0;
    
    public MaterialVisibilityScreen(long modelHandle, String modelName) {
        super(Component.literal("材质可见性控制"));
        this.modelHandle = modelHandle;
        this.modelName = modelName;
        this.materials = new ArrayList<>();
        loadMaterials();
    }
    
    /**
     * 从当前玩家模型创建界面
     */
    public static MaterialVisibilityScreen createForPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        
        String playerName = mc.player.getName().getString();
        String modelName = ModelSelectorConfig.getInstance().getSelectedModel();
        
        if (modelName == null || modelName.isEmpty()) {
            logger.warn("玩家未选择模型");
            return null;
        }
        
        // 模型缓存 key 格式: 模型名_玩家名
        MMDModelManager.Model model = MMDModelManager.GetModel(modelName, playerName);
        if (model == null) {
            logger.warn("无法获取玩家模型: {}_{}", modelName, playerName);
            return null;
        }
        
        if (model instanceof MMDModelManager.ModelWithEntityData mwed) {
            return new MaterialVisibilityScreen(mwed.model.GetModelLong(), modelName);
        }
        
        return null;
    }
    
    /**
     * 从女仆模型创建界面
     */
    public static MaterialVisibilityScreen createForMaid(java.util.UUID maidUUID, String maidName) {
        MMDModelManager.Model model = 
            com.shiroha.mmdskin.maid.MaidMMDModelManager.getModel(maidUUID);
        
        if (model == null) {
            logger.warn("无法获取女仆模型: {}", maidUUID);
            return null;
        }
        
        if (model instanceof MMDModelManager.ModelWithEntityData mwed) {
            String displayName = maidName != null ? maidName : "女仆";
            return new MaterialVisibilityScreen(mwed.model.GetModelLong(), displayName);
        }
        
        return null;
    }
    
    /**
     * 加载材质列表
     */
    private void loadMaterials() {
        materials.clear();
        NativeFunc nf = NativeFunc.GetInst();
        
        long materialCount = nf.GetMaterialCount(modelHandle);
        for (int i = 0; i < materialCount; i++) {
            String name = nf.GetMaterialName(modelHandle, i);
            boolean visible = nf.IsMaterialVisible(modelHandle, i);
            materials.add(new MaterialEntry(i, name, visible));
        }
        
        updateCounts();
        logger.info("加载了 {} 个材质", materials.size());
    }
    
    /**
     * 更新统计数量
     */
    private void updateCounts() {
        totalCount = materials.size();
        visibleCount = (int) materials.stream().filter(m -> m.visible).count();
    }
    
    @Override
    protected void init() {
        super.init();
        
        // 计算滚动
        int contentHeight = materials.size() * (ITEM_HEIGHT + ITEM_SPACING);
        int visibleHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT;
        maxScroll = Math.max(0, contentHeight - visibleHeight + ITEM_SPACING);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        
        // 底部按钮
        int centerX = this.width / 2;
        int buttonY = this.height - FOOTER_HEIGHT + 18;
        int buttonWidth = 72;
        int buttonSpacing = 8;
        int totalButtonWidth = buttonWidth * 4 + buttonSpacing * 3;
        int startX = centerX - totalButtonWidth / 2;
        
        // 全部显示
        this.addRenderableWidget(Button.builder(Component.literal("全部显示"), btn -> {
            setAllVisible(true);
        }).bounds(startX, buttonY, buttonWidth, 20).build());
        
        // 全部隐藏
        this.addRenderableWidget(Button.builder(Component.literal("全部隐藏"), btn -> {
            setAllVisible(false);
        }).bounds(startX + buttonWidth + buttonSpacing, buttonY, buttonWidth, 20).build());
        
        // 反选
        this.addRenderableWidget(Button.builder(Component.literal("反选"), btn -> {
            invertSelection();
        }).bounds(startX + (buttonWidth + buttonSpacing) * 2, buttonY, buttonWidth, 20).build());
        
        // 完成
        this.addRenderableWidget(Button.builder(Component.literal("完成"), btn -> {
            this.onClose();
        }).bounds(startX + (buttonWidth + buttonSpacing) * 3, buttonY, buttonWidth, 20).build());
    }
    
    /**
     * 设置所有材质可见性
     */
    private void setAllVisible(boolean visible) {
        NativeFunc nf = NativeFunc.GetInst();
        nf.SetAllMaterialsVisible(modelHandle, visible);
        for (MaterialEntry entry : materials) {
            entry.visible = visible;
        }
        updateCounts();
    }
    
    /**
     * 反选所有材质
     */
    private void invertSelection() {
        NativeFunc nf = NativeFunc.GetInst();
        for (MaterialEntry entry : materials) {
            entry.visible = !entry.visible;
            nf.SetMaterialVisible(modelHandle, entry.index, entry.visible);
        }
        updateCounts();
    }
    
    /**
     * 切换单个材质可见性
     */
    private void toggleMaterial(int index) {
        if (index < 0 || index >= materials.size()) return;
        
        MaterialEntry entry = materials.get(index);
        entry.visible = !entry.visible;
        
        NativeFunc nf = NativeFunc.GetInst();
        nf.SetMaterialVisible(modelHandle, entry.index, entry.visible);
        updateCounts();
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染暗化背景
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        
        // 主面板背景
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = 20;
        int panelHeight = this.height - 40;
        renderPanel(guiGraphics, panelX - 20, panelY, PANEL_WIDTH + 40, panelHeight);
        
        // 渲染头部
        renderHeader(guiGraphics);
        
        // 渲染材质列表
        renderMaterialList(guiGraphics, mouseX, mouseY);
        
        // 渲染滚动条
        renderScrollbar(guiGraphics);
        
        // 渲染底部统计
        renderFooter(guiGraphics);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    /**
     * 渲染面板背景
     */
    private void renderPanel(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        // 主背景
        guiGraphics.fill(x, y, x + w, y + h, COLOR_BG_PANEL);
        
        // 边框
        guiGraphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        guiGraphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        // 顶部高光线
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + 2, 0x20FFFFFF);
    }
    
    /**
     * 渲染头部
     */
    private void renderHeader(GuiGraphics guiGraphics) {
        int centerX = this.width / 2;
        
        // 图标装饰
        String icon = "◆";
        guiGraphics.drawCenteredString(this.font, icon, centerX - 70, 32, COLOR_ACCENT);
        guiGraphics.drawCenteredString(this.font, icon, centerX + 70, 32, COLOR_ACCENT);
        
        // 标题
        guiGraphics.drawCenteredString(this.font, "§l材质可见性控制", centerX, 30, COLOR_TEXT_PRIMARY);
        
        // 模型名称
        String modelInfo = "§7模型: §f" + (modelName.length() > 25 ? modelName.substring(0, 22) + "..." : modelName);
        guiGraphics.drawCenteredString(this.font, modelInfo, centerX, 46, COLOR_TEXT_SECONDARY);
        
        // 分隔线
        int lineY = HEADER_HEIGHT - 8;
        int lineWidth = PANEL_WIDTH - 40;
        guiGraphics.fill(centerX - lineWidth / 2, lineY, centerX + lineWidth / 2, lineY + 1, COLOR_BORDER);
    }
    
    /**
     * 渲染材质列表
     */
    private void renderMaterialList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startX = (this.width - PANEL_WIDTH) / 2;
        int startY = HEADER_HEIGHT;
        int endY = this.height - FOOTER_HEIGHT;
        
        guiGraphics.enableScissor(startX - 10, startY, startX + PANEL_WIDTH + 10, endY);
        
        hoveredIndex = -1;
        
        for (int i = 0; i < materials.size(); i++) {
            MaterialEntry entry = materials.get(i);
            int itemY = startY + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset + ITEM_SPACING;
            
            if (itemY + ITEM_HEIGHT < startY || itemY > endY) continue;
            
            int itemX = startX;
            boolean isHovered = mouseX >= itemX && mouseX <= itemX + PANEL_WIDTH
                             && mouseY >= Math.max(itemY, startY) 
                             && mouseY <= Math.min(itemY + ITEM_HEIGHT, endY);
            
            if (isHovered) hoveredIndex = i;
            
            renderMaterialItem(guiGraphics, entry, itemX, itemY, isHovered, mouseX, mouseY);
        }
        
        guiGraphics.disableScissor();
    }
    
    /**
     * 渲染单个材质条目
     */
    private void renderMaterialItem(GuiGraphics guiGraphics, MaterialEntry entry, 
                                     int x, int y, boolean isHovered, int mouseX, int mouseY) {
        // 背景
        int bgColor = isHovered ? COLOR_ITEM_HOVER : COLOR_ITEM_BG;
        guiGraphics.fill(x, y, x + PANEL_WIDTH, y + ITEM_HEIGHT, bgColor);
        
        // 左侧边框指示器
        int indicatorColor = entry.visible ? COLOR_TOGGLE_ON : COLOR_TOGGLE_OFF;
        guiGraphics.fill(x, y, x + 3, y + ITEM_HEIGHT, indicatorColor);
        
        // 序号
        String indexStr = String.format("%02d", entry.index + 1);
        guiGraphics.drawString(this.font, indexStr, x + 10, y + 7, COLOR_TEXT_DIM);
        
        // 材质名称
        String displayName = entry.name;
        if (displayName.isEmpty()) displayName = "(未命名)";
        if (displayName.length() > 24) displayName = displayName.substring(0, 21) + "...";
        int nameColor = entry.visible ? COLOR_TEXT_PRIMARY : COLOR_TEXT_SECONDARY;
        guiGraphics.drawString(this.font, displayName, x + 32, y + 7, nameColor);
        
        // 状态文字
        String status = entry.visible ? "显示中" : "已隐藏";
        int statusColor = entry.visible ? COLOR_TOGGLE_ON : COLOR_TEXT_DIM;
        guiGraphics.drawString(this.font, status, x + 32, y + 21, statusColor);
        
        // 开关按钮
        int toggleX = x + PANEL_WIDTH - TOGGLE_WIDTH - 12;
        int toggleY = y + (ITEM_HEIGHT - TOGGLE_HEIGHT) / 2;
        renderToggle(guiGraphics, toggleX, toggleY, entry.visible, 
                     mouseX >= toggleX && mouseX <= toggleX + TOGGLE_WIDTH 
                     && mouseY >= toggleY && mouseY <= toggleY + TOGGLE_HEIGHT);
    }
    
    /**
     * 渲染开关按钮
     */
    private void renderToggle(GuiGraphics guiGraphics, int x, int y, boolean on, boolean hovered) {
        // 背景
        int bgColor = on ? COLOR_TOGGLE_ON : COLOR_TOGGLE_OFF;
        if (hovered) bgColor = brighten(bgColor, 0.2f);
        
        // 圆角矩形背景（使用填充近似）
        guiGraphics.fill(x + 2, y, x + TOGGLE_WIDTH - 2, y + TOGGLE_HEIGHT, bgColor);
        guiGraphics.fill(x, y + 2, x + TOGGLE_WIDTH, y + TOGGLE_HEIGHT - 2, bgColor);
        guiGraphics.fill(x + 1, y + 1, x + TOGGLE_WIDTH - 1, y + TOGGLE_HEIGHT - 1, bgColor);
        
        // 滑块
        int knobSize = TOGGLE_HEIGHT - 6;
        int knobX = on ? x + TOGGLE_WIDTH - knobSize - 4 : x + 4;
        int knobY = y + 3;
        int knobColor = 0xFFFFFFFF;
        
        guiGraphics.fill(knobX + 1, knobY, knobX + knobSize - 1, knobY + knobSize, knobColor);
        guiGraphics.fill(knobX, knobY + 1, knobX + knobSize, knobY + knobSize - 1, knobColor);
        
        // 文字
        String text = on ? "ON" : "OFF";
        int textX = on ? x + 6 : x + TOGGLE_WIDTH - 22;
        int textColor = on ? 0xFF206040 : 0xFFAAAAAA;
        guiGraphics.drawString(this.font, text, textX, y + 7, textColor);
    }
    
    /**
     * 颜色增亮
     */
    private int brighten(int color, float amount) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int)(((color >> 16) & 0xFF) * (1 + amount)));
        int g = Math.min(255, (int)(((color >> 8) & 0xFF) * (1 + amount)));
        int b = Math.min(255, (int)((color & 0xFF) * (1 + amount)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    /**
     * 渲染滚动条
     */
    private void renderScrollbar(GuiGraphics guiGraphics) {
        if (maxScroll <= 0) return;
        
        int scrollbarX = (this.width + PANEL_WIDTH) / 2 + 8;
        int scrollbarY = HEADER_HEIGHT;
        int scrollbarHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT;
        
        // 轨道
        guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight, 0x40FFFFFF);
        
        // 滑块
        int thumbHeight = Math.max(20, scrollbarHeight * scrollbarHeight / (scrollbarHeight + maxScroll));
        int thumbY = scrollbarY + (int)((scrollbarHeight - thumbHeight) * ((float)scrollOffset / maxScroll));
        guiGraphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, COLOR_ACCENT);
    }
    
    /**
     * 渲染底部
     */
    private void renderFooter(GuiGraphics guiGraphics) {
        int centerX = this.width / 2;
        int footerY = this.height - FOOTER_HEIGHT;
        
        // 分隔线
        int lineWidth = PANEL_WIDTH - 40;
        guiGraphics.fill(centerX - lineWidth / 2, footerY + 2, centerX + lineWidth / 2, footerY + 3, COLOR_BORDER);
        
        // 统计信息
        String stats = String.format("§7显示: §a%d §7/ 总计: §f%d", visibleCount, totalCount);
        guiGraphics.drawCenteredString(this.font, stats, centerX, footerY + 6, COLOR_TEXT_SECONDARY);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredIndex >= 0) {
            // 检查是否点击了开关区域
            int startX = (this.width - PANEL_WIDTH) / 2;
            int toggleX = startX + PANEL_WIDTH - TOGGLE_WIDTH - 12;
            
            if (mouseX >= toggleX && mouseX <= toggleX + TOGGLE_WIDTH) {
                toggleMaterial(hoveredIndex);
                return true;
            }
            
            // 点击整行也可以切换
            toggleMaterial(hoveredIndex);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * 25)));
        return true;
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
    public boolean isPauseScreen() {
        return false;
    }
    
    /**
     * 材质条目
     */
    private static class MaterialEntry {
        final int index;
        final String name;
        boolean visible;
        
        MaterialEntry(int index, String name, boolean visible) {
            this.index = index;
            this.name = name;
            this.visible = visible;
        }
    }
}
