package com.shiroha.mmdskin.renderer.camera;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
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

/**
 * MMD 舞台模式相机控制器（单例）
 * 
 * 状态机：
 *   INACTIVE ──enterStageMode()──> INTRO ──过渡完成──> STANDBY
 *   STANDBY  ──startStage()────> PLAYING
 *   PLAYING  ──播放完/ESC──────> OUTRO ──过渡完成──> STANDBY
 *   STANDBY  ──ESC/exitStageMode()──> INACTIVE
 * 
 * INTRO   阶段：从当前相机位置平滑过渡到待机展示位置（进入舞台模式时立即开始）
 * STANDBY 阶段：相机停在展示位置，等待用户选择 VMD 或按 ESC 退出
 * PLAYING 阶段：按 VMD 相机数据驱动
 * OUTRO   阶段：从 VMD 最后一帧平滑回到待机展示位置
 * 
 * 通过 Mixin 在相机 setup 时覆盖位置/旋转/FOV。
 */
public class MMDCameraController {
    private static final Logger logger = LogManager.getLogger();
    
    private static final MMDCameraController INSTANCE = new MMDCameraController();
    
    // MMD 单位到 Minecraft 单位的缩放（与模型渲染 baseScale 一致）
    private static final float MMD_TO_MC_SCALE = 0.09f;
    // VMD 30fps
    private static final float VMD_FPS = 30.0f;
    
    // 状态机
    private enum StageState { INACTIVE, INTRO, STANDBY, PLAYING, OUTRO }
    private StageState state = StageState.INACTIVE;
    
    // 视角保存/恢复
    private CameraType savedCameraType = null;
    
    // 模型名（用于停止时重载）
    private String modelName = null;
    
    private boolean cinematicMode = false;
    private boolean previousHideGui = false;
    private float cameraHeightOffset = 0.0f; // 镇头高度偏移（MC单位）
    
    // 帧控制
    private float currentFrame = 0.0f;
    private float maxFrame = 0.0f;
    private float playbackSpeed = 1.0f;
    
    // 相机 VMD 句柄（可独立于动作 VMD）
    private long cameraAnimHandle = 0;
    // 动作 VMD 句柄（用于同步帧）
    private long motionAnimHandle = 0;
    
    // 模型句柄（用于禁用/恢复自动行为）
    private long modelHandle = 0;
    
    // 音频播放器
    private final StageAudioPlayer audioPlayer = new StageAudioPlayer();
    
    // 相机数据
    private final MMDCameraData cameraData = new MMDCameraData();
    
    // 玩家位置偏移（相机基于玩家位置）
    private double anchorX, anchorY, anchorZ;
    // 玩家进入舞台模式时的朝向（度），用于旋转 VMD 相机坐标
    private float anchorYaw;
    
    // 计算后的相机世界坐标
    private double cameraX, cameraY, cameraZ;
    private float cameraPitch, cameraYaw, cameraRoll;
    private float cameraFov = 70.0f;
    
    // 时间追踪
    private long lastTickTimeNs = 0;
    
    // 鼠标释放状态（PLAYING时右键切换）
    private boolean mouseReleased = false;
    
    // 双击ESC检测
    private boolean escWasPressed = false;
    private long lastEscTimeNs = 0;
    private static final long DOUBLE_ESC_WINDOW_NS = 600_000_000L; // 600ms
    
    // INTRO 过渡（当前相机 → 待机位）
    private static final float INTRO_DURATION = 1.0f;
    private float introElapsed = 0.0f;
    private double introStartX, introStartY, introStartZ;
    private float introStartPitch, introStartYaw, introStartFov;
    
    // 待机展示位置（INTRO 终点 / OUTRO 终点）
    private double standbyX, standbyY, standbyZ;
    private float standbyPitch, standbyYaw, standbyFov;
    
    // OUTRO 过渡（VMD 最后一帧 → 待机位）
    private static final float OUTRO_DURATION = 1.5f;
    private float outroElapsed = 0.0f;
    private double outroStartX, outroStartY, outroStartZ;
    private float outroStartPitch, outroStartYaw, outroStartFov;
    
    private MMDCameraController() {}
    
    public static MMDCameraController getInstance() {
        return INSTANCE;
    }

    /**
     * 判断当前是否处于舞台模式
     */
    public boolean isInStageMode() {
        return state != StageState.INACTIVE;
    }
    
    // ==================== 生命周期方法 ====================
    
