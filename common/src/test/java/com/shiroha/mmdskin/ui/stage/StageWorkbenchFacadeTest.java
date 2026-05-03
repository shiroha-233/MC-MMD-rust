package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.stage.client.viewmodel.StageLobbyViewModel;
import com.shiroha.mmdskin.stage.domain.model.StageMemberState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证舞台工作台门面的收口行为。 */
class StageWorkbenchFacadeTest {
    @Test
    void shouldSavePreferencesAndReusePackCatalog() {
        FakeLobbyAccess lobbyAccess = new FakeLobbyAccess();
        FakeConfigAccess configAccess = new FakeConfigAccess();
        List<StagePack> packs = List.of(createPack("alpha", true), createPack("beta", false));
        StageWorkbenchFacade facade = new StageWorkbenchFacade(
                lobbyAccess,
                () -> packs,
                (pack, cinematicMode, cameraHeightOffset, selectedMotionFileName) -> true,
                new FakeSelectionLifecycle(),
                configAccess
        );

        facade.savePreferences("beta", false, 1.25f);

        assertEquals("beta", facade.loadPreferences().lastStagePack());
        assertFalse(facade.loadPreferences().cinematicMode());
        assertEquals(1.25f, facade.loadPreferences().cameraHeightOffset());
        assertEquals(packs, facade.loadStagePacks());
    }

    @Test
    void shouldPreserveInviteAndReadySemantics() {
        UUID idleNearby = UUID.randomUUID();
        UUID invited = UUID.randomUUID();
        FakeLobbyAccess lobbyAccess = new FakeLobbyAccess();
        lobbyAccess.hostEntries = List.of(
                new StageLobbyViewModel.HostEntry(idleNearby, "Idle", null, true, false),
                new StageLobbyViewModel.HostEntry(invited, "Invited", StageMemberState.INVITED, true, false)
        );
        lobbyAccess.acceptedMembers = Set.of(UUID.randomUUID());
        lobbyAccess.allMembersReady = false;
        FakeStageStarter stageStarter = new FakeStageStarter();
        StageWorkbenchFacade facade = new StageWorkbenchFacade(
                lobbyAccess,
                () -> List.of(),
                stageStarter,
                new FakeSelectionLifecycle(),
                new FakeConfigAccess()
        );

        assertFalse(facade.canStartStage(createPack("alpha", true)));
        lobbyAccess.allMembersReady = true;
        assertTrue(facade.canStartStage(createPack("alpha", true)));
        assertFalse(facade.canStartStage(createPack("empty", false)));

        facade.inviteAllNearby();
        facade.handleHostAction(invited);
        assertTrue(facade.startStage(createPack("alpha", true), true, 0.5f, "dance.vmd"));

        assertEquals(List.of(idleNearby), lobbyAccess.invitedTargets);
        assertEquals(List.of(invited), lobbyAccess.cancelledTargets);
        assertEquals("alpha", stageStarter.lastPackName);
        assertTrue(stageStarter.lastCinematicMode);
        assertEquals(0.5f, stageStarter.lastCameraHeightOffset);
        assertEquals("dance.vmd", stageStarter.lastMotionFileName);
    }

    private static StagePack createPack(String name, boolean hasMotion) {
        List<StagePack.VmdFileInfo> motions = hasMotion
                ? List.of(new StagePack.VmdFileInfo("dance.vmd", name + "/dance.vmd", false, true, false))
                : List.of(new StagePack.VmdFileInfo("camera.vmd", name + "/camera.vmd", true, false, false));
        return new StagePack(name, name, motions, List.of());
    }

    private static final class FakeLobbyAccess implements StageWorkbenchFacade.LobbyAccess {
        private boolean useHostCamera;
        private boolean localReady;
        private boolean localCustomMotionEnabled;
        private boolean allMembersReady;
        private List<StageLobbyViewModel.MemberView> memberViews = List.of();
        private List<StageLobbyViewModel.HostEntry> hostEntries = List.of();
        private Set<UUID> acceptedMembers = Set.of();
        private final List<UUID> invitedTargets = new ArrayList<>();
        private final List<UUID> cancelledTargets = new ArrayList<>();

        @Override
        public boolean hasPendingInvite() {
            return false;
        }

        @Override
        public boolean isSessionMember() {
            return false;
        }

        @Override
        public boolean isUseHostCamera() {
            return useHostCamera;
        }

        @Override
        public boolean isLocalReady() {
            return localReady;
        }

        @Override
        public boolean isLocalCustomMotionEnabled() {
            return localCustomMotionEnabled;
        }

        @Override
        public boolean isLocalCustomMotionSelected(String fileName) {
            return false;
        }

        @Override
        public List<StageLobbyViewModel.MemberView> getSessionMembersView() {
            return memberViews;
        }

        @Override
        public List<StageLobbyViewModel.HostEntry> getHostPanelEntries() {
            return hostEntries;
        }

        @Override
        public Set<UUID> getAcceptedMembers() {
            return acceptedMembers;
        }

        @Override
        public boolean allMembersReady() {
            return allMembersReady;
        }

        @Override
        public void sendInvite(UUID targetUUID) {
            invitedTargets.add(targetUUID);
        }

        @Override
        public void cancelInvite(UUID targetUUID) {
            cancelledTargets.add(targetUUID);
        }

        @Override
        public void acceptInvite() {
        }

        @Override
        public void declineInvite() {
        }

        @Override
        public void setUseHostCamera(boolean useHostCamera) {
            this.useHostCamera = useHostCamera;
        }

        @Override
        public void setLocalReady(boolean ready) {
            this.localReady = ready;
        }

        @Override
        public void setLocalCustomMotionEnabled(boolean enabled) {
            this.localCustomMotionEnabled = enabled;
        }

        @Override
        public void toggleLocalCustomMotion(String fileName) {
        }

        @Override
        public void retainLocalCustomMotionFiles(List<String> availableMotionFiles) {
        }

        @Override
        public void setLocalCustomMotionPack(String packName) {
        }
    }

    private static final class FakeStageStarter implements StageWorkbenchFacade.StageStarter {
        private String lastPackName;
        private boolean lastCinematicMode;
        private float lastCameraHeightOffset;
        private String lastMotionFileName;

        @Override
        public boolean start(StagePack pack, boolean cinematicMode, float cameraHeightOffset, String selectedMotionFileName) {
            this.lastPackName = pack.getName();
            this.lastCinematicMode = cinematicMode;
            this.lastCameraHeightOffset = cameraHeightOffset;
            this.lastMotionFileName = selectedMotionFileName;
            return true;
        }
    }

    private static final class FakeSelectionLifecycle implements StageWorkbenchFacade.SelectionLifecycle {
        @Override
        public void onOpened() {
        }

        @Override
        public void onClosed(boolean stageStarted) {
        }
    }

    private static final class FakeConfigAccess implements StageWorkbenchFacade.ConfigAccess {
        private StageWorkbenchFacade.WorkbenchPreferences preferences = new StageWorkbenchFacade.WorkbenchPreferences("", true, 0.0f);

        @Override
        public StageWorkbenchFacade.WorkbenchPreferences loadPreferences() {
            return preferences;
        }

        @Override
        public void savePreferences(StageWorkbenchFacade.WorkbenchPreferences preferences) {
            this.preferences = preferences;
        }
    }
}
