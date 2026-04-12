package com.shiroha.mmdskin.ui.selector;

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
import java.util.List;
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
    private final SkiaMaterialVisibilityRenderer skiaRenderer = new SkiaMaterialVisibilityRenderer();
    private final List<MaterialEntryState> materials = new ArrayList<>();

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

            SkiaMaterialVisibilityRenderer.MaterialView view = buildView();
            if (!skiaRenderer.renderMaterialScreen(this, view)) {
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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!layout.listBox.contains(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        float step = 12.0f;
        targetScroll -= (float) delta * step;
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll());
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
        skiaRenderer.dispose();
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
        float listTop = layout.listBox.y + LIST_PADDING;
        float itemStride = CARD_HEIGHT + CARD_GAP;
        float localY = (float) mouseY - listTop + animatedScroll;
        if (localY < 0.0f) {
            return;
        }
        int index = (int) (localY / itemStride);
        if (index < 0 || index >= materials.size()) {
            return;
        }
        float offsetInItem = localY - index * itemStride;
        if (offsetInItem <= CARD_HEIGHT) {
            hoveredCard = index;
        }
    }

    private void updateScrollAnimation() {
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll());
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

    private SkiaMaterialVisibilityRenderer.MaterialView buildView() {
        List<SkiaMaterialVisibilityRenderer.MaterialCardView> cards = new ArrayList<>(materials.size());
        for (int i = 0; i < materials.size(); i++) {
            MaterialEntryState entry = materials.get(i);
            cards.add(new SkiaMaterialVisibilityRenderer.MaterialCardView(
                    buildMaterialLabel(entry),
                    entry.visible(),
                    i == hoveredCard,
                    i
            ));
        }

        return new SkiaMaterialVisibilityRenderer.MaterialView(
                this.title.getString(),
                shorten(context.modelName(), 8),
                visibleCount() + " / " + materials.size(),
                Component.translatable("gui.mmdskin.material_visibility.show_all").getString(),
                Component.translatable("gui.mmdskin.material_visibility.hide_all").getString(),
                Component.translatable("gui.mmdskin.material_visibility.invert").getString(),
                Component.translatable("gui.done").getString(),
                layout.panel,
                layout.header,
                layout.listBox,
                layout.showAllButton,
                layout.hideAllButton,
                layout.invertButton,
                layout.doneButton,
                hoveredButton == ButtonTarget.SHOW_ALL,
                hoveredButton == ButtonTarget.HIDE_ALL,
                hoveredButton == ButtonTarget.INVERT,
                hoveredButton == ButtonTarget.DONE,
                animatedScroll,
                cards,
                LIST_PADDING,
                CARD_HEIGHT,
                CARD_GAP,
                "-"
        );
    }

    private void renderFallback(GuiGraphics guiGraphics, SkiaMaterialVisibilityRenderer.MaterialView view) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x28000000);
        guiGraphics.fill(view.panel().x(), view.panel().y(), view.panel().x() + view.panel().w(), view.panel().y() + view.panel().h(), 0x2A000000);
        guiGraphics.fill(view.panel().x() + 1, view.panel().y() + 1, view.panel().x() + view.panel().w() - 1, view.panel().y() + view.panel().h() - 1, 0x20000000);

        guiGraphics.drawString(this.font, view.title(), view.header().x(), view.header().y() + 1, 0xFFF1F5FB, false);
        guiGraphics.drawString(this.font, view.modelName(), view.header().x(), view.header().y() + 10, 0xC8D5DFEC, false);
        guiGraphics.drawString(this.font, view.counter(), view.header().x(), view.header().y() + 19, 0xBCD0DCE9, false);

        drawFallbackButton(guiGraphics, view.showAllButton(), view.showAllText(), view.showAllHovered());
        drawFallbackButton(guiGraphics, view.hideAllButton(), view.hideAllText(), view.hideAllHovered());
        drawFallbackButton(guiGraphics, view.invertButton(), view.invertText(), view.invertHovered());
        drawFallbackButton(guiGraphics, view.doneButton(), view.doneText(), view.doneHovered());

        UiRect list = view.listBox();
        guiGraphics.fill(list.x, list.y, list.x + list.w, list.y + list.h, 0x22000000);
        if (view.cards().isEmpty()) {
            guiGraphics.drawCenteredString(this.font, view.emptyText(), list.centerX(), list.centerY() - 4, 0xFFDDE8F8);
            return;
        }

        int y = Math.round(list.y + view.listPadding() - view.scrollOffset());
        for (SkiaMaterialVisibilityRenderer.MaterialCardView card : view.cards()) {
            if (y + view.cardHeight() < list.y) {
                y += view.cardHeight() + view.cardGap();
                continue;
            }
            if (y > list.y + list.h) {
                break;
            }
            int bg = card.visible()
                    ? (card.hovered() ? 0x3EFFFFFF : 0x24000000)
                    : (card.hovered() ? 0x66FFFFFF : 0x4AFFFFFF);
            guiGraphics.fill(list.x + 4, y, list.x + list.w - 4, y + view.cardHeight(), bg);
            guiGraphics.drawString(this.font, card.label(), list.x + 7, y + 3, 0xFFE9F1FA, false);
            y += view.cardHeight() + view.cardGap();
        }
    }

    private void drawFallbackButton(GuiGraphics guiGraphics, UiRect rect, String text, boolean hovered) {
        int bg = hovered ? 0x4AFFFFFF : 0x30000000;
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, bg);
        guiGraphics.drawCenteredString(this.font, text, rect.centerX(), rect.y + 4, 0xFFF1F6FD);
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
        LOGGER.error("[MaterialVisibility] Skia material visibility screen failed and will close", throwable);
        skiaRenderer.dispose();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            minecraft.setScreen(null);
        }
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
