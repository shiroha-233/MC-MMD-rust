package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StageConfig;
import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.stage.client.StageClientRuntime;
import com.shiroha.mmdskin.stage.client.StagePlaybackCoordinator;
import com.shiroha.mmdskin.stage.client.asset.LocalStagePackRepository;
import com.shiroha.mmdskin.stage.client.playback.StageHostPlaybackService;
import com.shiroha.mmdskin.stage.client.viewmodel.StageLobbyViewModel;
import com.shiroha.mmdskin.stage.domain.model.StageMemberState;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 文件职责：为舞台工作台 UI 收口会话与开播操作。 */
public final class StageWorkbenchFacade {
    private static final StageWorkbenchFacade INSTANCE = new StageWorkbenchFacade(new StageConfigAccess());

    private final ConfigAccess configAccess;
    private final LobbyAccess lobbyAccess;
    private final PackCatalog packCatalog;
    private final StageStarter stageStarter;
    private final SelectionLifecycle selectionLifecycle;

    private StageWorkbenchFacade(ConfigAccess configAccess) {
        this(
                new RuntimeLobbyAccess(),
                () -> LocalStagePackRepository.getInstance().loadStagePacks(),
                (pack, cinematicMode, cameraHeightOffset, selectedMotionFileName) ->
                        StageClientRuntime.get().hostPlaybackService().startPack(pack, cinematicMode, cameraHeightOffset, selectedMotionFileName),
                new RuntimeSelectionLifecycle(),
                configAccess
        );
    }

    StageWorkbenchFacade(LobbyAccess lobbyAccess,
                         PackCatalog packCatalog,
                         StageStarter stageStarter,
                         SelectionLifecycle selectionLifecycle,
                         ConfigAccess configAccess) {
        this.lobbyAccess = Objects.requireNonNull(lobbyAccess, "lobbyAccess");
        this.packCatalog = Objects.requireNonNull(packCatalog, "packCatalog");
        this.stageStarter = Objects.requireNonNull(stageStarter, "stageStarter");
        this.selectionLifecycle = Objects.requireNonNull(selectionLifecycle, "selectionLifecycle");
        this.configAccess = Objects.requireNonNull(configAccess, "configAccess");
    }

    public static StageWorkbenchFacade getInstance() {
        return INSTANCE;
    }

    public WorkbenchPreferences loadPreferences() {
        return configAccess.loadPreferences();
    }

    public List<StagePack> loadStagePacks() {
        return packCatalog.loadStagePacks();
    }

    public void savePreferences(String selectedPackName, boolean cinematicMode, float cameraHeightOffset) {
        configAccess.savePreferences(new WorkbenchPreferences(
                selectedPackName == null ? "" : selectedPackName,
                cinematicMode,
                cameraHeightOffset
        ));
    }

    public void onStageSelectionOpened() {
        selectionLifecycle.onOpened();
    }

    public void onStageSelectionClosed(boolean stageStarted) {
        selectionLifecycle.onClosed(stageStarted);
    }

    public boolean hasPendingInvite() {
        return lobbyAccess.hasPendingInvite();
    }

    public boolean isSessionMember() {
        return lobbyAccess.isSessionMember();
    }

    public boolean isUseHostCamera() {
        return lobbyAccess.isUseHostCamera();
    }

    public boolean isLocalReady() {
        return lobbyAccess.isLocalReady();
    }

    public boolean isLocalCustomMotionEnabled() {
        return lobbyAccess.isLocalCustomMotionEnabled();
    }

    public boolean isLocalCustomMotionSelected(String fileName) {
        return lobbyAccess.isLocalCustomMotionSelected(fileName);
    }

    public List<StageLobbyViewModel.MemberView> getSessionMembersView() {
        return lobbyAccess.getSessionMembersView();
    }

    public List<StageLobbyViewModel.HostEntry> getHostPanelEntries() {
        return lobbyAccess.getHostPanelEntries();
    }

    public void toggleLocalCustomMotionEnabled() {
        lobbyAccess.setLocalCustomMotionEnabled(!lobbyAccess.isLocalCustomMotionEnabled());
    }