    /**
     * 进入舞台模式（由 StageSelectScreen.init() 调用）
     * 立即切换第三人称视角并开始相机过渡到展示位置
     */
    public void enterStageMode() {
        if (state != StageState.INACTIVE) return;
        
        Minecraft mc = Minecraft.getInstance();
        
        // 保存当前视角并强制第三人称（确保 MC 渲染玩家模型）
        this.savedCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        
        // 记录玩家当前位置作为锚点
        if (mc.player != null) {
            this.anchorX = mc.player.getX();
            this.anchorY = mc.player.getY();
            this.anchorZ = mc.player.getZ();
            this.anchorYaw = mc.player.getYRot();
            // 重置玩家朝向，确保进入舞台时面向正前方
            mc.player.setXRot(0.0f);
            mc.player.setYRot(this.anchorYaw);
            mc.player.yHeadRot = this.anchorYaw;
            mc.player.yBodyRot = this.anchorYaw;
        }
        
        // 重载模型（清除上次播放的残留姿势，仅本地玩家）
        if (mc.player != null) {
            String playerName = mc.player.getName().getString();
            MMDModelManager.forceReloadPlayerModels(playerName);
            logger.info("[舞台模式] 本地玩家模型已重载: {}", playerName);
        }
        
        // 计算 INTRO 起点和待机位
        computeIntroAndStandby(mc);
        
        this.introElapsed = 0.0f;
        this.lastTickTimeNs = System.nanoTime();
        this.escWasPressed = false;
        this.lastEscTimeNs = 0;
        this.mouseReleased = false;
        this.state = StageState.INTRO;
        
        // 立即设置相机到起点位置（避免第一帧跳动）
        this.cameraX = introStartX;
        this.cameraY = introStartY;
        this.cameraZ = introStartZ;
        this.cameraPitch = introStartPitch;
        this.cameraYaw = introStartYaw;
        this.cameraFov = introStartFov;
        
        logger.info("[舞台模式] 进入舞台模式, 开始视角过渡");
    }
    
    /**
     * 启动 VMD 播放（由 StageSelectScreen.startStage() 调用）
     * 从 STANDBY 或 INTRO 状态切换到 PLAYING
     */
    public void startStage(long motionAnim, long cameraAnim, boolean cinematic, 
                           long modelHandle, String modelName, String audioPath, float heightOffset) {
        if (state != StageState.STANDBY && state != StageState.INTRO) return;
        
        NativeFunc nf = NativeFunc.GetInst();
        
        this.motionAnimHandle = motionAnim;
        
        // 确定相机数据来源
        if (cameraAnim != 0 && nf.HasCameraData(cameraAnim)) {
            this.cameraAnimHandle = cameraAnim;
        } else if (motionAnim != 0 && nf.HasCameraData(motionAnim)) {
            this.cameraAnimHandle = motionAnim;
        } else {
            logger.warn("[舞台模式] 没有可用的相机数据");
            return;
        }
        
        this.maxFrame = nf.GetAnimMaxFrame(this.cameraAnimHandle);
        this.currentFrame = 0.0f;
        this.cinematicMode = cinematic;
        this.cameraHeightOffset = heightOffset;
        this.modelName = modelName;
        this.cameraData.setAnimHandle(this.cameraAnimHandle);
        
        // 影院模式：隐藏 HUD
        if (cinematic) {
            Minecraft mc = Minecraft.getInstance();
            this.previousHideGui = mc.options.hideGui;
            mc.options.hideGui = true;
        }
        
        // 禁用自动眨眼和视线追踪（避免与表情VMD冲突）
        this.modelHandle = modelHandle;
        if (modelHandle != 0) {
            nf.SetAutoBlinkEnabled(modelHandle, false);
            nf.SetEyeTrackingEnabled(modelHandle, false);
        }
        
        // 加载并播放音频（与动作同步）
        if (audioPath != null && !audioPath.isEmpty()) {
            if (audioPlayer.load(audioPath)) {
                audioPlayer.play();
                logger.info("[舞台模式] 音频已加载并开始播放: {}", audioPath);
            } else {
                logger.warn("[舞台模式] 音频加载失败: {}", audioPath);
            }
        }
        
        this.state = StageState.PLAYING;
        this.lastTickTimeNs = System.nanoTime();
        this.escWasPressed = false;
        this.lastEscTimeNs = 0;
        this.mouseReleased = false;
        
        logger.info("[舞台模式] 开始播放: 相机帧={}, 影院={}, 模型={}, 音频={}, 高度偏移={}", maxFrame, cinematic, modelHandle, audioPath != null, cameraHeightOffset);
    }
    
