package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.config.ModelAnimConfig;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.renderer.runtime.animation.MMDAnimManager;
import com.shiroha.mmdskin.player.runtime.EntityAnimState;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 模型动画映射配置界面。 */
public class ModelAnimationScreen extends Screen {
    private static final Logger logger = LogManager.getLogger();

    private static final int PANEL_WIDTH = 160;
    private static final int PANEL_MARGIN = 4;
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 36;
    private static final int ITEM_HEIGHT = 16;
    private static final int ITEM_SPACING = 1;

    private static final int COLOR_PANEL_BG = 0xC0101418;
    private static final int COLOR_PANEL_BORDER = 0xFF2A3A4A;
    private static final int COLOR_ACCENT = 0xFF60A0D0;
    private static final int COLOR_TEXT = 0xFFDDDDDD;
    private static final int COLOR_TEXT_DIM = 0xFF888888;
    private static final int COLOR_SEPARATOR = 0x30FFFFFF;
    private static final int COLOR_ITEM_HOVER = 0x30FFFFFF;
    private static final int COLOR_MAPPED = 0xFF40C080;
    private static final int COLOR_UNMAPPED = 0xFF505560;
    private static final int COLOR_VMD_ITEM_BG = 0x40203040;
    private static final int COLOR_VMD_ITEM_HOVER = 0x60305070;

    private final String modelName;
    private final String modelDir;
    private final Screen parentScreen;

    private final List<SlotEntry> slots = new ArrayList<>();

    private final List<String> availableVmds = new ArrayList<>();

    private final Map<String, String> editMapping = new LinkedHashMap<>();

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int panelX, panelY, panelH;
    private int listTop, listBottom;

    private int expandedSlot = -1;

    public ModelAnimationScreen(String modelName, Screen parentScreen) {
        super(Component.translatable("gui.mmdskin.model_anim.title"));
        this.modelName = modelName;
        this.modelDir = PathConstants.getModelDir(modelName).getAbsolutePath();
        this.parentScreen = parentScreen;

        initSlots();
        scanAvailableVmds();
        loadMapping();
    }

    private void initSlots() {
        for (EntityAnimState.State state : EntityAnimState.State.values()) {
            slots.add(new SlotEntry(state.propertyName, getSlotDisplayName(state)));
        }
    }

    private String getSlotDisplayName(EntityAnimState.State state) {
        String key = "gui.mmdskin.model_anim.slot." + state.propertyName;
        Component c = Component.translatable(key);
        String result = c.getString();

        return result.equals(key) ? state.propertyName : result;
    }

    private void scanAvailableVmds() {
        availableVmds.clear();
        FileFilter vmdFilter = f -> f.isFile() && f.getName().toLowerCase().endsWith(".vmd");

        File animsDir = PathConstants.getModelAnimsDirByPath(modelDir);
        if (!animsDir.exists()) {
            PathConstants.ensureDirectoryExists(animsDir);
        }
        File[] animFiles = animsDir.listFiles(vmdFilter);
        if (animFiles != null) {
            for (File f : animFiles) {
                availableVmds.add(f.getName());
            }
        }

        File[] rootFiles = new File(modelDir).listFiles(vmdFilter);
        if (rootFiles != null) {
            for (File f : rootFiles) {
                if (!availableVmds.contains(f.getName())) {
                    availableVmds.add(f.getName());
                }
            }
        }

        availableVmds.sort(String.CASE_INSENSITIVE_ORDER);
    }

    private void loadMapping() {
        editMapping.clear();
        editMapping.putAll(ModelAnimConfig.getMapping(modelDir));
    }

