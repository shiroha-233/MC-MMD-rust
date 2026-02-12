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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 材质可见性控制界面 — 简约右侧面板风格
 * 右侧面板展示材质开关列表，左侧留空用于模型预览
 */
public class MaterialVisibilityScreen extends Screen {
    private static final Logger logger = LogManager.getLogger();
    
    // 右侧面板布局（与 ModelSelectorScreen 一致）
    private static final int PANEL_WIDTH = 140;
    private static final int PANEL_MARGIN = 4;
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 52;
    private static final int ITEM_HEIGHT = 14;
    private static final int ITEM_SPACING = 1;
    
    // 统一简约配色
    private static final int COLOR_PANEL_BG = 0xC0101418;
    private static final int COLOR_PANEL_BORDER = 0xFF2A3A4A;
    private static final int COLOR_ITEM_HOVER = 0x30FFFFFF;
    private static final int COLOR_ACCENT = 0xFF60A0D0;
    private static final int COLOR_TEXT = 0xFFDDDDDD;
    private static final int COLOR_TEXT_DIM = 0xFF888888;
    private static final int COLOR_SEPARATOR = 0x30FFFFFF;
    private static final int COLOR_VISIBLE = 0xFF60C090;
    private static final int COLOR_HIDDEN = 0xFF666666;
    
    // 模型和材质数据
    private final long modelHandle;
    private final String modelName;
    private final String configModelName; // 配置持久化用的模型文件夹名
    private final List<MaterialEntry> materials;
    
    // UI 状态
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int hoveredIndex = -1;
    private int visibleCount = 0;
    private int totalCount = 0;
    
    // 面板区域缓存
    private int panelX, panelY, panelH;
    private int listTop, listBottom;
    
    public MaterialVisibilityScreen(long modelHandle, String modelName, String configModelName) {
        super(Component.literal("材质可见性"));
        this.modelHandle = modelHandle;
        this.modelName = modelName;
        this.configModelName = configModelName;
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
        
        MMDModelManager.Model model = MMDModelManager.GetModel(modelName, playerName);
        if (model == null) {
            logger.warn("无法获取玩家模型: {}_{}", modelName, playerName);
            return null;
        }
        
        if (model instanceof MMDModelManager.ModelWithEntityData mwed) {
            return new MaterialVisibilityScreen(mwed.model.GetModelLong(), modelName, modelName);
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
            return new MaterialVisibilityScreen(mwed.model.GetModelLong(), displayName, model.getModelName());
        }
        
        return null;
    }
    
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
    
    private void updateCounts() {
        totalCount = materials.size();
        visibleCount = (int) materials.stream().filter(m -> m.visible).count();
    }
    
    @Override
    protected void init() {
        super.init();
        
        panelX = this.width - PANEL_WIDTH - PANEL_MARGIN;
        panelY = PANEL_MARGIN;
        panelH = this.height - PANEL_MARGIN * 2;
        
        listTop = panelY + HEADER_HEIGHT;
        listBottom = panelY + panelH - FOOTER_HEIGHT;
        
        int contentHeight = materials.size() * (ITEM_HEIGHT + ITEM_SPACING);
        int visibleHeight = listBottom - listTop;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        
        // 底部按钮
        int btnY = listBottom + 4;
        int btnW = (PANEL_WIDTH - 16) / 3;
        
        this.addRenderableWidget(Button.builder(Component.literal("全显"), btn -> setAllVisible(true))
            .bounds(panelX + 4, btnY, btnW, 14).build());
        
        this.addRenderableWidget(Button.builder(Component.literal("全隐"), btn -> setAllVisible(false))
            .bounds(panelX + 6 + btnW, btnY, btnW, 14).build());
        
        this.addRenderableWidget(Button.builder(Component.literal("反选"), btn -> invertSelection())
            .bounds(panelX + 8 + btnW * 2, btnY, btnW, 14).build());
        
        this.addRenderableWidget(Button.builder(Component.literal("完成"), btn -> this.onClose())
            .bounds(panelX + 4, btnY + 18, PANEL_WIDTH - 8, 14).build());
    }
    
    private void setAllVisible(boolean visible) {
        NativeFunc nf = NativeFunc.GetInst();
        nf.SetAllMaterialsVisible(modelHandle, visible);
        for (MaterialEntry entry : materials) {
            entry.visible = visible;
        }
        updateCounts();
    }
    
    private void invertSelection() {
        NativeFunc nf = NativeFunc.GetInst();
        for (MaterialEntry entry : materials) {
            entry.visible = !entry.visible;
            nf.SetMaterialVisible(modelHandle, entry.index, entry.visible);
        }
        updateCounts();
    }
    
