package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StageConfig;
import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.stage.client.StagePlaybackCoordinator;
import com.shiroha.mmdskin.stage.client.asset.LocalStagePackRepository;
import com.shiroha.mmdskin.stage.client.playback.StageHostPlaybackService;
import com.shiroha.mmdskin.stage.client.viewmodel.StageLobbyViewModel;
import com.shiroha.mmdskin.stage.domain.model.StageMemberState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class StageWorkbenchScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();

    static final int WINDOW_MARGIN = 10;
    static final int LEFT_MIN_WIDTH = 176;
    static final int LEFT_MAX_WIDTH = 224;
    static final int RIGHT_MIN_WIDTH = 148;
    static final int RIGHT_MAX_WIDTH = 188;
    static final int RIGHT_MIN_HEIGHT = 126;
    static final int RIGHT_MAX_HEIGHT = 210;
    static final int HEADER_HEIGHT = 28;
    static final int LIST_PADDING = 4;
    static final int ROW_GAP = 3;
    static final int PACK_ROW_HEIGHT = 15;
    static final int MOTION_ROW_HEIGHT = 16;
    static final int SESSION_ROW_HEIGHT = 21;
    static final int BUTTON_HEIGHT = 16;
    static final int BUTTON_GAP = 5;

    private final StageLobbyViewModel lobbyViewModel = StageLobbyViewModel.getInstance();
    private final LocalStagePackRepository stagePackRepository = LocalStagePackRepository.getInstance();
    private final StageHostPlaybackService hostPlaybackService = StageHostPlaybackService.getInstance();
    private final StagePlaybackCoordinator playbackCoordinator = StagePlaybackCoordinator.getInstance();
    private final SkiaStageWorkbenchRenderer skiaRenderer = new SkiaStageWorkbenchRenderer();

    private List<StagePack> stagePacks = new ArrayList<>();
    private int selectedPackIndex = -1;
    private String selectedHostMotionFileName;
    private boolean cinematicMode;
    private float cameraHeightOffset;
    private boolean stageStarted;
    private boolean stageSelectionOpened;
    private boolean stageSelectionClosed;
    private boolean pendingScreenClose;

    private float packTargetScroll;
    private float packAnimatedScroll;
    private float motionTargetScroll;
    private float motionAnimatedScroll;
    private float sessionTargetScroll;
    private float sessionAnimatedScroll;

    private int hoveredPack = -1;
    private int hoveredMotion = -1;
    private int hoveredSession = -1;
    private HoverTarget hoveredTarget = HoverTarget.NONE;
    private ActiveSlider activeSlider = ActiveSlider.NONE;
    private Layout layout = Layout.empty();

    private enum HoverTarget {
        NONE,
        REFRESH,
        CUSTOM_MOTION,
        USE_HOST_CAMERA,
        CINEMATIC,
        CAMERA_SLIDER,
        PRIMARY_ACTION,
        SECONDARY_ACTION,
        SESSION_ACTION
    }

    private enum ActiveSlider {
        NONE,
        CAMERA_HEIGHT
    }

    public StageWorkbenchScreen() {
        super(Component.translatable("gui.mmdskin.stage.workbench.title"));
        StageConfig config = StageConfig.getInstance();
        this.cinematicMode = config.cinematicMode;
        this.cameraHeightOffset = config.cameraHeightOffset;
        this.stagePacks = stagePackRepository.loadStagePacks();
        restoreSelection(config);
        if (selectedPackIndex < 0 && !stagePacks.isEmpty()) {
            selectedPackIndex = 0;
        }
    }

    public static Screen createPrimary() {
        return new StageWorkbenchScreen();
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
            playbackCoordinator.onStageSelectionOpened();
            stageSelectionOpened = true;
        }
        syncGuestPackPreferences();
        updateLayout();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            StagePack selectedPack = getSelectedPack();
            normalizeSelectedHostMotion(selectedPack);

            updateLayout();
            updateHoverState(mouseX, mouseY, selectedPack);
            updateScrollAnimation(selectedPack);

            SkiaStageWorkbenchRenderer.WorkbenchView view = buildView(selectedPack);
            if (!skiaRenderer.renderWorkbench(this, view)) {
                renderFallback(guiGraphics, view);
            }
            flushPendingClose(minecraft);
        } catch (Throwable throwable) {
            closeAfterFailure(throwable);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (!layout.leftPanel.contains(mouseX, mouseY) && !layout.rightPanel.contains(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        StagePack selectedPack = getSelectedPack();
        if (layout.refreshButton.contains(mouseX, mouseY)) {
            reloadStagePacks();
            return true;
        }
        if (layout.customMotionToggle.contains(mouseX, mouseY) && lobbyViewModel.isSessionMember()) {
            lobbyViewModel.setLocalCustomMotionEnabled(!lobbyViewModel.isLocalCustomMotionEnabled());
            return true;
        }
        if (layout.useHostCameraToggle.contains(mouseX, mouseY) && lobbyViewModel.isSessionMember()) {
            lobbyViewModel.setUseHostCamera(!lobbyViewModel.isUseHostCamera());
            return true;
        }
        if (layout.cinematicToggle.contains(mouseX, mouseY)) {
            cinematicMode = !cinematicMode;
            return true;
        }
        if (layout.cameraSlider.contains(mouseX, mouseY) && !lobbyViewModel.isSessionMember()) {
            activeSlider = ActiveSlider.CAMERA_HEIGHT;
            updateCameraHeight(mouseX);
            return true;
        }
        if (layout.primaryButton.contains(mouseX, mouseY)) {
            handlePrimaryAction(selectedPack);
            return true;
        }
        if (layout.secondaryButton.contains(mouseX, mouseY)) {
            requestCloseAfterFrame();
            return true;
        }
        if (layout.sessionButton.contains(mouseX, mouseY) && !lobbyViewModel.isSessionMember()) {
            inviteAllNearby(lobbyViewModel.getHostPanelEntries());
            return true;
        }
        if (layout.packList.contains(mouseX, mouseY)) {
            List<PackRowView> rows = buildPackRows();
            if (hoveredPack >= 0 && hoveredPack < rows.size()) {
                selectPack(rows.get(hoveredPack).index());
                return true;
            }
        }
        if (layout.motionList.contains(mouseX, mouseY)) {
            List<MotionRowView> rows = buildMotionRows(selectedPack);
            if (hoveredMotion >= 0 && hoveredMotion < rows.size()) {
                handleMotionSelection(rows.get(hoveredMotion));
                return true;
            }
        }
        if (layout.sessionList.contains(mouseX, mouseY) && !lobbyViewModel.isSessionMember()) {
            List<SessionRowView> rows = buildSessionRows();
            if (hoveredSession >= 0 && hoveredSession < rows.size()) {
                SessionRowView row = rows.get(hoveredSession);
                if (row.actionable() && row.targetId() != null) {
                    handleHostAction(row.targetId());
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (activeSlider == ActiveSlider.CAMERA_HEIGHT) {
                persistUiState();
            }
            activeSlider = ActiveSlider.NONE;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && activeSlider == ActiveSlider.CAMERA_HEIGHT) {
            updateCameraHeight(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        float step = 14.0f;
        if (layout.packList.contains(mouseX, mouseY)) {
            packTargetScroll = Mth.clamp(packTargetScroll - (float) delta * step, 0.0f, maxPackScroll());
            return true;
        }
        if (layout.motionList.contains(mouseX, mouseY)) {
            motionTargetScroll = Mth.clamp(motionTargetScroll - (float) delta * step, 0.0f, maxMotionScroll(getSelectedPack()));
            return true;
        }
        if (layout.sessionList.contains(mouseX, mouseY)) {
            sessionTargetScroll = Mth.clamp(sessionTargetScroll - (float) delta * step, 0.0f, maxSessionScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        persistUiState();
        skiaRenderer.dispose();
        closeStageSelectionSession();
        super.onClose();
    }

    @Override
    public void removed() {
        skiaRenderer.dispose();
        closeStageSelectionSession();
        super.removed();
    }

    private void updateLayout() {
        int leftWidth = clamp(Math.round(this.width * 0.18f), LEFT_MIN_WIDTH, LEFT_MAX_WIDTH);
        int leftHeight = Math.max(272, this.height - WINDOW_MARGIN * 2);
        int leftX = WINDOW_MARGIN;
        int leftY = WINDOW_MARGIN;

        int rightWidth = clamp(Math.round(this.width * 0.11f), RIGHT_MIN_WIDTH, RIGHT_MAX_WIDTH);
        int rightHeight = clamp(Math.round(this.height * 0.22f), RIGHT_MIN_HEIGHT, RIGHT_MAX_HEIGHT);
        int rightX = this.width - rightWidth - WINDOW_MARGIN;
        int rightY = WINDOW_MARGIN;

        UiRect leftPanel = new UiRect(leftX, leftY, leftWidth, leftHeight);
        UiRect rightPanel = new UiRect(rightX, rightY, rightWidth, rightHeight);

        int contentX = leftPanel.x + 10;
        int contentW = leftPanel.w - 20;
        UiRect leftHeader = new UiRect(contentX, leftPanel.y + 10, contentW, HEADER_HEIGHT);

        UiRect refreshButton = new UiRect(contentX + contentW - 42, leftHeader.y + 2, 42, BUTTON_HEIGHT);
        int packListY = leftHeader.y + leftHeader.h + 10;
        int listSectionGap = 8;
        int listToDetailsGap = 4;
        int buttonY = leftPanel.y + leftPanel.h - 8 - BUTTON_HEIGHT;
        int buttonWidth = (contentW - BUTTON_GAP) / 2;
        int toggleRowHeight = 14;
        UiRect primaryButton = new UiRect(contentX, buttonY, buttonWidth, BUTTON_HEIGHT);
        UiRect secondaryButton = new UiRect(contentX + buttonWidth + BUTTON_GAP, buttonY, contentW - buttonWidth - BUTTON_GAP, BUTTON_HEIGHT);
        UiRect cinematicToggle;
        UiRect useHostCameraToggle;
        UiRect customMotionToggle;
        UiRect cameraSlider;
        UiRect detailsArea;
        UiRect packList;
        UiRect motionList;

        if (lobbyViewModel.isSessionMember()) {
            int bottomCursor = primaryButton.y - 8;
            cinematicToggle = new UiRect(contentX, bottomCursor - toggleRowHeight, contentW, toggleRowHeight);
            bottomCursor = cinematicToggle.y - 4;
            useHostCameraToggle = new UiRect(contentX, bottomCursor - toggleRowHeight, contentW, toggleRowHeight);
            bottomCursor = useHostCameraToggle.y - 4;
            customMotionToggle = new UiRect(contentX, bottomCursor - toggleRowHeight, contentW, toggleRowHeight);
            bottomCursor = customMotionToggle.y - 4;

            int detailsHeight = 30;
            int detailsY = bottomCursor - detailsHeight;
            int listsAvailableHeight = Math.max(80, detailsY - listToDetailsGap - packListY - listSectionGap);
            int motionListH = Math.max(36, Math.min(Math.max(52, Math.round(listsAvailableHeight * 0.24f)), listsAvailableHeight - 44));
            int packListH = Math.max(44, listsAvailableHeight - motionListH);
            int motionListY = packListY + packListH + listSectionGap;

            packList = new UiRect(contentX, packListY, contentW, packListH);
            motionList = new UiRect(contentX, motionListY, contentW, motionListH);
            detailsArea = new UiRect(contentX, detailsY, contentW, detailsHeight);
            cameraSlider = UiRect.empty();
            UiRect rightHeader = new UiRect(rightPanel.x + 10, rightPanel.y + 10, rightPanel.w - 20, 24);
            int sessionListY = rightHeader.y + rightHeader.h + 8;
            layout = new Layout(
                    leftPanel,
                    rightPanel,
                    leftHeader,
                    packList,
                    refreshButton,
                    motionList,
                    detailsArea,
                    customMotionToggle,
                    useHostCameraToggle,
                    cinematicToggle,
                    cameraSlider,
                    primaryButton,
                    secondaryButton,
                    rightHeader,
                    UiRect.empty(),
                    new UiRect(rightPanel.x + 10, sessionListY, rightPanel.w - 20, rightPanel.y + rightPanel.h - 10 - sessionListY)
            );
        } else {
            int bottomCursor = primaryButton.y - 8;
            cinematicToggle = new UiRect(contentX, bottomCursor - toggleRowHeight, contentW, toggleRowHeight);
            bottomCursor = cinematicToggle.y - 4;
            cameraSlider = new UiRect(contentX, bottomCursor - 10, contentW, 10);
            bottomCursor = cameraSlider.y - 10;

            int detailsHeight = 30;
            int detailsY = bottomCursor - detailsHeight;
            int listsAvailableHeight = Math.max(80, detailsY - listToDetailsGap - packListY - listSectionGap);
            int motionListH = Math.max(36, Math.min(Math.max(52, Math.round(listsAvailableHeight * 0.24f)), listsAvailableHeight - 44));
            int packListH = Math.max(44, listsAvailableHeight - motionListH);
            int motionListY = packListY + packListH + listSectionGap;

            packList = new UiRect(contentX, packListY, contentW, packListH);
            motionList = new UiRect(contentX, motionListY, contentW, motionListH);
            detailsArea = new UiRect(contentX, detailsY, contentW, detailsHeight);
            customMotionToggle = UiRect.empty();
            useHostCameraToggle = UiRect.empty();

            UiRect rightHeader = new UiRect(rightPanel.x + 10, rightPanel.y + 10, rightPanel.w - 20, 24);
            int sessionButtonY = rightHeader.y + rightHeader.h + 8;
            UiRect sessionButton = new UiRect(rightPanel.x + 10, sessionButtonY, rightPanel.w - 20, BUTTON_HEIGHT);
            int sessionListY = sessionButtonY + BUTTON_HEIGHT + 8;
            layout = new Layout(
                    leftPanel,
                    rightPanel,
                    leftHeader,
                    packList,
                    refreshButton,
                    motionList,
                    detailsArea,
                    customMotionToggle,
                    useHostCameraToggle,
                    cinematicToggle,
                    cameraSlider,
                    primaryButton,
                    secondaryButton,
                    rightHeader,
                    sessionButton,
                    new UiRect(rightPanel.x + 10, sessionListY, rightPanel.w - 20, rightPanel.y + rightPanel.h - 10 - sessionListY)
            );
        }

        packTargetScroll = Mth.clamp(packTargetScroll, 0.0f, maxPackScroll());
        packAnimatedScroll = Mth.clamp(packAnimatedScroll, 0.0f, maxPackScroll());
        motionTargetScroll = Mth.clamp(motionTargetScroll, 0.0f, maxMotionScroll(getSelectedPack()));
        motionAnimatedScroll = Mth.clamp(motionAnimatedScroll, 0.0f, maxMotionScroll(getSelectedPack()));
        sessionTargetScroll = Mth.clamp(sessionTargetScroll, 0.0f, maxSessionScroll());
        sessionAnimatedScroll = Mth.clamp(sessionAnimatedScroll, 0.0f, maxSessionScroll());
    }

    private void updateHoverState(int mouseX, int mouseY, StagePack selectedPack) {
        hoveredTarget = HoverTarget.NONE;
        hoveredPack = -1;
        hoveredMotion = -1;
        hoveredSession = -1;

        if (layout.refreshButton.contains(mouseX, mouseY)) {
            hoveredTarget = HoverTarget.REFRESH;
            return;
        }
        if (layout.customMotionToggle.contains(mouseX, mouseY) && lobbyViewModel.isSessionMember()) {
            hoveredTarget = HoverTarget.CUSTOM_MOTION;
            return;
        }
        if (layout.useHostCameraToggle.contains(mouseX, mouseY) && lobbyViewModel.isSessionMember()) {
            hoveredTarget = HoverTarget.USE_HOST_CAMERA;
            return;
        }
        if (layout.cinematicToggle.contains(mouseX, mouseY)) {
            hoveredTarget = HoverTarget.CINEMATIC;
            return;
        }
        if (layout.cameraSlider.contains(mouseX, mouseY) && !lobbyViewModel.isSessionMember()) {
            hoveredTarget = HoverTarget.CAMERA_SLIDER;
            return;
        }
        if (layout.primaryButton.contains(mouseX, mouseY)) {
            hoveredTarget = HoverTarget.PRIMARY_ACTION;
            return;
        }
        if (layout.secondaryButton.contains(mouseX, mouseY)) {
            hoveredTarget = HoverTarget.SECONDARY_ACTION;
            return;
        }
        if (layout.sessionButton.contains(mouseX, mouseY) && !lobbyViewModel.isSessionMember()) {
            hoveredTarget = HoverTarget.SESSION_ACTION;
            return;
        }

        hoveredPack = findHoveredIndex(layout.packList, mouseY, packAnimatedScroll, buildPackRows().size(), PACK_ROW_HEIGHT);
        if (hoveredPack >= 0) {
            return;
        }
        hoveredMotion = findHoveredIndex(layout.motionList, mouseY, motionAnimatedScroll, buildMotionRows(selectedPack).size(), MOTION_ROW_HEIGHT);
        if (hoveredMotion >= 0) {
            return;
        }
        hoveredSession = findHoveredIndex(layout.sessionList, mouseY, sessionAnimatedScroll, buildSessionRows().size(), SESSION_ROW_HEIGHT);
    }

    private int findHoveredIndex(UiRect rect, int mouseY, float scrollOffset, int count, int rowHeight) {
        if (count <= 0 || mouseY < rect.y || mouseY > rect.y + rect.h) {
            return -1;
        }
        float localY = mouseY - rect.y - LIST_PADDING + scrollOffset;
        if (localY < 0.0f) {
            return -1;
        }
        float stride = rowHeight + ROW_GAP;
        int index = (int) (localY / stride);
        if (index < 0 || index >= count) {
            return -1;
        }
        float offsetInItem = localY - index * stride;
        return offsetInItem <= rowHeight ? index : -1;
    }

    private void updateScrollAnimation(StagePack selectedPack) {
        packTargetScroll = Mth.clamp(packTargetScroll, 0.0f, maxPackScroll());
        motionTargetScroll = Mth.clamp(motionTargetScroll, 0.0f, maxMotionScroll(selectedPack));
        sessionTargetScroll = Mth.clamp(sessionTargetScroll, 0.0f, maxSessionScroll());
        packAnimatedScroll = animateScroll(packAnimatedScroll, packTargetScroll);
        motionAnimatedScroll = animateScroll(motionAnimatedScroll, motionTargetScroll);
        sessionAnimatedScroll = animateScroll(sessionAnimatedScroll, sessionTargetScroll);
    }

    private float animateScroll(float current, float target) {
        float next = Mth.lerp(0.24f, current, target);
        return Math.abs(next - target) < 0.25f ? target : next;
    }

    private float maxPackScroll() {
        return maxScroll(buildPackRows().size(), PACK_ROW_HEIGHT, layout.packList.h);
    }

    private float maxMotionScroll(StagePack selectedPack) {
        return maxScroll(buildMotionRows(selectedPack).size(), MOTION_ROW_HEIGHT, layout.motionList.h);
    }

    private float maxSessionScroll() {
        return maxScroll(buildSessionRows().size(), SESSION_ROW_HEIGHT, layout.sessionList.h);
    }

    private float maxScroll(int count, int rowHeight, int boxHeight) {
        float contentHeight = count <= 0 ? 0.0f : LIST_PADDING * 2.0f + count * rowHeight + Math.max(0, count - 1) * ROW_GAP;
        return Math.max(0.0f, contentHeight - boxHeight);
    }

    private SkiaStageWorkbenchRenderer.WorkbenchView buildView(StagePack selectedPack) {
        List<PackRowView> packRows = buildPackRows();
        List<MotionRowView> motionRows = buildMotionRows(selectedPack);
        List<SessionRowView> sessionRows = buildSessionRows();

        String leftSubtitle = wb(lobbyViewModel.isSessionMember() ? "flow.guest" : "flow.host")
                + " · " + wb("packs.count", stagePacks.size());
        String roomStats = lobbyViewModel.isSessionMember()
                ? wb("guest.members.short", sessionRows.size())
                : wb("host.stats.short", sessionRows.size(), readyMemberCount());

        return new SkiaStageWorkbenchRenderer.WorkbenchView(
                this.title.getString(),
                leftSubtitle,
                wb("packs.section"),
                wb("playback.section.short"),
                wb("session.section"),
                roomStats,
                Component.translatable("gui.mmdskin.refresh").getString(),
                !lobbyViewModel.isSessionMember() ? wb("host.invite_all.short") : "",
                lobbyViewModel.isSessionMember()
                        ? (lobbyViewModel.isLocalReady()
                        ? Component.translatable("gui.mmdskin.stage.unready").getString()
                        : Component.translatable("gui.mmdskin.stage.ready").getString())
                        : Component.translatable("gui.mmdskin.stage.start").getString(),
                Component.translatable("gui.cancel").getString(),
                buildFooterStatus(selectedPack),
                buildDetailLines(selectedPack),
                layout.leftPanel,
                layout.rightPanel,
                layout.leftHeader,
                layout.packList,
                layout.refreshButton,
                layout.motionList,
                layout.detailsArea,
                layout.customMotionToggle,
                layout.useHostCameraToggle,
                layout.cinematicToggle,
                layout.cameraSlider,
                layout.primaryButton,
                layout.secondaryButton,
                layout.rightHeader,
                layout.sessionButton,
                layout.sessionList,
                hoveredTarget == HoverTarget.REFRESH,
                hoveredTarget == HoverTarget.CUSTOM_MOTION,
                hoveredTarget == HoverTarget.USE_HOST_CAMERA,
                hoveredTarget == HoverTarget.CINEMATIC,
                hoveredTarget == HoverTarget.CAMERA_SLIDER,
                hoveredTarget == HoverTarget.PRIMARY_ACTION,
                hoveredTarget == HoverTarget.SECONDARY_ACTION,
                hoveredTarget == HoverTarget.SESSION_ACTION,
                lobbyViewModel.isSessionMember(),
                !lobbyViewModel.isSessionMember(),
                lobbyViewModel.isSessionMember() && selectedPack != null,
                lobbyViewModel.isSessionMember(),
                !lobbyViewModel.isSessionMember(),
                cinematicMode,
                lobbyViewModel.isUseHostCamera(),
                lobbyViewModel.isLocalCustomMotionEnabled(),
                normalizeCameraHeight(cameraHeightOffset),
                packAnimatedScroll,
                motionAnimatedScroll,
                sessionAnimatedScroll,
                packRows,
                motionRows,
                sessionRows,
                LIST_PADDING,
                PACK_ROW_HEIGHT,
                MOTION_ROW_HEIGHT,
                SESSION_ROW_HEIGHT,
                ROW_GAP
        );
    }

    private void renderFallback(GuiGraphics guiGraphics, SkiaStageWorkbenchRenderer.WorkbenchView view) {
        drawFallbackPanel(guiGraphics, view.leftPanel());
        drawFallbackPanel(guiGraphics, view.rightPanel());
        guiGraphics.drawString(this.font, view.leftTitle(), view.leftHeader().x(), view.leftHeader().y(), 0xFFE9F1FA, false);
        guiGraphics.drawString(this.font, view.leftSubtitle(), view.leftHeader().x(), view.leftHeader().y() + 10, 0xBFD1DEEC, false);
        guiGraphics.drawString(this.font, view.rightTitle(), view.rightHeader().x(), view.rightHeader().y(), 0xFFE9F1FA, false);
        guiGraphics.drawString(this.font, view.rightSubtitle(), view.rightHeader().x(), view.rightHeader().y() + 10, 0xBFD1DEEC, false);
    }

    private void drawFallbackPanel(GuiGraphics guiGraphics, UiRect rect) {
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, 0x12000000);
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + 1, 0x20FFFFFF);
        guiGraphics.fill(rect.x, rect.y + rect.h - 1, rect.x + rect.w, rect.y + rect.h, 0x20FFFFFF);
        guiGraphics.fill(rect.x, rect.y, rect.x + 1, rect.y + rect.h, 0x20FFFFFF);
        guiGraphics.fill(rect.x + rect.w - 1, rect.y, rect.x + rect.w, rect.y + rect.h, 0x20FFFFFF);
    }

    private void reloadStagePacks() {
        StageConfig config = StageConfig.getInstance();
        stagePacks = stagePackRepository.loadStagePacks();
        selectedPackIndex = -1;
        restoreSelection(config);
        if (selectedPackIndex < 0 && !stagePacks.isEmpty()) {
            selectedPackIndex = 0;
        }
        normalizeSelectedHostMotion(getSelectedPack());
        syncGuestPackPreferences();
        packTargetScroll = 0.0f;
        packAnimatedScroll = 0.0f;
        motionTargetScroll = 0.0f;
        motionAnimatedScroll = 0.0f;
    }

    private void restoreSelection(StageConfig config) {
        if (config.lastStagePack == null || config.lastStagePack.isEmpty()) {
            return;
        }
        for (int i = 0; i < stagePacks.size(); i++) {
            if (config.lastStagePack.equals(stagePacks.get(i).getName())) {
                selectedPackIndex = i;
                return;
            }
        }
    }

    private void selectPack(int packIndex) {
        if (packIndex < 0 || packIndex >= stagePacks.size()) {
            return;
        }
        selectedPackIndex = packIndex;
        normalizeSelectedHostMotion(getSelectedPack());
        syncGuestPackPreferences();
        motionTargetScroll = 0.0f;
        motionAnimatedScroll = 0.0f;
    }

    private void syncGuestPackPreferences() {
        if (!lobbyViewModel.isSessionMember()) {
            return;
        }
        StagePack selectedPack = getSelectedPack();
        lobbyViewModel.setLocalCustomMotionPack(selectedPack != null ? selectedPack.getName() : null);
        lobbyViewModel.retainLocalCustomMotionFiles(getMotionFiles(selectedPack).stream().map(info -> info.name).toList());
    }

    private StagePack getSelectedPack() {
        if (selectedPackIndex >= 0 && selectedPackIndex < stagePacks.size()) {
            return stagePacks.get(selectedPackIndex);
        }
        return null;
    }

    private void normalizeSelectedHostMotion(StagePack pack) {
        if (selectedHostMotionFileName == null || pack == null) {
            return;
        }
        boolean exists = getMotionFiles(pack).stream().anyMatch(info -> info.name.equals(selectedHostMotionFileName));
        if (!exists) {
            selectedHostMotionFileName = null;
        }
    }

    private List<PackRowView> buildPackRows() {
        List<PackRowView> rows = new ArrayList<>(stagePacks.size());
        for (int i = 0; i < stagePacks.size(); i++) {
            StagePack pack = stagePacks.get(i);
            rows.add(new PackRowView(shorten(pack.getName(), 15), i == selectedPackIndex, i == hoveredPack, i));
        }
        return rows;
    }

    private List<MotionRowView> buildMotionRows(StagePack pack) {
        List<MotionRowView> rows = new ArrayList<>();
        if (pack == null) {
            return rows;
        }
        if (!lobbyViewModel.isSessionMember()) {
            rows.add(new MotionRowView(
                    wb("playback.merge_all.short"),
                    "",
                    selectedHostMotionFileName == null,
                    hoveredMotion == 0,
                    0,
                    null,
                    true
            ));
        }
        for (StagePack.VmdFileInfo motionFile : getMotionFiles(pack)) {
            int index = rows.size();
            rows.add(new MotionRowView(
                    buildCompactMotionLabel(motionFile),
                    "",
                    motionFile.name.equals(selectedHostMotionFileName)
                            || (lobbyViewModel.isSessionMember() && lobbyViewModel.isLocalCustomMotionSelected(motionFile.name)),
                    hoveredMotion == index,
                    index,
                    motionFile.name,
                    false
            ));
        }
        return rows;
    }

    private List<SessionRowView> buildSessionRows() {
        List<SessionRowView> rows = new ArrayList<>();
        if (lobbyViewModel.isSessionMember()) {
            List<StageLobbyViewModel.MemberView> members = lobbyViewModel.getSessionMembersView();
            for (int i = 0; i < members.size(); i++) {
                StageLobbyViewModel.MemberView member = members.get(i);
                String prefix = member.host() ? wb("member_prefix.host") : member.local() ? wb("member_prefix.you") : wb("member_prefix.guest");
                rows.add(new SessionRowView(
                        shorten(prefix + member.name(), 14),
                        formatGuestState(member.state(), member.useHostCamera()),
                        "",
                        member.local() || member.host(),
                        hoveredSession == i,
                        false,
                        i,
                        member.uuid()
                ));
            }
            return rows;
        }
        List<StageLobbyViewModel.HostEntry> entries = lobbyViewModel.getHostPanelEntries();
        for (int i = 0; i < entries.size(); i++) {
            StageLobbyViewModel.HostEntry entry = entries.get(i);
            String action = hostActionLabel(entry);
            rows.add(new SessionRowView(
                    shorten(entry.name(), 13),
                    formatHostState(entry.state(), entry.nearby(), entry.useHostCamera()),
                    action == null ? "" : action,
                    entry.state() == StageMemberState.READY,
                    hoveredSession == i,
                    action != null,
                    i,
                    entry.uuid()
            ));
        }
        return rows;
    }

    private List<String> buildDetailLines(StagePack selectedPack) {
        List<String> lines = new ArrayList<>();
        if (selectedPack == null) {
            lines.add(wb("select_pack_hint"));
            lines.add(wb("editor.no_pack"));
            return lines;
        }

        lines.add(wb("active_pack", shorten(selectedPack.getName(), 14)));
        lines.add(wb("summary.compact", countMotionFiles(selectedPack), countCameraFiles(selectedPack), selectedPack.getAudioFiles().size()));
        if (!lobbyViewModel.isSessionMember()) {
            lines.add(wb("camera_height.short") + ": " + formatSigned(cameraHeightOffset));
        } else if (!selectedPack.getAudioFiles().isEmpty()) {
            lines.add(wb("lanes.audio_file.short", shorten(selectedPack.getAudioFiles().get(0).name, 14)));
        } else if (!getCameraFiles(selectedPack).isEmpty()) {
            lines.add(wb("lanes.camera_file.short", shorten(getCameraFiles(selectedPack).get(0).name, 14)));
        }
        return lines;
    }

    private String buildFooterStatus(StagePack selectedPack) {
        if (lobbyViewModel.isSessionMember()) {
            return lobbyViewModel.isLocalReady()
                    ? Component.translatable("gui.mmdskin.stage.ready_done").getString()
                    : Component.translatable("gui.mmdskin.stage.waiting_host").getString();
        }
        return canStartStage(selectedPack)
                ? wb("ready_to_launch")
                : Component.translatable("gui.mmdskin.stage.waiting_ready").getString();
    }

    private int readyMemberCount() {
        int readyCount = 0;
        for (StageLobbyViewModel.HostEntry entry : lobbyViewModel.getHostPanelEntries()) {
            if (entry.state() == StageMemberState.READY) {
                readyCount++;
            }
        }
        return readyCount;
    }

    private void handlePrimaryAction(StagePack selectedPack) {
        if (lobbyViewModel.isSessionMember()) {
            lobbyViewModel.setLocalReady(!lobbyViewModel.isLocalReady());
            return;
        }
        if (canStartStage(selectedPack)) {
            startStage(selectedPack);
        }
    }

    private void handleMotionSelection(MotionRowView row) {
        if (row.mergeAll()) {
            selectedHostMotionFileName = null;
            return;
        }
        if (row.fileName() == null || row.fileName().isEmpty()) {
            return;
        }
        if (lobbyViewModel.isSessionMember()) {
            lobbyViewModel.toggleLocalCustomMotion(row.fileName());
            return;
        }
        selectedHostMotionFileName = row.fileName().equals(selectedHostMotionFileName) ? null : row.fileName();
    }

    private void updateCameraHeight(double mouseX) {
        float t = (float) ((mouseX - layout.cameraSlider.x) / Math.max(1.0, layout.cameraSlider.w));
        cameraHeightOffset = Mth.clamp(-2.0f + 4.0f * Mth.clamp(t, 0.0f, 1.0f), -2.0f, 2.0f);
    }

    private float normalizeCameraHeight(float value) {
        return Mth.clamp((value + 2.0f) / 4.0f, 0.0f, 1.0f);
    }

    private List<StagePack.VmdFileInfo> getMotionFiles(StagePack pack) {
        List<StagePack.VmdFileInfo> result = new ArrayList<>();
        if (pack == null) {
            return result;
        }
        for (StagePack.VmdFileInfo info : pack.getVmdFiles()) {
            if (info.hasBones || info.hasMorphs) {
                result.add(info);
            }
        }
        return result;
    }

    private List<StagePack.VmdFileInfo> getCameraFiles(StagePack pack) {
        List<StagePack.VmdFileInfo> result = new ArrayList<>();
        if (pack == null) {
            return result;
        }
        for (StagePack.VmdFileInfo info : pack.getVmdFiles()) {
            if (info.hasCamera) {
                result.add(info);
            }
        }
        return result;
    }

    private int countMotionFiles(StagePack pack) {
        return getMotionFiles(pack).size();
    }

    private int countCameraFiles(StagePack pack) {
        return getCameraFiles(pack).size();
    }

    private boolean canStartStage(StagePack pack) {
        if (pack == null || !pack.hasMotionVmd()) {
            return false;
        }
        return lobbyViewModel.getAcceptedMembers().isEmpty() || lobbyViewModel.allMembersReady();
    }

    private void startStage(StagePack pack) {
        if (pack == null) {
            return;
        }
        persistUiState();
        boolean started = hostPlaybackService.startPack(pack, cinematicMode, cameraHeightOffset, selectedHostMotionFileName);
        if (!started) {
            return;
        }
        stageStarted = true;
        requestCloseAfterFrame();
    }

    private void inviteAllNearby(List<StageLobbyViewModel.HostEntry> entries) {
        for (StageLobbyViewModel.HostEntry entry : entries) {
            if (entry.nearby() && entry.state() == null) {
                lobbyViewModel.sendInvite(entry.uuid());
            }
        }
    }

    private void handleHostAction(UUID targetUUID) {
        for (StageLobbyViewModel.HostEntry entry : lobbyViewModel.getHostPanelEntries()) {
            if (!entry.uuid().equals(targetUUID)) {
                continue;
            }
            StageMemberState state = entry.state();
            if (state == null || state == StageMemberState.DECLINED || state == StageMemberState.BUSY) {
                if (entry.nearby()) {
                    lobbyViewModel.sendInvite(entry.uuid());
                }
                return;
            }
            if (state == StageMemberState.INVITED) {
                lobbyViewModel.cancelInvite(entry.uuid());
            }
            return;
        }
    }

    private String hostActionLabel(StageLobbyViewModel.HostEntry entry) {
        StageMemberState state = entry.state();
        if ((state == null || state == StageMemberState.DECLINED || state == StageMemberState.BUSY) && entry.nearby()) {
            return wb("host.action.invite");
        }
        if (state == StageMemberState.INVITED) {
            return wb("host.action.cancel_invite");
        }
        return null;
    }

    private static String buildCompactMotionLabel(StagePack.VmdFileInfo motionFile) {
        String base = stripExtension(motionFile.name);
        List<String> tags = new ArrayList<>();
        if (motionFile.hasBones) {
            tags.add("B");
        }
        if (motionFile.hasMorphs) {
            tags.add("M");
        }
        if (motionFile.hasCamera) {
            tags.add("C");
        }
        return shorten(base, 14) + (tags.isEmpty() ? "" : " [" + String.join("/", tags) + "]");
    }

    private static String formatHostState(StageMemberState state, boolean nearby, boolean useHostCamera) {
        if (state == null) {
            return nearby ? wb("state.nearby") : wb("state.offline");
        }
        return switch (state) {
            case HOST -> wb("state.host");
            case INVITED -> wb("state.invited");
            case ACCEPTED -> useHostCamera ? wb("state.with_host_camera", wb("state.accepted")) : wb("state.accepted");
            case READY -> useHostCamera ? wb("state.with_host_camera", wb("state.ready")) : wb("state.ready");
            case DECLINED -> wb("state.declined");
            case BUSY -> wb("state.busy");
        };
    }

    private static String formatGuestState(StageMemberState state, boolean useHostCamera) {
        if (state == null) {
            return wb("state.unknown");
        }
        return switch (state) {
            case HOST -> wb("state.host");
            case INVITED -> wb("state.invited");
            case ACCEPTED -> useHostCamera ? wb("state.with_host_camera", wb("state.accepted")) : wb("state.accepted");
            case READY -> useHostCamera ? wb("state.with_host_camera", wb("state.ready")) : wb("state.ready");
            case DECLINED -> wb("state.declined");
            case BUSY -> wb("state.busy");
        };
    }

    private void flushPendingClose(Minecraft minecraft) {
        if (!pendingScreenClose || minecraft.screen != this) {
            return;
        }
        pendingScreenClose = false;
        minecraft.setScreen(null);
    }

    private void requestCloseAfterFrame() {
        pendingScreenClose = true;
    }

    private void closeAfterFailure(Throwable throwable) {
        LOGGER.error("[StageWorkbench] Skia workbench failed and will close", throwable);
        skiaRenderer.dispose();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            minecraft.setScreen(null);
        }
    }

    private void closeStageSelectionSession() {
        if (stageSelectionClosed) {
            return;
        }
        stageSelectionClosed = true;
        playbackCoordinator.onStageSelectionClosed(stageStarted);
    }

    private void persistUiState() {
        StageConfig config = StageConfig.getInstance();
        StagePack selectedPack = getSelectedPack();
        config.lastStagePack = selectedPack != null ? selectedPack.getName() : "";
        config.cinematicMode = cinematicMode;
        config.cameraHeightOffset = cameraHeightOffset;
        config.save();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
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

    private static String formatSigned(float value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String wb(String suffix, Object... args) {
        return tr("gui.mmdskin.stage.workbench." + suffix, args);
    }

    public record UiRect(int x, int y, int w, int h) {
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

    public record PackRowView(String label, boolean selected, boolean hovered, int index) {
    }

    public record MotionRowView(
            String label,
            String subtitle,
            boolean selected,
            boolean hovered,
            int index,
            String fileName,
            boolean mergeAll
    ) {
    }

    public record SessionRowView(
            String label,
            String subtitle,
            String actionText,
            boolean selected,
            boolean hovered,
            boolean actionable,
            int index,
            UUID targetId
    ) {
    }

    private record Layout(
            UiRect leftPanel,
            UiRect rightPanel,
            UiRect leftHeader,
            UiRect packList,
            UiRect refreshButton,
            UiRect motionList,
            UiRect detailsArea,
            UiRect customMotionToggle,
            UiRect useHostCameraToggle,
            UiRect cinematicToggle,
            UiRect cameraSlider,
            UiRect primaryButton,
            UiRect secondaryButton,
            UiRect rightHeader,
            UiRect sessionButton,
            UiRect sessionList
    ) {
        static Layout empty() {
            UiRect empty = UiRect.empty();
            return new Layout(empty, empty, empty, empty, empty, empty, empty, empty, empty, empty, empty, empty, empty, empty, empty, empty);
        }
    }
}
