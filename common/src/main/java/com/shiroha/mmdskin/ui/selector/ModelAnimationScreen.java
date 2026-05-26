/* 文件职责：提供模型动画映射配置界面。 */
package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.config.ModelAnimConfig;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.player.runtime.EntityAnimState;
import com.shiroha.mmdskin.renderer.runtime.animation.MMDAnimManager;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
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

public class ModelAnimationScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final int PANEL_WIDTH = 160;
    private static final int PANEL_MARGIN = 4;
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 36;
    private static final int ITEM_HEIGHT = 16;
    private static final int ITEM_SPACING = 1;

    private static final int COLOR_TEXT = TranslucentTrayChrome.BODY_TEXT;
    private static final int COLOR_TEXT_DIM = TranslucentTrayChrome.SUBTITLE_TEXT;
    private static final int COLOR_MAPPED = 0xFF40C080;
    private static final int COLOR_UNMAPPED = 0xFF505560;
    private static final int COLOR_VMD_ITEM_BG = TranslucentTrayChrome.CARD_BACKGROUND;
    private static final int COLOR_VMD_ITEM_HOVER = TranslucentTrayChrome.CARD_HOVER;

    private final String modelName;
    private final String modelDir;
    private final Screen parentScreen;
    private final List<SlotEntry> slots = new ArrayList<>();
    private final List<String> availableVmds = new ArrayList<>();
    private final Map<String, String> editMapping = new LinkedHashMap<>();

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int panelX;
    private int panelY;
    private int panelH;
    private int listTop;
    private int listBottom;
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
        FileFilter vmdFilter = file -> file.isFile() && file.getName().toLowerCase().endsWith(".vmd");

        File animsDir = PathConstants.getModelAnimsDirByPath(modelDir);
        if (!animsDir.exists()) {
            PathConstants.ensureDirectoryExists(animsDir);
        }
        File[] animFiles = animsDir.listFiles(vmdFilter);
        if (animFiles != null) {
            for (File file : animFiles) {
                availableVmds.add(file.getName());
            }
        }

        File[] rootFiles = new File(modelDir).listFiles(vmdFilter);
        if (rootFiles != null) {
            for (File file : rootFiles) {
                if (!availableVmds.contains(file.getName())) {
                    availableVmds.add(file.getName());
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
        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.model_anim.save"), btn -> saveAndApply())
                .bounds(panelX + 4, btnY, btnW, 14).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.model_anim.clear"), btn -> clearAll())
                .bounds(panelX + 8 + btnW, btnY, btnW, 14).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.mmdskin.refresh"), btn -> refresh())
                .bounds(panelX + 12 + btnW * 2, btnY, btnW, 14).build());
    }

    private void updateMaxScroll() {
        int contentHeight = calculateContentHeight();
        int visibleHeight = listBottom - listTop;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
    }

    private int calculateContentHeight() {
        int height = 0;
        for (int i = 0; i < slots.size(); i++) {
            height += ITEM_HEIGHT + ITEM_SPACING;
            if (i == expandedSlot) {
                height += (availableVmds.size() + 1) * (ITEM_HEIGHT + ITEM_SPACING);
            }
        }
        return height;
    }

    private void saveAndApply() {
        ModelAnimConfig.saveMapping(modelDir, editMapping);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String selectedModel = ModelSelectorConfig.getInstance().getSelectedModel();
            if (modelName.equals(selectedModel)) {
                MMDModelManager.Model model = MMDModelManager.GetModel(selectedModel, PlayerModelResolver.getCacheKey(mc.player));
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
        TranslucentTrayChrome.drawOverlay(guiGraphics, this.width, this.height);
        TranslucentTrayChrome.drawPanel(guiGraphics, panelX, panelY, PANEL_WIDTH, panelH);

        renderHeader(guiGraphics);
        guiGraphics.enableScissor(panelX, listTop, panelX + PANEL_WIDTH, listBottom);
        renderSlotList(guiGraphics, mouseX, mouseY);
        guiGraphics.disableScissor();
        renderScrollbar(guiGraphics);
        renderFooterStats(guiGraphics);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics guiGraphics) {
        int centerX = panelX + PANEL_WIDTH / 2;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, panelY + 4, TranslucentTrayChrome.TITLE_TEXT);
        guiGraphics.drawCenteredString(this.font, truncate(modelName, 22), centerX, panelY + 16, COLOR_TEXT_DIM);
        TranslucentTrayChrome.drawSeparator(guiGraphics, panelX + 8, listTop - 2, PANEL_WIDTH - 16);
    }

    private void renderSlotList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int y = listTop - scrollOffset;
        int itemX = panelX + 4;
        int itemW = PANEL_WIDTH - 12;

        for (int i = 0; i < slots.size(); i++) {
            SlotEntry slot = slots.get(i);
            String mapped = editMapping.get(slot.name);
            boolean isMapped = mapped != null && !mapped.isEmpty();
            boolean isExpanded = i == expandedSlot;

            if (y + ITEM_HEIGHT > listTop && y < listBottom) {
                boolean isHovered = mouseX >= itemX && mouseX <= itemX + itemW
                        && mouseY >= Math.max(y, listTop)
                        && mouseY <= Math.min(y + ITEM_HEIGHT, listBottom);
                renderSlotItem(guiGraphics, slot, mapped, isMapped, isExpanded, isHovered, itemX, y, itemW);
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

                for (String vmd : availableVmds) {
                    if (y + ITEM_HEIGHT > listTop && y < listBottom) {
                        boolean hovered = mouseX >= itemX + 8 && mouseX <= itemX + itemW
                                && mouseY >= Math.max(y, listTop)
                                && mouseY <= Math.min(y + ITEM_HEIGHT, listBottom);
                        boolean selected = vmd.equals(mapped);
                        int bg = hovered ? COLOR_VMD_ITEM_HOVER : COLOR_VMD_ITEM_BG;
                        guiGraphics.fill(itemX + 8, y, itemX + itemW, y + ITEM_HEIGHT, bg);
                        if (selected) {
                            guiGraphics.fill(itemX + 8, y + 1, itemX + 10, y + ITEM_HEIGHT - 1, COLOR_MAPPED);
                        }
                        String display = truncate(vmd.replace(".vmd", "").replace(".VMD", ""), 18);
                        guiGraphics.drawString(this.font, display, itemX + 14, y + 4, selected ? COLOR_MAPPED : COLOR_TEXT);
                    }
                    y += ITEM_HEIGHT + ITEM_SPACING;
                }
            }
        }
    }

    private void renderSlotItem(GuiGraphics guiGraphics, SlotEntry slot, String mapped,
                                boolean isMapped, boolean isExpanded, boolean isHovered,
                                int x, int y, int w) {
        guiGraphics.fill(x, y, x + w, y + ITEM_HEIGHT, TranslucentTrayChrome.cardBackground(false, isHovered || isExpanded));
        guiGraphics.fill(x, y + 1, x + 2, y + ITEM_HEIGHT - 1, isMapped ? COLOR_MAPPED : COLOR_UNMAPPED);

        guiGraphics.drawString(this.font, isExpanded ? "▼" : "▶", x + 4, y + 4, COLOR_TEXT_DIM);
        guiGraphics.drawString(this.font, slot.displayName, x + 16, y + 4, COLOR_TEXT);
        if (isMapped) {
            String vmdName = truncate(mapped.replace(".vmd", ""), 8);
            int width = this.font.width(vmdName);
            guiGraphics.drawString(this.font, vmdName, x + w - width - 2, y + 4, COLOR_MAPPED);
        }
    }

    private void renderScrollbar(GuiGraphics guiGraphics) {
        if (maxScroll <= 0) {
            return;
        }
        TranslucentTrayChrome.drawScrollbar(guiGraphics, panelX + PANEL_WIDTH - 4, listTop, listBottom, scrollOffset, maxScroll);
    }

    private void renderFooterStats(GuiGraphics guiGraphics) {
        int centerX = panelX + PANEL_WIDTH / 2;
        int statsY = panelY + panelH - 10;
        long mappedCount = editMapping.values().stream().filter(value -> value != null && !value.isEmpty()).count();
        String stats = mappedCount + " / " + slots.size() + " 路 VMD: " + availableVmds.size();
        guiGraphics.drawCenteredString(this.font, stats, centerX, statsY, COLOR_TEXT_DIM);
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
            if (mouseY >= y && mouseY < y + ITEM_HEIGHT && mouseX >= itemX && mouseX <= itemX + itemW) {
                expandedSlot = expandedSlot == i ? -1 : i;
                updateMaxScroll();
                return true;
            }
            y += ITEM_HEIGHT + ITEM_SPACING;

            if (i == expandedSlot) {
                if (mouseY >= y && mouseY < y + ITEM_HEIGHT && mouseX >= itemX + 8 && mouseX <= itemX + itemW) {
                    editMapping.remove(slots.get(i).name);
                    expandedSlot = -1;
                    updateMaxScroll();
                    return true;
                }
                y += ITEM_HEIGHT + ITEM_SPACING;

                for (String vmd : availableVmds) {
                    if (mouseY >= y && mouseY < y + ITEM_HEIGHT && mouseX >= itemX + 8 && mouseX <= itemX + itemW) {
                        editMapping.put(slots.get(expandedSlot).name, vmd);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 24)));
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
    public void onClose() {
        Minecraft.getInstance().setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max - 2) + ".." : value;
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