    private void toggleMaterial(int index) {
        if (index < 0 || index >= materials.size()) return;
        
        MaterialEntry entry = materials.get(index);
        entry.visible = !entry.visible;
        
        NativeFunc nf = NativeFunc.GetInst();
        nf.SetMaterialVisible(modelHandle, entry.index, entry.visible);
        updateCounts();
    }
    
    @Override
    public void onClose() {
        // 保存材质可见性到模型配置
        saveMaterialVisibility();
        super.onClose();
    }

    private void saveMaterialVisibility() {
        try {
            ModelConfigData config = ModelConfigManager.getConfig(configModelName);
            config.hiddenMaterials.clear();
            for (MaterialEntry entry : materials) {
                if (!entry.visible) {
                    config.hiddenMaterials.add(entry.index);
                }
            }
            ModelConfigManager.saveConfig(configModelName, config);
            logger.info("已保存材质可见性配置: {}", configModelName);
        } catch (Exception e) {
            logger.warn("保存材质可见性配置失败: {}", configModelName, e);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 右侧面板背景
        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, COLOR_PANEL_BG);
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelH, COLOR_PANEL_BORDER);
        
        // 头部
        renderHeader(guiGraphics);
        
        // 材质列表（裁剪区域）
        guiGraphics.enableScissor(panelX, listTop, panelX + PANEL_WIDTH, listBottom);
        renderMaterialList(guiGraphics, mouseX, mouseY);
        guiGraphics.disableScissor();
        
        // 滚动条
        renderScrollbar(guiGraphics);
        
        // 底部统计
        renderFooterStats(guiGraphics);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void renderHeader(GuiGraphics guiGraphics) {
        int cx = panelX + PANEL_WIDTH / 2;
        guiGraphics.drawCenteredString(this.font, this.title, cx, panelY + 4, COLOR_ACCENT);
        
        String info = truncate(modelName, 18);
        guiGraphics.drawCenteredString(this.font, info, cx, panelY + 16, COLOR_TEXT_DIM);
        
        guiGraphics.fill(panelX + 8, listTop - 2, panelX + PANEL_WIDTH - 8, listTop - 1, COLOR_SEPARATOR);
    }
    
    private void renderMaterialList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        hoveredIndex = -1;
        
        for (int i = 0; i < materials.size(); i++) {
            MaterialEntry entry = materials.get(i);
            int itemY = listTop + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;
            
            if (itemY + ITEM_HEIGHT < listTop || itemY > listBottom) continue;
            
            boolean isHovered = mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
                             && mouseY >= Math.max(itemY, listTop) 
                             && mouseY <= Math.min(itemY + ITEM_HEIGHT, listBottom);
            
            if (isHovered) hoveredIndex = i;
            
            renderMaterialItem(guiGraphics, entry, panelX, itemY, PANEL_WIDTH, isHovered);
        }
    }
    
    private void renderMaterialItem(GuiGraphics guiGraphics, MaterialEntry entry,
                                     int x, int y, int w, boolean isHovered) {
        // 悬停背景
        if (isHovered) {
            guiGraphics.fill(x, y, x + w, y + ITEM_HEIGHT, COLOR_ITEM_HOVER);
        }
        
        // 左侧可见性指示条
        int barColor = entry.visible ? COLOR_VISIBLE : COLOR_HIDDEN;
        guiGraphics.fill(x, y + 1, x + 2, y + ITEM_HEIGHT - 1, barColor);
        
        // 材质名称（紧凑，不显示序号）
        String displayName = entry.name.isEmpty() ? "(未命名)" : truncate(entry.name, 16);
        int nameColor = entry.visible ? COLOR_TEXT : COLOR_TEXT_DIM;
        guiGraphics.drawString(this.font, displayName, x + 6, y + 3, nameColor);
        
        // 右侧状态标签
        String tag = entry.visible ? "ON" : "OFF";
        int tagColor = entry.visible ? COLOR_VISIBLE : COLOR_HIDDEN;
        int tagW = this.font.width(tag);
        guiGraphics.drawString(this.font, tag, x + w - tagW - 4, y + 3, tagColor);
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
    
    private void renderFooterStats(GuiGraphics guiGraphics) {
        int cx = panelX + PANEL_WIDTH / 2;
        int statsY = panelY + panelH - 10;
        String stats = visibleCount + " / " + totalCount;
        guiGraphics.drawCenteredString(this.font, stats, cx, statsY, COLOR_TEXT_DIM);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredIndex >= 0) {
            toggleMaterial(hoveredIndex);
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
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 2) + ".." : s;
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
