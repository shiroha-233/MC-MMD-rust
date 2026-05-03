package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.stage.client.viewmodel.StageLobbyViewModel;
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

/** 舞台工作台界面，只保留状态持有、生命周期与事件转发。 */
public class StageWorkbenchScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();

    private final StageWorkbenchFacade facade;
    private final SkiaStageWorkbenchRenderer skiaRenderer = new SkiaStageWorkbenchRenderer();
    private final StageWorkbenchLayoutCalculator layoutCalculator = new StageWorkbenchLayoutCalculator();
    private final StageWorkbenchInteractionHandler interactionHandler = new StageWorkbenchInteractionHandler();
    private final StageWorkbenchViewBuilder viewBuilder = new StageWorkbenchViewBuilder();

    private List<StagePack> stagePacks = new ArrayList<>();
    private int selectedPackIndex = -1;
    private String selectedHostMotionFileName;
    private boolean cinematicMode;
    private float cameraHeightOffset;
    private boolean stageStarted;
    private boolean stageSelectionOpened;
    private boolean stageSelectionClosed;
    private boolean pendingScreenClose;
    private boolean snapshotDirty = true;

    private float packTargetScroll;
    private float packAnimatedScroll;
    private float motionTargetScroll;
    private float motionAnimatedScroll;
    private float sessionTargetScroll;
    private float sessionAnimatedScroll;

    private StageWorkbenchInteractionHandler.ActiveSlider activeSlider = StageWorkbenchInteractionHandler.ActiveSlider.NONE;
    private StageWorkbenchInteractionHandler.HoverState hoverState = StageWorkbenchInteractionHandler.HoverState.empty();
    private StageWorkbenchLayout layout = StageWorkbenchLayout.empty();
    private StageWorkbenchUiStateSnapshot snapshot = StageWorkbenchUiStateSnapshot.empty();
    private List<StageLobbyViewModel.MemberView> cachedMemberViews = List.of();
    private List<StageLobbyViewModel.HostEntry> cachedHostEntries = List.of();
    private boolean cachedSessionMember;
    private boolean cachedLocalReady;
    private boolean cachedUseHostCamera;
    private boolean cachedCustomMotionEnabled;

    public StageWorkbenchScreen() {
        this(StageWorkbenchFacade.getInstance());
    }

    StageWorkbenchScreen(StageWorkbenchFacade facade) {
        super(Component.translatable("gui.mmdskin.stage.workbench.title"));
        this.facade = facade;
        StageWorkbenchFacade.WorkbenchPreferences preferences = facade.loadPreferences();
        this.cinematicMode = preferences.cinematicMode();
        this.cameraHeightOffset = preferences.cameraHeightOffset();
        this.stagePacks = facade.loadStagePacks();
        restoreSelection(preferences.lastStagePack());
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
            facade.onStageSelectionOpened();
            stageSelectionOpened = true;
        }
        syncGuestPackPreferences();
        updateLayout();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            updateLayout();
            normalizeSelectedHostMotion();
            ensureSnapshot();
            clampScrolls();
            hoverState = interactionHandler.resolveHover(
                    layout,
                    mouseX,
                    mouseY,
                    facade.isSessionMember(),
                    snapshot,
                    packAnimatedScroll,
                    motionAnimatedScroll,
                    sessionAnimatedScroll
            );
            animateScrolls();

            SkiaStageWorkbenchRenderer.WorkbenchView view = viewBuilder.buildWorkbenchView(
                    this.title,
                    facade,
                    layout,
                    snapshot,
                    hoverState,
                    cinematicMode,
                    cameraHeightOffset,
                    packAnimatedScroll,
                    motionAnimatedScroll,
                    sessionAnimatedScroll
            );
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
        if (!layout.leftPanel().contains(mouseX, mouseY) && !layout.rightPanel().contains(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        StagePack selectedPack = snapshot.selectedPack();
        if (layout.refreshButton().contains(mouseX, mouseY)) {
            reloadStagePacks();
            return true;
        }
        if (facade.isSessionMember() && layout.customMotionToggle().contains(mouseX, mouseY)) {
            facade.toggleLocalCustomMotionEnabled();
            invalidateSnapshot();
            return true;
        }
        if (facade.isSessionMember() && layout.useHostCameraToggle().contains(mouseX, mouseY)) {
            facade.toggleUseHostCamera();
            invalidateSnapshot();
            return true;
        }
        if (layout.cinematicToggle().contains(mouseX, mouseY)) {
            cinematicMode = !cinematicMode;
            return true;
        }
        if (!facade.isSessionMember() && layout.cameraSlider().contains(mouseX, mouseY)) {
            activeSlider = StageWorkbenchInteractionHandler.ActiveSlider.CAMERA_HEIGHT;
            updateCameraHeight(mouseX);
            invalidateSnapshot();
            return true;
        }
        if (layout.primaryButton().contains(mouseX, mouseY)) {
            handlePrimaryAction(selectedPack);
            return true;
        }
        if (layout.secondaryButton().contains(mouseX, mouseY)) {
            requestCloseAfterFrame();
            return true;
        }
        if (!facade.isSessionMember() && layout.sessionButton().contains(mouseX, mouseY)) {
            facade.inviteAllNearby();
            invalidateSnapshot();
            return true;
        }
        if (layout.packList().contains(mouseX, mouseY)) {
            handlePackClick();
            return true;
        }
        if (layout.motionList().contains(mouseX, mouseY)) {
            handleMotionClick();
            return true;
        }
        if (!facade.isSessionMember() && layout.sessionList().contains(mouseX, mouseY)) {
            handleSessionClick();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (activeSlider == StageWorkbenchInteractionHandler.ActiveSlider.CAMERA_HEIGHT) {
                persistUiState();
            }
            activeSlider = StageWorkbenchInteractionHandler.ActiveSlider.NONE;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && activeSlider == StageWorkbenchInteractionHandler.ActiveSlider.CAMERA_HEIGHT) {
            updateCameraHeight(mouseX);
            invalidateSnapshot();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (layout.packList().contains(mouseX, mouseY)) {
            packTargetScroll = interactionHandler.updateScrollTarget(
                    packTargetScroll,
                    delta,
                    snapshot.packRows().size(),
                    StageWorkbenchLayoutCalculator.PACK_ROW_HEIGHT,
                    layout.packList().h()
            );
            return true;
        }
        if (layout.motionList().contains(mouseX, mouseY)) {
            motionTargetScroll = interactionHandler.updateScrollTarget(
                    motionTargetScroll,
                    delta,
                    snapshot.motionRows().size(),
                    StageWorkbenchLayoutCalculator.MOTION_ROW_HEIGHT,
                    layout.motionList().h()
            );
            return true;
        }
        if (layout.sessionList().contains(mouseX, mouseY)) {
            sessionTargetScroll = interactionHandler.updateScrollTarget(
                    sessionTargetScroll,
                    delta,
                    snapshot.sessionRows().size(),
                    StageWorkbenchLayoutCalculator.SESSION_ROW_HEIGHT,
                    layout.sessionList().h()
            );
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
        StageWorkbenchLayout nextLayout = layoutCalculator.calculate(this.width, this.height, facade.isSessionMember());
        if (!nextLayout.equals(layout)) {
            layout = nextLayout;
            clampScrolls();
        }
    }

    private void ensureSnapshot() {
        boolean sessionMember = facade.isSessionMember();
        boolean localReady = facade.isLocalReady();
        boolean useHostCamera = facade.isUseHostCamera();
        boolean customMotionEnabled = facade.isLocalCustomMotionEnabled();
        List<StageLobbyViewModel.MemberView> memberViews = sessionMember ? facade.getSessionMembersView() : List.of();
        List<StageLobbyViewModel.HostEntry> hostEntries = sessionMember ? List.of() : facade.getHostPanelEntries();

        if (!snapshotDirty
                && sessionMember == cachedSessionMember
                && localReady == cachedLocalReady
                && useHostCamera == cachedUseHostCamera
                && customMotionEnabled == cachedCustomMotionEnabled
                && memberViews.equals(cachedMemberViews)
                && hostEntries.equals(cachedHostEntries)) {
            return;
        }

        snapshot = viewBuilder.buildSnapshot(
                facade,
                stagePacks,
                selectedPackIndex,
                selectedHostMotionFileName,
                cameraHeightOffset,
                memberViews,
                hostEntries
        );
        snapshotDirty = false;
        cachedSessionMember = sessionMember;
        cachedLocalReady = localReady;
        cachedUseHostCamera = useHostCamera;
        cachedCustomMotionEnabled = customMotionEnabled;
        cachedMemberViews = List.copyOf(memberViews);
        cachedHostEntries = List.copyOf(hostEntries);
    }

    private void clampScrolls() {
        packTargetScroll = interactionHandler.clampScroll(
                packTargetScroll,
                snapshot.packRows().size(),
                StageWorkbenchLayoutCalculator.PACK_ROW_HEIGHT,
                layout.packList().h()
        );
        packAnimatedScroll = interactionHandler.clampScroll(
                packAnimatedScroll,
                snapshot.packRows().size(),
                StageWorkbenchLayoutCalculator.PACK_ROW_HEIGHT,
                layout.packList().h()
        );
        motionTargetScroll = interactionHandler.clampScroll(
                motionTargetScroll,
                snapshot.motionRows().size(),
                StageWorkbenchLayoutCalculator.MOTION_ROW_HEIGHT,
                layout.motionList().h()
        );
        motionAnimatedScroll = interactionHandler.clampScroll(
                motionAnimatedScroll,
                snapshot.motionRows().size(),
                StageWorkbenchLayoutCalculator.MOTION_ROW_HEIGHT,
                layout.motionList().h()
        );
        sessionTargetScroll = interactionHandler.clampScroll(
                sessionTargetScroll,
                snapshot.sessionRows().size(),
                StageWorkbenchLayoutCalculator.SESSION_ROW_HEIGHT,
                layout.sessionList().h()
        );
        sessionAnimatedScroll = interactionHandler.clampScroll(
                sessionAnimatedScroll,
                snapshot.sessionRows().size(),
                StageWorkbenchLayoutCalculator.SESSION_ROW_HEIGHT,
                layout.sessionList().h()
        );
    }

    private void animateScrolls() {
        packAnimatedScroll = interactionHandler.animateScroll(packAnimatedScroll, packTargetScroll);
        motionAnimatedScroll = interactionHandler.animateScroll(motionAnimatedScroll, motionTargetScroll);
        sessionAnimatedScroll = interactionHandler.animateScroll(sessionAnimatedScroll, sessionTargetScroll);
    }

    private void renderFallback(GuiGraphics guiGraphics, SkiaStageWorkbenchRenderer.WorkbenchView view) {
        drawFallbackPanel(guiGraphics, view.leftPanel());
        drawFallbackPanel(guiGraphics, view.rightPanel());
        guiGraphics.drawString(this.font, view.leftTitle(), view.leftHeader().x(), view.leftHeader().y(), 0xFFE9F1FA, false);
        guiGraphics.drawString(this.font, view.leftSubtitle(), view.leftHeader().x(), view.leftHeader().y() + 10, 0xBFD1DEEC, false);
        guiGraphics.drawString(this.font, view.rightTitle(), view.rightHeader().x(), view.rightHeader().y(), 0xFFE9F1FA, false);
        guiGraphics.drawString(this.font, view.rightSubtitle(), view.rightHeader().x(), view.rightHeader().y() + 10, 0xBFD1DEEC, false);
    }

    private void drawFallbackPanel(GuiGraphics guiGraphics, StageWorkbenchLayout.UiRect rect) {
        guiGraphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), 0x12000000);
        guiGraphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + 1, 0x20FFFFFF);
        guiGraphics.fill(rect.x(), rect.y() + rect.h() - 1, rect.x() + rect.w(), rect.y() + rect.h(), 0x20FFFFFF);
        guiGraphics.fill(rect.x(), rect.y(), rect.x() + 1, rect.y() + rect.h(), 0x20FFFFFF);
        guiGraphics.fill(rect.x() + rect.w() - 1, rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), 0x20FFFFFF);
    }

    private void reloadStagePacks() {
        StageWorkbenchFacade.WorkbenchPreferences preferences = facade.loadPreferences();
        stagePacks = facade.loadStagePacks();
        selectedPackIndex = -1;
        restoreSelection(preferences.lastStagePack());
        if (selectedPackIndex < 0 && !stagePacks.isEmpty()) {
            selectedPackIndex = 0;
        }
        normalizeSelectedHostMotion();
        syncGuestPackPreferences();
        packTargetScroll = 0.0f;
        packAnimatedScroll = 0.0f;
        motionTargetScroll = 0.0f;
        motionAnimatedScroll = 0.0f;
        invalidateSnapshot();
    }

    private void restoreSelection(String lastStagePack) {
        if (lastStagePack == null || lastStagePack.isEmpty()) {
            return;
        }
        for (int i = 0; i < stagePacks.size(); i++) {
            if (lastStagePack.equals(stagePacks.get(i).getName())) {
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
        normalizeSelectedHostMotion();
        syncGuestPackPreferences();
        motionTargetScroll = 0.0f;
        motionAnimatedScroll = 0.0f;
        invalidateSnapshot();
    }

    private void syncGuestPackPreferences() {
        if (!facade.isSessionMember()) {
            return;
        }
        StagePack selectedPack = getSelectedPack();
        facade.syncGuestPackPreferences(selectedPack, viewBuilder.collectMotionFiles(selectedPack));
    }

    private StagePack getSelectedPack() {
        if (selectedPackIndex >= 0 && selectedPackIndex < stagePacks.size()) {
            return stagePacks.get(selectedPackIndex);
        }
        return null;
    }

    private void normalizeSelectedHostMotion() {
        if (selectedHostMotionFileName == null) {
            return;
        }
        StagePack selectedPack = getSelectedPack();
        if (selectedPack == null) {
            selectedHostMotionFileName = null;
            invalidateSnapshot();
            return;
        }
        boolean exists = false;
        for (StagePack.VmdFileInfo info : viewBuilder.collectMotionFiles(selectedPack)) {
            if (info.name.equals(selectedHostMotionFileName)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            selectedHostMotionFileName = null;
            invalidateSnapshot();
        }
    }

    private void handlePrimaryAction(StagePack selectedPack) {
        if (facade.isSessionMember()) {
            facade.toggleLocalReady();
            invalidateSnapshot();
            return;
        }
        if (facade.canStartStage(selectedPack)) {
            startStage(selectedPack);
        }
    }

    private void handlePackClick() {
        int hoveredPack = hoverState.packIndex();
        if (hoveredPack < 0 || hoveredPack >= snapshot.packRows().size()) {
            return;
        }
        selectPack(snapshot.packRows().get(hoveredPack).index());
    }

    private void handleMotionClick() {
        int hoveredMotion = hoverState.motionIndex();
        if (hoveredMotion < 0 || hoveredMotion >= snapshot.motionRows().size()) {
            return;
        }
        handleMotionSelection(snapshot.motionRows().get(hoveredMotion));
    }

    private void handleSessionClick() {
        int hoveredSession = hoverState.sessionIndex();
        if (hoveredSession < 0 || hoveredSession >= snapshot.sessionRows().size()) {
            return;
        }
        StageWorkbenchUiStateSnapshot.SessionRow row = snapshot.sessionRows().get(hoveredSession);
        if (row.actionable() && row.targetId() != null) {
            handleHostAction(row.targetId());
            invalidateSnapshot();
        }
    }

    private void handleMotionSelection(StageWorkbenchUiStateSnapshot.MotionRow row) {
        if (row.mergeAll()) {
            selectedHostMotionFileName = null;
            invalidateSnapshot();
            return;
        }
        if (row.fileName() == null || row.fileName().isEmpty()) {
            return;
        }
        if (facade.isSessionMember()) {
            facade.toggleLocalCustomMotion(row.fileName());
            invalidateSnapshot();
            return;
        }
        selectedHostMotionFileName = row.fileName().equals(selectedHostMotionFileName) ? null : row.fileName();
        invalidateSnapshot();
    }

    private void updateCameraHeight(double mouseX) {
        float t = (float) ((mouseX - layout.cameraSlider().x()) / Math.max(1.0, layout.cameraSlider().w()));
        cameraHeightOffset = Mth.clamp(-2.0f + 4.0f * Mth.clamp(t, 0.0f, 1.0f), -2.0f, 2.0f);
    }

    private void startStage(StagePack pack) {
        persistUiState();
        boolean started = facade.startStage(pack, cinematicMode, cameraHeightOffset, selectedHostMotionFileName);
        if (!started) {
            return;
        }
        stageStarted = true;
        requestCloseAfterFrame();
    }

    private void handleHostAction(UUID targetUUID) {
        facade.handleHostAction(targetUUID);
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
        facade.onStageSelectionClosed(stageStarted);
    }

    private void persistUiState() {
        StagePack selectedPack = getSelectedPack();
        facade.savePreferences(selectedPack != null ? selectedPack.getName() : "", cinematicMode, cameraHeightOffset);
    }

    private void invalidateSnapshot() {
        snapshotDirty = true;
    }
}
