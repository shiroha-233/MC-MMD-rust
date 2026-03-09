package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialEntryState;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialScreenContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 材质可见性控制界面 — 简约右侧面板风格
 * 右侧面板展示材质开关列表，左侧留空用于模型预览
 */
public class MaterialVisibilityScreen extends Screen {

    private static final int PANEL_WIDTH = 140;
    private static final int PANEL_MARGIN = 4;
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 52;
    private static final int ITEM_HEIGHT = 14;
    private static final int ITEM_SPACING = 1;

    private static final int COLOR_PANEL_BG = 0xC0101418;
    private static final int COLOR_PANEL_BORDER = 0xFF2A3A4A;
    private static final int COLOR_ITEM_HOVER = 0x30FFFFFF;
    private static final int COLOR_ACCENT = 0xFF60A0D0;
    private static final int COLOR_TEXT = 0xFFDDDDDD;
    private static final int COLOR_TEXT_DIM = 0xFF888888;
    private static final int COLOR_SEPARATOR = 0x30FFFFFF;
    private static final int COLOR_VISIBLE = 0xFF60C090;
    private static final int COLOR_HIDDEN = 0xFF666666;
    private static final MaterialVisibilityApplicationService SERVICE = ModelSelectorServices.materialVisibility();

    private final MaterialScreenContext context;
    private final List<MaterialEntryState> materials;

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int hoveredIndex = -1;
    private int visibleCount = 0;
    private int totalCount = 0;

    private int panelX, panelY, panelH;
    private int listTop, listBottom;

    public MaterialVisibilityScreen(MaterialScreenContext context) {
        super(Component.translatable("gui.mmdskin.material_visibility.title"));
        this.context = context;
        this.materials = new ArrayList<>();
        loadMaterials();
    }

    public static MaterialVisibilityScreen createForPlayer() {
        return SERVICE.createPlayerContext().map(MaterialVisibilityScreen::new).orElse(null);
    }

    public static MaterialVisibilityScreen createForMaid(java.util.UUID maidUUID, String maidName) {
        return SERVICE.createMaidContext(maidUUID, maidName).map(MaterialVisibilityScreen::new).orElse(null);
    }

    private void loadMaterials() {
        materials.clear();
        materials.addAll(SERVICE.loadMaterials(context));
        updateCounts();
    }

    private void updateCounts() {
        totalCount = materials.size();
        visibleCount = (int) materials.stream().filter(MaterialEntryState::visible).count();
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

        int btnRow1Y = listBottom + 4;
        int btnRow2Y = btnRow1Y + 16;
        int btnW = (PANEL_WIDTH - 16) / 3;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.material_visibility.show_all"), btn -> setAllVisible(true))
            .bounds(panelX + 4, btnRow1Y, btnW, 14).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.material_visibility.hide_all"), btn -> setAllVisible(false))
            .bounds(panelX + 4 + btnW + 4, btnRow1Y, btnW, 14).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.material_visibility.invert"), btn -> invertSelection())
            .bounds(panelX + 4 + (btnW + 4) * 2, btnRow1Y, btnW, 14).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> this.onClose())
            .bounds(panelX + 4, btnRow2Y, PANEL_WIDTH - 8, 14).build());
    }

    private void setAllVisible(boolean visible) {
        SERVICE.setAllVisible(context, materials, visible);
        updateCounts();
    }

    private void invertSelection() {
        SERVICE.invertSelection(context, materials);
        updateCounts();
    }

    private void toggleMaterial(int index) {
        SERVICE.toggleMaterial(context, materials, index);
        updateCounts();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, COLOR_PANEL_BG);

        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelH, COLOR_PANEL_BORDER);

        renderHeader(guiGraphics);

        renderMaterialList(guiGraphics, mouseX, mouseY);

        renderScrollbar(guiGraphics);

        renderFooterStats(guiGraphics);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics guiGraphics) {
        int cx = panelX + PANEL_WIDTH / 2;

        guiGraphics.drawCenteredString(this.font, this.title, cx, panelY + 4, COLOR_ACCENT);

        String info = truncate(context.modelName(), 18);
        guiGraphics.drawCenteredString(this.font, info, cx, panelY + 16, COLOR_TEXT_DIM);

        guiGraphics.fill(panelX + 8, listTop - 2, panelX + PANEL_WIDTH - 8, listTop - 1, COLOR_SEPARATOR);
    }

    private void renderMaterialList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.enableScissor(panelX, listTop, panelX + PANEL_WIDTH, listBottom);

        hoveredIndex = -1;

        for (int i = 0; i < materials.size(); i++) {
            MaterialEntryState entry = materials.get(i);
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

    private void renderMaterialItem(GuiGraphics guiGraphics, MaterialEntryState entry,
                                      int x, int y, int w, boolean isHovered) {

        if (isHovered) {
            guiGraphics.fill(x, y, x + w, y + ITEM_HEIGHT, COLOR_ITEM_HOVER);
        }

        int barColor = entry.visible() ? COLOR_VISIBLE : COLOR_HIDDEN;
        guiGraphics.fill(x, y + 1, x + 2, y + ITEM_HEIGHT - 1, barColor);

        String displayName = entry.name().isEmpty() ? Component.translatable("gui.mmdskin.material_visibility.unnamed").getString() : truncate(entry.name(), 16);
        int nameColor = entry.visible() ? COLOR_TEXT : COLOR_TEXT_DIM;
        guiGraphics.drawString(this.font, displayName, x + 6, y + 3, nameColor);

        String tag = Component.translatable(entry.visible() ? "gui.mmdskin.material_visibility.on" : "gui.mmdskin.material_visibility.off").getString();
        int tagColor = entry.visible() ? COLOR_VISIBLE : COLOR_HIDDEN;
        int tagW = this.font.width(tag);
        guiGraphics.drawString(this.font, tag, x + w - tagW - 4, y + 3, tagColor);
    }

    private void renderScrollbar(GuiGraphics guiGraphics) {
        if (maxScroll <= 0) return;

        int barX = panelX + PANEL_WIDTH - 4;
        int barH = listBottom - listTop;

        guiGraphics.fill(barX, listTop, barX + 2, listBottom, 0x20FFFFFF);

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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {

        if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(delta * 24)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        saveMaterialVisibility();
        super.onClose();
    }

    private void saveMaterialVisibility() {
        SERVICE.save(context, materials);
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

}
