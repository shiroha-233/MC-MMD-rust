package com.shiroha.mmdskin.ui.follow;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.renderer.camera.MMDCameraController;
import com.shiroha.mmdskin.renderer.camera.StageAudioPlayer;
import com.shiroha.mmdskin.renderer.render.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.renderer.render.PlayerModelResolver;
import com.shiroha.mmdskin.renderer.render.StageAnimSyncHelper;
import com.shiroha.mmdskin.ui.network.StageNetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.UUID;

/**
 * 跳舞跟随管理器（客户端单例）— 处理加入/退出跟随、舞台同步
 */
public final class DanceFollowManager {

    private static final Logger logger = LogManager.getLogger();
    private static final DanceFollowManager INSTANCE = new DanceFollowManager();

    private volatile boolean following = false;
    private UUID targetUUID = null;
    private boolean targetIsStage = false;
    private String followedStageData = null;

    private DanceFollowManager() {}

    public static DanceFollowManager getInstance() { return INSTANCE; }

    public boolean isFollowing() { return following; }

    public UUID getTargetUUID() { return targetUUID; }

    public void joinFollow(DanceFollowDetector.DanceTarget target) {
        if (following || target == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        AbstractClientPlayer targetPlayer = target.player();
        this.targetUUID = targetPlayer.getUUID();
        this.targetIsStage = target.isStageAnim();
        this.following = true;

        try {
            if (targetIsStage) {
                joinStageFollow(targetPlayer);
            } else {
                joinCustomAnimFollow(targetPlayer);
            }
        } catch (Exception e) {
            logger.error("[跳舞跟随] 加入失败", e);
            stopFollow();
        }
    }

    private void joinStageFollow(AbstractClientPlayer targetPlayer) {
        StageNetworkHandler.sendStageWatch(targetUUID, "FOLLOW_REQUEST");

        MMDCameraController controller = MMDCameraController.getInstance();
        if (!controller.isActive()) {
            controller.enterWatchMode(targetUUID);
        }

        logger.info("[跳舞跟随] 加入舞台跟随: {}", targetPlayer.getName().getString());
    }

    private void joinCustomAnimFollow(AbstractClientPlayer targetPlayer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String animId = RemoteAnimCache.get(targetPlayer.getUUID());
        if (animId == null || animId.isEmpty()) {
            logger.warn("[跳舞跟随] 无法获取目标动画 ID");
            stopFollow();
            return;
        }

        PlayerModelResolver.Result selfResolved = PlayerModelResolver.resolve(mc.player);
        if (selfResolved == null) {
            stopFollow();
            return;
        }

        MmdSkinRendererPlayerHelper.CustomAnim(mc.player, animId);
        StageNetworkHandler.sendStageStart("FOLLOW|" + targetUUID);
        logger.info("[跳舞跟随] 加入动画跟随: {}", targetPlayer.getName().getString());
    }

    public void onStageDataReceived(UUID hostUUID, String stageData) {
        if (!following || !targetIsStage) return;
        if (targetUUID == null || !targetUUID.equals(hostUUID)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        this.followedStageData = stageData;

        StageAnimSyncHelper.startStageAnim(mc.player, stageData);
        StageNetworkHandler.sendStageStart(stageData);

        loadFollowCamera(stageData);
        logger.info("[跳舞跟随] 收到舞台数据，已同步");
    }

    private void loadFollowCamera(String stageData) {
        String[] parts = stageData.split("\\|");
        if (parts.length < 2) return;

        String packName = parts[0];
        if (packName.contains("..") || packName.contains("/") || packName.contains("\\")) return;

        File stageDir = new File(PathConstants.getStageAnimDir(), packName);
        if (!stageDir.exists()) return;

        NativeFunc nf = NativeFunc.GetInst();
        MMDCameraController controller = MMDCameraController.getInstance();

        for (int i = 1; i < parts.length; i++) {
            String fileName = parts[i];
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) continue;

            String filePath = new File(stageDir, fileName).getAbsolutePath();
            long tempAnim = nf.LoadAnimation(0, filePath);
            if (tempAnim != 0 && nf.HasCameraData(tempAnim)) {
                controller.setWatchCamera(tempAnim, 0.0f);
                return;
            }
            if (tempAnim != 0) nf.DeleteAnimation(tempAnim);
        }
    }

    public void stopFollow() {
        if (!following) return;

        Minecraft mc = Minecraft.getInstance();

        try {
            if (targetIsStage) {
                MMDCameraController controller = MMDCameraController.getInstance();
                if (controller.isWatching()) {
                    controller.exitWatchMode();
                }
                if (mc.player != null) {
                    StageAnimSyncHelper.endStageAnim(mc.player);
                }
                if (targetUUID != null) {
                    StageAudioPlayer.stopRemoteAudio(targetUUID);
                }
            } else {
                if (mc.player != null) {
                    MmdSkinRendererPlayerHelper.ResetPhysics(mc.player);
                }
            }

            StageNetworkHandler.sendStageEnd();
        } catch (Exception e) {
            logger.error("[跳舞跟随] 退出清理异常", e);
        } finally {
            resetState();
        }
    }

    public void tick() {
        if (!following || targetUUID == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            stopFollow();
            return;
        }

        Player target = mc.level.getPlayerByUUID(targetUUID);
        if (target == null) {
            stopFollow();
            return;
        }

        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(target);
        if (resolved == null) {
            stopFollow();
            return;
        }

        boolean stillDancing = targetIsStage
                ? resolved.model().entityData.playStageAnim
                : resolved.model().entityData.playCustomAnim;

        if (!stillDancing) {
            stopFollow();
        }
    }

    public void onDisconnect() {
        resetState();
    }

    private void resetState() {
        this.following = false;
        this.targetUUID = null;
        this.targetIsStage = false;
        this.followedStageData = null;
        DanceFollowDetector.reset();
    }
}
