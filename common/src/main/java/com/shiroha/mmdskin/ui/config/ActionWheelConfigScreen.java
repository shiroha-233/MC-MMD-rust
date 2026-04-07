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

public class ActionWheelConfigScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final float WINDOW_MIN_WIDTH = 620.0f;
    private static final float WINDOW_MIN_HEIGHT = 360.0f;
    private static final float WINDOW_MARGIN = 14.0f;

    private final Screen parent;
    private final ImGuiScreenRenderer imguiRenderer = new ImGuiScreenRenderer();

    private List<ActionWheelConfig.ActionEntry> availableActions;
    private List<ActionWheelConfig.ActionEntry> selectedActions;

    private int activeAvailableIndex = -1;
    private int activeSelectedIndex = -1;
    private boolean pendingClose;

    public ActionWheelConfigScreen(Screen parent) {
        super(Component.translatable("gui.mmdskin.action_config"));
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
        ActionWheelConfig config = ActionWheelConfig.getInstance();
        this.availableActions = new ArrayList<>(config.getAvailableActions());
        this.selectedActions = new ArrayList<>(config.getDisplayedActions());

        availableActions.removeIf(available ->
                selectedActions.stream().anyMatch(selected -> selected.matches(available)));

        activeAvailableIndex = -1;
        activeSelectedIndex = -1;
    }

    private void renderConfigWindow() {
        float panelWidth = Math.max(WINDOW_MIN_WIDTH, this.width * 0.66f);
        float panelHeight = Math.max(WINDOW_MIN_HEIGHT, this.height * 0.72f);
        float panelX = (this.width - panelWidth) * 0.5f;
        float panelY = Math.max(WINDOW_MARGIN, (this.height - panelHeight) * 0.5f);
        int windowFlags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings;

        ImGui.setNextWindowPos(panelX, panelY, ImGuiCond.Appearing);
        ImGui.setNextWindowSize(panelWidth, panelHeight, ImGuiCond.Appearing);
        ImGui.begin(this.title.getString() + "##action_wheel_config_window", windowFlags);

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
                availableActions.size(),
                selectedActions.size()
        ).getString();
        ImGui.textDisabled(stats);
    }

    private void renderListsArea() {
        float totalWidth = Math.max(1.0f, ImGui.getContentRegionAvailX());
        float totalHeight = Math.max(160.0f, ImGui.getContentRegionAvailY() - 40.0f);
        float listWidth = Math.max(120.0f, (totalWidth - 10.0f) * 0.5f);

        ImGui.beginChild("##available_actions_panel", listWidth, totalHeight, true);
        ImGui.textDisabled(Component.translatable("gui.mmdskin.action_config.available").getString());
        ImGui.separator();
        renderActionList(availableActions, true);
        ImGui.endChild();

        ImGui.sameLine();

        ImGui.beginChild("##selected_actions_panel", listWidth, totalHeight, true);
        ImGui.textDisabled(Component.translatable("gui.mmdskin.action_config.selected").getString());
        ImGui.separator();
        renderActionList(selectedActions, false);
        ImGui.endChild();
    }

    private void renderActionList(List<ActionWheelConfig.ActionEntry> items, boolean availableSide) {
        for (int i = 0; i < items.size(); i++) {
            ActionWheelConfig.ActionEntry entry = items.get(i);
            boolean selected = availableSide ? i == activeAvailableIndex : i == activeSelectedIndex;

            if (ImGui.selectable(buildEntryTitle(entry) + "##entry_" + availableSide + "_" + i, selected, 0, fullWidth(), 0.0f)) {
                if (availableSide) {
                    moveToSelected(i);
                } else {
                    moveToAvailable(i);
                }
                return;
            }

            ImGui.textDisabled(buildEntryMeta(entry));
            ImGui.separator();
        }

        if (items.isEmpty()) {
            ImGui.textDisabled("-");
        }
    }

    private void moveToSelected(int index) {
        if (index < 0 || index >= availableActions.size()) {
            return;
        }
        ActionWheelConfig.ActionEntry entry = availableActions.remove(index);
        selectedActions.add(entry);
        activeAvailableIndex = -1;
        activeSelectedIndex = selectedActions.size() - 1;
    }

    private void moveToAvailable(int index) {
        if (index < 0 || index >= selectedActions.size()) {
            return;
        }
        ActionWheelConfig.ActionEntry entry = selectedActions.remove(index);
        availableActions.add(entry);
        availableActions.sort((a, b) -> a.animId.compareToIgnoreCase(b.animId));
        activeSelectedIndex = -1;
        activeAvailableIndex = availableActions.indexOf(entry);
    }

    private void renderFooterActions() {
        float totalWidth = Math.max(1.0f, ImGui.getContentRegionAvailX());
        float buttonWidth = Math.max(72.0f, (totalWidth - 16.0f) / 5.0f);

        if (ImGui.button(Component.translatable("gui.mmdskin.refresh").getString() + "##action_rescan", buttonWidth, 0.0f)) {
            rescan();
        }
        ImGui.sameLine();
        if (ImGui.button(Component.translatable("gui.mmdskin.select_all").getString() + "##action_select_all", buttonWidth, 0.0f)) {
            selectAll();
        }
        ImGui.sameLine();
        if (ImGui.button(Component.translatable("gui.mmdskin.clear_all").getString() + "##action_clear_all", buttonWidth, 0.0f)) {
            clearAll();
        }
        ImGui.sameLine();
        if (ImGui.button(Component.translatable("gui.done").getString() + "##action_save", buttonWidth, 0.0f)) {
            saveAndClose();
            return;
        }
        ImGui.sameLine();
        if (ImGui.button(Component.translatable("gui.cancel").getString() + "##action_cancel", buttonWidth, 0.0f)) {
            pendingClose = true;
        }
    }

    private void rescan() {
        ActionWheelConfig.getInstance().rescan();
        loadData();
    }

    private void selectAll() {
        selectedActions.addAll(availableActions);
        availableActions.clear();
        activeAvailableIndex = -1;
        activeSelectedIndex = selectedActions.isEmpty() ? -1 : selectedActions.size() - 1;
    }

    private void clearAll() {
        availableActions.addAll(selectedActions);
        selectedActions.clear();
        availableActions.sort((a, b) -> a.animId.compareToIgnoreCase(b.animId));
        activeAvailableIndex = -1;
        activeSelectedIndex = -1;
    }

    private void saveAndClose() {
        ActionWheelConfig config = ActionWheelConfig.getInstance();
        config.setDisplayedActions(new ArrayList<>(selectedActions));
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
        LOGGER.error("[ActionWheelConfig] ImGui config screen failed and will close", throwable);
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
        hints.add(Component.translatable("gui.mmdskin.action_config.available").getString());
        hints.add(Component.translatable("gui.mmdskin.action_config.selected").getString());
        for (ActionWheelConfig.ActionEntry entry : availableActions) {
            hints.add(entry.name);
            hints.add(entry.animId);
            hints.add(buildEntryTitle(entry));
            hints.add(buildEntryMeta(entry));
        }
        for (ActionWheelConfig.ActionEntry entry : selectedActions) {
            hints.add(entry.name);
            hints.add(entry.animId);
            hints.add(buildEntryTitle(entry));
            hints.add(buildEntryMeta(entry));
        }
        return hints;
    }

    private static String buildEntryTitle(ActionWheelConfig.ActionEntry entry) {
        return shorten(entry.name, 30);
    }

    private static String buildEntryMeta(ActionWheelConfig.ActionEntry entry) {
        String source = entry.source == null ? "?" : entry.source;
        String size = entry.fileSize == null || entry.fileSize.isEmpty() ? "-" : entry.fileSize;
        return shorten(entry.animId, 36) + " · " + source + " · " + size;
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