    public void toggleUseHostCamera() {
        lobbyAccess.setUseHostCamera(!lobbyAccess.isUseHostCamera());
    }

    public void toggleLocalReady() {
        lobbyAccess.setLocalReady(!lobbyAccess.isLocalReady());
    }

    public void toggleLocalCustomMotion(String fileName) {
        lobbyAccess.toggleLocalCustomMotion(fileName);
    }

    public void syncGuestPackPreferences(StagePack selectedPack, List<StagePack.VmdFileInfo> motionFiles) {
        if (!lobbyAccess.isSessionMember()) {
            return;
        }
        lobbyAccess.setLocalCustomMotionPack(selectedPack != null ? selectedPack.getName() : null);
        lobbyAccess.retainLocalCustomMotionFiles(motionFiles.stream().map(info -> info.name).toList());
    }

    public boolean canStartStage(StagePack pack) {
        if (pack == null || !pack.hasMotionVmd()) {
            return false;
        }
        Set<UUID> acceptedMembers = lobbyAccess.getAcceptedMembers();
        return acceptedMembers.isEmpty() || lobbyAccess.allMembersReady();
    }

    public boolean startStage(StagePack pack, boolean cinematicMode, float cameraHeightOffset, String selectedMotionFileName) {
        return pack != null && stageStarter.start(pack, cinematicMode, cameraHeightOffset, selectedMotionFileName);
    }

    public void inviteAllNearby() {
        for (StageLobbyViewModel.HostEntry entry : lobbyAccess.getHostPanelEntries()) {
            if (entry.nearby() && entry.state() == null) {
                lobbyAccess.sendInvite(entry.uuid());
            }
        }
    }

    public void handleHostAction(UUID targetUUID) {
        for (StageLobbyViewModel.HostEntry entry : lobbyAccess.getHostPanelEntries()) {
            if (!entry.uuid().equals(targetUUID)) {
                continue;
            }
            StageMemberState state = entry.state();
            if (state == null || state == StageMemberState.DECLINED || state == StageMemberState.BUSY) {
                if (entry.nearby()) {
                    lobbyAccess.sendInvite(entry.uuid());
                }
                return;
            }
            if (state == StageMemberState.INVITED) {
                lobbyAccess.cancelInvite(entry.uuid());
            }
            return;
        }
    }

    public void acceptInvite() {
        lobbyAccess.acceptInvite();
    }

    public void declineInvite() {
        lobbyAccess.declineInvite();
    }

    public record WorkbenchPreferences(String lastStagePack, boolean cinematicMode, float cameraHeightOffset) {
    }

    interface ConfigAccess {
        WorkbenchPreferences loadPreferences();

        void savePreferences(WorkbenchPreferences preferences);
    }

    interface LobbyAccess {
        boolean hasPendingInvite();

        boolean isSessionMember();

        boolean isUseHostCamera();

        boolean isLocalReady();

        boolean isLocalCustomMotionEnabled();

        boolean isLocalCustomMotionSelected(String fileName);

        List<StageLobbyViewModel.MemberView> getSessionMembersView();

        List<StageLobbyViewModel.HostEntry> getHostPanelEntries();

        Set<UUID> getAcceptedMembers();

        boolean allMembersReady();

        void sendInvite(UUID targetUUID);

        void cancelInvite(UUID targetUUID);

        void acceptInvite();

        void declineInvite();

        void setUseHostCamera(boolean useHostCamera);

        void setLocalReady(boolean ready);

        void setLocalCustomMotionEnabled(boolean enabled);

        void toggleLocalCustomMotion(String fileName);

        void retainLocalCustomMotionFiles(List<String> availableMotionFiles);

        void setLocalCustomMotionPack(String packName);
    }

    interface PackCatalog {
        List<StagePack> loadStagePacks();
    }

    interface StageStarter {
        boolean start(StagePack pack, boolean cinematicMode, float cameraHeightOffset, String selectedMotionFileName);
    }

    interface SelectionLifecycle {
        void onOpened();

        void onClosed(boolean stageStarted);
    }

