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
    
    // 统一简约配色（与 ModelSelectorScreen 完全一致）
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
        super(Component.translatable("gui.mmdskin.material_visibility.title"));
        this.modelHandle = modelHandle;
        this.modelName = modelName;
        this.configModelName = configModelName;
        this.materials = new ArrayList<>();
        loadMaterials();
    }

    // MC 1.21.1: 禁用默认背景模糊效果
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
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
        
        return new MaterialVisibilityScreen(model.model.getModelHandle(), modelName, modelName);
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
        
        String displayName = maidName != null ? maidName : Component.translatable("gui.mmdskin.maid.default_name").getString();
        return new MaterialVisibilityScreen(model.model.getModelHandle(), displayName, model.getModelName());
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
        
        // 面板位置：屏幕右侧（与 ModelSelectorScreen 一致）
        panelX = this.width - PANEL_WIDTH - PANEL_MARGIN;
        panelY = PANEL_MARGIN;
        panelH = this.height - PANEL_MARGIN * 2;
        
        listTop = panelY + HEADER_HEIGHT;
        listBottom = panelY + panelH - FOOTER_HEIGHT;
        
        // 计算滚动
        int contentHeight = materials.size() * (ITEM_HEIGHT + ITEM_SPACING);
        int visibleHeight = listBottom - listTop;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        
        // 按钮区域（面板底部，两行紧凑排列）
        int btnRow1Y = listBottom + 4;
        int btnRow2Y = btnRow1Y + 16;
        int btnW = (PANEL_WIDTH - 16) / 3;
        
        // 第一行：全显 / 全隐 / 反选
        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.material_visibility.show_all"), btn -> setAllVisible(true))
            .bounds(panelX + 4, btnRow1Y, btnW, 14).build());
        
        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.material_visibility.hide_all"), btn -> setAllVisible(false))
            .bounds(panelX + 4 + btnW + 4, btnRow1Y, btnW, 14).build());
        
        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.material_visibility.invert"), btn -> invertSelection())
            .bounds(panelX + 4 + (btnW + 4) * 2, btnRow1Y, btnW, 14).build());
        
        // 第二行：完成
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> this.onClose())
            .bounds(panelX + 4, btnRow2Y, PANEL_WIDTH - 8, 14).build());
    }
    
    /**
     * 设置所有材质可见性
     */
    private void setAllVisible(boolean visible) {
        try {
            NativeFunc nf = NativeFunc.GetInst();
            nf.SetAllMaterialsVisible(modelHandle, visible);
            for (MaterialEntry entry : materials) {
                entry.visible = visible;
            }
            updateCounts();
        } catch (Exception e) {
            logger.warn("材质操作失败，模型可能已被释放", e);
        }
    }
    
    /**
     * 反选所有材质
     */
    private void invertSelection() {
        try {
            NativeFunc nf = NativeFunc.GetInst();
            for (MaterialEntry entry : materials) {
                entry.visible = !entry.visible;
                nf.SetMaterialVisible(modelHandle, entry.index, entry.visible);
            }
            updateCounts();
        } catch (Exception e) {
            logger.warn("材质操作失败，模型可能已被释放", e);
        }
    }
    
    /**
     * 切换单个材质可见性
     */
    private void toggleMaterial(int index) {
        if (index < 0 || index >= materials.size()) return;
        
        MaterialEntry entry = materials.get(index);
        entry.visible = !entry.visible;
        
        try {
            NativeFunc nf = NativeFunc.GetInst();
            nf.SetMaterialVisible(modelHandle, entry.index, entry.visible);
        } catch (Exception e) {
            logger.warn("材质操作失败，模型可能已被释放", e);
        }
        updateCounts();
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 不渲染全屏背景，保持左侧透明用于模型预览
        
        // 右侧面板背景
        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, COLOR_PANEL_BG);
        // 面板左边框
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelH, COLOR_PANEL_BORDER);
        
        // 头部
        renderHeader(guiGraphics);
        
        // 材质列表
        renderMaterialList(guiGraphics, mouseX, mouseY);
        
        // 滚动条
        renderScrollbar(guiGraphics);
        
        // 底部统计
        renderFooterStats(guiGraphics);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    /**
     * 渲染头部
     */
    private void renderHeader(GuiGraphics guiGraphics) {
        int cx = panelX + PANEL_WIDTH / 2;
        
        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, cx, panelY + 4, COLOR_ACCENT);
        
        // 模型名称
        String info = truncate(modelName, 18);
        guiGraphics.drawCenteredString(this.font, info, cx, panelY + 16, COLOR_TEXT_DIM);
        
        // 分隔线
        guiGraphics.fill(panelX + 8, listTop - 2, panelX + PANEL_WIDTH - 8, listTop - 1, COLOR_SEPARATOR);
    }
    
    /**
     * 渲染材质列表
     */
    private void renderMaterialList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.enableScissor(panelX, listTop, panelX + PANEL_WIDTH, listBottom);
        
        hoveredIndex = -1;
        
        for (int i = 0; i < materials.size(); i++) {
            MaterialEntry entry = materials.get(i);
            int itemY = listTop + i * (ITEM_HEIGHT + ITEM_SPACING) - scrollOffset;
            
            if (itemY + ITEM_HEIGHT < listTop || itemY > listBottom) continue;
            
            int itemX = panelX + 6;
            int itemW = PANEL_WIDTH - 12;
            boolean isHovered = mouseX >= itemX && mouseX <= itemX + itemW
                             && mouseY >= Math.max(itemY, listTop) 
                             && mouseY <= Math.min(itemY + ITEM_HEIGHT, listBottom);
            
            if (isHovered) hoveredIndex = i;
            
            renderMaterialItem(guiGraphics, entry, itemX, itemY, itemW, isHovered);
        }
        
        guiGraphics.disableScissor();
    }
    
    /**
     * 渲染单个材质条目 — 简约风格
     */
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
        String displayName = entry.name.isEmpty() ? Component.translatable("gui.mmdskin.material_visibility.unnamed").getString() : truncate(entry.name, 16);
        int nameColor = entry.visible ? COLOR_TEXT : COLOR_TEXT_DIM;
        guiGraphics.drawString(this.font, displayName, x + 6, y + 3, nameColor);
        
        // 右侧状态标签
        String tag = Component.translatable(entry.visible ? "gui.mmdskin.material_visibility.on" : "gui.mmdskin.material_visibility.off").getString();
        int tagColor = entry.visible ? COLOR_VISIBLE : COLOR_HIDDEN;
        int tagW = this.font.width(tag);
        guiGraphics.drawString(this.font, tag, x + w - tagW - 4, y + 3, tagColor);
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
    
    /**
     * 渲染底部统计
     */
    private void renderFooterStats(GuiGraphics guiGraphics) {
        // 统计信息放在面板最底部（按钮下方）
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
    public void onClose() {
        saveMaterialVisibility();
        super.onClose();
    }
    
    /**
     * 保存当前材质可见性状态到模型配置
     */
    private void saveMaterialVisibility() {
        if (configModelName == null || configModelName.isEmpty()) return;
        
        try {
            ModelConfigData config = ModelConfigManager.getConfig(configModelName);
            Set<Integer> hidden = new HashSet<>();
            for (MaterialEntry entry : materials) {
                if (!entry.visible) {
                    hidden.add(entry.index);
                }
            }
            config.hiddenMaterials = hidden;
            ModelConfigManager.saveConfig(configModelName, config);
            logger.debug("材质可见性已保存: {} (隐藏 {})", configModelName, hidden.size());
        } catch (Exception e) {
            logger.warn("保存材质可见性失败: {}", configModelName, e);
        }
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
