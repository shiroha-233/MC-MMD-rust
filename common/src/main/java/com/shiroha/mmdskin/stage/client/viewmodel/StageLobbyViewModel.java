package com.shiroha.mmdskin.stage.client.viewmodel;

import com.shiroha.mmdskin.stage.application.StageSessionService;
import com.shiroha.mmdskin.stage.client.StageClientContext;
import com.shiroha.mmdskin.stage.client.playback.StageLocalPlaybackPreferences;
import com.shiroha.mmdskin.stage.domain.model.StageMember;
import com.shiroha.mmdskin.stage.domain.model.StageMemberState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class StageLobbyViewModel {
    private static final double NEARBY_RANGE = 15.0;
    private static final StageLobbyViewModel INSTANCE = new StageLobbyViewModel();

    public record MemberView(UUID uuid, String name, StageMemberState state,
                             boolean local, boolean host, boolean useHostCamera) {
    }

    public record HostEntry(UUID uuid, String name, StageMemberState state,
                            boolean nearby, boolean useHostCamera) {
    }

    private final StageSessionService sessionService = StageSessionService.getInstance();
    private final StageLocalPlaybackPreferences localPlaybackPreferences = StageLocalPlaybackPreferences.getInstance();

    private StageLobbyViewModel() {
    }

    public static StageLobbyViewModel getInstance() {
        return INSTANCE;
    }

    public boolean isSessionMember() {
        return sessionService.isSessionMember();
    }

    public boolean isSessionHost() {
        return sessionService.isSessionHost();
    }

    public boolean hasPendingInvite() {
        return sessionService.hasPendingInvite();
    }

    public boolean isUseHostCamera() {
        return sessionService.isUseHostCamera();
    }

    public boolean isLocalReady() {
        return sessionService.isLocalReady();
    }

    public boolean isLocalCustomMotionEnabled() {
        return localPlaybackPreferences.isCustomMotionEnabled();
    }

    public List<String> getLocalCustomMotionFiles() {
        return localPlaybackPreferences.getSelectedMotionFiles();
    }

    public boolean isLocalCustomMotionSelected(String fileName) {
        return localPlaybackPreferences.isSelected(fileName);
    }

    public UUID getSessionId() {
        return sessionService.getSessionId();
    }

    public UUID getWatchingHostUUID() {
        return sessionService.getHostPlayerId();
    }

    public boolean isWatchingStage() {
        return sessionService.isWatchingStage();
    }

    public List<MemberView> getSessionMembersView() {
        List<MemberView> result = new ArrayList<>();
        for (StageMember member : sessionService.getMembers()) {
            result.add(new MemberView(
                    member.uuid(),
                    member.name(),
                    member.state(),
                    member.local(),
                    member.state() == StageMemberState.HOST,
                    member.cameraMode().usesHostCamera()
            ));
        }
        return result;
    }

    public List<HostEntry> getHostPanelEntries() {
        List<StageClientContext.NearbyPlayer> nearbyPlayers = StageClientContext.getNearbyPlayers(NEARBY_RANGE);
        LinkedHashMap<UUID, HostEntry> result = new LinkedHashMap<>();
        Set<UUID> nearbyIds = nearbyPlayers.stream().map(StageClientContext.NearbyPlayer::uuid)
                .collect(java.util.stream.Collectors.toSet());

        for (StageMember member : sessionService.getMembers()) {
            if (member.local()) {
                continue;
            }
            result.put(member.uuid(), new HostEntry(
                    member.uuid(),
                    member.name(),
                    member.state(),
                    nearbyIds.contains(member.uuid()),
                    member.cameraMode().usesHostCamera()
            ));
        }

        for (StageClientContext.NearbyPlayer nearbyPlayer : nearbyPlayers) {
            result.putIfAbsent(nearbyPlayer.uuid(), new HostEntry(
                    nearbyPlayer.uuid(),
                    nearbyPlayer.name(),
                    null,
                    true,
                    false
            ));
        }

        return new ArrayList<>(result.values());
    }

    public Set<UUID> getAcceptedMembers() {
        return sessionService.getAcceptedMembers();
    }

    public boolean allMembersReady() {
        return sessionService.allMembersReady();
    }

    public void sendInvite(UUID targetUUID) {
        sessionService.sendInvite(targetUUID);
    }

    public void cancelInvite(UUID targetUUID) {
        sessionService.cancelInvite(targetUUID);
    }

    public void acceptInvite() {
        sessionService.acceptInvite();
    }

    public void declineInvite() {
        sessionService.declineInvite();
    }

    public void setUseHostCamera(boolean useHostCamera) {
        sessionService.setUseHostCamera(useHostCamera);
    }

    public void setLocalReady(boolean ready) {
        sessionService.setLocalReady(ready);
    }

    public void setLocalCustomMotionEnabled(boolean enabled) {
        localPlaybackPreferences.setCustomMotionEnabled(enabled);
        sessionService.syncLocalPlaybackPreferences();
    }

    public void toggleLocalCustomMotion(String fileName) {
        localPlaybackPreferences.toggleMotionFile(fileName);
        sessionService.syncLocalPlaybackPreferences();
    }

    public void retainLocalCustomMotionFiles(List<String> availableMotionFiles) {
        localPlaybackPreferences.retainAvailableMotionFiles(availableMotionFiles);
        sessionService.syncLocalPlaybackPreferences();
    }

    public void setLocalCustomMotionPack(String packName) {
        localPlaybackPreferences.setSelectedPackName(packName);
        sessionService.syncLocalPlaybackPreferences();
    }

    public void stopWatching() {
        sessionService.stopWatching();
    }

    public void closeHostedSession() {
        sessionService.closeHostedSession();
    }

    public void onDisconnect() {
        sessionService.onDisconnect();
    }
}
