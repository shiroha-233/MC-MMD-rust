package com.shiroha.mmdskin.ui.network;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.renderer.camera.MMDCameraController;
import com.shiroha.mmdskin.renderer.render.StageAnimSyncHelper;
import com.shiroha.mmdskin.ui.stage.StageInviteManager;
import com.shiroha.mmdskin.ui.stage.StageSelectScreen;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.UUID;

/**
 * 多人舞台消息客户端处理（opCode 11）
 */
public final class StageMultiHandler {
    private static final Logger logger = LogManager.getLogger();

    private StageMultiHandler() {}

    public static void handle(UUID senderUUID, String data) {
        if (data == null || data.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID selfUUID = mc.player.getUUID();

        String[] parts = data.split("\\|", 3);
        if (parts.length < 2) return;

        String action = parts[0];

        if ("SYNC_FRAME".equals(action)) {
            handleFrameSync(senderUUID, parts[1]);
            return;
        }

        UUID targetUUID;
        try {
            targetUUID = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            return;
        }

        if (!selfUUID.equals(targetUUID)) return;

        StageInviteManager mgr = StageInviteManager.getInstance();

        switch (action) {
            case "INVITE":
                mgr.onInviteReceived(senderUUID);
                break;
            case "ACCEPT":
                mgr.onMemberAccepted(senderUUID);
                break;
            case "DECLINE":
                mgr.onMemberDeclined(senderUUID);
                break;
            case "READY":
                mgr.onMemberReady(senderUUID);
                break;
            case "LEAVE":
                mgr.onMemberLeft(senderUUID);
                break;
            case "WATCH_START":
                if (parts.length >= 3) handleWatchStart(senderUUID, parts[2]);
                break;
            case "WATCH_END":
                // exitStageMode 内部会根据 watchingStage 判断身份并清理
                MMDCameraController.getInstance().exitStageMode();
                mgr.onWatchStageEnd(senderUUID);
                break;
        }
    }

    private static void handleFrameSync(UUID hostUUID, String frameStr) {
        StageInviteManager mgr = StageInviteManager.getInstance();
        if (!mgr.isWatchingStage()) return;
        UUID watchingHost = mgr.getWatchingHostUUID();
        if (watchingHost == null || !watchingHost.equals(hostUUID)) return;

        try {
            float hostFrame = Float.parseFloat(frameStr);
            MMDCameraController.getInstance().onFrameSync(hostFrame);
            StageAnimSyncHelper.syncAllRemoteStageFrame(hostFrame);
            StageAnimSyncHelper.syncLocalStageFrame(hostFrame);
            float seconds = hostFrame / 30.0f;
            com.shiroha.mmdskin.renderer.camera.StageAudioPlayer.syncRemoteAudioPosition(hostUUID, seconds);
        } catch (NumberFormatException e) {
            logger.warn("[帧同步] 无效帧号: {}", frameStr);
        }
    }

    private static void handleWatchStart(UUID hostUUID, String stageData) {
        Minecraft mc = Minecraft.getInstance();
        StageInviteManager mgr = StageInviteManager.getInstance();

        float startFrame = 0.0f;
        float hostHeightOffset = 0.0f;
        String cleanStageData = stageData;
        
        // 解析 FRAME 参数
        int frameIdx = cleanStageData.lastIndexOf("|FRAME:");
        if (frameIdx >= 0) {
            try {
                startFrame = Float.parseFloat(cleanStageData.substring(frameIdx + 7));
            } catch (NumberFormatException ignored) {}
            cleanStageData = cleanStageData.substring(0, frameIdx);
        }
        
        // 解析房主相机高度偏移
        int heightIdx = cleanStageData.lastIndexOf("|HEIGHT:");
        if (heightIdx >= 0) {
            try {
                hostHeightOffset = Float.parseFloat(cleanStageData.substring(heightIdx + 8));
            } catch (NumberFormatException ignored) {}
            cleanStageData = cleanStageData.substring(0, heightIdx);
        }

        MMDCameraController controller = MMDCameraController.getInstance();

        if (controller.isWaitingForHost() && mc.screen instanceof StageSelectScreen) {
            ((StageSelectScreen) mc.screen).markStartedByHost();
            mc.setScreen(null);
        }

        if (!controller.isInStageMode()) {
            controller.enterStageMode();
        }

        mgr.onWatchStageStart(hostUUID, cleanStageData);
        controller.setWaitingForHost(false);

        float effectiveHeight = mgr.isUseHostCamera() ? hostHeightOffset 
            : com.shiroha.mmdskin.config.StageConfig.getInstance().cameraHeightOffset;
        
        loadAndStartAsGuest(cleanStageData, controller, mc, effectiveHeight, mgr.isUseHostCamera());

        StageNetworkHandler.sendStageStart(cleanStageData);

        if (startFrame > 0) {
            StageAnimSyncHelper.syncAllRemoteStageFrame(startFrame);
            StageAnimSyncHelper.syncLocalStageFrame(startFrame);
        }
    }

    private static void loadAndStartAsGuest(String stageData, MMDCameraController controller, 
                                              Minecraft mc, float heightOffset, boolean useHostCamera) {
        String[] parts = stageData.split("\\|");
        if (parts.length < 2) return;

        String packName = parts[0];
        if (packName.contains("..") || packName.contains("/") || packName.contains("\\")) return;
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].contains("..") || parts[i].contains("/") || parts[i].contains("\\")) return;
        }

        File stageDir = new File(PathConstants.getStageAnimDir(), packName);
        if (!stageDir.exists()) return;

        NativeFunc nf = NativeFunc.GetInst();

        long mergedAnim = 0;
        long cameraAnim = 0;

        for (int i = 1; i < parts.length; i++) {
            String filePath = new File(stageDir, parts[i]).getAbsolutePath();
            long tempAnim = nf.LoadAnimation(0, filePath);
            if (tempAnim == 0) continue;

            if (nf.HasCameraData(tempAnim) && cameraAnim == 0) {
                cameraAnim = tempAnim;
            }

            if (nf.HasBoneData(tempAnim) || nf.HasMorphData(tempAnim)) {
                if (mergedAnim == 0) {
                    mergedAnim = tempAnim;
                } else {
                    nf.MergeAnimation(mergedAnim, tempAnim);
                    if (tempAnim != cameraAnim) nf.DeleteAnimation(tempAnim);
                }
            } else if (tempAnim != cameraAnim) {
                nf.DeleteAnimation(tempAnim);
            }
        }

        if (cameraAnim == 0) {
            File[] files = stageDir.listFiles((d, name) -> name.toLowerCase().endsWith(".vmd"));
            if (files != null) {
                for (File f : files) {
                    long tempAnim = nf.LoadAnimation(0, f.getAbsolutePath());
                    if (tempAnim != 0 && nf.HasCameraData(tempAnim)) {
                        cameraAnim = tempAnim;
                        break;
                    }
                    if (tempAnim != 0) nf.DeleteAnimation(tempAnim);
                }
            }
        }

        if (cameraAnim == 0) {
            logger.warn("[被邀请者] 未找到相机 VMD");
            if (mergedAnim != 0) nf.DeleteAnimation(mergedAnim);
            return;
        }

        long modelHandle = 0;
        String modelName = null;
        if (mc.player != null && mergedAnim != 0) {
            String playerName = mc.player.getName().getString();
            modelName = com.shiroha.mmdskin.ui.config.ModelSelectorConfig.getInstance().getSelectedModel();
            if (modelName != null && !modelName.isEmpty()) {
                com.shiroha.mmdskin.renderer.model.MMDModelManager.Model modelData =
                        com.shiroha.mmdskin.renderer.model.MMDModelManager.GetModel(modelName, playerName);
                if (modelData != null) {
                    modelHandle = modelData.model.getModelHandle();
                    nf.TransitionLayerTo(modelHandle, 0, mergedAnim, 0.3f);
                    modelData.entityData.playCustomAnim = true;
                    modelData.entityData.playStageAnim = true;
                }
            }
        }

        String audioPath = findAudioInPack(stageDir);
        StageInviteManager mgr = StageInviteManager.getInstance();

        if (useHostCamera) {
            controller.enterWatchMode(mgr.getWatchingHostUUID());
            controller.setWatchCamera(cameraAnim, heightOffset);
            controller.setWatchMotion(mergedAnim, modelHandle, modelName);

            if (audioPath != null && !audioPath.isEmpty()) {
                controller.loadWatchAudio(audioPath);
            }
        } else {
            boolean started = controller.startStage(
                    mergedAnim != 0 ? mergedAnim : cameraAnim,
                    cameraAnim,
                    com.shiroha.mmdskin.config.StageConfig.getInstance().cinematicMode,
                    modelHandle, modelName, audioPath, heightOffset);

            if (!started) {
                if (mergedAnim != 0) nf.DeleteAnimation(mergedAnim);
                if (cameraAnim != 0 && cameraAnim != mergedAnim) nf.DeleteAnimation(cameraAnim);
                logger.warn("[被邀请者] startStage 失败");
            }
        }
    }

    private static String findAudioInPack(File stageDir) {
        File[] audioFiles = stageDir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".mp3") || lower.endsWith(".ogg") || lower.endsWith(".wav");
        });
        if (audioFiles != null && audioFiles.length > 0) {
            return audioFiles[0].getAbsolutePath();
        }
        return null;
    }
}