    /**
     * 结束 VMD 播放并过渡到待机位（内部方法）
     * VMD 播放完毕或 PLAYING 阶段按 ESC 时调用
     */
    private void endPlayback() {
        // 恢复鼠标状态
        restoreMouseGrab();
        
        // 停止音频
        audioPlayer.cleanup();
        
        // 恢复 HUD
        if (cinematicMode) {
            Minecraft.getInstance().options.hideGui = previousHideGui;
        }
        
        NativeFunc nf = NativeFunc.GetInst();
        
        // 重载模型或恢复自动行为（仅本地玩家）
        if (this.modelName != null && !this.modelName.isEmpty()) {
            Minecraft mcEnd = Minecraft.getInstance();
            if (mcEnd.player != null) {
                MMDModelManager.forceReloadPlayerModels(mcEnd.player.getName().getString());
            }
            logger.info("[舞台模式] 模型已重载: {}", this.modelName);
        } else if (this.modelHandle != 0) {
            nf.SetAutoBlinkEnabled(this.modelHandle, true);
            nf.SetEyeTrackingEnabled(this.modelHandle, true);
        }
        
        // 清理动画句柄
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
        
        // 记录 OUTRO 起点（当前相机位置）
        this.outroStartX = cameraX;
        this.outroStartY = cameraY;
        this.outroStartZ = cameraZ;
        this.outroStartPitch = cameraPitch;
        this.outroStartYaw = cameraYaw;
        this.outroStartFov = cameraFov;
        
        this.outroElapsed = 0.0f;
        this.lastTickTimeNs = System.nanoTime();
        this.state = StageState.OUTRO;
        
        // 广播舞台结束到其他客户端
        StageNetworkHandler.sendStageEnd();
        
        logger.info("[舞台模式] 播放结束, 开始回归过渡");
    }
    
    /**
     * 退出舞台模式（由 StageSelectScreen.onClose() 或 ESC 调用）
     * 从任意状态恢复到 INACTIVE
     */
    public void exitStageMode() {
        if (state == StageState.INACTIVE) return;
        
        // 如果正在播放，先清理播放资源
        if (state == StageState.PLAYING) {
            audioPlayer.cleanup();
            if (cinematicMode) {
                Minecraft.getInstance().options.hideGui = previousHideGui;
            }
            NativeFunc nf = NativeFunc.GetInst();
            if (this.modelName != null && !this.modelName.isEmpty()) {
                Minecraft mcExit = Minecraft.getInstance();
                if (mcExit.player != null) {
                    MMDModelManager.forceReloadPlayerModels(mcExit.player.getName().getString());
                }
            } else if (this.modelHandle != 0) {
                nf.SetAutoBlinkEnabled(this.modelHandle, true);
                nf.SetEyeTrackingEnabled(this.modelHandle, true);
            }
            if (this.motionAnimHandle != 0) {
                nf.DeleteAnimation(this.motionAnimHandle);
            }
            if (this.cameraAnimHandle != 0 && this.cameraAnimHandle != this.motionAnimHandle) {
                nf.DeleteAnimation(this.cameraAnimHandle);
            }
        }
        
        // 恢复鼠标状态
        restoreMouseGrab();
        
        // 恢复视角
        Minecraft mc = Minecraft.getInstance();
        if (savedCameraType != null) {
            mc.options.setCameraType(savedCameraType);
            savedCameraType = null;
        }
        
        // 重置所有状态
        this.state = StageState.INACTIVE;
        this.cameraAnimHandle = 0;
        this.motionAnimHandle = 0;
        this.modelHandle = 0;
        this.modelName = null;
        this.currentFrame = 0.0f;
        this.maxFrame = 0.0f;
        
        // 清空播放期间累积的按键队列，防止退出后延迟触发游戏操作
        KeyMapping.releaseAll();
        
        logger.info("[舞台模式] 退出舞台模式");
    }
    
    // ==================== 过渡计算 ====================
    
