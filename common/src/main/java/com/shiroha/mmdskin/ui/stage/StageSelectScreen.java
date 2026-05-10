/* 文件职责：提供原生舞台选择界面，并承载房主与成员的舞台配置操作。 */
package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StagePack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 文件职责：提供原生舞台选择界面并承接房主与成员的舞台配置操作。 */
public class StageSelectScreen extends Screen {
    private static final int PANEL_MARGIN = 10;
    private static final int LEFT_PANEL_WIDTH = 214;
    private static final int HEADER_HEIGHT = 44;
    private static final int LIST_ROW_HEIGHT = 18;
    private static final int ROW_GAP = 3;
    private static final int GUEST_FOOTER_HEIGHT = 96;
    private static final int HOST_FOOTER_HEIGHT = 104;
    private static final int SECTION_GAP = 8;
    private static final int BUTTON_HEIGHT = 18;

    private static final int COLOR_BG = 0xC012171D;
    private static final int COLOR_BORDER = 0xFF314253;
    private static final int COLOR_ACCENT = 0xFF65A9D8;
    private static final int COLOR_TEXT = 0xFFEAF2FB;
    private static final int COLOR_TEXT_DIM = 0xFFA5B4C3;
    private static final int COLOR_TEXT_MUTED = 0xFF7E8D9D;
    private static final int COLOR_ROW = 0x16000000;
    private static final int COLOR_ROW_HOVER = 0x24FFFFFF;
    private static final int COLOR_ROW_SELECTED = 0x2C65A9D8;
    private static final int COLOR_TOGGLE_ON = 0xFF4BB97C;
    private static final int COLOR_TOGGLE_OFF = 0xFF53606D;
    private static final int COLOR_BUTTON = 0xFF40586F;
    private static final int COLOR_BUTTON_HOVER = 0xFF557492;
    private static final int COLOR_BUTTON_GO = 0xFF3FA66B;
    private static final int COLOR_BUTTON_GO_HOVER = 0xFF52C67F;
    private static final int COLOR_BUTTON_CANCEL = 0xFF5C6671;
    private static final int COLOR_BUTTON_CANCEL_HOVER = 0xFF6C7782;
    private static final int COLOR_BUTTON_DISABLED = 0xFF36424D;
    private static final int COLOR_SCROLL_TRACK = 0x26000000;
    private static final int COLOR_SCROLL_THUMB = 0xFF65A9D8;
    private static final int COLOR_SEPARATOR = 0x18FFFFFF;

    private final StageWorkbenchFacade facade;
    private final StageSelectState state;

    private StageAssignPanel assignPanel;
    private boolean stageStarted;
    private boolean stageSelectionOpened;
    private boolean stageSelectionClosed;
    private boolean draggingCameraHeight;

    private float packScroll;
    private float motionScroll;
    private int hoveredPackIndex = -1;
    private int hoveredMotionIndex = -1;
    private boolean hoveredRefresh;
    private boolean hoveredCinematic;
    private boolean hoveredUseHostCamera;
    private boolean hoveredPrimary;
    private boolean hoveredCancel;
    private boolean hoveredMergeAll;

    private int leftPanelX;
    private int leftPanelY;
    private int leftPanelHeight;
    private int packListTop;
    private int packListBottom;
    private int motionHeaderY;
    private int motionListTop;
    private int motionListBottom;
    private int footerTop;
    private int footerButtonY;
    private int footerPrimaryX;
    private int footerSecondaryX;
    private int footerButtonWidth;
    private int cameraSliderLabelY;
    private int cameraSliderTrackY;
    private int cameraSliderX;
    private int cameraSliderWidth;
    private int useHostCameraY;
    private int cinematicToggleY;

    public StageSelectScreen() {
        this(StageWorkbenchFacade.getInstance());
    }

