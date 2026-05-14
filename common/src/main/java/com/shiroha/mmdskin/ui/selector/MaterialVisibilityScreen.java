/* 文件职责：提供材质可见性原生界面。 */
package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialEntryState;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialScreenContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MaterialVisibilityScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final MaterialVisibilityApplicationService SERVICE = ModelSelectorServices.materialVisibility();

    private static final int WINDOW_MARGIN = 10;
    private static final int MIN_WINDOW_WIDTH = 150;
    private static final int MAX_WINDOW_WIDTH = 190;
    private static final int MIN_WINDOW_HEIGHT = 220;

    private static final int HEADER_HEIGHT = 42;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 4;
    private static final int LIST_PADDING = 5;
    private static final int CARD_HEIGHT = 14;
    private static final int CARD_GAP = 4;

    private final MaterialScreenContext context;
    private final List<MaterialEntryState> materials = new ArrayList<>();
    private final Set<Integer> initialHiddenMaterials = new HashSet<>();

    private boolean pendingClose;
    private float targetScroll;
    private float animatedScroll;
    private int hoveredCard = -1;
    private ButtonTarget hoveredButton = ButtonTarget.NONE;
    private Layout layout = Layout.empty();

    private enum ButtonTarget {
        NONE,
        SHOW_ALL,
        HIDE_ALL,
        INVERT,
        DONE
    }

    public MaterialVisibilityScreen(MaterialScreenContext context) {
        super(Component.translatable("gui.mmdskin.material_visibility.title"));
        this.context = context;
        loadMaterials();
    }

    public static MaterialVisibilityScreen createForPlayer() {
        return SERVICE.createPlayerContext().map(MaterialVisibilityScreen::new).orElse(null);
    }

    public static MaterialVisibilityScreen createForMaid(UUID maidUUID, String maidName) {
        return SERVICE.createMaidContext(maidUUID, maidName).map(MaterialVisibilityScreen::new).orElse(null);
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
            updateScrollAnimation();
            renderScreen(guiGraphics);
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

        if (layout.showAllButton.contains(mouseX, mouseY)) {
            SERVICE.setAllVisible(context, materials, true);
            return true;
        }
        if (layout.hideAllButton.contains(mouseX, mouseY)) {
            SERVICE.setAllVisible(context, materials, false);
            return true;
        }
        if (layout.invertButton.contains(mouseX, mouseY)) {
            SERVICE.invertSelection(context, materials);
            return true;
        }
        if (layout.doneButton.contains(mouseX, mouseY)) {
            pendingClose = true;
            return true;
        }
        if (layout.listBox.contains(mouseX, mouseY) && hoveredCard >= 0 && hoveredCard < materials.size()) {
            SERVICE.toggleMaterial(context, materials, hoveredCard);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!layout.listBox.contains(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        targetScroll = Mth.clamp(targetScroll - (float) scrollY * 12.0f, 0.0f, maxScroll());
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
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void updateLayout() {
        int panelWidth = Mth.clamp(Math.round(this.width * 0.14f), MIN_WINDOW_WIDTH, MAX_WINDOW_WIDTH);
        int panelHeight = Math.max(MIN_WINDOW_HEIGHT, this.height - WINDOW_MARGIN * 2);
        int panelX = this.width - panelWidth - WINDOW_MARGIN;
        int panelY = WINDOW_MARGIN;

        UiRect panel = new UiRect(panelX, panelY, panelWidth, panelHeight);
        UiRect header = new UiRect(panelX + 8, panelY + 5, panelWidth - 16, HEADER_HEIGHT);

        int doneY = panel.y + panel.h - BUTTON_HEIGHT - 6;
        UiRect doneButton = new UiRect(header.x, doneY, header.w, BUTTON_HEIGHT);

        int rowY = doneY - BUTTON_HEIGHT - BUTTON_GAP;
        int rowButtonWidth = (header.w - BUTTON_GAP * 2) / 3;
        UiRect showAllButton = new UiRect(header.x, rowY, rowButtonWidth, BUTTON_HEIGHT);
        UiRect hideAllButton = new UiRect(header.x + rowButtonWidth + BUTTON_GAP, rowY, rowButtonWidth, BUTTON_HEIGHT);
        UiRect invertButton = new UiRect(header.x + (rowButtonWidth + BUTTON_GAP) * 2, rowY, rowButtonWidth, BUTTON_HEIGHT);

        int listY = header.y + header.h + 2;
        int listBottom = rowY - 4;
        int listHeight = Math.max(48, listBottom - listY);
        UiRect listBox = new UiRect(header.x, listY, header.w, listHeight);
        layout = new Layout(panel, header, listBox, showAllButton, hideAllButton, invertButton, doneButton);

        float maxScroll = maxScroll();
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll);
        animatedScroll = Mth.clamp(animatedScroll, 0.0f, maxScroll);
    }

    private void updateHoverState(int mouseX, int mouseY) {
        hoveredButton = ButtonTarget.NONE;
        hoveredCard = -1;

        if (layout.showAllButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.SHOW_ALL;
            return;
        }
        if (layout.hideAllButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.HIDE_ALL;
            return;
        }
        if (layout.invertButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.INVERT;
            return;
        }
        if (layout.doneButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.DONE;
            return;
        }
        if (!layout.listBox.contains(mouseX, mouseY) || materials.isEmpty()) {
            return;
        }

        float localY = (float) mouseY - (layout.listBox.y + LIST_PADDING) + animatedScroll;
        if (localY < 0.0f) {
            return;
        }
        int index = (int) (localY / (CARD_HEIGHT + CARD_GAP));
        if (index < 0 || index >= materials.size()) {
            return;
        }
        if (localY - index * (CARD_HEIGHT + CARD_GAP) <= CARD_HEIGHT) {
            hoveredCard = index;
        }
    }

    private void updateScrollAnimation() {
        animatedScroll = Mth.lerp(0.24f, animatedScroll, targetScroll);
        if (Math.abs(animatedScroll - targetScroll) < 0.25f) {
            animatedScroll = targetScroll;
        }
    }

    private float maxScroll() {
        int count = materials.size();
        float contentHeight = count <= 0
                ? 0.0f
                : LIST_PADDING * 2.0f + count * CARD_HEIGHT + Math.max(0, count - 1) * CARD_GAP;
        return Math.max(0.0f, contentHeight - layout.listBox.h);
    }

    private void renderScreen(GuiGraphics guiGraphics) {
        TranslucentTrayChrome.drawOverlay(guiGraphics, this.width, this.height);
        TranslucentTrayChrome.drawPanel(guiGraphics, layout.panel.x, layout.panel.y, layout.panel.w, layout.panel.h);
        guiGraphics.drawString(this.font, this.title.getString(), layout.header.x, layout.header.y + 1, TranslucentTrayChrome.TITLE_TEXT, false);
        guiGraphics.drawString(this.font, shorten(context.modelName(), 8), layout.header.x, layout.header.y + 10, TranslucentTrayChrome.SUBTITLE_TEXT, false);
        guiGraphics.drawString(this.font, visibleCount() + " / " + materials.size(), layout.header.x, layout.header.y + 19, TranslucentTrayChrome.DETAIL_TEXT, false);

        drawButton(guiGraphics, layout.showAllButton, Component.translatable("gui.mmdskin.material_visibility.show_all").getString(), hoveredButton == ButtonTarget.SHOW_ALL);
        drawButton(guiGraphics, layout.hideAllButton, Component.translatable("gui.mmdskin.material_visibility.hide_all").getString(), hoveredButton == ButtonTarget.HIDE_ALL);
        drawButton(guiGraphics, layout.invertButton, Component.translatable("gui.mmdskin.material_visibility.invert").getString(), hoveredButton == ButtonTarget.INVERT);
        drawButton(guiGraphics, layout.doneButton, Component.translatable("gui.done").getString(), hoveredButton == ButtonTarget.DONE);

        UiRect list = layout.listBox;
        TranslucentTrayChrome.fillListArea(guiGraphics, list.x, list.y, list.w, list.h);
        if (materials.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "-", list.centerX(), list.centerY() - 4, TranslucentTrayChrome.BODY_TEXT);
            return;
        }

        guiGraphics.enableScissor(list.x, list.y, list.x + list.w, list.y + list.h);
        int y = Math.round(list.y + LIST_PADDING - animatedScroll);
        for (int i = 0; i < materials.size(); i++) {
            MaterialEntryState material = materials.get(i);
            if (y + CARD_HEIGHT < list.y) {
                y += CARD_HEIGHT + CARD_GAP;
                continue;
            }
            if (y > list.y + list.h) {
                break;
            }
            boolean hovered = i == hoveredCard;
            int bg = material.visible()
                    ? TranslucentTrayChrome.cardBackground(false, hovered)
                    : hovered ? 0x66FFFFFF : TranslucentTrayChrome.BUTTON_HOVER;
            guiGraphics.fill(list.x + 4, y, list.x + list.w - 4, y + CARD_HEIGHT, bg);
            guiGraphics.drawString(this.font, buildMaterialLabel(material), list.x + 7, y + 3, TranslucentTrayChrome.BODY_TEXT, false);
            y += CARD_HEIGHT + CARD_GAP;
        }
        guiGraphics.disableScissor();
        TranslucentTrayChrome.drawScrollbar(guiGraphics, list.x + list.w - 3, list.y, list.y + list.h, animatedScroll, maxScroll());
    }

    private void drawButton(GuiGraphics guiGraphics, UiRect rect, String text, boolean hovered) {
        TranslucentTrayChrome.drawButton(guiGraphics, this.font, rect.x, rect.y, rect.w, rect.h, text, hovered, true);
    }

    private void loadMaterials() {
        materials.clear();
        materials.addAll(SERVICE.loadMaterials(context));
        initialHiddenMaterials.clear();
        initialHiddenMaterials.addAll(SERVICE.snapshotHiddenMaterials(materials));
    }

    private void saveMaterialVisibility() {
        SERVICE.saveIfChanged(context, materials, initialHiddenMaterials);
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
        LOGGER.error("[MaterialVisibility] Native material visibility render failed and will close", throwable);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            minecraft.setScreen(null);
        }
    }

    private static String buildMaterialLabel(MaterialEntryState entry) {
        String name = entry.name().isEmpty()
                ? Component.translatable("gui.mmdskin.material_visibility.unnamed").getString()
                : entry.name();
        String state = Component.translatable(
                entry.visible()
                        ? "gui.mmdskin.material_visibility.on"
                        : "gui.mmdskin.material_visibility.off"
        ).getString();
        return shorten(name, 12) + " [" + state + "]";
    }

    private static String shorten(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        if (maxChars <= 3) {
            return text.substring(0, Math.max(0, maxChars));
        }
        return text.substring(0, maxChars - 2) + "..";
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
            UiRect listBox,
            UiRect showAllButton,
            UiRect hideAllButton,
            UiRect invertButton,
            UiRect doneButton
    ) {
        static Layout empty() {
            UiRect empty = UiRect.empty();
            return new Layout(empty, empty, empty, empty, empty, empty, empty);
        }
    }
}
