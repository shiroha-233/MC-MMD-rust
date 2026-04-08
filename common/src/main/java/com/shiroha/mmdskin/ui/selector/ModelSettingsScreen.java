package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.ui.imgui.ImGuiScreenRenderer;
import com.shiroha.mmdskin.ui.selector.application.ModelSettingsApplicationService;
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

public class ModelSettingsScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final float WINDOW_MARGIN = 8.0f;
    private static final float MIN_WINDOW_WIDTH = 240.0f;
    private static final float MAX_WINDOW_WIDTH = 340.0f;
    private static final float MIN_WINDOW_HEIGHT = 260.0f;
    private static final ModelSettingsApplicationService SERVICE = ModelSelectorServices.modelSettings();

    private final String modelName;
    private final Screen parentScreen;
    private final ImGuiScreenRenderer imguiRenderer = new ImGuiScreenRenderer();
    private final float[] eyeMaxAngleSlider = new float[1];
    private final float[] modelScaleSlider = new float[1];

    private ModelConfigData config;
    private boolean pendingClose;
    private boolean pendingOpenAnimConfig;
    private boolean pendingOpenVoiceConfig;

    public ModelSettingsScreen(String modelName, Screen parentScreen) {
        super(Component.translatable("gui.mmdskin.model_settings.title"));
        this.modelName = modelName;
        this.parentScreen = parentScreen;
        this.config = SERVICE.loadEditableConfig(modelName);
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
            renderSettingsWindow();
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
            minecraft.setScreen(parentScreen);
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

    private void renderSettingsWindow() {
        float panelWidth = clamp(this.width * 0.19f, MIN_WINDOW_WIDTH, MAX_WINDOW_WIDTH);
        float panelHeight = Math.max(MIN_WINDOW_HEIGHT, this.height - WINDOW_MARGIN);
        float panelX = this.width - panelWidth - WINDOW_MARGIN;
        float panelY = WINDOW_MARGIN;
        int windowFlags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings;

        ImGui.setNextWindowPos(panelX, panelY, ImGuiCond.Appearing);
        ImGui.setNextWindowSize(panelWidth, panelHeight, ImGuiCond.Appearing);
        ImGui.begin(this.title.getString() + "##model_settings_window", windowFlags);

        renderHeader();
        ImGui.separator();
        renderConfigSection();
        ImGui.separator();
        renderQuickSlots();
        ImGui.separator();
        renderActionButtons();

        ImGui.end();
    }

    private void renderHeader() {
        ImGui.textDisabled(shorten(modelName, 28));
    }

    private void renderConfigSection() {
        ImGui.textDisabled(Component.translatable("gui.mmdskin.model_settings.eye_tracking").getString());

        boolean eyeTrackingEnabled = config.eyeTrackingEnabled;
        if (ImGui.checkbox(Component.translatable("gui.mmdskin.model_settings.eye_tracking_enabled").getString()
                + "##eye_tracking_enabled", eyeTrackingEnabled)) {
            config.eyeTrackingEnabled = !eyeTrackingEnabled;
        }

        eyeMaxAngleSlider[0] = config.eyeMaxAngle;
        String eyeAngleLabel = Component.translatable(
                "gui.mmdskin.model_settings.eye_max_angle",
                String.format("%.0f", Math.toDegrees(config.eyeMaxAngle))
        ).getString();
        ImGui.textDisabled(eyeAngleLabel);
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.sliderFloat("##eye_max_angle", eyeMaxAngleSlider, 0.05f, 1.0f, "%.3f")) {
            config.eyeMaxAngle = eyeMaxAngleSlider[0];
        }

        ImGui.separator();
        ImGui.textDisabled(Component.translatable("gui.mmdskin.model_settings.model_display").getString());

        modelScaleSlider[0] = config.modelScale;
        String modelScaleLabel = Component.translatable(
                "gui.mmdskin.model_settings.model_scale",
                String.format("%.2f", config.modelScale)
        ).getString();
        ImGui.textDisabled(modelScaleLabel);
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.sliderFloat("##model_scale", modelScaleSlider, 0.5f, 2.0f, "%.2f")) {
            config.modelScale = modelScaleSlider[0];
        }
    }

    private void renderQuickSlots() {
        ImGui.textDisabled(Component.translatable("gui.mmdskin.model_settings.quick_bind").getString());

        List<ModelSettingsApplicationService.QuickSlotBinding> bindings = SERVICE.getQuickSlotBindings(modelName);
        float fullWidth = Math.max(1.0f, ImGui.getContentRegionAvailX());
        float buttonWidth = Math.max(1.0f, (fullWidth - 4.0f) * 0.5f);

        for (int i = 0; i < bindings.size(); i++) {
            ModelSettingsApplicationService.QuickSlotBinding binding = bindings.get(i);
            String label = buildQuickSlotLabel(binding);
            if (ImGui.button(label + "##quick_slot_" + binding.slot(), buttonWidth, 0.0f)) {
                SERVICE.toggleQuickSlot(modelName, binding.slot());
            }
            if (i % 2 == 0 && i + 1 < bindings.size()) {
                ImGui.sameLine();
            }

            if (binding.boundModel() != null && !binding.boundModel().isEmpty()) {
                ImGui.textDisabled(shorten(binding.boundModel(), 26));
            } else {
                ImGui.textDisabled("-");
            }
        }
    }

    private void renderActionButtons() {
        if (fullWidthButton(Component.translatable("gui.mmdskin.model_settings.save").getString() + "##save")) {
            saveAndClose();
            return;
        }

        if (fullWidthButton(Component.translatable("gui.mmdskin.model_settings.reset").getString() + "##reset")) {
            config = SERVICE.resetToDefaults();
        }

        if (fullWidthButton(Component.translatable("gui.mmdskin.model_settings.anim_config").getString() + "##anim")) {
            pendingOpenAnimConfig = true;
        }

        if (fullWidthButton(Component.translatable("gui.mmdskin.model_settings.voice_config").getString() + "##voice")) {
            pendingOpenVoiceConfig = true;
        }

        if (fullWidthButton(Component.translatable("gui.done").getString() + "##done")) {
            pendingClose = true;
        }
    }

    private void saveAndClose() {
        SERVICE.save(modelName, config);
        pendingClose = true;
    }

    private void flushPendingActions(Minecraft minecraft) {
        if (pendingOpenAnimConfig && minecraft.screen == this) {
            pendingOpenAnimConfig = false;
            minecraft.setScreen(new ModelAnimationScreen(modelName, this));
            return;
        }

        if (pendingOpenVoiceConfig && minecraft.screen == this) {
            pendingOpenVoiceConfig = false;
            minecraft.setScreen(VoicePackBindingScreen.createForPlayer(this, modelName));
            return;
        }

        if (pendingClose && minecraft.screen == this) {
            pendingClose = false;
            minecraft.setScreen(parentScreen);
        }
    }

    private void closeAfterFailure(Throwable throwable) {
        LOGGER.error("[ModelSettings] ImGui settings failed and will close", throwable);
        imguiRenderer.dispose();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            minecraft.setScreen(parentScreen);
        }
    }

    private List<String> collectVisibleGlyphHints() {
        List<String> hints = new ArrayList<>();
        hints.add(this.title.getString());
        hints.add(modelName);
        hints.add(Component.translatable("gui.mmdskin.model_settings.save").getString());
        hints.add(Component.translatable("gui.mmdskin.model_settings.reset").getString());
        hints.add(Component.translatable("gui.mmdskin.model_settings.anim_config").getString());
        hints.add(Component.translatable("gui.mmdskin.model_settings.voice_config").getString());
        hints.add(Component.translatable("gui.done").getString());
        hints.add(Component.translatable("gui.mmdskin.model_settings.eye_tracking").getString());
        hints.add(Component.translatable("gui.mmdskin.model_settings.eye_tracking_enabled").getString());
        hints.add(Component.translatable("gui.mmdskin.model_settings.model_display").getString());
        hints.add(Component.translatable("gui.mmdskin.model_settings.quick_bind").getString());
        hints.add(Component.translatable("gui.mmdskin.model_settings.eye_max_angle",
                String.format("%.0f", Math.toDegrees(config.eyeMaxAngle))).getString());
        hints.add(Component.translatable("gui.mmdskin.model_settings.model_scale",
                String.format("%.2f", config.modelScale)).getString());
        for (ModelSettingsApplicationService.QuickSlotBinding binding : SERVICE.getQuickSlotBindings(modelName)) {
            hints.add(buildQuickSlotLabel(binding));
            if (binding.boundModel() != null) {
                hints.add(binding.boundModel());
            }
        }
        return hints;
    }

    private static String buildQuickSlotLabel(ModelSettingsApplicationService.QuickSlotBinding binding) {
        String base = Component.translatable("gui.mmdskin.model_settings.slot", binding.slot() + 1).getString();
        if (binding.boundToCurrentModel()) {
            return "[x] " + base;
        }
        if (binding.boundModel() != null && !binding.boundModel().isEmpty()) {
            return "[*] " + base;
        }
        return "[ ] " + base;
    }

    private static boolean fullWidthButton(String label) {
        return ImGui.button(label, Math.max(1.0f, ImGui.getContentRegionAvailX()), 0.0f);
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