    StageSelectScreen(StageWorkbenchFacade facade) {
        super(Component.translatable("gui.mmdskin.config.stage_mode"));
        this.facade = Objects.requireNonNull(facade, "facade");
        this.state = new StageSelectState(facade.loadPreferences(), facade.loadStagePacks());
    }

    public void markStartedByHost() {
        this.stageStarted = true;
    }

    public void prepareForExternalClose() {
        this.stageStarted = true;
    }

    @Override
    protected void init() {
        super.init();
        if (!stageSelectionOpened) {
            facade.onStageSelectionOpened();
            stageSelectionOpened = true;
        }
        if (assignPanel == null && this.font != null) {
            assignPanel = new StageAssignPanel(this.font, facade);
        }
        updateLayout();
        syncGuestPackPreferences();
        syncAssignPanel();
    }

    @Override
    public void tick() {
        super.tick();
        syncAssignPanel();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateLayout();
        syncAssignPanel();
        updateHoverState(mouseX, mouseY);

        graphics.fill(0, 0, this.width, this.height, 0x28000000);
        drawPanel(graphics, leftPanelX, leftPanelY, LEFT_PANEL_WIDTH, leftPanelHeight);
        drawHeader(graphics);
        drawPackList(graphics);
        drawMotionSection(graphics);
        drawFooter(graphics);
        if (assignPanel != null) {
            assignPanel.render(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (assignPanel != null && assignPanel.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        if (!containsLeftPanel(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (hoveredRefresh) {
            reloadStagePacks();
            return true;
        }
        if (hoveredCinematic) {
            state.toggleCinematicMode();
            return true;
        }
        if (facade.isSessionMember() && hoveredUseHostCamera) {
            facade.toggleUseHostCamera();
            return true;
        }
        if (!facade.isSessionMember() && isCameraSliderHovered(mouseX, mouseY)) {
            draggingCameraHeight = true;
            updateCameraHeight(mouseX);
            return true;
        }
        if (hoveredPrimary) {
            if (facade.isSessionMember()) {
                facade.toggleLocalReady();
            } else if (facade.canStartStage(state.selectedPack())) {
                startStage();
            }
            return true;
        }
        if (hoveredCancel) {
            onClose();
            return true;
        }
        if (hoveredMergeAll) {
            state.clearSelectedHostMotion();
            return true;
        }
        if (hoveredPackIndex >= 0 && hoveredPackIndex < state.stagePacks().size()) {
            if (state.selectPack(hoveredPackIndex)) {
                motionScroll = 0.0f;
                syncGuestPackPreferences();
            }
            return true;
        }
        if (!facade.isSessionMember() && hoveredMotionIndex >= 0) {
            List<StagePack.VmdFileInfo> motionFiles = collectMotionFiles();
            if (hoveredMotionIndex < motionFiles.size()) {
                state.toggleSelectedHostMotion(motionFiles.get(hoveredMotionIndex).name);
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingCameraHeight) {
            updateCameraHeight(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingCameraHeight) {
            draggingCameraHeight = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (assignPanel != null && assignPanel.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        if (!containsLeftPanel(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        float step = (float) ((LIST_ROW_HEIGHT + ROW_GAP) * 2.5);
        if (mouseY >= packListTop && mouseY <= packListBottom) {
            packScroll = Mth.clamp(packScroll - (float) delta * step, 0.0f, maxPackScroll());
            return true;
        }
        if (mouseY >= motionListTop && mouseY <= motionListBottom) {
            motionScroll = Mth.clamp(motionScroll - (float) delta * step, 0.0f, maxMotionScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        persistPreferences();
        closeStageSelectionSession();
        if (this.minecraft != null) {
            super.onClose();
        }
    }

    @Override
    public void removed() {
        closeStageSelectionSession();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void updateLayout() {
        leftPanelX = PANEL_MARGIN;
        leftPanelY = PANEL_MARGIN;
        leftPanelHeight = Math.max(220, this.height - PANEL_MARGIN * 2);

        int footerHeight = facade.isSessionMember() ? GUEST_FOOTER_HEIGHT : HOST_FOOTER_HEIGHT;
        footerTop = leftPanelY + leftPanelHeight - footerHeight;
        packListTop = leftPanelY + HEADER_HEIGHT + SECTION_GAP;
        motionHeaderY = leftPanelY + Math.max(92, (leftPanelHeight - footerHeight - HEADER_HEIGHT) / 2 + HEADER_HEIGHT);
        packListBottom = motionHeaderY - SECTION_GAP;
        motionListTop = motionHeaderY + 16;
        motionListBottom = footerTop - SECTION_GAP;

        footerButtonY = footerTop + footerHeight - BUTTON_HEIGHT - 10;
        footerButtonWidth = (LEFT_PANEL_WIDTH - 28) / 2;
        footerPrimaryX = leftPanelX + 10;
        footerSecondaryX = leftPanelX + LEFT_PANEL_WIDTH - 10 - footerButtonWidth;
        cameraSliderLabelY = footerTop + 8;
        cameraSliderTrackY = footerTop + 22;
        cameraSliderX = leftPanelX + 10;
        cameraSliderWidth = LEFT_PANEL_WIDTH - 20;
        useHostCameraY = footerTop + 8;
        cinematicToggleY = facade.isSessionMember() ? footerTop + 28 : footerTop + 40;

        if (assignPanel != null) {
            assignPanel.layout(this.width, this.height, facade.isSessionMember());
        }
        packScroll = Mth.clamp(packScroll, 0.0f, maxPackScroll());
        motionScroll = Mth.clamp(motionScroll, 0.0f, maxMotionScroll());
    }

    private void syncAssignPanel() {
        if (assignPanel != null) {
            assignPanel.sync(collectMotionFiles());
        }
    }

    void syncGuestPackPreferences() {
        facade.syncGuestPackPreferences(state.selectedPack(), collectMotionFiles());
    }

    private void updateHoverState(int mouseX, int mouseY) {
        hoveredPackIndex = -1;
        hoveredMotionIndex = -1;
        hoveredRefresh = isRectHovered(mouseX, mouseY, leftPanelX + LEFT_PANEL_WIDTH - 70, leftPanelY + 8, 60, BUTTON_HEIGHT);
        hoveredCinematic = isRectHovered(mouseX, mouseY, leftPanelX + 10, cinematicToggleY, LEFT_PANEL_WIDTH - 20, 14);
        hoveredUseHostCamera = facade.isSessionMember() && isRectHovered(mouseX, mouseY, leftPanelX + 10, useHostCameraY, LEFT_PANEL_WIDTH - 20, 14);
        hoveredPrimary = isRectHovered(mouseX, mouseY, footerPrimaryX, footerButtonY, footerButtonWidth, BUTTON_HEIGHT);
        hoveredCancel = isRectHovered(mouseX, mouseY, footerSecondaryX, footerButtonY, footerButtonWidth, BUTTON_HEIGHT);
        hoveredMergeAll = false;

        hoveredPackIndex = resolveHoveredIndex(mouseX, mouseY, packListTop, packScroll, state.stagePacks().size());
        if (!facade.isSessionMember() && state.selectedPack() != null && isRectHovered(mouseX, mouseY, leftPanelX + 10, motionListTop, LEFT_PANEL_WIDTH - 20, LIST_ROW_HEIGHT)) {
            hoveredMergeAll = true;
        }
        int motionRowTop = motionListTop + (!facade.isSessionMember() && state.selectedPack() != null ? LIST_ROW_HEIGHT + ROW_GAP : 0);
        hoveredMotionIndex = resolveHoveredIndex(mouseX, mouseY, motionRowTop, motionScroll, collectMotionFiles().size());
    }

    private void drawHeader(GuiGraphics graphics) {
        int titleY = leftPanelY + 8;
        int statsY = titleY + this.font.lineHeight + 5;
        graphics.drawString(this.font, this.title.getString(), leftPanelX + 10, titleY, COLOR_ACCENT, false);
        String stats = wb("packs.count", state.stagePacks().size());
        graphics.drawString(this.font, stats, leftPanelX + 10, statsY, COLOR_TEXT_MUTED, false);
        drawButton(graphics, leftPanelX + LEFT_PANEL_WIDTH - 70, leftPanelY + 8, 60, BUTTON_HEIGHT,
                Component.translatable("gui.mmdskin.refresh").getString(), hoveredRefresh, false, false, true);
    }

    private void drawPackList(GuiGraphics graphics) {
        drawSectionLabel(graphics, wb("packs.section"), leftPanelX + 10, packListTop - 14);
        drawListBackground(graphics, leftPanelX + 8, packListTop, LEFT_PANEL_WIDTH - 16, packListBottom - packListTop);
        graphics.enableScissor(leftPanelX + 8, packListTop, leftPanelX + LEFT_PANEL_WIDTH - 8, packListBottom);
        int y = rowY(packListTop, 0, packScroll);
        for (int i = 0; i < state.stagePacks().size(); i++) {
            StagePack pack = state.stagePacks().get(i);
            if (y + LIST_ROW_HEIGHT < packListTop) {
                y += LIST_ROW_HEIGHT + ROW_GAP;
                continue;
            }
            if (y > packListBottom) {
                break;
            }
            boolean selected = i == state.selectedPackIndex();
            boolean hovered = i == hoveredPackIndex;
            drawRow(graphics, leftPanelX + 10, y, LEFT_PANEL_WIDTH - 20, selected, hovered);
            graphics.drawString(this.font, shorten(pack.getName(), 18), leftPanelX + 14, y + 5, COLOR_TEXT, false);
            String stats = shortPackStats(pack);
            graphics.drawString(this.font, stats, leftPanelX + LEFT_PANEL_WIDTH - 14 - this.font.width(stats), y + 5, COLOR_TEXT_MUTED, false);
            y += LIST_ROW_HEIGHT + ROW_GAP;
        }
        graphics.disableScissor();
        drawScrollbar(graphics, leftPanelX + LEFT_PANEL_WIDTH - 4, packListTop, packListBottom, packScroll, maxPackScroll());
    }

    private void drawMotionSection(GuiGraphics graphics) {
        drawSectionLabel(graphics, wb("playback.section.short"), leftPanelX + 10, motionHeaderY + 2);
        drawListBackground(graphics, leftPanelX + 8, motionListTop, LEFT_PANEL_WIDTH - 16, motionListBottom - motionListTop);

        if (state.selectedPack() == null) {
            graphics.drawString(this.font, wb("select_pack_hint"), leftPanelX + 12, motionListTop + 6, COLOR_TEXT_DIM, false);
            return;
        }

        List<StagePack.VmdFileInfo> motionFiles = collectMotionFiles();
        graphics.enableScissor(leftPanelX + 8, motionListTop, leftPanelX + LEFT_PANEL_WIDTH - 8, motionListBottom);
        int y = motionListTop;
        if (!facade.isSessionMember()) {
            drawRow(graphics, leftPanelX + 10, y, LEFT_PANEL_WIDTH - 20, state.selectedHostMotionFileName() == null, hoveredMergeAll);
            graphics.drawString(this.font, wb("playback.merge_all.short"), leftPanelX + 14, y + 5, COLOR_TEXT, false);
            y += LIST_ROW_HEIGHT + ROW_GAP;
        }
        y = rowY(y, 0, motionScroll);
        for (int i = 0; i < motionFiles.size(); i++) {
            StagePack.VmdFileInfo motionFile = motionFiles.get(i);
            if (y + LIST_ROW_HEIGHT < motionListTop) {
                y += LIST_ROW_HEIGHT + ROW_GAP;
                continue;
            }
            if (y > motionListBottom) {
                break;
            }
            boolean selected = !facade.isSessionMember() && motionFile.name.equals(state.selectedHostMotionFileName());
            drawRow(graphics, leftPanelX + 10, y, LEFT_PANEL_WIDTH - 20, selected, i == hoveredMotionIndex);
            graphics.drawString(this.font, shorten(stripExtension(motionFile.name), 15), leftPanelX + 14, y + 5, COLOR_TEXT, false);
            String tag = motionTag(motionFile);
            graphics.drawString(this.font, tag, leftPanelX + LEFT_PANEL_WIDTH - 14 - this.font.width(tag), y + 5, COLOR_TEXT_MUTED, false);
            y += LIST_ROW_HEIGHT + ROW_GAP;
        }
        graphics.disableScissor();
        drawScrollbar(graphics, leftPanelX + LEFT_PANEL_WIDTH - 4, motionListTop, motionListBottom, motionScroll, maxMotionScroll());
    }

    private void drawFooter(GuiGraphics graphics) {
        graphics.fill(leftPanelX + 10, footerTop - 4, leftPanelX + LEFT_PANEL_WIDTH - 10, footerTop - 3, COLOR_SEPARATOR);
        if (facade.isSessionMember()) {
            drawToggle(graphics, leftPanelX + 10, useHostCameraY, LEFT_PANEL_WIDTH - 20, 14, facade.isUseHostCamera(), hoveredUseHostCamera,
                    Component.translatable("gui.mmdskin.stage.use_host_camera").getString());
        } else {
            drawCameraSlider(graphics);
        }
        drawToggle(graphics, leftPanelX + 10, cinematicToggleY, LEFT_PANEL_WIDTH - 20, 14, state.cinematicMode(), hoveredCinematic,
                Component.translatable("gui.mmdskin.stage.cinematic").getString());

        boolean hostCanStart = facade.canStartStage(state.selectedPack());
        String primaryText = facade.isSessionMember()
                ? Component.translatable(facade.isLocalReady() ? "gui.mmdskin.stage.unready" : "gui.mmdskin.stage.ready").getString()
                : Component.translatable("gui.mmdskin.stage.start").getString();
        drawButton(graphics, footerPrimaryX, footerButtonY, footerButtonWidth, BUTTON_HEIGHT, primaryText, hoveredPrimary, !facade.isSessionMember(), !hostCanStart, facade.isSessionMember() || hostCanStart);
        drawButton(graphics, footerSecondaryX, footerButtonY, footerButtonWidth, BUTTON_HEIGHT, Component.translatable("gui.cancel").getString(), hoveredCancel, false, false, true);

        String footerText = facade.isSessionMember()
                ? Component.translatable(facade.isLocalReady() ? "gui.mmdskin.stage.ready_done" : "gui.mmdskin.stage.waiting_host").getString()
                : (hostCanStart ? wb("ready_to_launch") : Component.translatable("gui.mmdskin.stage.waiting_ready").getString());
        graphics.drawString(this.font, shorten(footerText, 28), leftPanelX + 10, footerButtonY - 14, COLOR_TEXT_DIM, false);
    }

    private void drawCameraSlider(GuiGraphics graphics) {
        String label = wb("camera_height.short") + ": " + String.format(Locale.ROOT, "%+.2f", state.cameraHeightOffset());
        graphics.drawString(this.font, label, leftPanelX + 10, cameraSliderLabelY, COLOR_TEXT_DIM, false);
        graphics.fill(cameraSliderX, cameraSliderTrackY, cameraSliderX + cameraSliderWidth, cameraSliderTrackY + 4, 0x26000000);
        int thumbX = cameraSliderX + Math.round(((state.cameraHeightOffset() + 2.0f) / 4.0f) * (cameraSliderWidth - 6));
        graphics.fill(thumbX, cameraSliderTrackY - 2, thumbX + 6, cameraSliderTrackY + 6, COLOR_ACCENT);
    }

    private void drawToggle(GuiGraphics graphics, int x, int y, int width, int height, boolean checked, boolean hovered, String label) {
        if (hovered) {
            graphics.fill(x, y, x + width, y + height, 0x12000000);
        }
        int labelY = y + Math.max(0, (height - this.font.lineHeight) / 2);
        graphics.drawString(this.font, label, x, labelY, COLOR_TEXT, false);

        int trackWidth = 28;
        int trackHeight = Math.max(10, height - 2);
        int trackX = x + width - trackWidth;
        int trackY = y + Math.max(0, (height - trackHeight) / 2);
        int trackColor = checked ? COLOR_TOGGLE_ON : COLOR_TOGGLE_OFF;
        graphics.fill(trackX, trackY, trackX + trackWidth, trackY + trackHeight, hovered ? brighten(trackColor) : trackColor);
        int knobSize = Math.max(8, trackHeight - 4);
        int knobX = checked ? trackX + trackWidth - knobSize - 2 : trackX + 2;
        int knobY = trackY + (trackHeight - knobSize) / 2;
        graphics.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, 0xFFF5FAFF);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int width, int height, String text, boolean hovered, boolean primary, boolean disabled, boolean enabled) {
        int color;
        if (!enabled || disabled) {
            color = COLOR_BUTTON_DISABLED;
        } else if (text.equals(Component.translatable("gui.cancel").getString())) {
            color = hovered ? COLOR_BUTTON_CANCEL_HOVER : COLOR_BUTTON_CANCEL;
        } else if (primary) {
            color = hovered ? COLOR_BUTTON_GO_HOVER : COLOR_BUTTON_GO;
        } else {
            color = hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON;
        }
        graphics.fill(x, y, x + width, y + height, color);
        graphics.drawCenteredString(this.font, text, x + width / 2, y + 5, enabled && !disabled ? 0xFFFFFFFF : COLOR_TEXT_MUTED);
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, COLOR_BG);
        graphics.fill(x, y, x + width, y + 1, COLOR_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + height, COLOR_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, COLOR_BORDER);
    }

    private void drawListBackground(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, COLOR_ROW);
    }

    private void drawSectionLabel(GuiGraphics graphics, String text, int x, int y) {
        graphics.drawString(this.font, text, x, y, COLOR_TEXT_DIM, false);
    }

    private void drawRow(GuiGraphics graphics, int x, int y, int width, boolean selected, boolean hovered) {
        int bg = selected ? COLOR_ROW_SELECTED : hovered ? COLOR_ROW_HOVER : COLOR_ROW;
        graphics.fill(x, y, x + width, y + LIST_ROW_HEIGHT, bg);
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int top, int bottom, float offset, float maxScroll) {
        if (maxScroll <= 0.0f) {
            return;
        }
        graphics.fill(x, top, x + 2, bottom, COLOR_SCROLL_TRACK);
        int barHeight = bottom - top;
        int thumbHeight = Math.max(12, Math.round(barHeight * ((float) barHeight / (barHeight + maxScroll))));
        int travel = Math.max(1, barHeight - thumbHeight);
        int thumbY = top + Math.round((offset / maxScroll) * travel);
        graphics.fill(x, thumbY, x + 2, thumbY + thumbHeight, COLOR_SCROLL_THUMB);
    }

    private void reloadStagePacks() {
        String preferred = state.selectedPack() != null ? state.selectedPack().getName() : facade.loadPreferences().lastStagePack();
        state.replaceStagePacks(facade.loadStagePacks(), preferred);
        packScroll = 0.0f;
        motionScroll = 0.0f;
        syncGuestPackPreferences();
    }

    private void updateCameraHeight(double mouseX) {
        float t = (float) ((mouseX - cameraSliderX) / Math.max(1.0f, cameraSliderWidth));
        state.setCameraHeightOffset(Mth.clamp(-2.0f + 4.0f * Mth.clamp(t, 0.0f, 1.0f), -2.0f, 2.0f));
    }

    private void startStage() {
        if (!facade.canStartStage(state.selectedPack())) {
            return;
        }
        if (!facade.startStage(state.selectedPack(), state.cinematicMode(), state.cameraHeightOffset(), state.selectedHostMotionFileName())) {
            return;
        }
        stageStarted = true;
        Minecraft.getInstance().setScreen(null);
    }

    private void persistPreferences() {
        StagePack selectedPack = state.selectedPack();
        facade.savePreferences(selectedPack != null ? selectedPack.getName() : "", state.cinematicMode(), state.cameraHeightOffset());
    }

    private void closeStageSelectionSession() {
        if (stageSelectionClosed) {
            return;
        }
        stageSelectionClosed = true;
        facade.onStageSelectionClosed(stageStarted);
    }

    private float maxPackScroll() {
        return Math.max(0.0f, state.stagePacks().size() * (LIST_ROW_HEIGHT + ROW_GAP) - (packListBottom - packListTop));
    }

    private float maxMotionScroll() {
        int extra = !facade.isSessionMember() && state.selectedPack() != null ? 1 : 0;
        return Math.max(0.0f, (collectMotionFiles().size() + extra) * (LIST_ROW_HEIGHT + ROW_GAP) - (motionListBottom - motionListTop));
    }

    private int rowY(int top, int index, float scroll) {
        return top + index * (LIST_ROW_HEIGHT + ROW_GAP) - Math.round(scroll);
    }

    private int resolveHoveredIndex(int mouseX, int mouseY, int top, float scroll, int size) {
        if (mouseX < leftPanelX + 10 || mouseX > leftPanelX + LEFT_PANEL_WIDTH - 10 || mouseY < top || size <= 0) {
            return -1;
        }
        float localY = (float) mouseY - top + scroll;
        if (localY < 0.0f) {
            return -1;
        }
        int index = (int) (localY / (LIST_ROW_HEIGHT + ROW_GAP));
        if (index < 0 || index >= size) {
            return -1;
        }
        return localY - index * (LIST_ROW_HEIGHT + ROW_GAP) <= LIST_ROW_HEIGHT ? index : -1;
    }

    private boolean isRectHovered(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private boolean isCameraSliderHovered(double mouseX, double mouseY) {
        return mouseX >= cameraSliderX
                && mouseX <= cameraSliderX + cameraSliderWidth
                && mouseY >= cameraSliderTrackY - 4
                && mouseY <= cameraSliderTrackY + 8;
    }

    private boolean containsLeftPanel(double mouseX, double mouseY) {
        return mouseX >= leftPanelX && mouseX <= leftPanelX + LEFT_PANEL_WIDTH
                && mouseY >= leftPanelY && mouseY <= leftPanelY + leftPanelHeight;
    }

    private List<StagePack.VmdFileInfo> collectMotionFiles() {
        StagePack selectedPack = state.selectedPack();
        if (selectedPack == null) {
            return List.of();
        }
        return selectedPack.getVmdFiles().stream()
                .filter(info -> info.hasBones || info.hasMorphs)
                .toList();
    }

    StageSelectState debugState() {
        return state;
    }

    private static String shortPackStats(StagePack pack) {
        return StageScreenUtils.shortPackStats(pack);
    }

    private static int brighten(int color) {
        return StageScreenUtils.brighten(color);
    }

    private static String motionTag(StagePack.VmdFileInfo info) {
        return StageScreenUtils.motionTag(info);
    }

    private static String stripExtension(String text) {
        return StageScreenUtils.stripExtension(text);
    }

    private static String shorten(String text, int maxChars) {
        return StageScreenUtils.shorten(text, maxChars);
    }

    private static String wb(String suffix, Object... args) {
        return StageScreenUtils.wb(suffix, args);
    }
}
