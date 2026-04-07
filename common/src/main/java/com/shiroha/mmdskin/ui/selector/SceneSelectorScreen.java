package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.scene.client.SceneModelCatalog;
import com.shiroha.mmdskin.scene.client.SceneModelManager;
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

public class SceneSelectorScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final SceneModelCatalog SCENE_CATALOG = SceneModelCatalog.getInstance();
    private static final float WINDOW_MARGIN = 8.0f;
    private static final float MIN_WINDOW_WIDTH = 220.0f;
    private static final float MAX_WINDOW_WIDTH = 320.0f;
    private static final float MIN_WINDOW_HEIGHT = 220.0f;

    private final ImGuiScreenRenderer imguiRenderer = new ImGuiScreenRenderer();
    private final List<SceneCardEntry> sceneCards = new ArrayList<>();

    private String currentScene;
    private boolean pendingClose;

    public SceneSelectorScreen() {
        super(Component.translatable("gui.mmdskin.scene_selector"));
        SceneModelManager manager = SceneModelManager.getInstance();
        this.currentScene = manager.isActive() || manager.isLoading() ? manager.getSceneModelName() : null;
        loadAvailableScenes();
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
        imguiRenderer.dispose();
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

    private void renderSelectorWindow() {
        float panelWidth = clamp(this.width * 0.18f, MIN_WINDOW_WIDTH, MAX_WINDOW_WIDTH);
        float panelHeight = Math.max(MIN_WINDOW_HEIGHT, this.height - WINDOW_MARGIN);
        float panelX = this.width - panelWidth - WINDOW_MARGIN;
        float panelY = WINDOW_MARGIN;
        int windowFlags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings;

        ImGui.setNextWindowPos(panelX, panelY, ImGuiCond.Appearing);
        ImGui.setNextWindowSize(panelWidth, panelHeight, ImGuiCond.Appearing);
        ImGui.begin(this.title.getString() + "##scene_selector_window", windowFlags);

        renderHeader();
        ImGui.separator();
        renderSceneList();

        ImGui.end();
    }

    private void renderHeader() {
        ImGui.textDisabled(buildStatusText());

        if (fullWidthButton(Component.translatable("gui.done").getString() + "##scene_done")) {
            pendingClose = true;
        }

        SceneModelManager manager = SceneModelManager.getInstance();
        boolean hasScene = manager.isActive() || manager.isLoading();
        String secondaryLabel = hasScene
                ? Component.translatable("gui.mmdskin.scene_selector.cancel").getString()
                : Component.translatable("gui.mmdskin.refresh").getString();

        if (fullWidthButton(secondaryLabel + "##scene_secondary")) {
            if (hasScene) {
                manager.removeScene();
                currentScene = null;
                loadAvailableScenes();
            } else {
                refreshScenes();
            }
        }
    }

    private void renderSceneList() {
        if (sceneCards.isEmpty()) {
            ImGui.textDisabled("No scenes");
            return;
        }

        float listHeight = Math.max(80.0f, ImGui.getContentRegionAvailY());
        ImGui.beginChild("##scene_selector_list", 0.0f, listHeight, true);

        for (SceneCardEntry card : sceneCards) {
            boolean selected = card.displayName.equals(currentScene);
            String label = shorten(card.displayName, 24);
            if (ImGui.selectable(label + "##scene_card_" + card.displayName, selected, 0, fullWidth(), 0.0f)) {
                selectScene(card);
            }
        }

        ImGui.endChild();
    }

    private void loadAvailableScenes() {
        sceneCards.clear();
        List<ModelInfo> models = SCENE_CATALOG.listModels();
        for (ModelInfo info : models) {
            sceneCards.add(new SceneCardEntry(info.getFolderName(), info));
        }
    }

    private void refreshScenes() {
        SCENE_CATALOG.invalidate();
        loadAvailableScenes();
    }

    private void selectScene(SceneCardEntry card) {
        currentScene = card.displayName;
        SceneModelManager.getInstance().placeScene(card.displayName);
        LOGGER.info("Placed scene model: {}", card.displayName);
    }

    private void flushPendingActions(Minecraft minecraft) {
        if (pendingClose && minecraft.screen == this) {
            pendingClose = false;
            minecraft.setScreen(null);
        }
    }

    private void closeAfterFailure(Throwable throwable) {
        LOGGER.error("[SceneSelector] ImGui selector failed and will close", throwable);
        imguiRenderer.dispose();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            minecraft.setScreen(null);
        }
    }

    private String buildStatusText() {
        SceneModelManager manager = SceneModelManager.getInstance();
        if (manager.isLoading()) {
            return Component.translatable("gui.mmdskin.scene_selector.loading").getString();
        }
        if (manager.isActive()) {
            return Component.translatable(
                    "gui.mmdskin.scene_selector.active",
                    shorten(currentScene, 14)
            ).getString();
        }
        return sceneCards.size() + " " + Component.translatable("gui.mmdskin.scene_selector.models").getString();
    }

    private List<String> collectVisibleGlyphHints() {
        List<String> hints = new ArrayList<>();
        hints.add(this.title.getString());
        hints.add(Component.translatable("gui.done").getString());
        hints.add(Component.translatable("gui.mmdskin.refresh").getString());
        hints.add(Component.translatable("gui.mmdskin.scene_selector.cancel").getString());
        hints.add(Component.translatable("gui.mmdskin.scene_selector.loading").getString());
        if (currentScene != null) {
            hints.add(currentScene);
            hints.add(Component.translatable("gui.mmdskin.scene_selector.active", currentScene).getString());
        }
        for (SceneCardEntry card : sceneCards) {
            hints.add(card.displayName);
        }
        return hints;
    }

    private static boolean fullWidthButton(String label) {
        return ImGui.button(label, fullWidth(), 0.0f);
    }

    private static float fullWidth() {
        return Math.max(1.0f, ImGui.getContentRegionAvailX());
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

    private static final class SceneCardEntry {
        final String displayName;
        @SuppressWarnings("unused")
        final ModelInfo modelInfo;

        private SceneCardEntry(String displayName, ModelInfo modelInfo) {
            this.displayName = displayName;
            this.modelInfo = modelInfo;
        }
    }
}
