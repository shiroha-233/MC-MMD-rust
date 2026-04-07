package com.shiroha.mmdskin.ui.stage.imgui;

import com.shiroha.mmdskin.config.StageConfig;
import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.stage.client.StagePlaybackCoordinator;
import com.shiroha.mmdskin.stage.client.asset.LocalStagePackRepository;
import com.shiroha.mmdskin.stage.client.playback.StageHostPlaybackService;
import com.shiroha.mmdskin.stage.client.viewmodel.StageLobbyViewModel;
import com.shiroha.mmdskin.stage.domain.model.StageMemberState;
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

public class StageWorkbenchScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();

    private final StageLobbyViewModel lobbyViewModel = StageLobbyViewModel.getInstance();
    private final LocalStagePackRepository stagePackRepository = LocalStagePackRepository.getInstance();
    private final StageHostPlaybackService hostPlaybackService = StageHostPlaybackService.getInstance();
    private final StagePlaybackCoordinator playbackCoordinator = StagePlaybackCoordinator.getInstance();
    private final StageImGuiRenderer imguiRenderer = new StageImGuiRenderer();
    private final float[] cameraHeightSlider = new float[1];

    private List<StagePack> stagePacks = new ArrayList<>();
    private int selectedPackIndex = -1;
    private String selectedHostMotionFileName;
    private boolean cinematicMode;
    private float cameraHeightOffset;
    private boolean stageStarted;
    private boolean stageSelectionOpened;
    private boolean stageSelectionClosed;
    private boolean pendingScreenClose;

    public StageWorkbenchScreen() {
        super(Component.translatable("gui.mmdskin.stage.workbench.title"));
        StageConfig config = StageConfig.getInstance();
        this.cinematicMode = config.cinematicMode;
        this.cameraHeightOffset = config.cameraHeightOffset;
        this.stagePacks = stagePackRepository.loadStagePacks();
        restoreSelection(config);
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
        try {
            imguiRenderer.ensureInitialized();
        } catch (Throwable throwable) {
            closeAfterFailure(throwable);
            return;
        }

        if (!stageSelectionOpened) {
            playbackCoordinator.onStageSelectionOpened();
            stageSelectionOpened = true;
        }
        syncGuestPackPreferences();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            StagePack selectedPack = getSelectedPack();
            normalizeSelectedHostMotion(selectedPack);

            float framebufferScaleX = this.width > 0
                    ? (float) minecraft.getWindow().getWidth() / (float) this.width
                    : 1.0f;
            float framebufferScaleY = this.height > 0
                    ? (float) minecraft.getWindow().getHeight() / (float) this.height
                    : 1.0f;

            imguiRenderer.setGlyphHintTexts(collectVisibleGlyphHints(selectedPack));
            imguiRenderer.beginFrame(this.width, this.height, framebufferScaleX, framebufferScaleY, mouseX, mouseY);
            renderWorkbench(selectedPack);
            imguiRenderer.renderFrame();
            flushPendingClose(minecraft);
        } catch (Throwable throwable) {
            closeAfterFailure(throwable);
        }
    }

    private void closeAfterFailure(Throwable throwable) {
        LOGGER.error("[StageWorkbench] ImGui workbench failed and will close (legacy fallback removed)", throwable);
        closeStageSelectionSession();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == this) {
            minecraft.setScreen(null);
        }
    }

    private void flushPendingClose(Minecraft minecraft) {
        if (!pendingScreenClose || minecraft.screen != this) {
            return;
        }
        pendingScreenClose = false;
        closeStageSelectionSession();
        minecraft.setScreen(null);
    }

    private void requestCloseAfterFrame() {
        pendingScreenClose = true;
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
        closeStageSelectionSession();
        super.onClose();
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

    private void closeStageSelectionSession() {
        imguiRenderer.dispose();
        if (stageSelectionClosed) {
            return;
        }
        stageSelectionClosed = true;
        playbackCoordinator.onStageSelectionClosed(stageStarted);
    }

    private void renderWorkbench(StagePack selectedPack) {
        float margin = 8.0f;
        float gap = 6.0f;
        float packWidth = Math.min(152.0f, Math.max(132.0f, this.width * 0.090f));
        float workbenchWidth = Math.min(248.0f, Math.max(210.0f, this.width * 0.145f));
        float roomWidth = Math.min(138.0f, Math.max(118.0f, this.width * 0.080f));

        float packHeight = Math.min(Math.max(182.0f, this.height * 0.34f), this.height * 0.42f);
        float roomHeight = Math.min(Math.max(118.0f, this.height * 0.24f), this.height * 0.34f);
        float workbenchY = margin + packHeight + gap;
        float workbenchHeight = Math.max(210.0f, this.height - workbenchY - margin);

        float packX = margin;
        float packY = margin;
        float workbenchX = margin;
        float roomX = Math.max(this.width - roomWidth - margin, workbenchX + workbenchWidth + 16.0f);
        float roomY = margin;

        int floatingFlags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings;

        ImGui.setNextWindowPos(packX, packY, ImGuiCond.Appearing);
        ImGui.setNextWindowSize(packWidth, packHeight, ImGuiCond.Appearing);
        ImGui.begin(wb("packs.section") + "##mmdskin_stage_pack_window", floatingFlags);
        renderPackWindow(selectedPack);
        ImGui.end();

        ImGui.setNextWindowPos(workbenchX, workbenchY, ImGuiCond.Appearing);
        ImGui.setNextWindowSize(workbenchWidth, workbenchHeight, ImGuiCond.Appearing);
        ImGui.begin(wb("title") + "##mmdskin_stage_workbench_window", floatingFlags);
        renderWorkbenchWindow(selectedPack);
        ImGui.end();

        ImGui.setNextWindowPos(roomX, roomY, ImGuiCond.Appearing);
        ImGui.setNextWindowSize(roomWidth, roomHeight, ImGuiCond.Appearing);
        ImGui.begin(wb("session.section") + "##mmdskin_stage_session_window", floatingFlags);
        renderSessionPanel(selectedPack);
        ImGui.end();
    }
    private void renderWorkbenchWindow(StagePack selectedPack) {
        renderHeaderPanel(selectedPack);
        ImGui.separator();
        renderEditorPanel(selectedPack);
        renderFooter(selectedPack);
    }

    private void renderPackWindow(StagePack selectedPack) {
        if (selectedPack == null) {
            wrappedDisabled(wb("select_pack_hint"));
            ImGui.separator();
        }
        renderPackColumn(selectedPack);
    }

    private void renderHeaderPanel(StagePack selectedPack) {
        ImGui.textDisabled(wb(lobbyViewModel.isSessionMember() ? "flow.guest" : "flow.host")
                + " · " + wb("packs.count", stagePacks.size()));

        if (selectedPack != null) {
            wrappedDisabled(wb("active_pack", shorten(selectedPack.getName(), 26)));
            wrappedDisabled(wb("summary.compact",
                    countMotionFiles(selectedPack),
                    countCameraFiles(selectedPack),
                    selectedPack.getAudioFiles().size()));
        } else {
            wrappedDisabled(wb("select_pack_hint"));
        }

        if (fullWidthButton(wb("refresh_packs.short") + "##stage_refresh_packs_header")) {
            reloadStagePacks();
        }
    }

    private void renderEditorPanel(StagePack selectedPack) {
        if (selectedPack == null) {
            wrappedDisabled(wb("editor.no_pack"));
            return;
        }

        List<StagePack.VmdFileInfo> motionFiles = getMotionFiles(selectedPack);
        List<StagePack.VmdFileInfo> cameraFiles = getCameraFiles(selectedPack);

        renderMotionSelection(selectedPack, motionFiles);
        ImGui.separator();
        renderLaneSummary(selectedPack, cameraFiles);
    }

    private void renderMotionSelection(StagePack selectedPack, List<StagePack.VmdFileInfo> motionFiles) {
        ImGui.textDisabled(wb("playback.section.short"));
        wrappedDisabled(wb("playback.help.short"));

        if (motionFiles.isEmpty()) {
            wrappedDisabled(wb("guest.no_local_motion.short"));
            return;
        }

        if (!lobbyViewModel.isSessionMember()) {
            boolean mergeAll = selectedHostMotionFileName == null;
            if (fullWidthSelectable(wb("playback.merge_all.short") + "##host_merge_all", mergeAll)) {
                selectedHostMotionFileName = null;
            }
        }

        for (StagePack.VmdFileInfo motionFile : motionFiles) {
            String motionLabel = buildCompactMotionLabel(motionFile);
            if (lobbyViewModel.isSessionMember()) {
                boolean selected = lobbyViewModel.isLocalCustomMotionSelected(motionFile.name);
                if (fullWidthSelectable(motionLabel + "##guest_motion_" + motionFile.name, selected)) {
                    lobbyViewModel.toggleLocalCustomMotion(motionFile.name);
                }
                continue;
            }

            boolean selected = motionFile.name.equals(selectedHostMotionFileName);
            if (fullWidthSelectable(motionLabel + "##host_motion_" + motionFile.name, selected)) {
                selectedHostMotionFileName = selected ? null : motionFile.name;
            }
        }

        if (lobbyViewModel.isSessionMember()) {
            wrappedDisabled(wb("guest.override_pack.short", shorten(selectedPack.getName(), 20)));
            wrappedDisabled(wb("guest.override_help.short"));
        }
    }

    private void renderLaneSummary(StagePack selectedPack, List<StagePack.VmdFileInfo> cameraFiles) {
        ImGui.textDisabled(wb("lanes.section.short"));

        if (cameraFiles.isEmpty()) {
            wrappedDisabled(wb("lanes.no_camera.short"));
        } else {
            wrappedDisabled(wb("lanes.camera_file.short", shorten(cameraFiles.get(0).name, 24)));
        }

        if (selectedPack.getAudioFiles().isEmpty()) {
            wrappedDisabled(wb("lanes.no_audio.short"));
        } else {
            wrappedDisabled(wb("lanes.audio_file.short", shorten(selectedPack.getAudioFiles().get(0).name, 24)));
        }
    }

    private void renderSessionPanel(StagePack selectedPack) {
        if (lobbyViewModel.isSessionMember()) {
            renderGuestSession(selectedPack);
        } else {
            renderHostSession();
        }
    }

    private void renderPackColumn(StagePack selectedPack) {
        if (stagePacks.isEmpty()) {
            wrappedDisabled(wb("packs.empty"));
            return;
        }

        for (int i = 0; i < stagePacks.size(); i++) {
            StagePack pack = stagePacks.get(i);
            boolean selected = i == selectedPackIndex;
            String label = shorten(pack.getName(), 18) + "##stage_pack_" + i;
            if (fullWidthSelectable(label, selected)) {
                selectPack(i);
                selectedPack = pack;
            }
            if (selected) {
                wrappedDisabled(wb("packs.stats.short", countMotionFiles(pack), countCameraFiles(pack), pack.getAudioFiles().size()));
            }
        }
    }

    private void renderHostSession() {
        List<StageLobbyViewModel.HostEntry> entries = lobbyViewModel.getHostPanelEntries();
        int readyCount = 0;
        for (StageLobbyViewModel.HostEntry entry : entries) {
            if (entry.state() == StageMemberState.READY) {
                readyCount++;
            }
        }

        wrappedDisabled(wb("host.stats.short", entries.size(), readyCount));
        if (fullWidthButton(wb("host.invite_all.short") + "##stage_invite_all")) {
            inviteAllNearby(entries);
        }
        ImGui.separator();

        if (entries.isEmpty()) {
            wrappedDisabled(wb("host.empty"));
            return;
        }

        for (StageLobbyViewModel.HostEntry entry : entries) {
            ImGui.textWrapped(shorten(entry.name(), 18) + " · " + formatHostState(entry.state(), entry.nearby(), entry.useHostCamera()));

            String actionLabel = hostActionLabel(entry);
            if (actionLabel != null && fullWidthButton(actionLabel + "##host_action_" + entry.uuid())) {
                handleHostAction(entry);
            }
        }

    }

    private void renderGuestSession(StagePack selectedPack) {
        List<StageLobbyViewModel.MemberView> members = lobbyViewModel.getSessionMembersView();
        wrappedDisabled(wb("guest.members.short", members.size()));

        boolean customEnabled = lobbyViewModel.isLocalCustomMotionEnabled();
        if (ImGui.checkbox(wb("guest.enable_local_override") + "##guest_custom_override", customEnabled)) {
            lobbyViewModel.setLocalCustomMotionEnabled(!customEnabled);
            customEnabled = !customEnabled;
        }

        if (selectedPack == null) {
            wrappedDisabled(wb("guest.select_pack_first"));
        } else {
            wrappedDisabled(wb("guest.override_pack", selectedPack.getName()));
            if (!customEnabled) {
                wrappedDisabled(wb("guest.override_help"));
            }
        }

        ImGui.separator();

        if (members.isEmpty()) {
            wrappedDisabled(wb("guest.waiting_sync.short"));
            return;
        }

        for (StageLobbyViewModel.MemberView member : members) {
            String prefix = member.host() ? wb("member_prefix.host") : member.local() ? wb("member_prefix.you") : wb("member_prefix.guest");
            ImGui.textWrapped(prefix + shorten(member.name(), 18) + " · " + formatGuestState(member.state(), member.useHostCamera()));
        }

    }

    private void renderFooter(StagePack selectedPack) {
        boolean isGuest = lobbyViewModel.isSessionMember();

        if (isGuest) {
            boolean useHostCamera = lobbyViewModel.isUseHostCamera();
            boolean ready = lobbyViewModel.isLocalReady();

            if (ImGui.checkbox(tr("gui.mmdskin.stage.use_host_camera") + "##guest_use_host_camera", useHostCamera)) {
                lobbyViewModel.setUseHostCamera(!useHostCamera);
            }
            ImGui.sameLine();
            if (ImGui.checkbox(tr("gui.mmdskin.stage.cinematic") + "##guest_cinematic", cinematicMode)) {
                cinematicMode = !cinematicMode;
            }

            float actionWidth = Math.max(1.0f, (ImGui.getContentRegionAvailX() - 4.0f) * 0.5f);
            if (ImGui.button((ready ? tr("gui.mmdskin.stage.unready") : tr("gui.mmdskin.stage.ready")) + "##guest_ready_toggle", actionWidth, 0.0f)) {
                lobbyViewModel.setLocalReady(!ready);
            }
            ImGui.sameLine();
            if (ImGui.button(tr("gui.cancel") + "##guest_cancel_stage", actionWidth, 0.0f)) {
                requestCloseAfterFrame();
                return;
            }

            wrappedDisabled(ready ? tr("gui.mmdskin.stage.ready_done") : tr("gui.mmdskin.stage.waiting_host"));
            return;
        }

        cameraHeightSlider[0] = cameraHeightOffset;
        ImGui.textDisabled(wb("camera_height.short"));
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.sliderFloat("##host_stage_height", cameraHeightSlider, -2.0f, 2.0f, "%+.2f")) {
            cameraHeightOffset = cameraHeightSlider[0];
        }

        if (ImGui.checkbox(tr("gui.mmdskin.stage.cinematic") + "##host_cinematic", cinematicMode)) {
            cinematicMode = !cinematicMode;
        }

        float actionWidth = Math.max(1.0f, (ImGui.getContentRegionAvailX() - 4.0f) * 0.5f);
        if (ImGui.button(tr("gui.mmdskin.stage.start") + "##host_start_stage", actionWidth, 0.0f) && canStartStage(selectedPack)) {
            startStage(selectedPack);
            return;
        }
        ImGui.sameLine();
        if (ImGui.button(tr("gui.cancel") + "##host_cancel_stage", actionWidth, 0.0f)) {
            requestCloseAfterFrame();
            return;
        }

        wrappedDisabled(canStartStage(selectedPack)
                ? wb("ready_to_launch")
                : tr("gui.mmdskin.stage.waiting_ready"));
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

    private void handleHostAction(StageLobbyViewModel.HostEntry entry) {
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

    private static String buildMotionLabel(StagePack.VmdFileInfo motionFile) {
        List<String> tags = new ArrayList<>();
        if (motionFile.hasBones) {
            tags.add(wb("tag.bones"));
        }
        if (motionFile.hasMorphs) {
            tags.add(wb("tag.morph"));
        }
        if (motionFile.hasCamera) {
            tags.add(wb("tag.camera"));
        }
        return stripExtension(motionFile.name) + (tags.isEmpty() ? "" : " [" + String.join(", ", tags) + "]");
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
        return shorten(base, 20) + (tags.isEmpty() ? "" : " [" + String.join("/", tags) + "]");
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

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
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

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String wb(String suffix, Object... args) {
        return tr("gui.mmdskin.stage.workbench." + suffix, args);
    }

    private static float fullWidth() {
        return Math.max(1.0f, ImGui.getContentRegionAvailX());
    }

    private static boolean fullWidthButton(String label) {
        return ImGui.button(label, fullWidth(), 0.0f);
    }

    private static boolean fullWidthSelectable(String label, boolean selected) {
        return ImGui.selectable(label, selected, 0, fullWidth(), 0.0f);
    }

    private static void wrappedDisabled(String text) {
        ImGui.pushTextWrapPos();
        ImGui.textDisabled(text);
        ImGui.popTextWrapPos();
    }

    private List<String> collectVisibleGlyphHints(StagePack selectedPack) {
        List<String> hints = new ArrayList<>();
        int readyCount = 0;
        for (StageLobbyViewModel.HostEntry entry : lobbyViewModel.getHostPanelEntries()) {
            if (entry.state() == StageMemberState.READY) {
                readyCount++;
            }
        }

        hints.add(this.title.getString());
        hints.add(wb("packs.section"));
        hints.add(wb("packs.help"));
        hints.add(wb("packs.empty"));
        hints.add(wb("selected_pack.details"));
        hints.add(wb("refresh_packs"));
        hints.add(wb("refresh_packs.short"));
        hints.add(wb("title"));
        hints.add(wb("flow.guest"));
        hints.add(wb("flow.host"));
        hints.add(wb("packs.count", stagePacks.size()));
        hints.add(wb("select_pack_hint"));
        hints.add(wb("editor.section"));
        hints.add(wb("editor.no_pack"));
        hints.add(wb("summary.section"));
        hints.add(wb("summary.motion_clips", 0));
        hints.add(wb("summary.camera_clips", 0));
        hints.add(wb("summary.audio_tracks", 0));
        hints.add(wb("playback.section"));
        hints.add(wb("playback.help"));
        hints.add(wb("playback.merge_all"));
        hints.add(wb("playback.merge_all.short"));
        hints.add(wb("lanes.section"));
        hints.add(wb("lanes.no_camera"));
        hints.add(wb("lanes.no_audio"));
        hints.add(wb("session.section"));
        hints.add(wb("host.nearby_players", lobbyViewModel.getHostPanelEntries().size()));
        hints.add(wb("host.ready_guests", readyCount));
        hints.add(wb("host.invite_all"));
        hints.add(wb("host.empty"));
        hints.add(wb("guest.watching_host"));
        hints.add(wb("guest.members", lobbyViewModel.getSessionMembersView().size()));
        hints.add(wb("guest.waiting_sync"));
        hints.add(wb("guest.enable_local_override"));
        hints.add(wb("guest.select_pack_first"));
        hints.add(wb("guest.override_pack", selectedPack != null ? selectedPack.getName() : ""));
        hints.add(wb("guest.override_help"));
        hints.add(wb("guest.no_local_motion"));
        hints.add(wb("member_prefix.host"));
        hints.add(wb("member_prefix.you"));
        hints.add(wb("member_prefix.guest"));
        hints.add(wb("camera_height"));
        hints.add(wb("ready_to_launch"));
        hints.add(wb("packs.stats.short", 0, 0, 0));
        hints.add(wb("summary.compact", 0, 0, 0));
        hints.add(wb("playback.section.short"));
        hints.add(wb("playback.help.short"));
        hints.add(wb("lanes.section.short"));
        hints.add(wb("lanes.no_camera.short"));
        hints.add(wb("lanes.no_audio.short"));
        hints.add(wb("host.stats.short", 0, 0));
        hints.add(wb("host.invite_all.short"));
        hints.add(wb("host.empty.short"));
        hints.add(wb("guest.members.short", 0));
        hints.add(wb("guest.waiting_sync.short"));
        hints.add(wb("guest.override_mode.short"));
        hints.add(wb("guest.select_pack_first.short"));
        hints.add(wb("guest.override_help.short"));
        hints.add(wb("guest.no_local_motion.short"));
        hints.add(wb("camera_height.short"));
        hints.add(tr("gui.mmdskin.stage.local_motion_override"));
        hints.add(tr("gui.mmdskin.stage.use_host_camera"));
        hints.add(tr("gui.mmdskin.stage.cinematic"));
        hints.add(tr("gui.mmdskin.stage.ready"));
        hints.add(tr("gui.mmdskin.stage.unready"));
        hints.add(tr("gui.mmdskin.stage.ready_done"));
        hints.add(tr("gui.mmdskin.stage.waiting_host"));
        hints.add(tr("gui.mmdskin.stage.waiting_ready"));
        hints.add(tr("gui.mmdskin.stage.start"));
        hints.add(tr("gui.cancel"));

        for (StagePack pack : stagePacks) {
            hints.add(pack.getName());
            hints.add(wb("packs.stats", countMotionFiles(pack), countCameraFiles(pack), pack.getAudioFiles().size()));
        }

        if (selectedPack != null) {
            hints.add(selectedPack.getName());
            hints.add(wb("active_pack", selectedPack.getName()));
            hints.add(selectedPack.getFolderPath());
            hints.add(wb("guest.override_pack.short", selectedPack.getName()));

            for (StagePack.VmdFileInfo motionFile : getMotionFiles(selectedPack)) {
                hints.add(motionFile.name);
                hints.add(buildMotionLabel(motionFile));
            }
            for (StagePack.VmdFileInfo cameraFile : getCameraFiles(selectedPack)) {
                hints.add(cameraFile.name);
                hints.add(wb("lanes.camera_file", cameraFile.name));
                hints.add(wb("lanes.camera_file.short", cameraFile.name));
            }
            for (StagePack.AudioFileInfo audioFile : selectedPack.getAudioFiles()) {
                hints.add(audioFile.name);
                hints.add(wb("lanes.audio_file", audioFile.name));
                hints.add(wb("lanes.audio_file.short", audioFile.name));
            }
        }

        if (selectedHostMotionFileName != null) {
            hints.add(selectedHostMotionFileName);
        }

        for (StageLobbyViewModel.HostEntry entry : lobbyViewModel.getHostPanelEntries()) {
            hints.add(entry.name());
            hints.add(formatHostState(entry.state(), entry.nearby(), entry.useHostCamera()));
            hints.add(wb("state.label", formatHostState(entry.state(), entry.nearby(), entry.useHostCamera())));
            String actionLabel = hostActionLabel(entry);
            if (actionLabel != null) {
                hints.add(actionLabel);
            }
        }

        for (StageLobbyViewModel.MemberView member : lobbyViewModel.getSessionMembersView()) {
            hints.add(member.name());
            hints.add(formatGuestState(member.state(), member.useHostCamera()));
            hints.add(wb("state.label", formatGuestState(member.state(), member.useHostCamera())));
        }

        return hints;
    }
}









