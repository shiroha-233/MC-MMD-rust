package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.stage.client.viewmodel.StageLobbyViewModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageWorkbenchFacadeTest {

    @Test
    void shouldSaveAudioVolumeIntoPreferences() {
        RecordingConfigAccess configAccess = new RecordingConfigAccess();
        StageWorkbenchFacade facade = new StageWorkbenchFacade(
                new StubLobbyAccess(),
                List::of,
                (pack, cinematicMode, cameraHeightOffset, selectedMotionFileName) -> true,
                new NoopSelectionLifecycle(),
                configAccess
        );

        facade.savePreferences("demo", false, 1.25f, 0.35f);

        assertEquals("demo", configAccess.saved.lastStagePack());
        assertFalse(configAccess.saved.cinematicMode());
        assertEquals(1.25f, configAccess.saved.cameraHeightOffset());
        assertEquals(0.35f, configAccess.saved.audioVolume());
    }

    @Test
    void shouldRequireReadyMembersBeforeHostStart() {
        StagePack pack = new StagePack(
                "demo",
                "demo",
                List.of(new StagePack.VmdFileInfo("dance.vmd", "demo/dance.vmd", false, true, false)),
                List.of()
        );
        StubLobbyAccess lobby = new StubLobbyAccess();
        lobby.acceptedMembers = Set.of(UUID.randomUUID());
        lobby.allMembersReady = false;

        StageWorkbenchFacade facade = new StageWorkbenchFacade(
                lobby,
                List::of,
                (stagePack, cinematicMode, cameraHeightOffset, selectedMotionFileName) -> true,
                new NoopSelectionLifecycle(),
                new RecordingConfigAccess()
        );

        assertFalse(facade.canStartStage(pack));
        lobby.allMembersReady = true;
        assertTrue(facade.canStartStage(pack));
    }

    private static final class RecordingConfigAccess implements StageWorkbenchFacade.ConfigAccess {
        private StageWorkbenchFacade.WorkbenchPreferences saved;

        @Override
        public StageWorkbenchFacade.WorkbenchPreferences loadPreferences() {
            return new StageWorkbenchFacade.WorkbenchPreferences("", true, 0.0f, 1.0f);
        }

        @Override
        public void savePreferences(StageWorkbenchFacade.WorkbenchPreferences preferences) {
            this.saved = preferences;
        }
    }

    private static final class StubLobbyAccess implements StageWorkbenchFacade.LobbyAccess {
        private Set<UUID> acceptedMembers = Set.of();
        private boolean allMembersReady;

        @Override
        public boolean isSessionMember() {
            return false;
        }

        @Override
        public boolean isUseHostCamera() {
            return true;
        }

        @Override
        public boolean isLocalReady() {
            return false;
        }

        @Override
        public boolean isLocalCustomMotionEnabled() {
            return false;
        }

        @Override
        public boolean isLocalCustomMotionSelected(String fileName) {
            return false;
        }

        @Override
        public List<StageLobbyViewModel.MemberView> getSessionMembersView() {
            return List.of();
        }

        @Override
        public List<StageLobbyViewModel.HostEntry> getHostPanelEntries() {
            return List.of();
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
        }

        @Override
        public void cancelInvite(UUID targetUUID) {
        }

        @Override
        public void setUseHostCamera(boolean useHostCamera) {
        }

        @Override
        public void setLocalReady(boolean ready) {
        }

        @Override
        public void setLocalCustomMotionEnabled(boolean enabled) {
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

    private static final class NoopSelectionLifecycle implements StageWorkbenchFacade.SelectionLifecycle {
        @Override
        public void onOpened() {
        }

        @Override
        public void onClosed(boolean stageStarted) {
        }
    }
}
