package com.shiroha.mmdskin.renderer.camera;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
import com.shiroha.mmdskin.renderer.render.PlayerModelResolver;
import com.shiroha.mmdskin.renderer.render.StageAnimSyncHelper;
import com.shiroha.mmdskin.ui.network.StageNetworkHandler;
import com.shiroha.mmdskin.ui.stage.StageSelectScreen;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

/** MMD 舞台模式相机控制器（单例） */
public class MMDCameraController {
    private static final Logger logger = LogManager.getLogger();

    private static final MMDCameraController INSTANCE = new MMDCameraController();

    private static final float MMD_TO_MC_SCALE = 0.09f;
    private static final float VMD_FPS = 30.0f;

    private enum StageState { INACTIVE, INTRO, STANDBY, PLAYING, OUTRO, WATCHING }
    private StageState state = StageState.INACTIVE;

    private CameraType savedCameraType = null;
    private String modelName = null;
    private boolean cinematicMode = false;
    private boolean previousHideGui = false;
    private float cameraHeightOffset = 0.0f;

    private float currentFrame = 0.0f;
    private float maxFrame = 0.0f;
    private float playbackSpeed = 1.0f;

    private long cameraAnimHandle = 0;
    private long motionAnimHandle = 0;
    private long modelHandle = 0;

    private final StageAudioPlayer audioPlayer = new StageAudioPlayer();
    private final MMDCameraData cameraData = new MMDCameraData();

    private double anchorX, anchorY, anchorZ;
    private float anchorYaw;

    private double cameraX, cameraY, cameraZ;
    private float cameraPitch, cameraYaw, cameraRoll;
    private float cameraFov = 70.0f;

    private long lastTickTimeNs = 0;
    private boolean mouseReleased = false;

    private boolean escWasPressed = false;
    private long lastEscTimeNs = 0;
    private static final long DOUBLE_ESC_WINDOW_NS = 600_000_000L;

    private java.util.UUID watchingHostUUID = null;
    private long watchCameraAnimHandle = 0;

    private static final int SYNC_INTERVAL_FRAMES = 60;
    private static final float CATCHUP_SPEED_MAX = 1.15f;
    private static final float CATCHUP_SPEED_MIN = 0.85f;
    private static final float SYNC_TOLERANCE = 2.0f;
    private int frameSyncCounter = 0;
    private float targetSyncFrame = -1.0f;

    private static final float INTRO_DURATION = 2.0f;
    private float introElapsed = 0.0f;
    private double introStartX, introStartY, introStartZ;
    private float introStartPitch, introStartYaw, introStartFov;

    private double standbyX, standbyY, standbyZ;
    private float standbyPitch, standbyYaw, standbyFov;

    private static final float OUTRO_DURATION = 2.5f;
    private float outroElapsed = 0.0f;
    private double outroStartX, outroStartY, outroStartZ;
    private float outroStartPitch, outroStartYaw, outroStartFov;
    private boolean outroIsGuest = false;

    private volatile boolean waitingForHost = false;
    


    private MMDCameraController() {}

    public static MMDCameraController getInstance() {
        return INSTANCE;
    }

    public void enterStageMode() {
        if (state != StageState.INACTIVE) return;

        Minecraft mc = Minecraft.getInstance();

        this.savedCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);

        if (mc.player != null) {
            this.anchorX = mc.player.getX();
            this.anchorY = mc.player.getY();
            this.anchorZ = mc.player.getZ();
            this.anchorYaw = mc.player.getYRot();
            mc.player.setXRot(0.0f);
            mc.player.setYRot(this.anchorYaw);
            mc.player.yHeadRot = this.anchorYaw;
            mc.player.yBodyRot = this.anchorYaw;
        }

        computeIntroAndStandby(mc);

        this.introElapsed = 0.0f;
        this.lastTickTimeNs = System.nanoTime();
        this.escWasPressed = false;
        this.lastEscTimeNs = 0;
        this.mouseReleased = false;
        
