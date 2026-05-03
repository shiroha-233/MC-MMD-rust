package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.stage.client.viewmodel.StageLobbyViewModel;
import com.shiroha.mmdskin.stage.domain.model.StageMemberState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证工作台拆分后的纯计算与视图构建行为。 */
class StageWorkbenchScreenSupportTest {
    @Test
    void shouldBuildHostSnapshotWithCachedRows() {
        FakeLobbyAccess lobbyAccess = new FakeLobbyAccess();
        UUID nearby = UUID.randomUUID();
        UUID ready = UUID.randomUUID();
        lobbyAccess.hostEntries = List.of(
                new StageLobbyViewModel.HostEntry(nearby, "Nearby", null, true, false),
                new StageLobbyViewModel.HostEntry(ready, "Ready", StageMemberState.READY, true, true)
        );
        StageWorkbenchFacade facade = new StageWorkbenchFacade(
                lobbyAccess,
                List::of,
                (pack, cinematicMode, cameraHeightOffset, selectedMotionFileName) -> true,
                new NoopSelectionLifecycle(),
                new InMemoryConfigAccess()
        );
        StageWorkbenchViewBuilder builder = new StageWorkbenchViewBuilder();
        StagePack pack = createPack();

        StageWorkbenchUiStateSnapshot snapshot = builder.buildSnapshot(
                facade,
                List.of(pack),
                0,
                "dance.vmd",
                0.5f,
                List.of(),
                lobbyAccess.hostEntries
        );

        assertEquals("alpha", snapshot.selectedPack().getName());
        assertEquals(1, snapshot.packRows().size());
        assertEquals(2, snapshot.motionRows().size());
        assertTrue(snapshot.motionRows().get(1).selected());
        assertEquals(2, snapshot.sessionRows().size());
        assertEquals(1, snapshot.cameraFiles().size());
    }

    @Test
    void shouldResolveHoveredRowsFromCachedSnapshot() {
        StageWorkbenchLayout layout = new StageWorkbenchLayoutCalculator().calculate(960, 540, false);
        StageWorkbenchUiStateSnapshot snapshot = new StageWorkbenchUiStateSnapshot(
                null,
                List.of(),
                List.of(),
                List.of(
                        new StageWorkbenchUiStateSnapshot.PackRow("A", true, 0),
                        new StageWorkbenchUiStateSnapshot.PackRow("B", false, 1)
                ),
                List.of(new StageWorkbenchUiStateSnapshot.MotionRow("M", "", false, 0, "m.vmd", false)),
                List.of(new StageWorkbenchUiStateSnapshot.SessionRow("S", "", "", false, false, 0, UUID.randomUUID())),
                List.of(),
                "",
                "",
                ""
        );
        StageWorkbenchInteractionHandler interactionHandler = new StageWorkbenchInteractionHandler();

        StageWorkbenchInteractionHandler.HoverState packHover = interactionHandler.resolveHover(
                layout,
                layout.packList().x() + 8,
                layout.packList().y() + StageWorkbenchLayoutCalculator.LIST_PADDING + 6,
                false,
                snapshot,
                0.0f,
                0.0f,
                0.0f
        );
        StageWorkbenchInteractionHandler.HoverState motionHover = interactionHandler.resolveHover(
                layout,
                layout.motionList().x() + 8,
                layout.motionList().y() + StageWorkbenchLayoutCalculator.LIST_PADDING + 6,
                false,
                snapshot,
                0.0f,
                0.0f,
                0.0f
        );

        assertEquals(0, packHover.packIndex());
        assertEquals(0, motionHover.motionIndex());
        assertTrue(interactionHandler.updateScrollTarget(0.0f, -1.0, 10, StageWorkbenchLayoutCalculator.PACK_ROW_HEIGHT, 24) > 0.0f);
    }

    private static StagePack createPack() {
        return new StagePack(
                "alpha",
                "alpha",
                List.of(
                        new StagePack.VmdFileInfo("camera.vmd", "alpha/camera.vmd", true, false, false),
                        new StagePack.VmdFileInfo("dance.vmd", "alpha/dance.vmd", false, true, false)
                ),
                List.of(new StagePack.AudioFileInfo("theme.mp3", "alpha/theme.mp3", "MP3"))
        );
    }

    private static final class FakeLobbyAccess implements StageWorkbenchFacade.LobbyAccess {
        private List<StageLobbyViewModel.HostEntry> hostEntries = List.of();

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
            return false;
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
            return hostEntries;
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

    private static final class InMemoryConfigAccess implements StageWorkbenchFacade.ConfigAccess {
        @Override
        public StageWorkbenchFacade.WorkbenchPreferences loadPreferences() {
            return new StageWorkbenchFacade.WorkbenchPreferences("", false, 0.0f);
        }

        @Override
        public void savePreferences(StageWorkbenchFacade.WorkbenchPreferences preferences) {
        }
    }
}
