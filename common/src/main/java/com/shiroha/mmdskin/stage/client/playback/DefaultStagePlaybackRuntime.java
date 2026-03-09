package com.shiroha.mmdskin.stage.client.playback;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.config.StageConfig;
import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import com.shiroha.mmdskin.stage.client.playback.port.StageLocalModelBindingPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackRuntimePort;
import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class DefaultStagePlaybackRuntime implements StagePlaybackRuntimePort {
    public static final DefaultStagePlaybackRuntime INSTANCE = new DefaultStagePlaybackRuntime();

    private static final Logger LOGGER = LogManager.getLogger();
    private static final float VMD_FPS = 30.0f;
    private static volatile StageLocalModelBindingPort defaultLocalModelBindingPort =
            mergedAnim -> StageLocalModelBindingPort.StageLocalModelBinding.empty();

    private volatile StageLocalModelBindingPort localModelBindingPort;

    private DefaultStagePlaybackRuntime() {
        resetCollaborators();
    }

    public synchronized void configureRuntimeCollaborators(StageLocalModelBindingPort localModelBindingPort) {
        DefaultStagePlaybackRuntime.defaultLocalModelBindingPort = java.util.Objects.requireNonNull(
                localModelBindingPort,
                "localModelBindingPort"
        );
        resetCollaborators();
    }

    synchronized void setCollaboratorsForTesting(StageLocalModelBindingPort localModelBindingPort) {
        this.localModelBindingPort = java.util.Objects.requireNonNull(localModelBindingPort, "localModelBindingPort");
    }

    synchronized void resetCollaborators() {
        this.localModelBindingPort = defaultLocalModelBindingPort;
    }

    @Override
    public void enterStageSelection(boolean waitingForHost) {
        MMDCameraController controller = MMDCameraController.getInstance();
        controller.enterStageMode();
        controller.setWaitingForHost(waitingForHost);
    }

    @Override
    public void setWaitingForHost(boolean waitingForHost) {
        MMDCameraController.getInstance().setWaitingForHost(waitingForHost);
    }

    @Override
    public void exitStageSelection() {
        MMDCameraController controller = MMDCameraController.getInstance();
        controller.setWaitingForHost(false);
        if (controller.isInStageMode()) {
            controller.exitStageMode();
        }
    }

    @Override
    public void stopActivePlaybackForRemoteEnd() {
        MMDCameraController controller = MMDCameraController.getInstance();
        controller.setWaitingForHost(false);
        if (controller.isWatching()) {
            controller.exitWatchMode(false);
        } else if (controller.isInStageMode()) {
            controller.exitStageMode();
        }
    }

    @Override
    public void applyFrameSync(float frame) {
        MMDCameraController.getInstance().onFrameSync(frame);
    }

    @Override
    public void applyInitialFrameSync(float frame) {
        MMDCameraController controller = MMDCameraController.getInstance();
        controller.onFrameSync(frame);
        controller.syncAudioPosition(frame / VMD_FPS);
    }

    @Override
    public HostStartResult startHostPlayback(StagePack pack, boolean cinematicMode,
                                             float cameraHeightOffset, String selectedMotionFileName) {
        if (pack == null || !pack.hasMotionVmd()) {
            return HostStartResult.failed();
        }

        NativeFunc nativeFunc = NativeFunc.GetInst();
        StageConfig config = StageConfig.getInstance();
        config.lastStagePack = pack.getName();
        config.cinematicMode = cinematicMode;
        config.cameraHeightOffset = cameraHeightOffset;
        config.save();

        StagePack.VmdFileInfo cameraFile = null;
        List<StagePack.VmdFileInfo> motionFiles = new ArrayList<>();
        for (StagePack.VmdFileInfo info : pack.getVmdFiles()) {
            if (info.hasCamera && cameraFile == null) {
                cameraFile = info;
            }
            if (info.hasBones || info.hasMorphs) {
                motionFiles.add(info);
            }
        }

        if (selectedMotionFileName != null && !selectedMotionFileName.isEmpty()) {
            motionFiles = motionFiles.stream()
                    .filter(info -> selectedMotionFileName.equals(info.name))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        if (motionFiles.isEmpty()) {
            LOGGER.warn("[舞台模式] 没有可用的动作 VMD");
            return HostStartResult.failed();
        }

        long mergedAnim = nativeFunc.LoadAnimation(0, motionFiles.get(0).path);
        if (mergedAnim == 0) {
            LOGGER.error("[舞台模式] 动作 VMD 加载失败: {}", motionFiles.get(0).path);
            return HostStartResult.failed();
        }

        List<Long> tempHandles = new ArrayList<>();
        for (int i = 1; i < motionFiles.size(); i++) {
            long tempAnim = nativeFunc.LoadAnimation(0, motionFiles.get(i).path);
            if (tempAnim != 0) {
                nativeFunc.MergeAnimation(mergedAnim, tempAnim);
                tempHandles.add(tempAnim);
            }
        }
        for (long handle : tempHandles) {
            nativeFunc.DeleteAnimation(handle);
        }

        long cameraAnim = 0;
        if (cameraFile != null) {
            cameraAnim = nativeFunc.LoadAnimation(0, cameraFile.path);
        }

        StageDescriptor sessionDescriptor = buildSessionDescriptor(pack, motionFiles, cameraFile);
        if (sessionDescriptor == null || !sessionDescriptor.isValid()) {
            cleanupHandles(nativeFunc, mergedAnim, cameraAnim);
            LOGGER.warn("[舞台模式] 默认舞台描述构建失败");
            return HostStartResult.failed();
        }

        StageLocalModelBindingPort.StageLocalModelBinding localModelBinding = localModelBindingPort.bindLocalModel(mergedAnim);
        boolean started = MMDCameraController.getInstance().startStage(
                mergedAnim,
                cameraAnim,
                cinematicMode,
                localModelBinding.modelHandle(),
                localModelBinding.modelName(),
                pack.getFirstAudioPath(),
                cameraHeightOffset
        );
        if (!started) {
            cleanupHandles(nativeFunc, mergedAnim, cameraAnim);
            LOGGER.warn("[舞台模式] 相机控制器启动失败，已释放动画句柄");
            return HostStartResult.failed();
        }

        StageDescriptor remoteDescriptor = buildRemoteStageDescriptor(sessionDescriptor, null);
        return HostStartResult.success(sessionDescriptor, remoteDescriptor);
    }

    @Override
    public GuestStartResult startGuestPlayback(UUID hostUUID, StagePlaybackStartRequest request, boolean useHostCamera) {
        if (request == null || request.descriptor() == null || !request.descriptor().isValid()) {
            return GuestStartResult.failed();
        }

        StageDescriptor effectiveDescriptor = request.descriptor().copy();
        File hostStageDir = new File(PathConstants.getStageAnimDir(), effectiveDescriptor.getPackName());
        if (!hostStageDir.exists() || !hostStageDir.isDirectory()) {
            LOGGER.warn("[多人舞台] 本地缺少舞台包: {}", effectiveDescriptor.getPackName());
            return GuestStartResult.failed();
        }

        String motionPackName = request.motionPackName();
        File motionStageDir = resolveMotionStageDir(hostStageDir, motionPackName);
        if (motionStageDir == null) {
            return GuestStartResult.failed();
        }

        NativeFunc nativeFunc = NativeFunc.GetInst();
        long mergedAnim = 0;
        long cameraAnim = 0;

        try {
            for (String motionFile : effectiveDescriptor.getMotionFiles()) {
                String filePath = new File(motionStageDir, motionFile).getAbsolutePath();
                long tempAnim = nativeFunc.LoadAnimation(0, filePath);
                if (tempAnim == 0) {
                    continue;
                }

                boolean hasMotion = nativeFunc.HasBoneData(tempAnim) || nativeFunc.HasMorphData(tempAnim);
                if (hasMotion) {
                    if (mergedAnim == 0) {
                        mergedAnim = tempAnim;
                    } else {
                        nativeFunc.MergeAnimation(mergedAnim, tempAnim);
                        nativeFunc.DeleteAnimation(tempAnim);
                    }
                } else {
                    nativeFunc.DeleteAnimation(tempAnim);
                }
            }

            if (effectiveDescriptor.getCameraFile() != null && !effectiveDescriptor.getCameraFile().isEmpty()) {
                cameraAnim = nativeFunc.LoadAnimation(
                        0,
                        new File(hostStageDir, effectiveDescriptor.getCameraFile()).getAbsolutePath()
                );
            }

            if (mergedAnim == 0 && cameraAnim == 0) {
                LOGGER.warn("[多人舞台] 没有可用的动作或相机数据");
                return GuestStartResult.failed();
            }

            StageLocalModelBindingPort.StageLocalModelBinding localModelBinding = mergedAnim != 0
                    ? localModelBindingPort.bindLocalModel(mergedAnim)
                    : StageLocalModelBindingPort.StageLocalModelBinding.empty();

            String audioPath = effectiveDescriptor.resolveAudioPath(hostStageDir);
            if (audioPath == null) {
                audioPath = findFirstAudio(hostStageDir);
            }

            float hostHeightOffset = request.hostHeightOffset() != null ? request.hostHeightOffset() : 0.0f;
            float effectiveHeight = useHostCamera ? hostHeightOffset : StageConfig.getInstance().cameraHeightOffset;
            MMDCameraController controller = MMDCameraController.getInstance();

            if (useHostCamera) {
                controller.enterWatchMode(hostUUID);
                if (!controller.isWatching()) {
                    cleanupHandles(nativeFunc, mergedAnim, cameraAnim);
                    LOGGER.warn("[多人舞台] 进入观演模式失败");
                    return GuestStartResult.failed();
                }
                if (cameraAnim != 0) {
                    controller.setWatchCamera(cameraAnim, effectiveHeight);
                }
                controller.setWatchMotion(mergedAnim, localModelBinding.modelHandle(), localModelBinding.modelName());
                if (audioPath != null && !audioPath.isEmpty()) {
                    controller.loadWatchAudio(audioPath);
                }
                return GuestStartResult.success(
                        effectiveDescriptor,
                        buildRemoteStageDescriptor(effectiveDescriptor, motionPackName)
                );
            }

            boolean started = controller.startStage(
                    mergedAnim != 0 ? mergedAnim : cameraAnim,
                    cameraAnim,
                    StageConfig.getInstance().cinematicMode,
                    localModelBinding.modelHandle(),
                    localModelBinding.modelName(),
                    audioPath,
                    effectiveHeight
            );
            if (!started) {
                cleanupHandles(nativeFunc, mergedAnim, cameraAnim);
                LOGGER.warn("[多人舞台] 启动播放失败");
                return GuestStartResult.failed();
            }

            return GuestStartResult.success(
                    effectiveDescriptor,
                    buildRemoteStageDescriptor(effectiveDescriptor, motionPackName)
            );
        } catch (Exception e) {
            LOGGER.error("[多人舞台] 启动来宾播放失败", e);
            cleanupHandles(nativeFunc, mergedAnim, cameraAnim);
            return GuestStartResult.failed();
        }
    }

    private StageDescriptor buildSessionDescriptor(StagePack pack, List<StagePack.VmdFileInfo> motionFiles,
                                                   StagePack.VmdFileInfo cameraFile) {
        List<String> motionNames = new ArrayList<>();
        for (StagePack.VmdFileInfo motionFile : motionFiles) {
            motionNames.add(motionFile.name);
        }

        String audioFileName = null;
        if (pack.hasAudio() && !pack.getAudioFiles().isEmpty()) {
            audioFileName = pack.getAudioFiles().get(0).name;
        }

        return new StageDescriptor(
                pack.getName(),
                motionNames,
                cameraFile != null ? cameraFile.name : null,
                audioFileName
        );
    }

    private String findFirstAudio(File stageDir) {
        File[] audioFiles = stageDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".mp3") || lower.endsWith(".ogg") || lower.endsWith(".wav");
        });
        if (audioFiles != null && audioFiles.length > 0) {
            return audioFiles[0].getAbsolutePath();
        }
        return null;
    }

    private File resolveMotionStageDir(File hostStageDir, String motionPackName) {
        if (motionPackName == null || motionPackName.equals(hostStageDir.getName())) {
            return hostStageDir;
        }
        File motionStageDir = new File(PathConstants.getStageAnimDir(), motionPackName);
        if (!motionStageDir.exists() || !motionStageDir.isDirectory()) {
            LOGGER.warn("[多人舞台] 本地缺少自选动作包: {}", motionPackName);
            return null;
        }
        return motionStageDir;
    }

    private StageDescriptor buildRemoteStageDescriptor(StageDescriptor descriptor, String motionPackName) {
        if (descriptor == null || descriptor.getMotionFiles().isEmpty()) {
            return null;
        }
        StageDescriptor remoteDescriptor = descriptor.copy();
        if (motionPackName != null && !motionPackName.isEmpty()) {
            remoteDescriptor.setPackName(motionPackName);
        }
        remoteDescriptor.setCameraFile(null);
        remoteDescriptor.setAudioFile(null);
        return remoteDescriptor.isValid() ? remoteDescriptor : null;
    }

    private void cleanupHandles(NativeFunc nativeFunc, long mergedAnim, long cameraAnim) {
        if (mergedAnim != 0) {
            nativeFunc.DeleteAnimation(mergedAnim);
        }
        if (cameraAnim != 0 && cameraAnim != mergedAnim) {
            nativeFunc.DeleteAnimation(cameraAnim);
        }
    }
}