    private static final class RuntimeLobbyAccess implements LobbyAccess {
        @Override
        public boolean hasPendingInvite() {
            return StageLobbyViewModel.getInstance().hasPendingInvite();
        }

        @Override
        public boolean isSessionMember() {
            return StageLobbyViewModel.getInstance().isSessionMember();
        }

        @Override
        public boolean isUseHostCamera() {
            return StageLobbyViewModel.getInstance().isUseHostCamera();
        }

        @Override
        public boolean isLocalReady() {
            return StageLobbyViewModel.getInstance().isLocalReady();
        }

        @Override
        public boolean isLocalCustomMotionEnabled() {
            return StageLobbyViewModel.getInstance().isLocalCustomMotionEnabled();
        }

        @Override
        public boolean isLocalCustomMotionSelected(String fileName) {
            return StageLobbyViewModel.getInstance().isLocalCustomMotionSelected(fileName);
        }

        @Override
        public List<StageLobbyViewModel.MemberView> getSessionMembersView() {
            return StageLobbyViewModel.getInstance().getSessionMembersView();
        }

        @Override
        public List<StageLobbyViewModel.HostEntry> getHostPanelEntries() {
            return StageLobbyViewModel.getInstance().getHostPanelEntries();
        }

        @Override
        public Set<UUID> getAcceptedMembers() {
            return StageLobbyViewModel.getInstance().getAcceptedMembers();
        }

        @Override
        public boolean allMembersReady() {
            return StageLobbyViewModel.getInstance().allMembersReady();
        }

        @Override
        public void sendInvite(UUID targetUUID) {
            StageLobbyViewModel.getInstance().sendInvite(targetUUID);
        }

        @Override
        public void cancelInvite(UUID targetUUID) {
            StageLobbyViewModel.getInstance().cancelInvite(targetUUID);
        }

        @Override
        public void acceptInvite() {
            StageLobbyViewModel.getInstance().acceptInvite();
        }

        @Override
        public void declineInvite() {
            StageLobbyViewModel.getInstance().declineInvite();
        }

        @Override
        public void setUseHostCamera(boolean useHostCamera) {
            StageLobbyViewModel.getInstance().setUseHostCamera(useHostCamera);
        }

        @Override
        public void setLocalReady(boolean ready) {
            StageLobbyViewModel.getInstance().setLocalReady(ready);
        }

        @Override
        public void setLocalCustomMotionEnabled(boolean enabled) {
            StageLobbyViewModel.getInstance().setLocalCustomMotionEnabled(enabled);
        }

        @Override
        public void toggleLocalCustomMotion(String fileName) {
            StageLobbyViewModel.getInstance().toggleLocalCustomMotion(fileName);
        }

        @Override
        public void retainLocalCustomMotionFiles(List<String> availableMotionFiles) {
            StageLobbyViewModel.getInstance().retainLocalCustomMotionFiles(availableMotionFiles);
        }

        @Override
        public void setLocalCustomMotionPack(String packName) {
            StageLobbyViewModel.getInstance().setLocalCustomMotionPack(packName);
        }
    }

    private static final class RuntimeSelectionLifecycle implements SelectionLifecycle {
        @Override
        public void onOpened() {
            StageClientRuntime.get().playbackCoordinator().onStageSelectionOpened();
        }

        @Override
        public void onClosed(boolean stageStarted) {
            StageClientRuntime.get().playbackCoordinator().onStageSelectionClosed(stageStarted);
        }
    }

    private static final class StageConfigAccess implements ConfigAccess {
        @Override
        public WorkbenchPreferences loadPreferences() {
            StageConfig config = StageConfig.getInstance();
            return new WorkbenchPreferences(config.lastStagePack, config.cinematicMode, config.cameraHeightOffset);
        }

        @Override
        public void savePreferences(WorkbenchPreferences preferences) {
            StageConfig config = StageConfig.getInstance();
            config.lastStagePack = preferences.lastStagePack();
            config.cinematicMode = preferences.cinematicMode();
            config.cameraHeightOffset = preferences.cameraHeightOffset();
            config.save();
        }
    }
}