        com.shiroha.mmdskin.ui.stage.StageInviteManager mgr = 
            com.shiroha.mmdskin.ui.stage.StageInviteManager.getInstance();
        if (mgr.getWatchingHostUUID() == null) {
            this.waitingForHost = false;
        }
        
        this.state = StageState.INTRO;
        this.cameraFov = introStartFov;
    }


    public boolean startStage(long motionAnim, long cameraAnim, boolean cinematic,
                              long modelHandle, String modelName, String audioPath, float heightOffset) {
        if (state != StageState.STANDBY && state != StageState.INTRO) return false;

        NativeFunc nf = NativeFunc.GetInst();

        this.motionAnimHandle = motionAnim;

        // 修复：无相机数据时不再 return，改为设置 cameraAnimHandle = 0 并继续执行
        // 这样可以继续播放音频，只是相机视角保持不变
        boolean hasCameraData = false;
        if (cameraAnim != 0 && nf.HasCameraData(cameraAnim)) {
            this.cameraAnimHandle = cameraAnim;
            hasCameraData = true;
        } else if (motionAnim != 0 && nf.HasCameraData(motionAnim)) {
            this.cameraAnimHandle = motionAnim;
            hasCameraData = true;
        } else {
            // 无相机数据：记录警告但不 return，继续执行（音频仍会播放）
            logger.warn("[舞台模式] 没有可用的相机数据，将以无相机模式继续");
            this.cameraAnimHandle = 0;
        }

        // 修复：无相机数据时使用动作动画的帧数作为最大帧
        if (hasCameraData) {
            this.maxFrame = nf.GetAnimMaxFrame(this.cameraAnimHandle);
            this.cameraData.setAnimHandle(this.cameraAnimHandle);
        } else if (motionAnim != 0) {
            // 无相机但有动作时，使用动作帧数
            this.maxFrame = nf.GetAnimMaxFrame(motionAnim);
        } else {
            this.maxFrame = 0;
        }

        if (cinematic) {
            Minecraft mc = Minecraft.getInstance();
            this.previousHideGui = mc.options.hideGui;
            mc.options.hideGui = true;
        }

        this.modelHandle = modelHandle;
        if (modelHandle != 0) {
            nf.SetAutoBlinkEnabled(modelHandle, false);
            nf.SetEyeTrackingEnabled(modelHandle, false);
        }

        if (audioPath != null && !audioPath.isEmpty()) {
            if (audioPlayer.load(audioPath)) {
                audioPlayer.play();
            } else {
                logger.warn("[舞台模式] 音频加载失败: {}", audioPath);
            }
        }

        this.state = StageState.PLAYING;
        this.lastTickTimeNs = System.nanoTime();
        this.escWasPressed = false;
        this.lastEscTimeNs = 0;
        this.mouseReleased = false;
        this.frameSyncCounter = 0;
        this.targetSyncFrame = -1.0f;

        return true;
    }

    private void endPlayback() {
        restoreMouseGrab();
        audioPlayer.cleanup();

        if (cinematicMode) {
            Minecraft.getInstance().options.hideGui = previousHideGui;
        }

        NativeFunc nf = NativeFunc.GetInst();

        clearLocalPlayerStageFlags();
        restoreModelState(nf);

        if (this.motionAnimHandle != 0) {
            nf.DeleteAnimation(this.motionAnimHandle);
        }
        if (this.cameraAnimHandle != 0 && this.cameraAnimHandle != this.motionAnimHandle) {
            nf.DeleteAnimation(this.cameraAnimHandle);
        }

        this.cameraAnimHandle = 0;
        this.motionAnimHandle = 0;
        this.modelHandle = 0;
        this.modelName = null;
        this.currentFrame = 0.0f;
        this.maxFrame = 0.0f;

        this.outroStartX = cameraX;
        this.outroStartY = cameraY;
        this.outroStartZ = cameraZ;
        this.outroStartPitch = cameraPitch;
        this.outroStartYaw = cameraYaw;
        this.outroStartFov = cameraFov;

        this.outroElapsed = 0.0f;
        this.lastTickTimeNs = System.nanoTime();

        com.shiroha.mmdskin.ui.stage.StageInviteManager mgr =
                com.shiroha.mmdskin.ui.stage.StageInviteManager.getInstance();
        this.outroIsGuest = mgr.isWatchingStage();
        this.state = StageState.OUTRO;

        if (outroIsGuest) {
            StageAnimSyncHelper.endStageAnim(Minecraft.getInstance().player);
            StageNetworkHandler.sendStageEnd();
            mgr.stopWatching();
        } else {
            StageNetworkHandler.sendStageEnd();
            mgr.notifyMembersStageEnd();
        }
    }

    public void exitStageMode() {
        if (state == StageState.INACTIVE) return;

        if (state == StageState.WATCHING) {
            exitWatchMode();
            return;
        }

        boolean wasPlaying = (state == StageState.PLAYING);

        if (wasPlaying) {
            audioPlayer.cleanup();
            if (cinematicMode) {
                Minecraft.getInstance().options.hideGui = previousHideGui;
            }
            NativeFunc nf = NativeFunc.GetInst();

            clearLocalPlayerStageFlags();
            restoreModelState(nf);
            if (this.motionAnimHandle != 0) {
                nf.DeleteAnimation(this.motionAnimHandle);
            }
            if (this.cameraAnimHandle != 0 && this.cameraAnimHandle != this.motionAnimHandle) {
                nf.DeleteAnimation(this.cameraAnimHandle);
            }
        }

        restoreMouseGrab();

        Minecraft mc = Minecraft.getInstance();
        if (savedCameraType != null) {
            mc.options.setCameraType(savedCameraType);
            savedCameraType = null;
        }

        this.state = StageState.INACTIVE;
        this.waitingForHost = false;
        this.cameraAnimHandle = 0;
        this.motionAnimHandle = 0;
        this.modelHandle = 0;
        this.modelName = null;
        this.currentFrame = 0.0f;
        this.maxFrame = 0.0f;

        if (wasPlaying) {
            StageNetworkHandler.sendStageEnd();
            com.shiroha.mmdskin.ui.stage.StageInviteManager mgr =
                    com.shiroha.mmdskin.ui.stage.StageInviteManager.getInstance();
            if (mgr.isWatchingStage()) {
                if (mc.player != null) {
                    StageAnimSyncHelper.endStageAnim(mc.player);
                }
                this.waitingForHost = true;
                mc.setScreen(new com.shiroha.mmdskin.ui.stage.StageSelectScreen());
            } else {
                mgr.notifyMembersStageEnd();
                mgr.resetHostState();
            }
        }

        KeyMapping.releaseAll();
    }

    private void computeIntroAndStandby(Minecraft mc) {
        if (mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null) {
            var cam = mc.gameRenderer.getMainCamera();
            introStartX = cam.getPosition().x;
            introStartY = cam.getPosition().y;
            introStartZ = cam.getPosition().z;
            introStartPitch = cam.getXRot();
            introStartYaw = cam.getYRot();
        } else if (mc.player != null) {
            introStartX = mc.player.getX();
            introStartY = mc.player.getEyeY();
            introStartZ = mc.player.getZ();
            introStartPitch = mc.player.getXRot();
            introStartYaw = mc.player.getYRot();
        }
        introStartFov = (float) mc.options.fov().get();

        float yawRad = (float) Math.toRadians(anchorYaw);
        standbyX = anchorX - Math.sin(yawRad) * 3.5;
        standbyY = anchorY + 1.8;
        standbyZ = anchorZ + Math.cos(yawRad) * 3.5;
        standbyYaw = anchorYaw + 180.0f;
        standbyPitch = 15.0f;
        standbyFov = 70.0f;
    }

    public void updateCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (state == StageState.INTRO || state == StageState.STANDBY
                || state == StageState.PLAYING || state == StageState.OUTRO)) {
            mc.player.setPos(anchorX, anchorY, anchorZ);
            mc.player.setDeltaMovement(0, 0, 0);
        }

        switch (state) {
            case INTRO:    updateIntro();    break;
            case PLAYING:  updatePlaying();  break;
            case OUTRO:    updateOutro();    break;
            case WATCHING: updateWatching(); break;
            case STANDBY: break;
            default: break;
        }
    }

    private void updateIntro() {
        long now = System.nanoTime();
        float deltaTime = (now - lastTickTimeNs) / 1_000_000_000.0f;
        lastTickTimeNs = now;
        deltaTime = Math.min(deltaTime, 0.1f);

        introElapsed += deltaTime;
        float t = easeOutCubic(introElapsed / INTRO_DURATION);

        cameraX = lerp(introStartX, standbyX, t);
        cameraY = lerp(introStartY, standbyY, t);
        cameraZ = lerp(introStartZ, standbyZ, t);
        cameraPitch = lerp(introStartPitch, standbyPitch, t);
        cameraYaw = lerpAngle(introStartYaw, standbyYaw, t);
        cameraFov = lerp(introStartFov, standbyFov, t);
        cameraRoll = 0.0f;

        if (introElapsed >= INTRO_DURATION) {
            cameraX = standbyX;
            cameraY = standbyY;
            cameraZ = standbyZ;
            cameraPitch = standbyPitch;
            cameraYaw = standbyYaw;
            cameraFov = standbyFov;
            state = StageState.STANDBY;
        }
    }

    private void updatePlaying() {
        long now = System.nanoTime();
        float deltaTime = (now - lastTickTimeNs) / 1_000_000_000.0f;
        lastTickTimeNs = now;
        deltaTime = Math.min(deltaTime, 0.1f);

        if (cinematicMode && lastEscTimeNs != 0
                && now - lastEscTimeNs >= DOUBLE_ESC_WINDOW_NS) {
            Minecraft.getInstance().options.hideGui = true;
            lastEscTimeNs = 0;
        }

        float effectiveSpeed = playbackSpeed;
        if (targetSyncFrame >= 0) {
            float drift = targetSyncFrame - currentFrame;
            if (Math.abs(drift) > SYNC_TOLERANCE) {
                effectiveSpeed = drift > 0 ? CATCHUP_SPEED_MAX : CATCHUP_SPEED_MIN;
            } else {
                targetSyncFrame = -1.0f;
            }
        }

        currentFrame += deltaTime * VMD_FPS * effectiveSpeed;

        if (currentFrame >= maxFrame) {
            currentFrame = maxFrame;
            endPlayback();
            return;
        }

        if (!com.shiroha.mmdskin.ui.stage.StageInviteManager.getInstance().isWatchingStage()) {
            com.shiroha.mmdskin.renderer.render.StageAnimSyncHelper.syncAllRemoteStageFrame(currentFrame);
            com.shiroha.mmdskin.renderer.render.StageAnimSyncHelper.syncLocalStageFrame(currentFrame);
            frameSyncCounter++;
            if (frameSyncCounter >= SYNC_INTERVAL_FRAMES) {
                frameSyncCounter = 0;
                StageNetworkHandler.sendFrameSync(currentFrame);
            }
        }

        // 修复：无相机数据时不更新相机位置，保持玩家当前视角
        if (cameraAnimHandle != 0) {
            cameraData.update(currentFrame);

            Vector3f mmdPos = cameraData.getPosition();
            float sx = mmdPos.x * MMD_TO_MC_SCALE;
            float sy = mmdPos.y * MMD_TO_MC_SCALE;
            float sz = mmdPos.z * MMD_TO_MC_SCALE;

            float yawRad = (float) Math.toRadians(anchorYaw);
            float cos = (float) Math.cos(yawRad);
            float sin = (float) Math.sin(yawRad);
            cameraX = anchorX + sx * cos - sz * sin;
            cameraY = anchorY + sy + cameraHeightOffset;
            cameraZ = anchorZ + sx * sin + sz * cos;

            cameraPitch = (float) Math.toDegrees(cameraData.getPitch());
            cameraYaw = (float) Math.toDegrees(cameraData.getYaw()) + anchorYaw;
            cameraRoll = (float) Math.toDegrees(cameraData.getRoll());
            cameraFov = cameraData.getFov();
        }
    }

    private void updateOutro() {
        long now = System.nanoTime();
        float deltaTime = (now - lastTickTimeNs) / 1_000_000_000.0f;
        lastTickTimeNs = now;
        deltaTime = Math.min(deltaTime, 0.1f);

        outroElapsed += deltaTime;
        float t = easeInOutQuart(outroElapsed / OUTRO_DURATION);

        cameraX = lerp(outroStartX, standbyX, t);
        cameraY = lerp(outroStartY, standbyY, t);
        cameraZ = lerp(outroStartZ, standbyZ, t);
        cameraPitch = lerp(outroStartPitch, standbyPitch, t);
        cameraYaw = lerpAngle(outroStartYaw, standbyYaw, t);
        cameraFov = lerp(outroStartFov, standbyFov, t);
        cameraRoll = 0.0f;

        if (outroElapsed >= OUTRO_DURATION) {
            cameraX = standbyX;
            cameraY = standbyY;
            cameraZ = standbyZ;
            cameraPitch = standbyPitch;
            cameraYaw = standbyYaw;
            cameraFov = standbyFov;

            state = StageState.STANDBY;
            Minecraft.getInstance().setScreen(new StageSelectScreen());
        }
    }

    public void checkEscapeKey() {
        if (state == StageState.INACTIVE) return;
        Minecraft mc = Minecraft.getInstance();

        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
            return;
        }

        if (mc.screen != null) return;

        long window = mc.getWindow().getWindow();
        boolean escNow = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

        if (escNow && !escWasPressed) {
            if (state == StageState.PLAYING) {
                long now = System.nanoTime();
                if (now - lastEscTimeNs < DOUBLE_ESC_WINDOW_NS && lastEscTimeNs != 0) {
                    lastEscTimeNs = 0;
                    endPlayback();
                } else {
                    lastEscTimeNs = now;
                    if (cinematicMode) {
                        mc.options.hideGui = false;
                    }
                    if (mc.gui != null) {
                        mc.gui.setOverlayMessage(Component.translatable("gui.mmdskin.stage.esc_hint"), false);
                    }
                }
            } else if (state == StageState.WATCHING) {
                exitWatchMode();
            } else {
                exitStageMode();
            }
        }
        escWasPressed = escNow;
    }

    public void toggleMouseGrab() {
        Minecraft mc = Minecraft.getInstance();
        if (mouseReleased) {
            mc.mouseHandler.grabMouse();
            mouseReleased = !mc.mouseHandler.isMouseGrabbed();
        } else {
            mc.mouseHandler.releaseMouse();
            mouseReleased = true;
            if (mc.gui != null) {
                mc.gui.setOverlayMessage(Component.translatable("gui.mmdskin.stage.mouse_released"), false);
            }
        }
    }

    public boolean isMouseReleased() {
        return mouseReleased;
    }

    private void restoreMouseGrab() {
        if (mouseReleased) {
            Minecraft mc = Minecraft.getInstance();
            mc.mouseHandler.grabMouse();
            mouseReleased = !mc.mouseHandler.isMouseGrabbed();
        }
        escWasPressed = false;
        lastEscTimeNs = 0;
    }

    private void clearLocalPlayerStageFlags() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(mc.player);
        if (resolved != null) {
            resolved.model().entityData.playCustomAnim = false;
            resolved.model().entityData.playStageAnim = false;
        }
    }

    private void restoreModelState(NativeFunc nf) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(mc.player);
        if (resolved != null) {
            MMDModelManager.Model mwed = resolved.model();
            long handle = mwed.model.getModelHandle();
            if (handle != 0) {
                nf.SetAutoBlinkEnabled(handle, true);
                nf.SetEyeTrackingEnabled(handle, true);
            }
            mwed.model.resetPhysics();
            mwed.entityData.invalidateStateLayers();
        }
    }

    private static float easeOutCubic(float t) {
        t = Math.max(0, Math.min(1, t));
        float f = 1 - t;
        return 1 - f * f * f;
    }

    private static float easeInOutQuart(float t) {
        t = Math.max(0, Math.min(1, t));
        return t < 0.5f ? 8 * t * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 4) / 2;
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float lerpAngle(float a, float b, float t) {
        float diff = ((b - a) % 360 + 540) % 360 - 180;
        return a + diff * t;
    }

    public boolean isActive() {
        return state != StageState.INACTIVE;
    }

    public boolean isPlaying() {
        return state == StageState.PLAYING;
    }

    public boolean isInStageMode() {
        return state != StageState.INACTIVE;
    }

    public boolean isStagePlayingModel(long handle) {
        return state == StageState.PLAYING && modelHandle != 0 && modelHandle == handle;
    }

    public float getAnchorYaw() {
        return anchorYaw;
    }

    public boolean isCinematicMode() {
        return cinematicMode;
    }

    public float getCurrentFrame() {
        return currentFrame;
    }

    public float getMaxFrame() {
        return maxFrame;
    }

    public float getProgress() {
        return maxFrame > 0 ? currentFrame / maxFrame : 0.0f;
    }

    public double getCameraX() { return cameraX; }
    public double getCameraY() { return cameraY; }
    public double getCameraZ() { return cameraZ; }

    public float getCameraPitch() { return cameraPitch; }
    public float getCameraYaw() { return cameraYaw; }
    public float getCameraRoll() { return cameraRoll; }

    public float getCameraFov() { return cameraFov; }

    public void setPlaybackSpeed(float speed) {
        this.playbackSpeed = speed;
    }

    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    public boolean isWaitingForHost() {
        return waitingForHost;
    }

    public void setWaitingForHost(boolean waiting) {
        this.waitingForHost = waiting;
    }


    public java.util.UUID getWatchingHostUUID() {
        return watchingHostUUID;
    }

    public void enterWatchMode(java.util.UUID hostUUID) {
        if (state == StageState.WATCHING) return;

        if (state != StageState.INACTIVE) {
            forceCleanupForWatch();
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        var host = mc.level.getPlayerByUUID(hostUUID);
        if (host == null) return;

        this.savedCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);

        this.anchorX = host.getX();
        this.anchorY = host.getY();
        this.anchorZ = host.getZ();
        this.anchorYaw = host.getYRot();
        this.watchingHostUUID = hostUUID;

        this.cinematicMode = com.shiroha.mmdskin.config.StageConfig.getInstance().cinematicMode;
        if (this.cinematicMode) {
            this.previousHideGui = mc.options.hideGui;
            mc.options.hideGui = true;
        }

        this.lastTickTimeNs = System.nanoTime();
        this.escWasPressed = false;
        this.mouseReleased = false;
        this.targetSyncFrame = -1.0f;
        this.currentFrame = 0.0f;
        this.state = StageState.WATCHING;
    }


    public void setWatchCamera(long cameraAnimHandle, float heightOffset) {
        if (state != StageState.WATCHING) return;

        NativeFunc nf = NativeFunc.GetInst();

        if (this.watchCameraAnimHandle != 0) {
            nf.DeleteAnimation(this.watchCameraAnimHandle);
        }

        this.watchCameraAnimHandle = cameraAnimHandle;
        this.cameraHeightOffset = heightOffset;

        if (cameraAnimHandle != 0 && nf.HasCameraData(cameraAnimHandle)) {
            this.maxFrame = nf.GetAnimMaxFrame(cameraAnimHandle);
            this.currentFrame = 0.0f;
            this.cameraData.setAnimHandle(cameraAnimHandle);
        }
    }

    public void setWatchMotion(long motionAnim, long modelHandle, String modelName) {
        if (state != StageState.WATCHING) return;
        this.motionAnimHandle = motionAnim;
        this.modelHandle = modelHandle;
        this.modelName = modelName;
        if (modelHandle != 0) {
            NativeFunc nf = NativeFunc.GetInst();
            nf.SetAutoBlinkEnabled(modelHandle, false);
            nf.SetEyeTrackingEnabled(modelHandle, false);
        }
    }

    public void loadWatchAudio(String audioPath) {
        if (state != StageState.WATCHING) return;
        if (audioPlayer.load(audioPath)) {
            audioPlayer.play();
        } else {
            logger.warn("[WATCHING] 音频加载失败: {}", audioPath);
        }
    }

    public void exitWatchMode() {
        exitWatchMode(true);
    }
    
    public void exitWatchMode(boolean sendLeave) {
        if (state != StageState.WATCHING) return;

        audioPlayer.cleanup();

        if (cinematicMode) {
            Minecraft.getInstance().options.hideGui = previousHideGui;
        }

        Minecraft mc = Minecraft.getInstance();
        com.shiroha.mmdskin.ui.stage.StageInviteManager mgr = 
            com.shiroha.mmdskin.ui.stage.StageInviteManager.getInstance();
            
        if (mc.player != null) {
            com.shiroha.mmdskin.renderer.render.StageAnimSyncHelper.endStageAnim(mc.player);
            StageNetworkHandler.sendStageEnd();
            
            if (sendLeave && mgr.getWatchingHostUUID() != null) {
                StageNetworkHandler.sendLeave(mgr.getWatchingHostUUID());
            }

            this.anchorX = mc.player.getX();
            this.anchorY = mc.player.getY();
            this.anchorZ = mc.player.getZ();
            this.anchorYaw = mc.player.getYRot();
        }

        computeIntroAndStandby(mc);

        clearLocalPlayerStageFlags();

        NativeFunc nf = NativeFunc.GetInst();
        restoreModelState(nf);
        if (this.motionAnimHandle != 0) {
            nf.DeleteAnimation(this.motionAnimHandle);
        }

        if (watchCameraAnimHandle != 0) {
            nf.DeleteAnimation(watchCameraAnimHandle);
            watchCameraAnimHandle = 0;
        }

        this.cameraAnimHandle = 0;
        this.motionAnimHandle = 0;
        this.modelHandle = 0;
        this.modelName = null;
        this.currentFrame = 0.0f;
        this.maxFrame = 0.0f;

        this.outroStartX = cameraX;
        this.outroStartY = cameraY;
        this.outroStartZ = cameraZ;
        this.outroStartPitch = cameraPitch;
        this.outroStartYaw = cameraYaw;
        this.outroStartFov = cameraFov;

        this.outroElapsed = 0.0f;
        this.lastTickTimeNs = System.nanoTime();
        this.outroIsGuest = true;

        restoreMouseGrab();

        if (sendLeave) {
            mgr.stopWatching();
            this.watchingHostUUID = null;
        } else {
            mgr.stopWatchingStageOnly();
            this.waitingForHost = true;
        }
        
        this.state = StageState.OUTRO;
    }


    private void forceCleanupForWatch() {
        NativeFunc nf = NativeFunc.GetInst();

        if (state == StageState.PLAYING) {
            audioPlayer.cleanup();
            if (cinematicMode) {
                Minecraft.getInstance().options.hideGui = previousHideGui;
            }
            clearLocalPlayerStageFlags();
            if (this.motionAnimHandle != 0) nf.DeleteAnimation(this.motionAnimHandle);
            if (this.cameraAnimHandle != 0 && this.cameraAnimHandle != this.motionAnimHandle) {
                nf.DeleteAnimation(this.cameraAnimHandle);
            }
        }

        if (watchCameraAnimHandle != 0) {
            nf.DeleteAnimation(watchCameraAnimHandle);
            watchCameraAnimHandle = 0;
        }

        restoreMouseGrab();

        this.cameraAnimHandle = 0;
        this.motionAnimHandle = 0;
        this.modelHandle = 0;
        this.modelName = null;
        this.currentFrame = 0.0f;
        this.maxFrame = 0.0f;
        this.waitingForHost = false;
        this.state = StageState.INACTIVE;
    }

    private void updateWatching() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level != null && watchingHostUUID != null) {
            var host = mc.level.getPlayerByUUID(watchingHostUUID);
            if (host != null) {
                this.anchorX = host.getX();
                this.anchorY = host.getY();
                this.anchorZ = host.getZ();
                this.anchorYaw = host.getYRot();
            }
        }

        if (watchCameraAnimHandle == 0) {
            float yawRad = (float) Math.toRadians(anchorYaw);
            cameraX = anchorX - Math.sin(yawRad) * 3.5;
            cameraY = anchorY + 1.8;
            cameraZ = anchorZ + Math.cos(yawRad) * 3.5;
            cameraYaw = anchorYaw + 180.0f;
            cameraPitch = 15.0f;
            cameraFov = 70.0f;
            cameraRoll = 0.0f;
            return;
        }

        long now = System.nanoTime();
        float deltaTime = (now - lastTickTimeNs) / 1_000_000_000.0f;
        lastTickTimeNs = now;
        deltaTime = Math.min(deltaTime, 0.1f);

        float effectiveSpeed = playbackSpeed;
        if (targetSyncFrame >= 0) {
            float drift = targetSyncFrame - currentFrame;
            if (Math.abs(drift) > SYNC_TOLERANCE) {
                effectiveSpeed = drift > 0 ? CATCHUP_SPEED_MAX : CATCHUP_SPEED_MIN;
            } else {
                targetSyncFrame = -1.0f;
            }
        }

        currentFrame += deltaTime * VMD_FPS * effectiveSpeed;

        if (currentFrame >= maxFrame) {
            exitWatchMode(true);
            return;
        }

        cameraData.update(currentFrame);

        Vector3f mmdPos = cameraData.getPosition();
        float sx = mmdPos.x * MMD_TO_MC_SCALE;
        float sy = mmdPos.y * MMD_TO_MC_SCALE;
        float sz = mmdPos.z * MMD_TO_MC_SCALE;

        float yawRad = (float) Math.toRadians(anchorYaw);
        float cos = (float) Math.cos(yawRad);
        float sin = (float) Math.sin(yawRad);
        cameraX = anchorX + sx * cos - sz * sin;
        cameraY = anchorY + sy + cameraHeightOffset;
        cameraZ = anchorZ + sx * sin + sz * cos;

        cameraPitch = (float) Math.toDegrees(cameraData.getPitch());
        cameraYaw = (float) Math.toDegrees(cameraData.getYaw()) + anchorYaw;
        cameraRoll = (float) Math.toDegrees(cameraData.getRoll());
        cameraFov = cameraData.getFov();
    }

    public void onFrameSync(float hostFrame) {
        if (state != StageState.WATCHING && state != StageState.PLAYING) return;
        this.targetSyncFrame = hostFrame;
    }

    public boolean isWatching() {
        return state == StageState.WATCHING;
    }

    public boolean shouldBlockInput() {
        return state == StageState.PLAYING || state == StageState.WATCHING
            || state == StageState.INTRO || state == StageState.OUTRO;
    }

}
