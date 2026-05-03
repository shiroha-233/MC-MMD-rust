package com.shiroha.mmdskin.stage.client.playback;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationPort;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.config.StageConfig;
import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import com.shiroha.mmdskin.stage.client.playback.port.StageLocalModelBindingPort;
import com.shiroha.mmdskin.stage.client.playback.port.StagePlaybackRuntimePort;
import com.shiroha.mmdskin.stage.domain.model.StageDescriptor;
import com.shiroha.mmdskin.voice.runtime.VoicePlaybackManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** 文件职责：执行舞台播放运行时逻辑并驱动相机控制器。 */
public final class DefaultStagePlaybackRuntime implements StagePlaybackRuntimePort {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final float VMD_FPS = 30.0f;

    private final StageLocalModelBindingPort localModelBindingPort;
    private final NativeAnimationPort animationPort;
    private final MMDCameraController cameraController;

    public DefaultStagePlaybackRuntime(StageLocalModelBindingPort localModelBindingPort,
                                       NativeAnimationPort animationPort,
                                       MMDCameraController cameraController) {
        this.localModelBindingPort = Objects.requireNonNull(localModelBindingPort, "localModelBindingPort");
        this.animationPort = Objects.requireNonNull(animationPort, "animationPort");
        this.cameraController = Objects.requireNonNull(cameraController, "cameraController");
    }

    @Override
    public void enterStageSelection(boolean waitingForHost) {
        cameraController.enterStageMode();
        cameraController.setWaitingForHost(waitingForHost);
    }

    @Override
    public void setWaitingForHost(boolean waitingForHost) {
        cameraController.setWaitingForHost(waitingForHost);
    }

    @Override
    public void exitStageSelection() {
        boolean wasInStageMode = cameraController.isInStageMode();
        cameraController.setWaitingForHost(false);
        if (cameraController.isInStageMode()) {
            cameraController.exitStageMode();
            if (wasInStageMode) {
                VoicePlaybackManager.getInstance().onLocalPlayerStageEnd(resolveCurrentStageModelName());
            }
        }
    }

    @Override
    public void stopActivePlaybackForRemoteEnd() {
        boolean wasWatching = cameraController.isWatching();
        boolean wasInStageMode = cameraController.isInStageMode();
        cameraController.setWaitingForHost(false);
        if (cameraController.isWatching()) {
            cameraController.exitWatchMode(false);
        } else if (cameraController.isInStageMode()) {
            cameraController.exitStageMode();
        }
        if (wasWatching || wasInStageMode) {
            VoicePlaybackManager.getInstance().onLocalPlayerStageEnd(resolveCurrentStageModelName());
        }
    }

    @Override
    public void applyFrameSync(float frame) {
        cameraController.onFrameSync(frame);
    }

    @Override
    public void applyInitialFrameSync(float frame) {
        cameraController.onFrameSync(frame);
        cameraController.syncAudioPosition(frame / VMD_FPS);
    }

    @Override
    public HostStartResult startHostPlayback(StagePack pack, boolean cinematicMode,
                                             float cameraHeightOffset, String selectedMotionFileName) {
        if (pack == null || !pack.hasMotionVmd()) {
            return HostStartResult.failed();
        }

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

        long mergedAnim = animationPort.loadAnimation(0, motionFiles.get(0).path);
        if (mergedAnim == 0) {
            LOGGER.error("[舞台模式] 动作 VMD 加载失败: {}", motionFiles.get(0).path);
            return HostStartResult.failed();
        }

        List<Long> tempHandles = new ArrayList<>();
        for (int i = 1; i < motionFiles.size(); i++) {
            long tempAnim = animationPort.loadAnimation(0, motionFiles.get(i).path);
            if (tempAnim != 0) {
                animationPort.mergeAnimation(mergedAnim, tempAnim);
                tempHandles.add(tempAnim);
            }
        }
        for (long handle : tempHandles) {
            animationPort.deleteAnimation(handle);
        }

        long cameraAnim = 0;
        if (cameraFile != null) {
            cameraAnim = animationPort.loadAnimation(0, cameraFile.path);
        }

        StageDescriptor sessionDescriptor = buildSessionDescriptor(pack, motionFiles, cameraFile);
        if (sessionDescriptor == null || !sessionDescriptor.isValid()) {
            cleanupHandles(mergedAnim, cameraAnim);
            LOGGER.warn("[舞台模式] 舞台描述构建失败");
            return HostStartResult.failed();
        }

        StageLocalModelBindingPort.StageLocalModelBinding localModelBinding = localModelBindingPort.bindLocalModel(mergedAnim);
        boolean started = cameraController.startStage(
                mergedAnim,
                cameraAnim,
                cinematicMode,
                localModelBinding.modelHandle(),
                localModelBinding.modelName(),
                pack.getFirstAudioPath(),
                cameraHeightOffset
        );
        if (!started) {
            cleanupHandles(mergedAnim, cameraAnim);
            LOGGER.warn("[舞台模式] 相机控制器启动失败，已释放动画句柄");
            return HostStartResult.failed();
        }
        VoicePlaybackManager.getInstance().onLocalPlayerStageStart(localModelBinding.modelName());

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
            LOGGER.warn("[多人舞台] 本地缺少舞台包 {}", effectiveDescriptor.getPackName());
            return GuestStartResult.failed();
        }

