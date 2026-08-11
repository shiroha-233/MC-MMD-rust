package com.shiroha.mmdskin.player.runtime;

import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.compat.tacz.TaczGunDetector;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.config.RuntimeConfigPortHolder;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.player.animation.AnimationStateManager;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.player.port.VrRuntimePort;
import com.shiroha.mmdskin.player.render.InventoryRenderHelper;
import com.shiroha.mmdskin.player.render.PlayerRenderHelper;
import com.shiroha.mmdskin.render.backend.BaseModelInstance;
import com.shiroha.mmdskin.render.scene.MutableRenderPose;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 文件职责：维护本地第一人称与 VR 视角相关的模型状态。 */
public final class FirstPersonManager {
    private static final Logger logger = LogManager.getLogger();
    private static final float MODEL_SCALE = 0.09f;

    private static final NativeModelPort NOOP_MODEL_PORT = new NativeModelPort() {
        @Override public boolean setLayerBoneMask(long h, int l, String b) { return false; }
        @Override public boolean setLayerBoneExclude(long h, int l, String b) { return false; }
        @Override public long getModelMemoryUsage(long h) { return 0L; }
        @Override public void setFirstPersonMode(long h, boolean e) {}
        @Override public void getEyeBonePosition(long h, float[] o) {}
        @Override public void applyVrTrackingInput(long h, float[] d) {}
        @Override public void setVrEnabled(long h, boolean e) {}
        @Override public void setVrIkParams(long h, float s) {}
        @Override public int getMaterialCount(long h) { return 0; }
        @Override public void setMaterialVisible(long h, int i, boolean v) {}
        @Override public void setAllMaterialsVisible(long h, boolean v) {}
        @Override public void deleteModel(long h) {}
    };

    private static volatile NativeModelPort modelPort = NOOP_MODEL_PORT;
    private static volatile VrRuntimePort vrRuntimePort = VrRuntimePort.noop();

    private static float cachedModelScale = 1.0f;
    private static long trackedModelHandle = 0;
    private static boolean activeDesktopFirstPerson = false;
    private static boolean activeVrEyeCamera = false;
    private static final float[] eyeBonePos = new float[3];
    private static final float[] preparedEyeBonePos = new float[3];
    private static boolean eyeBoneValid = false;
    private static BaseModelInstance preparedFirstPersonModel;

    private static Vec3 vrModelRootOffset = Vec3.ZERO;
    private static boolean vrModelRootOffsetValid = false;
    private static Vec3 lastCameraPos = Vec3.ZERO;

    private FirstPersonManager() {}

    public static void configureRuntimeCollaborators(NativeModelPort port) {
        modelPort = port != null ? port : NOOP_MODEL_PORT;
    }

    public static void configureVrRuntime(VrRuntimePort vrRuntimePort) {
        FirstPersonManager.vrRuntimePort = vrRuntimePort != null ? vrRuntimePort : VrRuntimePort.noop();
    }

    public static VrRuntimePort vrRuntime() {
        return vrRuntimePort;
    }

    public static void setLastCameraPos(Vec3 pos) {
        lastCameraPos = pos;
    }

    public static Vec3 getLastCameraPos() {
        return lastCameraPos;
    }

    public static boolean shouldRenderFirstPerson() {
        if (isLocalVrMmdModelActive()) return false;
        if (!RuntimeConfigPortHolder.get().isFirstPersonModelEnabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) return false;
        return mc.player != null && MmdSkinRendererPlayerHelper.isUsingMmdModel(mc.player);
    }

