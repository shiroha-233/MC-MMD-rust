package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.ui.selector.application.ModelSettingsApplicationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ModelSettingsScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ModelSettingsApplicationService SERVICE = ModelSelectorServices.modelSettings();

    private static final int WINDOW_MARGIN = 10;
    private static final int MIN_WINDOW_WIDTH = 168;
    private static final int MAX_WINDOW_WIDTH = 210;
    private static final int MIN_WINDOW_HEIGHT = 292;

    private static final int HEADER_HEIGHT = 34;
    private static final int SECTION_GAP = 4;
    private static final int CARD_HEIGHT = 44;
    private static final int QUICK_CARD_HEIGHT = 56;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 4;

    private final String modelName;
    private final Screen parentScreen;
    private final SkiaModelSettingsRenderer skiaRenderer = new SkiaModelSettingsRenderer();

    private ModelConfigData config;
    private boolean pendingClose;
    private boolean pendingOpenAnimConfig;
    private boolean pendingOpenVoiceConfig;
    private HoverTarget hoveredTarget = HoverTarget.NONE;
    private Layout layout = Layout.empty();
    private ActiveSlider activeSlider = ActiveSlider.NONE;

    private enum ActiveSlider {
        NONE,
        EYE,
        SCALE
    }

    private enum HoverTarget {
        NONE,
        EYE_TOGGLE,
        EYE_SLIDER,
        SCALE_SLIDER,
        SLOT_0,
        SLOT_1,
        SLOT_2,
        SLOT_3,
        SAVE,
        RESET,
        ANIM,
        VOICE,
        DONE
    }

    public ModelSettingsScreen(String modelName, Screen parentScreen) {
        super(Component.translatable("gui.mmdskin.model_settings.title"));
        this.modelName = modelName;
        this.parentScreen = parentScreen;
        this.config = SERVICE.loadEditableConfig(modelName);
    }

    @Override
    protected void init() {
        super.init();
        updateLayout();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            updateLayout();
            updateHoverState(mouseX, mouseY);

            SkiaModelSettingsRenderer.SettingsView view = buildView();
            if (!skiaRenderer.renderSettings(this, view)) {
                renderFallback(guiGraphics, view);
            }
            flushPendingActions(minecraft);
        } catch (Throwable throwable) {
            closeAfterFailure(throwable);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (!layout.panel.contains(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (layout.eyeToggle.contains(mouseX, mouseY)) {
            config.eyeTrackingEnabled = !config.eyeTrackingEnabled;
            return true;
        }
        if (layout.eyeSlider.contains(mouseX, mouseY)) {
            activeSlider = ActiveSlider.EYE;
            updateSliderValue(activeSlider, mouseX);
            return true;
        }
        if (layout.scaleSlider.contains(mouseX, mouseY)) {
            activeSlider = ActiveSlider.SCALE;
            updateSliderValue(activeSlider, mouseX);
            return true;
        }

        for (int i = 0; i < layout.quickSlotButtons.length; i++) {
            UiRect slotButton = layout.quickSlotButtons[i];
            if (slotButton != null && slotButton.contains(mouseX, mouseY)) {
                SERVICE.toggleQuickSlot(modelName, i);
                return true;
            }
        }

        if (layout.saveButton.contains(mouseX, mouseY)) {
            saveAndClose();
            return true;
        }
        if (layout.resetButton.contains(mouseX, mouseY)) {
            config = SERVICE.resetToDefaults();
            return true;
        }
        if (layout.animButton.contains(mouseX, mouseY)) {
            pendingOpenAnimConfig = true;
            return true;
        }
        if (layout.voiceButton.contains(mouseX, mouseY)) {
            pendingOpenVoiceConfig = true;
            return true;
        }
        if (layout.doneButton.contains(mouseX, mouseY)) {
            pendingClose = true;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            activeSlider = ActiveSlider.NONE;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && activeSlider != ActiveSlider.NONE) {
            updateSliderValue(activeSlider, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return layout.panel.contains(mouseX, mouseY) || super.mouseScrolled(mouseX, mouseY, delta);
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
        skiaRenderer.dispose();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            minecraft.setScreen(parentScreen);
            return;
        }
        super.onClose();
    }

    @Override
    public void removed() {
        skiaRenderer.dispose();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void updateLayout() {
        int panelWidth = Mth.clamp(Math.round(this.width * 0.16f), MIN_WINDOW_WIDTH, MAX_WINDOW_WIDTH);
        int panelHeight = Math.max(MIN_WINDOW_HEIGHT, this.height - WINDOW_MARGIN * 2);
        int panelX = this.width - panelWidth - WINDOW_MARGIN;
        int panelY = WINDOW_MARGIN;

        UiRect panel = new UiRect(panelX, panelY, panelWidth, panelHeight);
        UiRect header = new UiRect(panelX + 8, panelY + 5, panelWidth - 16, HEADER_HEIGHT);

        int eyeY = header.y + header.h + 2;
        UiRect eyeCard = new UiRect(header.x, eyeY, header.w, CARD_HEIGHT);
        UiRect eyeToggle = new UiRect(eyeCard.x + eyeCard.w - 34, eyeCard.y + 10, 26, 10);
        UiRect eyeSlider = new UiRect(eyeCard.x + 4, eyeCard.y + 26, eyeCard.w - 8, 10);

        int scaleY = eyeCard.y + eyeCard.h + SECTION_GAP;
        UiRect scaleCard = new UiRect(header.x, scaleY, header.w, CARD_HEIGHT);
        UiRect scaleSlider = new UiRect(scaleCard.x + 4, scaleCard.y + 26, scaleCard.w - 8, 10);

        int quickY = scaleCard.y + scaleCard.h + SECTION_GAP;
        UiRect quickCard = new UiRect(header.x, quickY, header.w, QUICK_CARD_HEIGHT);
        UiRect[] quickButtons = new UiRect[4];
        int quickButtonWidth = (quickCard.w - BUTTON_GAP) / 2;
        quickButtons[0] = new UiRect(quickCard.x + 4, quickCard.y + 16, quickButtonWidth - 4, BUTTON_HEIGHT);
        quickButtons[1] = new UiRect(quickCard.x + 4 + quickButtonWidth, quickCard.y + 16, quickButtonWidth - 4, BUTTON_HEIGHT);
        quickButtons[2] = new UiRect(quickCard.x + 4, quickCard.y + 16 + BUTTON_HEIGHT + BUTTON_GAP, quickButtonWidth - 4, BUTTON_HEIGHT);
        quickButtons[3] = new UiRect(quickCard.x + 4 + quickButtonWidth, quickCard.y + 16 + BUTTON_HEIGHT + BUTTON_GAP, quickButtonWidth - 4, BUTTON_HEIGHT);

        int actionsBottom = panel.y + panel.h - 6;
        UiRect doneButton = new UiRect(header.x, actionsBottom - BUTTON_HEIGHT, header.w, BUTTON_HEIGHT);
        UiRect voiceButton = new UiRect(header.x, doneButton.y - BUTTON_GAP - BUTTON_HEIGHT, header.w, BUTTON_HEIGHT);
        UiRect animButton = new UiRect(header.x, voiceButton.y - BUTTON_GAP - BUTTON_HEIGHT, header.w, BUTTON_HEIGHT);
        UiRect resetButton = new UiRect(header.x, animButton.y - BUTTON_GAP - BUTTON_HEIGHT, header.w, BUTTON_HEIGHT);
        UiRect saveButton = new UiRect(header.x, resetButton.y - BUTTON_GAP - BUTTON_HEIGHT, header.w, BUTTON_HEIGHT);

        layout = new Layout(panel, header, eyeCard, eyeToggle, eyeSlider, scaleCard, scaleSlider, quickCard, quickButtons,
                saveButton, resetButton, animButton, voiceButton, doneButton);
    }

    private void updateHoverState(int mouseX, int mouseY) {
        hoveredTarget = HoverTarget.NONE;
        if (layout.eyeToggle.contains(mouseX, mouseY)) {
            hoveredTarget = HoverTarget.EYE_TOGGLE;
            return;
        }
        if (layout.eyeSlider.contains(mouseX, mouseY)) {
            hoveredTarget = HoverTarget.EYE_SLIDER;
            return;
        }
        if (layout.scaleSlider.contains(mouseX, mouseY)) {
            hoveredTarget = HoverTarget.SCALE_SLIDER;
            return;
        }
        for (int i = 0; i < layout.quickSlotButtons.length; i++) {
            UiRect slotButton = layout.quickSlotButtons[i];
            if (slotButton != null && slotButton.contains(mouseX, mouseY)) {
                hoveredTarget = switch (i) {
                    case 0 -> HoverTarget.SLOT_0;
                    case 1 -> HoverTarget.SLOT_1;
                    case 2 -> HoverTarget.SLOT_2;
                    default -> HoverTarget.SLOT_3;
                };
                return;
            }
        }
        if (layout.saveButton.contains(mouseX, mouseY)) {
            hoveredTarget = HoverTarget.SAVE;
            return;
        }
        if (layout.resetButton.contains(mouseX, mouseY)) {
            hoveredTarget = HoverTarget.RESET;
            return;
        }
        if (layout.animButton.contains(mouseX, mouseY)) {
            hoveredTarget = HoverTarget.ANIM;
            return;
        }
        if (layout.voiceButton.contains(mouseX, mouseY)) {
            hoveredTarget = HoverTarget.VOICE;
            return;
        }
        if (layout.doneButton.contains(mouseX, mouseY)) {
            hoveredTarget = HoverTarget.DONE;
        }
    }

    private void updateSliderValue(ActiveSlider slider, double mouseX) {
        if (slider == ActiveSlider.EYE) {
            config.eyeMaxAngle = valueFromSlider(layout.eyeSlider, mouseX, 0.05f, 1.0f);
            return;
        }
        if (slider == ActiveSlider.SCALE) {
            config.modelScale = valueFromSlider(layout.scaleSlider, mouseX, 0.5f, 2.0f);
        }
    }

    private float valueFromSlider(UiRect sliderRect, double mouseX, float min, float max) {
        float t = (float) ((mouseX - sliderRect.x) / Math.max(1.0, sliderRect.w));
        return Mth.clamp(min + (max - min) * Mth.clamp(t, 0.0f, 1.0f), min, max);
    }

    private SkiaModelSettingsRenderer.SettingsView buildView() {
        List<ModelSettingsApplicationService.QuickSlotBinding> quickSlots = SERVICE.getQuickSlotBindings(modelName);
        List<SkiaModelSettingsRenderer.QuickSlotView> slotViews = new ArrayList<>(quickSlots.size());
        for (int i = 0; i < quickSlots.size(); i++) {
            ModelSettingsApplicationService.QuickSlotBinding binding = quickSlots.get(i);
            slotViews.add(new SkiaModelSettingsRenderer.QuickSlotView(
                    buildQuickSlotLabel(binding),
                    shorten(binding.boundModel(), 12),
                    binding.boundToCurrentModel(),
                    hoveredTarget == slotHoverTarget(i),
                    i,
                    layout.quickSlotButtons[i]
            ));
        }

        return new SkiaModelSettingsRenderer.SettingsView(
                this.title.getString(),
                shorten(modelName, 14),
                Component.translatable("gui.mmdskin.model_settings.eye_tracking").getString(),
                Component.translatable("gui.mmdskin.model_settings.eye_tracking_enabled").getString(),
                Component.translatable("gui.mmdskin.model_settings.eye_max_angle", String.format("%.0f", Math.toDegrees(config.eyeMaxAngle))).getString(),
                Component.translatable("gui.mmdskin.model_settings.model_display").getString(),
                Component.translatable("gui.mmdskin.model_settings.model_scale", String.format("%.2f", config.modelScale)).getString(),
                Component.translatable("gui.mmdskin.model_settings.quick_bind").getString(),
                Component.translatable("gui.mmdskin.model_settings.save").getString(),
                Component.translatable("gui.mmdskin.model_settings.reset").getString(),
                Component.translatable("gui.mmdskin.model_settings.anim_config").getString(),
                Component.translatable("gui.mmdskin.model_settings.voice_config").getString(),
                Component.translatable("gui.done").getString(),
                layout.panel,
                layout.header,
                layout.eyeCard,
                layout.eyeToggle,
                layout.eyeSlider,
                layout.scaleCard,
                layout.scaleSlider,
                layout.quickCard,
                layout.saveButton,
                layout.resetButton,
                layout.animButton,
                layout.voiceButton,
                layout.doneButton,
                config.eyeTrackingEnabled,
                normalized(config.eyeMaxAngle, 0.05f, 1.0f),
                normalized(config.modelScale, 0.5f, 2.0f),
                hoveredTarget == HoverTarget.EYE_TOGGLE,
                hoveredTarget == HoverTarget.EYE_SLIDER,
                hoveredTarget == HoverTarget.SCALE_SLIDER,
                hoveredTarget == HoverTarget.SAVE,
                hoveredTarget == HoverTarget.RESET,
                hoveredTarget == HoverTarget.ANIM,
                hoveredTarget == HoverTarget.VOICE,
                hoveredTarget == HoverTarget.DONE,
                slotViews
        );
    }

    private float normalized(float value, float min, float max) {
        return Mth.clamp((value - min) / (max - min), 0.0f, 1.0f);
    }

    private HoverTarget slotHoverTarget(int index) {
        return switch (index) {
            case 0 -> HoverTarget.SLOT_0;
            case 1 -> HoverTarget.SLOT_1;
            case 2 -> HoverTarget.SLOT_2;
            default -> HoverTarget.SLOT_3;
        };
    }

    private void renderFallback(GuiGraphics guiGraphics, SkiaModelSettingsRenderer.SettingsView view) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x28000000);
        guiGraphics.fill(view.panel().x(), view.panel().y(), view.panel().x() + view.panel().w(), view.panel().y() + view.panel().h(), 0x2A000000);
        guiGraphics.fill(view.panel().x() + 1, view.panel().y() + 1, view.panel().x() + view.panel().w() - 1, view.panel().y() + view.panel().h() - 1, 0x20000000);

        guiGraphics.drawString(this.font, view.title(), view.header().x(), view.header().y() + 1, 0xFFF1F5FB, false);
        guiGraphics.drawString(this.font, view.modelName(), view.header().x(), view.header().y() + 10, 0xC8D5DFEC, false);

        drawFallbackCard(guiGraphics, view.eyeCard(), view.eyeSectionTitle());
        guiGraphics.drawString(this.font, view.eyeToggleText(), view.eyeCard().x() + 4, view.eyeCard().y() + 13, 0xFFE9F1FA, false);
        drawFallbackSlider(guiGraphics, view.eyeSlider(), view.eyeAngleLabel(), view.eyeAngleNormalized());

        drawFallbackCard(guiGraphics, view.scaleCard(), view.scaleSectionTitle());
        drawFallbackSlider(guiGraphics, view.scaleSlider(), view.modelScaleLabel(), view.modelScaleNormalized());

        drawFallbackCard(guiGraphics, view.quickCard(), view.quickSectionTitle());
        for (SkiaModelSettingsRenderer.QuickSlotView slot : view.quickSlots()) {
            int bg = slot.boundToCurrentModel() ? 0x52FFFFFF : (slot.hovered() ? 0x38FFFFFF : 0x24000000);
            guiGraphics.fill(slot.rect().x(), slot.rect().y(), slot.rect().x() + slot.rect().w(), slot.rect().y() + slot.rect().h(), bg);
            guiGraphics.drawCenteredString(this.font, slot.slotLabel(), slot.rect().centerX(), slot.rect().y() + 4, 0xFFF1F6FD);
        }

        drawFallbackButton(guiGraphics, view.saveButton(), view.saveText(), view.saveHovered());
        drawFallbackButton(guiGraphics, view.resetButton(), view.resetText(), view.resetHovered());
        drawFallbackButton(guiGraphics, view.animButton(), view.animText(), view.animHovered());
        drawFallbackButton(guiGraphics, view.voiceButton(), view.voiceText(), view.voiceHovered());
        drawFallbackButton(guiGraphics, view.doneButton(), view.doneText(), view.doneHovered());
    }

    private void drawFallbackCard(GuiGraphics guiGraphics, UiRect rect, String title) {
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, 0x22000000);
        guiGraphics.drawString(this.font, title, rect.x + 4, rect.y + 3, 0xFFE9F1FA, false);
    }

    private void drawFallbackSlider(GuiGraphics guiGraphics, UiRect rect, String label, float normalized) {
        guiGraphics.drawString(this.font, label, rect.x, rect.y - 8, 0xC8D5DFEC, false);
        guiGraphics.fill(rect.x, rect.y + 3, rect.x + rect.w, rect.y + 7, 0x28FFFFFF);
        int fillRight = rect.x + Math.round(rect.w * normalized);
        guiGraphics.fill(rect.x, rect.y + 3, fillRight, rect.y + 7, 0x58FFFFFF);
    }

    private void drawFallbackButton(GuiGraphics guiGraphics, UiRect rect, String text, boolean hovered) {
        int bg = hovered ? 0x4AFFFFFF : 0x30000000;
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, bg);
        guiGraphics.drawCenteredString(this.font, text, rect.centerX(), rect.y + 4, 0xFFF1F6FD);
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
        LOGGER.error("[ModelSettings] Skia settings failed and will close", throwable);
        skiaRenderer.dispose();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            minecraft.setScreen(parentScreen);
        }
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

    private static String shorten(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        if (maxChars <= 3) {
            return value.substring(0, Math.max(0, maxChars));
        }
        return value.substring(0, maxChars - 2) + "..";
    }

    record UiRect(int x, int y, int w, int h) {
        static UiRect empty() {
            return new UiRect(0, 0, 0, 0);
        }

        boolean contains(double px, double py) {
            return px >= x && py >= y && px <= x + w && py <= y + h;
        }

        int centerX() {
            return x + w / 2;
        }

        int centerY() {
            return y + h / 2;
        }
    }

    private record Layout(
            UiRect panel,
            UiRect header,
            UiRect eyeCard,
            UiRect eyeToggle,
            UiRect eyeSlider,
            UiRect scaleCard,
            UiRect scaleSlider,
            UiRect quickCard,
            UiRect[] quickSlotButtons,
            UiRect saveButton,
            UiRect resetButton,
            UiRect animButton,
            UiRect voiceButton,
            UiRect doneButton
    ) {
        static Layout empty() {
            UiRect empty = UiRect.empty();
            return new Layout(empty, empty, empty, empty, empty, empty, empty, empty, new UiRect[4], empty, empty, empty, empty, empty);
        }
    }
}