        String motionPackName = request.motionPackName();
        File motionStageDir = resolveMotionStageDir(hostStageDir, motionPackName);
        if (motionStageDir == null) {
            return GuestStartResult.failed();
        }

        long mergedAnim = 0;
        long cameraAnim = 0;

        try {
            for (String motionFile : effectiveDescriptor.getMotionFiles()) {
                String filePath = new File(motionStageDir, motionFile).getAbsolutePath();
                long tempAnim = animationPort.loadAnimation(0, filePath);
                if (tempAnim == 0) {
                    continue;
                }

                boolean hasMotion = animationPort.hasBoneData(tempAnim) || animationPort.hasMorphData(tempAnim);
                if (hasMotion) {
                    if (mergedAnim == 0) {
                        mergedAnim = tempAnim;
                    } else {
                        animationPort.mergeAnimation(mergedAnim, tempAnim);
                        animationPort.deleteAnimation(tempAnim);
                    }
                } else {
                    animationPort.deleteAnimation(tempAnim);
                }
            }

            if (effectiveDescriptor.getCameraFile() != null && !effectiveDescriptor.getCameraFile().isEmpty()) {
                cameraAnim = animationPort.loadAnimation(
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

            if (useHostCamera) {
                cameraController.enterWatchMode(hostUUID);
                if (!cameraController.isWatching()) {
                    cleanupHandles(mergedAnim, cameraAnim);
                    LOGGER.warn("[多人舞台] 进入观演模式失败");
                    return GuestStartResult.failed();
                }
                if (cameraAnim != 0) {
                    cameraController.setWatchCamera(cameraAnim, effectiveHeight);
                }
                cameraController.setWatchMotion(mergedAnim, localModelBinding.modelHandle(), localModelBinding.modelName());
                if (audioPath != null && !audioPath.isEmpty()) {
                    cameraController.loadWatchAudio(audioPath);
                }
                VoicePlaybackManager.getInstance().onLocalPlayerStageStart(localModelBinding.modelName());
                return GuestStartResult.success(
                        effectiveDescriptor,
                        buildRemoteStageDescriptor(effectiveDescriptor, motionPackName)
                );
            }

            boolean started = cameraController.startStage(
                    mergedAnim != 0 ? mergedAnim : cameraAnim,
                    cameraAnim,
                    StageConfig.getInstance().cinematicMode,
                    localModelBinding.modelHandle(),
                    localModelBinding.modelName(),
                    audioPath,
                    effectiveHeight
            );
            if (!started) {
                cleanupHandles(mergedAnim, cameraAnim);
                LOGGER.warn("[多人舞台] 启动播放失败");
                return GuestStartResult.failed();
            }
            VoicePlaybackManager.getInstance().onLocalPlayerStageStart(localModelBinding.modelName());

            return GuestStartResult.success(
                    effectiveDescriptor,
                    buildRemoteStageDescriptor(effectiveDescriptor, motionPackName)
            );
        } catch (Exception e) {
            LOGGER.error("[多人舞台] 启动来宾播放失败", e);
            cleanupHandles(mergedAnim, cameraAnim);
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

    private void cleanupHandles(long mergedAnim, long cameraAnim) {
        if (mergedAnim != 0) {
            animationPort.deleteAnimation(mergedAnim);
        }
        if (cameraAnim != 0 && cameraAnim != mergedAnim) {
            animationPort.deleteAnimation(cameraAnim);
        }
    }

    private String resolveCurrentStageModelName() {
        String modelName = cameraController.getActiveStageModelName();
        return modelName == null || modelName.isBlank() ? null : modelName;
    }
}
