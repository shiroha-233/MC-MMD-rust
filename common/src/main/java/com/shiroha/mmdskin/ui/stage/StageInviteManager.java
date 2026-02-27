package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.ui.network.StageNetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 多人舞台邀请管理器（客户端单例）
 * 管理邀请状态、成员列表、观看模式
 */
public final class StageInviteManager {

    private static final StageInviteManager INSTANCE = new StageInviteManager();
    private static final double NEARBY_RANGE = 15.0;

    public enum MemberState { NONE, PENDING, ACCEPTED, DECLINED }

    private final ConcurrentHashMap<UUID, MemberState> memberStates = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<AbstractClientPlayer> nearbyPlayers = new CopyOnWriteArrayList<>();

    private volatile UUID pendingInviteHost = null;
    private volatile boolean watchingStage = false;
    private volatile UUID watchingHostUUID = null;
    private volatile String watchingStageData = null;

    private StageInviteManager() {}

    public static StageInviteManager getInstance() { return INSTANCE; }

    public MemberState getMemberState(UUID uuid) {
        return memberStates.getOrDefault(uuid, MemberState.NONE);
    }

    public List<AbstractClientPlayer> getNearbyPlayers() {
        refreshNearbyPlayers();
        return Collections.unmodifiableList(nearbyPlayers);
    }

    public void sendInvite(UUID targetUUID) {
        memberStates.put(targetUUID, MemberState.PENDING);
        StageNetworkHandler.sendStageInvite(targetUUID);
    }

    public void onInviteReceived(UUID hostUUID) {
        this.pendingInviteHost = hostUUID;
        StageInvitePopup.show(hostUUID);
    }

    public void onMemberAccepted(UUID memberUUID) {
        memberStates.put(memberUUID, MemberState.ACCEPTED);
    }

    public void onMemberDeclined(UUID memberUUID) {
        memberStates.put(memberUUID, MemberState.DECLINED);
    }

    public boolean hasPendingInvite() {
        return pendingInviteHost != null;
    }

    public void acceptInvite() {
        UUID host = pendingInviteHost;
        if (host == null) return;
        pendingInviteHost = null;
        this.watchingHostUUID = host;
        StageNetworkHandler.sendInviteResponse(host, true);
    }

    public void declineInvite() {
        UUID host = pendingInviteHost;
        if (host == null) return;
        pendingInviteHost = null;
        StageNetworkHandler.sendInviteResponse(host, false);
    }

    public boolean isWatchingStage() { return watchingStage; }

    public UUID getWatchingHostUUID() { return watchingHostUUID; }

    public void onWatchStageStart(UUID hostUUID, String stageData) {
        this.watchingStage = true;
        this.watchingHostUUID = hostUUID;
        this.watchingStageData = stageData;
    }

    public void onWatchStageEnd(UUID hostUUID) {
        if (watchingHostUUID != null && watchingHostUUID.equals(hostUUID)) {
            stopWatching();
        }
    }

    public void stopWatching() {
        this.watchingStage = false;
        this.watchingHostUUID = null;
        this.watchingStageData = null;
    }

    public Set<UUID> getAcceptedMembers() {
        Set<UUID> accepted = new HashSet<>();
        memberStates.forEach((uuid, state) -> {
            if (state == MemberState.ACCEPTED) accepted.add(uuid);
        });
        return Collections.unmodifiableSet(accepted);
    }

    public void notifyMembersStageEnd() {
        for (UUID memberUUID : getAcceptedMembers()) {
            StageNetworkHandler.sendStageWatchEnd(memberUUID);
        }
    }

    public void resetHostState() {
        memberStates.clear();
    }

    public void onDisconnect() {
        memberStates.clear();
        nearbyPlayers.clear();
        pendingInviteHost = null;
        stopWatching();
    }

    private void refreshNearbyPlayers() {
        nearbyPlayers.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        UUID selfUUID = mc.player.getUUID();
        for (Player p : mc.level.players()) {
            if (p.getUUID().equals(selfUUID)) continue;
            if (mc.player.distanceTo(p) <= NEARBY_RANGE && p instanceof AbstractClientPlayer acp) {
                nearbyPlayers.add(acp);
            }
        }
    }
}