    /**
     * 在 Camera.setup 前准备本地玩家当前帧的双眼锚点。
     */
    public static void prepareCameraFrame(float partialTick) {
        discardPreparedFirstPersonPose();

        Minecraft minecraft = Minecraft.getInstance();
        AbstractClientPlayer player = minecraft.player;
        if (player == null) {
            deactivateDesktopCameraState();
            return;
        }
        if (isLocalVrMmdModelActive()) {
            syncVrCameraActivationState();
            return;
        }
        if (!shouldRenderFirstPerson()
                || InventoryRenderHelper.isInventoryScreen()
                || isStageCameraActive()) {
            deactivateDesktopCameraState();
            return;
        }

        BaseModelInstance preparingModel = null;
        try {
            PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
            if (resolved == null) {
                deactivateDesktopCameraState();
                return;
            }

            ManagedModel managedModel = resolved.model();
            if (!(managedModel.modelInstance() instanceof BaseModelInstance baseModel)) {
                deactivateDesktopCameraState();
                return;
            }
            preparingModel = baseModel;

            long modelHandle = baseModel.getModelHandle();
            if (modelHandle == 0L) {
                deactivateDesktopCameraState();
                return;
            }

            ModelConfigData modelConfig = ModelConfigManager.getConfig(managedModel.requestKey().modelName());
            float combinedScale = managedModel.renderProperties().modelScale() * modelConfig.modelScale;
            preRender(modelHandle, combinedScale, true);

            float safePartialTick = Float.isFinite(partialTick) ? Mth.clamp(partialTick, 0.0f, 1.0f) : 0.0f;
            exitVrForDesktopPreparation(player, managedModel, baseModel, modelHandle);
            AnimationStateManager.updateAnimationState(player, managedModel);
            MutableRenderPose pose = PlayerRenderHelper.calculateMutableRenderPose(player, managedModel, safePartialTick);
            if (!baseModel.prepareFirstPersonPose(player, pose.bodyYaw, safePartialTick)) {
                deactivateDesktopCameraState();
                return;
            }

            if (!cacheCameraAnchor(modelHandle)) {
                baseModel.discardPreparedFirstPersonPose();
                deactivateDesktopCameraState();
                return;
            }

            preparedFirstPersonModel = baseModel;
        } catch (RuntimeException | LinkageError e) {
            if (preparingModel != null) {
                preparingModel.discardPreparedFirstPersonPose();
            }
            deactivateDesktopCameraState();
            logger.warn("Failed to prepare current first-person camera frame", e);
        }
    }

    public static void preRender(long modelHandle, float modelScale, boolean isLocalPlayer) {
        if (!isLocalPlayer) return;

        if (modelHandle != trackedModelHandle) {
            trackedModelHandle = modelHandle;
            clearEyeBoneState();
        }

        boolean desktopFirstPerson = shouldRenderFirstPerson();
        boolean vrModelActive = isLocalVrMmdModelActive();
        activeDesktopFirstPerson = desktopFirstPerson;
        activeVrEyeCamera = vrModelActive
                && isVrFirstPersonRequested()
                && vrRuntimePort.isLocalPlayerEyePass();

        // 可见性由单次 RenderScene Draw 决定，这里只维护相机相关状态。
        if (desktopFirstPerson || vrModelActive) {
            cachedModelScale = modelScale;
        }
    }

    public static void postRender(long modelHandle, Player player, float tickDelta) {
        if (activeDesktopFirstPerson && modelHandle != 0) {
            try {
                if (!cacheCameraAnchor(modelHandle)) {
                    clearEyeBoneState();
                }
            } catch (Exception | LinkageError e) {
                logger.warn("GetFirstPersonCameraAnchorPosition failed for model {}", modelHandle, e);
                clearEyeBoneState();
            }
        } else {
            clearEyeBoneState();
        }
        updateVrModelRootOffset(player, tickDelta);
    }

    public static boolean isActive() {
        ensureActiveState();
        return activeDesktopFirstPerson;
    }

    public static boolean isEyeCameraActive() {
        ensureActiveState();
        return activeDesktopFirstPerson || activeVrEyeCamera;
    }

    public static boolean isVrEyeCameraActive() {
        ensureActiveState();
        return activeVrEyeCamera;
    }

    public static boolean isEyeBoneValid() {
        return eyeBoneValid;
    }