    /**
     * 计算 INTRO 起点（当前相机）和待机展示位置（玩家正前方上方）
     */
    private void computeIntroAndStandby(Minecraft mc) {
        // 起点：当前相机位置/角度
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
        
        // 待机展示位置：玩家正前方 3.5 格 + 向上 2.5 格
        if (mc.player != null) {
            float yawRad = (float) Math.toRadians(mc.player.getYRot());
            standbyX = anchorX - Math.sin(yawRad) * 3.5;
            standbyY = anchorY + 1.8;
            standbyZ = anchorZ + Math.cos(yawRad) * 3.5;
            standbyYaw = mc.player.getYRot() + 180.0f;
            standbyPitch = 15.0f;
        } else {
            standbyX = introStartX;
            standbyY = introStartY;
            standbyZ = introStartZ;
            standbyYaw = introStartYaw;
            standbyPitch = introStartPitch;
        }
        standbyFov = 70.0f;
    }
    
    // ==================== 每帧更新 ====================
    
    /**
     * 每帧更新（由 Mixin 在 Camera.setup 中调用）
     */
    public void updateCamera() {
        // 锁定玩家位置（防止移动）
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.setPos(anchorX, anchorY, anchorZ);
            mc.player.setDeltaMovement(0, 0, 0);
        }
        