    @Override
    protected void init() {
        super.init();

        panelX = this.width - PANEL_WIDTH - PANEL_MARGIN;
        panelY = PANEL_MARGIN;
        panelH = this.height - PANEL_MARGIN * 2;

        listTop = panelY + HEADER_HEIGHT;
        listBottom = panelY + panelH - FOOTER_HEIGHT;

        updateMaxScroll();

        int btnY = listBottom + 4;
        int btnW = (PANEL_WIDTH - 16) / 3;

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.mmdskin.model_anim.save"), btn -> saveAndApply())
            .bounds(panelX + 4, btnY, btnW, 14).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.mmdskin.model_anim.clear"), btn -> clearAll())
            .bounds(panelX + 8 + btnW, btnY, btnW, 14).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.mmdskin.refresh"), btn -> refresh())
            .bounds(panelX + 12 + btnW * 2, btnY, btnW, 14).build());
    }

    private void updateMaxScroll() {
        int contentHeight = calculateContentHeight();
        int visibleHeight = listBottom - listTop;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
    }

    private int calculateContentHeight() {
        int h = 0;
        for (int i = 0; i < slots.size(); i++) {
            h += ITEM_HEIGHT + ITEM_SPACING;
            if (i == expandedSlot) {

                h += (availableVmds.size() + 1) * (ITEM_HEIGHT + ITEM_SPACING);
            }
        }
        return h;
    }

    private void saveAndApply() {
        ModelAnimConfig.saveMapping(modelDir, editMapping);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String selectedModel = ModelSelectorConfig.getInstance().getSelectedModel();
            if (modelName.equals(selectedModel)) {
                MMDModelManager.Model model = MMDModelManager.GetModel(
                        selectedModel,
                        PlayerModelResolver.getCacheKey(mc.player)
                );
                if (model != null) {
                    MMDAnimManager.invalidateAnimCache(model.model);

                    model.model.changeAnim(MMDAnimManager.GetAnimModel(model.model, "idle"), 0);
                }
            }
        }

        this.onClose();
    }

    private void clearAll() {
        editMapping.clear();
        expandedSlot = -1;
    }

    private void refresh() {
        scanAvailableVmds();
        expandedSlot = -1;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, COLOR_PANEL_BG);
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelH, COLOR_PANEL_BORDER);

        renderHeader(guiGraphics);

        guiGraphics.enableScissor(panelX, listTop, panelX + PANEL_WIDTH, listBottom);
        renderSlotList(guiGraphics, mouseX, mouseY);
        guiGraphics.disableScissor();

        renderScrollbar(guiGraphics);

        renderFooterStats(guiGraphics);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics guiGraphics) {
        int cx = panelX + PANEL_WIDTH / 2;
        guiGraphics.drawCenteredString(this.font, this.title, cx, panelY + 4, COLOR_ACCENT);
        String info = truncate(modelName, 22);
        guiGraphics.drawCenteredString(this.font, info, cx, panelY + 16, COLOR_TEXT_DIM);
        guiGraphics.fill(panelX + 8, listTop - 2, panelX + PANEL_WIDTH - 8, listTop - 1, COLOR_SEPARATOR);
    }

    private void renderSlotList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int y = listTop - scrollOffset;
        int itemX = panelX + 4;
        int itemW = PANEL_WIDTH - 12;

        for (int i = 0; i < slots.size(); i++) {
            SlotEntry slot = slots.get(i);
            String mapped = editMapping.get(slot.name);
            boolean isMapped = mapped != null && !mapped.isEmpty();
            boolean isExpanded = (i == expandedSlot);

            if (y + ITEM_HEIGHT > listTop && y < listBottom) {
                boolean isHovered = mouseX >= itemX && mouseX <= itemX + itemW
                                 && mouseY >= Math.max(y, listTop)
                                 && mouseY <= Math.min(y + ITEM_HEIGHT, listBottom);

                renderSlotItem(guiGraphics, slot, mapped, isMapped, isExpanded, isHovered,
                               itemX, y, itemW);
            }
            y += ITEM_HEIGHT + ITEM_SPACING;

            if (isExpanded) {

                if (y + ITEM_HEIGHT > listTop && y < listBottom) {
                    boolean clearHovered = mouseX >= itemX + 8 && mouseX <= itemX + itemW
                                        && mouseY >= Math.max(y, listTop)
                                        && mouseY <= Math.min(y + ITEM_HEIGHT, listBottom);
                    int clearBg = clearHovered ? COLOR_VMD_ITEM_HOVER : COLOR_VMD_ITEM_BG;
                    guiGraphics.fill(itemX + 8, y, itemX + itemW, y + ITEM_HEIGHT, clearBg);
                    String clearLabel = Component.translatable("gui.mmdskin.model_anim.clear_slot").getString();
                    guiGraphics.drawString(this.font, "× " + clearLabel, itemX + 12, y + 4, COLOR_TEXT_DIM);
                }
                y += ITEM_HEIGHT + ITEM_SPACING;

                for (int j = 0; j < availableVmds.size(); j++) {
                    if (y + ITEM_HEIGHT > listTop && y < listBottom) {
                        String vmd = availableVmds.get(j);
                        boolean vmdHovered = mouseX >= itemX + 8 && mouseX <= itemX + itemW
                                          && mouseY >= Math.max(y, listTop)
                                          && mouseY <= Math.min(y + ITEM_HEIGHT, listBottom);
                        boolean isSelected = vmd.equals(mapped);

                        int bg = vmdHovered ? COLOR_VMD_ITEM_HOVER : COLOR_VMD_ITEM_BG;
                        guiGraphics.fill(itemX + 8, y, itemX + itemW, y + ITEM_HEIGHT, bg);

                        if (isSelected) {
                            guiGraphics.fill(itemX + 8, y + 1, itemX + 10, y + ITEM_HEIGHT - 1, COLOR_MAPPED);
                        }

                        String displayVmd = truncate(vmd.replace(".vmd", "").replace(".VMD", ""), 18);
                        int vmdColor = isSelected ? COLOR_MAPPED : COLOR_TEXT;
                        guiGraphics.drawString(this.font, displayVmd, itemX + 14, y + 4, vmdColor);
                    }
                    y += ITEM_HEIGHT + ITEM_SPACING;
                }
            }
        }
    }

    private void renderSlotItem(GuiGraphics guiGraphics, SlotEntry slot, String mapped,
                                 boolean isMapped, boolean isExpanded, boolean isHovered,
                                 int x, int y, int w) {

        if (isHovered || isExpanded) {
            guiGraphics.fill(x, y, x + w, y + ITEM_HEIGHT, COLOR_ITEM_HOVER);
        }

        int barColor = isMapped ? COLOR_MAPPED : COLOR_UNMAPPED;
        guiGraphics.fill(x, y + 1, x + 2, y + ITEM_HEIGHT - 1, barColor);

        String arrow = isExpanded ? "▼ " : "▶ ";
        guiGraphics.drawString(this.font, arrow, x + 4, y + 4, COLOR_TEXT_DIM);

        guiGraphics.drawString(this.font, slot.displayName, x + 16, y + 4, COLOR_TEXT);

        if (isMapped) {
            String vmdName = truncate(mapped.replace(".vmd", ""), 8);
            int tagW = this.font.width(vmdName);
            guiGraphics.drawString(this.font, vmdName, x + w - tagW - 2, y + 4, COLOR_MAPPED);
        }
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
        long mappedCount = editMapping.values().stream().filter(v -> v != null && !v.isEmpty()).count();
        String stats = mappedCount + " / " + slots.size() + " · VMD: " + availableVmds.size();
        guiGraphics.drawCenteredString(this.font, stats, cx, statsY, COLOR_TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
                && mouseY >= listTop && mouseY <= listBottom) {
            return handleListClick(mouseX, mouseY);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleListClick(double mouseX, double mouseY) {
        int y = listTop - scrollOffset;
        int itemX = panelX + 4;
        int itemW = PANEL_WIDTH - 12;

        for (int i = 0; i < slots.size(); i++) {

            if (mouseY >= y && mouseY < y + ITEM_HEIGHT
                    && mouseX >= itemX && mouseX <= itemX + itemW) {
                if (expandedSlot == i) {
                    expandedSlot = -1;
                } else {
                    expandedSlot = i;
                }
                updateMaxScroll();
                return true;
            }
            y += ITEM_HEIGHT + ITEM_SPACING;

            if (i == expandedSlot) {

                if (mouseY >= y && mouseY < y + ITEM_HEIGHT
                        && mouseX >= itemX + 8 && mouseX <= itemX + itemW) {
                    editMapping.remove(slots.get(i).name);
                    expandedSlot = -1;
                    updateMaxScroll();
                    return true;
                }
                y += ITEM_HEIGHT + ITEM_SPACING;

                for (int j = 0; j < availableVmds.size(); j++) {
                    if (mouseY >= y && mouseY < y + ITEM_HEIGHT
                            && mouseX >= itemX + 8 && mouseX <= itemX + itemW) {
                        editMapping.put(slots.get(expandedSlot).name, availableVmds.get(j));
                        expandedSlot = -1;
                        updateMaxScroll();
                        return true;
                    }
                    y += ITEM_HEIGHT + ITEM_SPACING;
                }
            }
        }
        return false;
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

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 2) + ".." : s;
    }

    private static class SlotEntry {
        final String name;
        final String displayName;

        SlotEntry(String name, String displayName) {
            this.name = name;
            this.displayName = displayName;
        }
    }
}