    public static Vec3 getLocalVrModelRootOffset(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player == null || minecraft.player == null || !minecraft.player.getUUID().equals(player.getUUID())) {
            return Vec3.ZERO;
        }
        return vrModelRootOffsetValid ? vrModelRootOffset : Vec3.ZERO;
    }

    public static void getEyeWorldOffset(float[] out) {
        float scale = MODEL_SCALE * cachedModelScale;
        out[0] = eyeBonePos[0] * scale;
        out[1] = eyeBonePos[1] * scale;
        out[2] = eyeBonePos[2] * scale;
    }

    public static Vec3 getRotatedEyePosition(Entity entity, float partialTick) {
        float[] eyeOffset = new float[3];
        getEyeWorldOffset(eyeOffset);
        Vec3 renderOrigin = entity instanceof Player player
                ? fallbackRenderOrigin(player, partialTick)
                : new Vec3(
                        Mth.lerp(partialTick, entity.xo, entity.getX()),
                        Mth.lerp(partialTick, entity.yo, entity.getY()),
                        Mth.lerp(partialTick, entity.zo, entity.getZ())
                );
        if (entity instanceof Player player) {
            renderOrigin = renderOrigin.add(getLocalVrModelRootOffset(player));
        }
        double px = renderOrigin.x;
        double py = renderOrigin.y;
        double pz = renderOrigin.z;

        float bodyYaw = entity instanceof Player player
                ? resolveFirstPersonModelYaw(player, partialTick, fallbackBodyYawDegrees(player, partialTick))
                : entity instanceof LivingEntity livingEntity
                ? Mth.rotLerp(partialTick, livingEntity.yBodyRotO, livingEntity.yBodyRot)
                : Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float yawRad = (float) Math.toRadians(bodyYaw);
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);
        double worldOffX = eyeOffset[0] * cosYaw - eyeOffset[2] * sinYaw;
        double worldOffZ = eyeOffset[0] * sinYaw + eyeOffset[2] * cosYaw;
        return new Vec3(px + worldOffX, py + eyeOffset[1], pz + worldOffZ);
    }

    /** TaCZ 第一人称枪械以镜头为基准，姿态、眼位和模型根必须使用同一个 yaw。 */
    public static float resolveFirstPersonModelYaw(Player player, float tickDelta, float fallbackYaw) {
        if (player != null && activeDesktopFirstPerson && TaczGunDetector.isGun(player.getMainHandItem())) {
            float viewYaw = player.getViewYRot(tickDelta);
            if (Float.isFinite(viewYaw)) {
                return viewYaw;
            }
        }
        return fallbackYaw;
    }

    public static Vec3 getVanillaEyePosition(LivingEntity entity, float partialTick) {
        double px = Mth.lerp(partialTick, entity.xo, entity.getX());
        double py = Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getEyeHeight();
        double pz = Mth.lerp(partialTick, entity.zo, entity.getZ());
        return new Vec3(px, py, pz);
    }

    public static boolean shouldUseVanillaReachValidation(LivingEntity entity) {
        if (!isActive() || !isEyeBoneValid()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.getUUID().equals(entity.getUUID())) return false;
        return mc.options.getCameraType() == CameraType.FIRST_PERSON;
    }

    public static Vec3 getVrCameraPosition(Entity entity, float partialTick) {
        if (!(entity instanceof Player player)) {
            return getRotatedEyePosition(entity, partialTick);
        }
        Vec3 headRenderPos = vrRuntimePort.getWorldRenderHeadPosition(player);
        if (headRenderPos == null) {
            return getRotatedEyePosition(entity, partialTick);
        }
        return headRenderPos;
    }

    public static void reset() {
        discardPreparedFirstPersonPose();
        activeDesktopFirstPerson = false;
        activeVrEyeCamera = false;
        trackedModelHandle = 0;
        cachedModelScale = 1.0f;
        clearEyeBoneState();
        clearVrModelRootOffset();
        lastCameraPos = Vec3.ZERO;
    }

    private static void ensureActiveState() {
        if (!activeDesktopFirstPerson && !activeVrEyeCamera) return;

        boolean desktopFirstPerson = shouldRenderFirstPerson();
        boolean vrModelActive = isLocalVrMmdModelActive();
        boolean vrEyeCamera = vrModelActive && isVrFirstPersonRequested() && vrRuntimePort.isLocalPlayerEyePass();
        if (desktopFirstPerson || vrEyeCamera) return;

        if (vrModelActive) {
            activeDesktopFirstPerson = false;
            activeVrEyeCamera = false;
            lastCameraPos = Vec3.ZERO;
            return;
        }

        reset();
    }

    private static boolean isLocalVrMmdModelActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && vrRuntimePort.isLocalPlayerInVr()
                && MmdSkinRendererPlayerHelper.isUsingMmdModel(minecraft.player);
    }

    private static boolean isVrFirstPersonRequested() {
        return Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON;
    }

    private static void syncVrCameraActivationState() {
        // 切入 VR 时只刷新激活标志，保留 VR 正常使用的锚点与模型缩放。
        activeDesktopFirstPerson = false;
        activeVrEyeCamera = isVrFirstPersonRequested() && vrRuntimePort.isLocalPlayerEyePass();
    }

    private static void exitVrForDesktopPreparation(AbstractClientPlayer player,
                                                    ManagedModel managedModel,
                                                    BaseModelInstance baseModel,
                                                    long modelHandle) {
        vrRuntimePort.applyMmdRenderState(false);
        if (!baseModel.isVrActive()) {
            return;
        }

        // 与正式渲染协调器保持相同顺序，确保普通动画基于非 VR 姿态求值。
        vrRuntimePort.setModelVrEnabled(modelHandle, false);
        baseModel.setVrActive(false);
        MmdSkinRendererPlayerHelper.resetModelAnimationState(player, managedModel);
    }

    private static Vec3 fallbackRenderOrigin(Player player, float tickDelta) {
        Vec3 renderOrigin = vrRuntimePort.getRenderOrigin(player, tickDelta);
        if (renderOrigin != null) return renderOrigin;
        return new Vec3(
                Mth.lerp(tickDelta, player.xo, player.getX()),
                Mth.lerp(tickDelta, player.yo, player.getY()),
                Mth.lerp(tickDelta, player.zo, player.getZ())
        );
    }

    private static float fallbackBodyYawDegrees(Player player, float tickDelta) {
        float bodyYaw = vrRuntimePort.getBodyYawDegrees(player, tickDelta);
        if (Float.isFinite(bodyYaw)) return bodyYaw;
        return Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot);
    }

    private static void updateVrModelRootOffset(Player player, float tickDelta) {
        if (player == null || !eyeBoneValid || !isLocalVrMmdModelActive()) return;
        Vec3 headRenderPos = vrRuntimePort.getWorldRenderHeadPosition(player);
        if (headRenderPos == null) return;
        Vec3 avatarEyePos = getRotatedEyePosition(player, tickDelta);
        double correctedY = Mth.clamp(vrModelRootOffset.y + (headRenderPos.y - avatarEyePos.y), -2.5d, 2.5d);
        vrModelRootOffset = new Vec3(0.0d, correctedY, 0.0d);
        vrModelRootOffsetValid = true;
    }

    private static boolean isStageCameraActive() {
        try {
            return MMDCameraController.getInstance().isActive();
        } catch (RuntimeException | LinkageError e) {
            logger.debug("Stage camera state is not ready during first-person preparation", e);
            return false;
        }
    }

    private static boolean cacheCameraAnchor(long modelHandle) {
        preparedEyeBonePos[0] = 0.0f;
        preparedEyeBonePos[1] = 0.0f;
        preparedEyeBonePos[2] = 0.0f;
        modelPort.getFirstPersonCameraAnchorPosition(modelHandle, preparedEyeBonePos);
        if (!isValidCameraAnchor(preparedEyeBonePos)) {
            return false;
        }
        System.arraycopy(preparedEyeBonePos, 0, eyeBonePos, 0, eyeBonePos.length);
        eyeBoneValid = true;
        return true;
    }

    private static boolean isValidCameraAnchor(float[] anchor) {
        if (anchor == null || anchor.length < 3) return false;
        if (!Float.isFinite(anchor[0]) || !Float.isFinite(anchor[1]) || !Float.isFinite(anchor[2])) return false;
        return anchor[0] != 0.0f || anchor[1] != 0.0f || anchor[2] != 0.0f;
    }

    private static void discardPreparedFirstPersonPose() {
        if (preparedFirstPersonModel != null) {
            preparedFirstPersonModel.discardPreparedFirstPersonPose();
            preparedFirstPersonModel = null;
        }
    }

    private static void deactivateDesktopCameraState() {
        discardPreparedFirstPersonPose();
        activeDesktopFirstPerson = false;
        activeVrEyeCamera = false;
        trackedModelHandle = 0L;
        cachedModelScale = 1.0f;
        clearEyeBoneState();
        lastCameraPos = Vec3.ZERO;
    }

    private static void clearEyeBoneState() {
        eyeBonePos[0] = 0.0f;
        eyeBonePos[1] = 0.0f;
        eyeBonePos[2] = 0.0f;
        eyeBoneValid = false;
    }

    private static void clearVrModelRootOffset() {
        vrModelRootOffset = Vec3.ZERO;
        vrModelRootOffsetValid = false;
    }
}
