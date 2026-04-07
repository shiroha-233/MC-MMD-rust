package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.ui.imgui.ImGuiScreenRenderer;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialEntryState;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialScreenContext;
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

public class MaterialVisibilityScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final float WINDOW_MARGIN = 8.0f;
    private static final float MIN_WINDOW_WIDTH = 220.0f;
    private static final float MAX_WINDOW_WIDTH = 320.0f;
    private static final float MIN_WINDOW_HEIGHT = 260.0f;
    private static final MaterialVisibilityApplicationService SERVICE = ModelSelectorServices.materialVisibility();

    private final MaterialScreenContext context;
    private final ImGuiScreenRenderer imguiRenderer = new ImGuiScreenRenderer();
    private final List<MaterialEntryState> materials = new ArrayList<>();

    private boolean pendingClose;

    public MaterialVisibilityScreen(MaterialScreenContext context) {
        super(Component.translatable("gui.mmdskin.material_visibility.title"));
        this.context = context;
        loadMaterials();
    }

    public static MaterialVisibilityScreen createForPlayer() {
        return SERVICE.createPlayerContext().map(MaterialVisibilityScreen::new).orElse(null);
    }

    public static MaterialVisibilityScreen createForMaid(java.util.UUID maidUUID, String maidName) {
        return SERVICE.createMaidContext(maidUUID, maidName).map(MaterialVisibilityScreen::new).orElse(null);
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
            renderMaterialWindow();
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
        saveMaterialVisibility();
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

    private void renderMaterialWindow() {
        float panelWidth = clamp(this.width * 0.18f, MIN_WINDOW_WIDTH, MAX_WINDOW_WIDTH);
        float panelHeight = Math.max(MIN_WINDOW_HEIGHT, this.height - WINDOW_MARGIN);
        float panelX = this.width - panelWidth - WINDOW_MARGIN;
        float panelY = WINDOW_MARGIN;
        int windowFlags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings;

        ImGui.setNextWindowPos(panelX, panelY, ImGuiCond.Appearing);
        ImGui.setNextWindowSize(panelWidth, panelHeight, ImGuiCond.Appearing);
        ImGui.begin(this.title.getString() + "##material_visibility_window", windowFlags);

        renderHeader();
        ImGui.separator();
        renderMaterialList();
        ImGui.separator();
        renderFooterActions();

        ImGui.end();
    }

    private void renderHeader() {
        ImGui.textDisabled(shorten(context.modelName(), 24));
        ImGui.textDisabled(visibleCount() + " / " + materials.size());
    }

    private void renderMaterialList() {
        if (materials.isEmpty()) {
            ImGui.textDisabled("-");
            return;
        }

        float listHeight = Math.max(120.0f, ImGui.getContentRegionAvailY() - 58.0f);
        ImGui.beginChild("##material_list", 0.0f, listHeight, true);

        for (int i = 0; i < materials.size(); i++) {
            MaterialEntryState entry = materials.get(i);
            String label = buildMaterialLabel(entry);
            if (ImGui.selectable(label + "##material_" + entry.index(), false, 0, fullWidth(), 0.0f)) {
                SERVICE.toggleMaterial(context, materials, i);
            }
        }

        ImGui.endChild();
    }

    private void renderFooterActions() {
        float totalWidth = Math.max(1.0f, ImGui.getContentRegionAvailX());
        float buttonWidth = Math.max(72.0f, (totalWidth - 8.0f) / 3.0f);

        if (ImGui.button(Component.translatable("gui.mmdskin.material_visibility.show_all").getString() + "##show_all", buttonWidth, 0.0f)) {
            SERVICE.setAllVisible(context, materials, true);
        }
        ImGui.sameLine();
        if (ImGui.button(Component.translatable("gui.mmdskin.material_visibility.hide_all").getString() + "##hide_all", buttonWidth, 0.0f)) {
            SERVICE.setAllVisible(context, materials, false);
        }
        ImGui.sameLine();
        if (ImGui.button(Component.translatable("gui.mmdskin.material_visibility.invert").getString() + "##invert", buttonWidth, 0.0f)) {
            SERVICE.invertSelection(context, materials);
        }

        if (fullWidthButton(Component.translatable("gui.done").getString() + "##material_done")) {
            pendingClose = true;
        }
    }

    private void loadMaterials() {
        materials.clear();
        materials.addAll(SERVICE.loadMaterials(context));
    }

    private void saveMaterialVisibility() {
        SERVICE.save(context, materials);
    }

    private int visibleCount() {
        int count = 0;
        for (MaterialEntryState material : materials) {
            if (material.visible()) {
                count++;
            }
        }
        return count;
    }

    private void flushPendingActions(Minecraft minecraft) {
        if (pendingClose && minecraft.screen == this) {
            pendingClose = false;
            minecraft.setScreen(null);
        }
    }

    private void closeAfterFailure(Throwable throwable) {
        LOGGER.error("[MaterialVisibility] ImGui material visibility screen failed and will close", throwable);
        imguiRenderer.dispose();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            minecraft.setScreen(null);
        }
    }

    private List<String> collectVisibleGlyphHints() {
        List<String> hints = new ArrayList<>();
        hints.add(this.title.getString());
        hints.add(context.modelName());
        hints.add(Component.translatable("gui.mmdskin.material_visibility.show_all").getString());
        hints.add(Component.translatable("gui.mmdskin.material_visibility.hide_all").getString());
        hints.add(Component.translatable("gui.mmdskin.material_visibility.invert").getString());
        hints.add(Component.translatable("gui.done").getString());
        for (MaterialEntryState entry : materials) {
            hints.add(entry.name());
            hints.add(buildMaterialLabel(entry));
        }
        return hints;
    }

    private static String buildMaterialLabel(MaterialEntryState entry) {
        String name = entry.name() == null || entry.name().isEmpty()
                ? Component.translatable("gui.mmdskin.material_visibility.unnamed").getString()
                : entry.name();
        String state = Component.translatable(
                entry.visible()
                        ? "gui.mmdskin.material_visibility.on"
                        : "gui.mmdskin.material_visibility.off"
        ).getString();
        return shorten(name, 22) + " [" + state + "]";
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