        switch (state) {
            case INTRO:   updateIntro();   break;
            case PLAYING: updatePlaying(); break;
            case OUTRO:   updateOutro();   break;
            case STANDBY: /* 相机保持在当前位置 */ break;
            default: break;
        }
    }
    
    /**
     * INTRO 阶段：从当前相机平滑过渡到待机展示位置
     */
    private void updateIntro() {
        long now = System.nanoTime();
        float deltaTime = (now - lastTickTimeNs) / 1_000_000_000.0f;
        lastTickTimeNs = now;
        deltaTime = Math.min(deltaTime, 0.1f);
        
        introElapsed += deltaTime;
        float t = smoothstep(introElapsed / INTRO_DURATION);
        
        cameraX = lerp(introStartX, standbyX, t);
        cameraY = lerp(introStartY, standbyY, t);
        cameraZ = lerp(introStartZ, standbyZ, t);
        cameraPitch = lerp(introStartPitch, standbyPitch, t);
        cameraYaw = lerpAngle(introStartYaw, standbyYaw, t);
        cameraFov = lerp(introStartFov, standbyFov, t);
        cameraRoll = 0.0f;
        
        // 过渡完成 → 待机
        if (introElapsed >= INTRO_DURATION) {
            cameraX = standbyX;
            cameraY = standbyY;
            cameraZ = standbyZ;
            cameraPitch = standbyPitch;
            cameraYaw = standbyYaw;
            cameraFov = standbyFov;
            state = StageState.STANDBY;
            logger.info("[舞台模式] 视角过渡完成, 进入待机");
        }
    }
    
    /**
     * PLAYING 阶段：VMD 帧推进 + 相机数据读取
     */
    private void updatePlaying() {
        long now = System.nanoTime();
        float deltaTime = (now - lastTickTimeNs) / 1_000_000_000.0f;
        lastTickTimeNs = now;
        deltaTime = Math.min(deltaTime, 0.1f);
        
        currentFrame += deltaTime * VMD_FPS * playbackSpeed;
        
        // 播放完毕 → OUTRO
        if (currentFrame >= maxFrame) {
            currentFrame = maxFrame;
            endPlayback();
            return;
        }
        
        // 更新相机数据
        cameraData.update(currentFrame);
        
        // MMD 坐标 -> Minecraft 世界坐标（按玩家朝向旋转 XZ 平面）
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
    
    /**
     * OUTRO 阶段：从 VMD 最后一帧平滑过渡回待机展示位置
     */
    private void updateOutro() {
        long now = System.nanoTime();
        float deltaTime = (now - lastTickTimeNs) / 1_000_000_000.0f;
        lastTickTimeNs = now;
        deltaTime = Math.min(deltaTime, 0.1f);
        
        outroElapsed += deltaTime;
        float t = smoothstep(outroElapsed / OUTRO_DURATION);
        
        cameraX = lerp(outroStartX, standbyX, t);
        cameraY = lerp(outroStartY, standbyY, t);
        cameraZ = lerp(outroStartZ, standbyZ, t);
        cameraPitch = lerp(outroStartPitch, standbyPitch, t);
        cameraYaw = lerpAngle(outroStartYaw, standbyYaw, t);
        cameraFov = lerp(outroStartFov, standbyFov, t);
        cameraRoll = 0.0f;
        
        // 过渡完成 → 待机 + 打开舞台选择界面
        if (outroElapsed >= OUTRO_DURATION) {
            cameraX = standbyX;
            cameraY = standbyY;
            cameraZ = standbyZ;
            cameraPitch = standbyPitch;
            cameraYaw = standbyYaw;
            cameraFov = standbyFov;
            state = StageState.STANDBY;
            logger.info("[舞台模式] 回归过渡完成, 打开舞台选择界面");
            
            // 自动打开舞台选择界面，用户可直接选择下一个舞蹈
            Minecraft.getInstance().setScreen(new StageSelectScreen());
        }
    }
    
    /**
     * 检查输入（由 Mixin 每帧调用）
     * 
     * ESC 处理：
     *   PLAYING → 双击 ESC 才结束播放，第一次显示提示
     *   STANDBY/INTRO/OUTRO → 单击 ESC 退出舞台模式
     * 
     * pauseGame 已被 MinecraftMixin 在舞台激活时拦截，此处统一用 GLFW 检测 ESC
     */
    public void checkEscapeKey() {
        if (state == StageState.INACTIVE) return;
        Minecraft mc = Minecraft.getInstance();
        
        // 防御性处理：若 PauseScreen 仍然出现（如 pauseGame 未被拦截的场景），直接关闭
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
            return;
        }
        
        // 如果有其他 Screen 打开（如 StageSelectScreen），不拦截 ESC（由 Screen.onClose 处理）
        if (mc.screen != null) return;
        
        long window = mc.getWindow().getWindow();
        boolean escNow = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        
        // 边缘检测：仅在按下瞬间触发
        if (escNow && !escWasPressed) {
            if (state == StageState.PLAYING) {
                long now = System.nanoTime();
                if (now - lastEscTimeNs < DOUBLE_ESC_WINDOW_NS && lastEscTimeNs != 0) {
                    // 双击 ESC → 结束播放
                    lastEscTimeNs = 0;
                    endPlayback();
                } else {
                    // 第一次 ESC → 记录时间，显示提示
                    lastEscTimeNs = now;
                    if (mc.gui != null) {
                        mc.gui.setOverlayMessage(Component.translatable("gui.mmdskin.stage.esc_hint"), false);
                    }
                }
            } else {
                // STANDBY/INTRO/OUTRO → 单击即退出
                exitStageMode();
            }
        }
        escWasPressed = escNow;
    }
    
    // ==================== 鼠标释放/捕获 ====================
    
    /**
     * 切换鼠标释放/捕获（由 MouseHandlerMixin 在 PLAYING 时右键触发）
     */
    public void toggleMouseGrab() {
        Minecraft mc = Minecraft.getInstance();
        if (mouseReleased) {
            // 重新捕获鼠标
            mc.mouseHandler.grabMouse();
            // 确认实际状态（窗口非活跃时 grabMouse 会静默跳过）
            mouseReleased = !mc.mouseHandler.isMouseGrabbed();
        } else {
            // 释放鼠标，允许用户切换到其他窗口
            mc.mouseHandler.releaseMouse();
            mouseReleased = true;
            if (mc.gui != null) {
                mc.gui.setOverlayMessage(Component.translatable("gui.mmdskin.stage.mouse_released"), false);
            }
        }
    }
    
    /**
     * 鼠标是否已被释放（用于 Mixin 判断是否阻止 grabMouse/pauseGame）
     */
    public boolean isMouseReleased() {
        return mouseReleased;
    }
    
    /**
     * 恢复鼠标捕获状态（播放结束或退出舞台时调用）
     */
    private void restoreMouseGrab() {
        if (mouseReleased) {
            Minecraft mc = Minecraft.getInstance();
            mc.mouseHandler.grabMouse();
            // 窗口非活跃时 grabMouse 静默跳过，以实际状态为准
            mouseReleased = !mc.mouseHandler.isMouseGrabbed();
        }
        escWasPressed = false;
        lastEscTimeNs = 0;
    }
    
    // ==================== 缓动工具 ====================
    
    private static float smoothstep(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
    
    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }
    
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
    
    /**
     * 角度插值（处理 360° 环绕）
     */
    private static float lerpAngle(float a, float b, float t) {
        float diff = ((b - a) % 360 + 540) % 360 - 180;
        return a + diff * t;
    }
    
    // ==================== 状态查询 ====================
    
    public boolean isActive() {
        return state != StageState.INACTIVE;
    }
    
    public boolean isPlaying() {
        return state == StageState.PLAYING;
    }
    
    /**
     * 判断指定模型句柄是否正处于舞台播放状态
     * 用于渲染器在播放期间跳过玩家输入的头部角度和眼球追踪
     */
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
    
    // ==================== 相机参数（供 Mixin 读取） ====================
    
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
}
