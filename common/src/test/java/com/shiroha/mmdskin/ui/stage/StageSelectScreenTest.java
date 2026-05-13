/* 文件职责：验证原生舞台选择界面的状态同步与关闭语义。 */
package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.stage.client.viewmodel.StageLobbyViewModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 文件职责：验证原生舞台选择界面的状态同步与关闭语义。 */
class StageSelectScreenTest {
    @Test
    void shouldRestorePackSelectionAndDropMissingHostMotion() {
        FakeLobbyAccess lobbyAccess = new FakeLobbyAccess();
        FakeConfigAccess configAccess = new FakeConfigAccess();
        configAccess.preferences = new StageWorkbenchFacade.WorkbenchPreferences("beta", true, 0.75f, 0.55f);
        StageWorkbenchFacade facade = new StageWorkbenchFacade(
                lobbyAccess,
                () -> List.of(createPack("alpha", List.of("dance.vmd")), createPack("beta", List.of("solo.vmd"))),
                (pack, cinematicMode, cameraHeightOffset, selectedMotionFileName) -> true,
                new FakeSelectionLifecycle(),
                configAccess
        );
        StageSelectScreen screen = new StageSelectScreen(facade);

        assertEquals("beta", screen.debugState().selectedPack().getName());
        assertEquals(0.55f, screen.debugState().audioVolume());
        screen.debugState().toggleSelectedHostMotion("ghost.vmd");

        screen.debugState().replaceStagePacks(List.of(createPack("alpha", List.of("dance.vmd"))), "alpha");

        assertEquals("alpha", screen.debugState().selectedPack().getName());
        assertNull(screen.debugState().selectedHostMotionFileName());
    }

    @Test
    void shouldSyncGuestPackPreferencesWhenPackChanges() {
        FakeLobbyAccess lobbyAccess = new FakeLobbyAccess();
        lobbyAccess.sessionMember = true;
        StageWorkbenchFacade facade = new StageWorkbenchFacade(
                lobbyAccess,
                () -> List.of(createPack("alpha", List.of("a.vmd", "b.vmd")), createPack("beta", List.of("solo.vmd"))),
                (pack, cinematicMode, cameraHeightOffset, selectedMotionFileName) -> true,
                new FakeSelectionLifecycle(),
                new FakeConfigAccess()
        );
        StageSelectScreen screen = new StageSelectScreen(facade);

        screen.debugState().selectPack(1);
        screen.syncGuestPackPreferences();

        assertEquals("beta", lobbyAccess.lastCustomMotionPack);
        assertEquals(List.of("solo.vmd"), lobbyAccess.lastRetainedFiles);
    }

    @Test
    void shouldCloseSelectionLifecycleOnlyOnceAndPreserveStartedClose() {
        FakeLobbyAccess lobbyAccess = new FakeLobbyAccess();
        FakeSelectionLifecycle lifecycle = new FakeSelectionLifecycle();
        StageWorkbenchFacade facade = new StageWorkbenchFacade(
                lobbyAccess,
                List::of,
                (pack, cinematicMode, cameraHeightOffset, selectedMotionFileName) -> true,
                lifecycle,
                new FakeConfigAccess()
        );
        StageSelectScreen screen = new StageSelectScreen(facade);

        screen.init();
        screen.markStartedByHost();
        screen.onClose();
        screen.removed();

        assertEquals(1, lifecycle.openCount);
        assertEquals(1, lifecycle.closeCount);
        assertTrue(lifecycle.lastStageStarted);
    }

    private static StagePack createPack(String name, List<String> motions) {
        List<StagePack.VmdFileInfo> files = new ArrayList<>();
        for (String motion : motions) {
            files.add(new StagePack.VmdFileInfo(motion, name + "/" + motion, false, true, false));
        }
        return new StagePack(name, name, files, List.of());
    }

    private static final class FakeLobbyAccess implements StageWorkbenchFacade.LobbyAccess {
        private boolean sessionMember;
        private boolean useHostCamera;
        private boolean localReady;
        private boolean localCustomMotionEnabled;
        private String lastCustomMotionPack;
        private List<String> lastRetainedFiles = List.of();

        @Override
        public boolean hasPendingInvite() {
            return false;
        }

        @Override
        public boolean isSessionMember() {
            return sessionMember;
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
            return List.of();
        }

        @Override
        public List<StageLobbyViewModel.HostEntry> getHostPanelEntries() {
            return List.of();
        }

        @Override
        public Set<UUID> getAcceptedMembers() {
            return Set.of();
        }

        @Override
        public boolean allMembersReady() {
            return false;
        }

        @Override
        public void sendInvite(UUID targetUUID) {
        }

        @Override
        public void cancelInvite(UUID targetUUID) {
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
            this.lastRetainedFiles = List.copyOf(availableMotionFiles);
        }

        @Override
        public void setLocalCustomMotionPack(String packName) {
            this.lastCustomMotionPack = packName;
        }
    }

    private static final class FakeSelectionLifecycle implements StageWorkbenchFacade.SelectionLifecycle {
        private int openCount;
        private int closeCount;
        private boolean lastStageStarted;

        @Override
        public void onOpened() {
            openCount++;
        }

        @Override
        public void onClosed(boolean stageStarted) {
            closeCount++;
            lastStageStarted = stageStarted;
        }
    }

    private static final class FakeConfigAccess implements StageWorkbenchFacade.ConfigAccess {
        private StageWorkbenchFacade.WorkbenchPreferences preferences =
                new StageWorkbenchFacade.WorkbenchPreferences("", false, 0.0f, 1.0f, true, true);

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
