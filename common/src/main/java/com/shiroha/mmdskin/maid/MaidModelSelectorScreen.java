package com.shiroha.mmdskin.maid;

import com.shiroha.mmdskin.maid.service.DefaultMaidModelSelectionService;
import com.shiroha.mmdskin.maid.service.MaidModelSelectionService;
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
import java.util.UUID;

public class MaidModelSelectorScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final float WINDOW_MARGIN = 8.0f;
    private static final float MIN_WINDOW_WIDTH = 220.0f;
    private static final float MAX_WINDOW_WIDTH = 320.0f;
    private static final float MIN_WINDOW_HEIGHT = 220.0f;

    private final UUID maidUUID;
    private final int maidEntityId;
    private final String maidName;
    private final MaidModelSelectionService maidModelSelectionService;
    private final ImGuiScreenRenderer imguiRenderer = new ImGuiScreenRenderer();
    private final List<String> modelCards = new ArrayList<>();

    private String currentModel;
    private boolean pendingClose;

    public MaidModelSelectorScreen(UUID maidUUID, int maidEntityId, String maidName) {
        this(maidUUID, maidEntityId, maidName, new DefaultMaidModelSelectionService());
    }

    MaidModelSelectorScreen(UUID maidUUID, int maidEntityId, String maidName,
                            MaidModelSelectionService maidModelSelectionService) {
        super(Component.translatable("gui.mmdskin.maid_model_selector"));
        this.maidUUID = maidUUID;
        this.maidEntityId = maidEntityId;
        this.maidName = maidName;
        this.maidModelSelectionService = maidModelSelectionService;
        reloadModelCards();
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
            renderSelectorWindow();
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
        disposeRenderer();
        super.onClose();
    }

    @Override
    public void removed() {
        disposeRenderer();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderSelectorWindow() {
        float panelWidth = clamp(this.width * 0.18f, MIN_WINDOW_WIDTH, MAX_WINDOW_WIDTH);
        float panelHeight = Math.max(MIN_WINDOW_HEIGHT, this.height - WINDOW_MARGIN);
        float panelX = this.width - panelWidth - WINDOW_MARGIN;
        float panelY = WINDOW_MARGIN;

        int windowFlags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings;

        ImGui.setNextWindowPos(panelX, panelY, ImGuiCond.Appearing);
        ImGui.setNextWindowSize(panelWidth, panelHeight, ImGuiCond.Appearing);
        ImGui.begin(this.title.getString() + "##maid_model_selector_window", windowFlags);

        renderHeader();
        ImGui.separator();
        renderModelList();

        ImGui.end();
    }

    private void renderHeader() {
        ImGui.textDisabled(shorten(maidName, 24));
        ImGui.textDisabled(Component.translatable(
                "gui.mmdskin.model_selector.stats",
                Math.max(0, modelCards.size() - 1),
                shorten(currentModel, 14)
        ).getString());

        if (fullWidthButton(Component.translatable("gui.done").getString() + "##maid_model_done")) {
            pendingClose = true;
        }

        if (fullWidthButton(Component.translatable("gui.mmdskin.refresh").getString() + "##maid_model_refresh")) {
            reloadModelCards();
        }
    }

    private void renderModelList() {
        if (modelCards.isEmpty()) {
            ImGui.textDisabled("No models");
            return;
        }

        float listHeight = Math.max(80.0f, ImGui.getContentRegionAvailY());
        ImGui.beginChild("##maid_model_selector_list", 0.0f, listHeight, true);

        for (String modelName : modelCards) {
            boolean selected = modelName.equals(currentModel);
            String label = shorten(modelName, 24);
            if (ImGui.selectable(label + "##maid_model_card_" + modelName, selected, 0, fullWidth(), 0.0f)) {
                selectModel(modelName);
            }
        }

        ImGui.endChild();
    }

    private void reloadModelCards() {
        modelCards.clear();
        modelCards.addAll(maidModelSelectionService.loadAvailableModels());
        currentModel = maidModelSelectionService.getCurrentModel(maidUUID);
    }

    private void selectModel(String modelName) {
        currentModel = modelName;
        maidModelSelectionService.selectModel(maidUUID, maidEntityId, modelName);
    }

    private void flushPendingActions(Minecraft minecraft) {
        if (pendingClose && minecraft.screen == this) {
            pendingClose = false;
            minecraft.setScreen(null);
        }
    }

    private void closeAfterFailure(Throwable throwable) {
        LOGGER.error("[MaidModelSelector] ImGui selector failed and will close", throwable);
        disposeRenderer();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            minecraft.setScreen(null);
        }
    }

    private void disposeRenderer() {
        imguiRenderer.dispose();
    }

    private List<String> collectVisibleGlyphHints() {
        List<String> hints = new ArrayList<>();
        hints.add(this.title.getString());
        hints.add(maidName);
        hints.add(Component.translatable("gui.done").getString());
        hints.add(Component.translatable("gui.mmdskin.refresh").getString());
        if (currentModel != null) {
            hints.add(currentModel);
        }
        hints.addAll(modelCards);
        return hints;
    }

    private static float fullWidth() {
        return Math.max(1.0f, ImGui.getContentRegionAvailX());
    }

    private static boolean fullWidthButton(String label) {
        return ImGui.button(label, fullWidth(), 0.0f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String shorten(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        if (maxChars <= 3) {
            return value.substring(0, Math.max(0, maxChars));
        }
        return value.substring(0, maxChars - 3) + "...";
    }
}
