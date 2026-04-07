package com.shiroha.mmdskin.ui.config;

import com.shiroha.mmdskin.ui.imgui.ImGuiScreenRenderer;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class MorphWheelConfigScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final float WINDOW_MIN_WIDTH = 620.0f;
    private static final float WINDOW_MIN_HEIGHT = 360.0f;
    private static final float WINDOW_MARGIN = 14.0f;

    private final Screen parent;
    private final ImGuiScreenRenderer imguiRenderer = new ImGuiScreenRenderer();

    private List<MorphWheelConfig.MorphEntry> availableMorphs;
    private List<MorphWheelConfig.MorphEntry> selectedMorphs;
    private boolean pendingClose;

    public MorphWheelConfigScreen(Screen parent) {
        super(Component.translatable("gui.mmdskin.morph_config"));
        this.parent = parent;
        loadData();
    }

    @Override
    protected void init() {
        super.init();
        try {
            imguiRenderer.ensureInitialized();
        } catch (Throwable throwable) {
            closeAfterFailure(throwable);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            float framebufferScaleX = this.width > 0
                    ? (float) minecraft.getWindow().getWidth() / (float) this.width
                    : 1.0f;
            float framebufferScaleY = this.height > 0
                    ? (float) minecraft.getWindow().getHeight() / (float) this.height
                    : 1.0f;

            imguiRenderer.setGlyphHintTexts(collectVisibleGlyphHints());
            imguiRenderer.beginFrame(this.width, this.height, framebufferScaleX, framebufferScaleY, mouseX, mouseY);
            renderConfigWindow();
            imguiRenderer.renderFrame();
            flushPendingActions(minecraft);
        } catch (Throwable throwable) {
            closeAfterFailure(throwable);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        imguiRenderer.onMouseButton(button, true);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        imguiRenderer.onMouseButton(button, false);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        imguiRenderer.onMouseScroll(0.0, delta);
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
    public void onClose() {
        imguiRenderer.dispose();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            minecraft.setScreen(parent);
            return;
        }
        super.onClose();
    }

    @Override
    public void removed() {
        imguiRenderer.dispose();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void loadData() {
        MorphWheelConfig config = MorphWheelConfig.getInstance();
        this.availableMorphs = new ArrayList<>(config.getAvailableMorphs());
        this.selectedMorphs = new ArrayList<>(config.getDisplayedMorphs());

        availableMorphs.removeIf(available ->
                selectedMorphs.stream().anyMatch(selected -> selected.matches(available)));
    }

    private void renderConfigWindow() {
        float panelWidth = Math.max(WINDOW_MIN_WIDTH, this.width * 0.66f);
        float panelHeight = Math.max(WINDOW_MIN_HEIGHT, this.height * 0.72f);
        float panelX = (this.width - panelWidth) * 0.5f;
        float panelY = Math.max(WINDOW_MARGIN, (this.height - panelHeight) * 0.5f);
        int windowFlags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings;

        ImGui.setNextWindowPos(panelX, panelY, ImGuiCond.Appearing);
        ImGui.setNextWindowSize(panelWidth, panelHeight, ImGuiCond.Appearing);
        ImGui.begin(this.title.getString() + "##morph_wheel_config_window", windowFlags);

        renderHeader();
        ImGui.separator();
        renderListsArea();
        ImGui.separator();
        renderFooterActions();

        ImGui.end();
    }

    private void renderHeader() {
        String stats = Component.translatable(
                "gui.mmdskin.config.stats",
                availableMorphs.size(),
                selectedMorphs.size()
        ).getString();
        ImGui.textDisabled(stats);
    }

    private void renderListsArea() {
        float totalWidth = Math.max(1.0f, ImGui.getContentRegionAvailX());
        float totalHeight = Math.max(160.0f, ImGui.getContentRegionAvailY() - 40.0f);
        float listWidth = Math.max(120.0f, (totalWidth - 10.0f) * 0.5f);

        ImGui.beginChild("##available_morphs_panel", listWidth, totalHeight, true);
        ImGui.textDisabled(Component.translatable("gui.mmdskin.morph_config.available").getString());
        ImGui.separator();
        renderMorphList(availableMorphs, true);
        ImGui.endChild();

        ImGui.sameLine();

        ImGui.beginChild("##selected_morphs_panel", listWidth, totalHeight, true);
        ImGui.textDisabled(Component.translatable("gui.mmdskin.morph_config.selected").getString());
        ImGui.separator();
        renderMorphList(selectedMorphs, false);
        ImGui.endChild();
    }

    private void renderMorphList(List<MorphWheelConfig.MorphEntry> items, boolean availableSide) {
        for (int i = 0; i < items.size(); i++) {
            MorphWheelConfig.MorphEntry entry = items.get(i);
            if (ImGui.selectable(buildMorphTitle(entry) + "##morph_entry_" + availableSide + "_" + i,
                    false, 0, fullWidth(), 0.0f)) {
                if (availableSide) {
                    moveToSelected(i);
                } else {
                    moveToAvailable(i);
                }
                return;
            }

            ImGui.textDisabled(buildMorphMeta(entry));
            ImGui.separator();
        }

        if (items.isEmpty()) {
            ImGui.textDisabled("-");
        }
    }

    private void moveToSelected(int index) {
        if (index < 0 || index >= availableMorphs.size()) {
            return;
        }
        MorphWheelConfig.MorphEntry entry = availableMorphs.remove(index);
        selectedMorphs.add(entry);
    }

    private void moveToAvailable(int index) {
        if (index < 0 || index >= selectedMorphs.size()) {
            return;
        }
        MorphWheelConfig.MorphEntry entry = selectedMorphs.remove(index);
        availableMorphs.add(entry);
        availableMorphs.sort((a, b) -> a.morphName.compareToIgnoreCase(b.morphName));
    }

    private void renderFooterActions() {
        float totalWidth = Math.max(1.0f, ImGui.getContentRegionAvailX());
        float buttonWidth = Math.max(72.0f, (totalWidth - 16.0f) / 5.0f);

        if (ImGui.button(Component.translatable("gui.mmdskin.refresh").getString() + "##morph_rescan", buttonWidth, 0.0f)) {
            rescan();
        }
        ImGui.sameLine();
        if (ImGui.button(Component.translatable("gui.mmdskin.select_all").getString() + "##morph_select_all", buttonWidth, 0.0f)) {
            selectAll();
        }
        ImGui.sameLine();
        if (ImGui.button(Component.translatable("gui.mmdskin.clear_all").getString() + "##morph_clear_all", buttonWidth, 0.0f)) {
            clearAll();
        }
        ImGui.sameLine();
        if (ImGui.button(Component.translatable("gui.done").getString() + "##morph_save", buttonWidth, 0.0f)) {
            saveAndClose();
            return;
        }
        ImGui.sameLine();
        if (ImGui.button(Component.translatable("gui.cancel").getString() + "##morph_cancel", buttonWidth, 0.0f)) {
            pendingClose = true;
        }
    }

    private void rescan() {
        MorphWheelConfig.getInstance().scanAvailableMorphs();
        loadData();
    }

    private void selectAll() {
        selectedMorphs.addAll(availableMorphs);
        availableMorphs.clear();
    }

    private void clearAll() {
        availableMorphs.addAll(selectedMorphs);
        selectedMorphs.clear();
        availableMorphs.sort((a, b) -> a.morphName.compareToIgnoreCase(b.morphName));
    }

    private void saveAndClose() {
        MorphWheelConfig config = MorphWheelConfig.getInstance();
        config.setDisplayedMorphs(selectedMorphs);
        config.save();
        pendingClose = true;
    }

    private void flushPendingActions(Minecraft minecraft) {
        if (pendingClose && minecraft.screen == this) {
            pendingClose = false;
            minecraft.setScreen(parent);
        }
    }

    private void closeAfterFailure(Throwable throwable) {
        LOGGER.error("[MorphWheelConfig] ImGui config screen failed and will close", throwable);
        imguiRenderer.dispose();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            minecraft.setScreen(parent);
        }
    }

    private List<String> collectVisibleGlyphHints() {
        List<String> hints = new ArrayList<>();
        hints.add(this.title.getString());
        hints.add(Component.translatable("gui.mmdskin.refresh").getString());
        hints.add(Component.translatable("gui.mmdskin.select_all").getString());
        hints.add(Component.translatable("gui.mmdskin.clear_all").getString());
        hints.add(Component.translatable("gui.done").getString());
        hints.add(Component.translatable("gui.cancel").getString());
        hints.add(Component.translatable("gui.mmdskin.morph_config.available").getString());
        hints.add(Component.translatable("gui.mmdskin.morph_config.selected").getString());
        for (MorphWheelConfig.MorphEntry entry : availableMorphs) {
            hints.add(entry.displayName);
            hints.add(entry.morphName);
            hints.add(buildMorphTitle(entry));
            hints.add(buildMorphMeta(entry));
        }
        for (MorphWheelConfig.MorphEntry entry : selectedMorphs) {
            hints.add(entry.displayName);
            hints.add(entry.morphName);
            hints.add(buildMorphTitle(entry));
            hints.add(buildMorphMeta(entry));
        }
        return hints;
    }

    private static String buildMorphTitle(MorphWheelConfig.MorphEntry entry) {
        return shorten(entry.displayName, 30);
    }

    private static String buildMorphMeta(MorphWheelConfig.MorphEntry entry) {
        String source = entry.source == null ? "?" : entry.source;
        String size = entry.fileSize == null || entry.fileSize.isEmpty() ? "-" : entry.fileSize;
        return shorten(entry.morphName, 28) + " · " + source + " · " + size;
    }

    private static float fullWidth() {
        return Math.max(1.0f, ImGui.getContentRegionAvailX());
    }

    private static String shorten(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        if (maxChars <= 3) {
            return text.substring(0, Math.max(0, maxChars));
        }
        return text.substring(0, maxChars - 3) + "...";
    }
}
