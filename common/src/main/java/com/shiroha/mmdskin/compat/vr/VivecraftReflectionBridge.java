package com.shiroha.mmdskin.compat.vr;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Vivecraft 运行时反射桥接。
 * 仅在客户端渲染链路中使用，避免把 Vivecraft 设为硬依赖。
 */
public final class VivecraftReflectionBridge {

    private static final Logger LOGGER = LogManager.getLogger();

    private static volatile Support support;
    private static volatile boolean initialized;

    private VivecraftReflectionBridge() {
    }

    public static boolean isAvailable() {
        return getSupport() != null;
    }

    public static boolean isVRPlayer(Player player) {
        Support activeSupport = getSupport();
        return activeSupport != null && activeSupport.isVRPlayer(player);
    }

    public static float[] getTrackingData(Player player) {
        Support activeSupport = getSupport();
        return activeSupport != null ? activeSupport.getTrackingData(player) : null;
    }

    public static float getBodyYawRadians(Player player) {
        Support activeSupport = getSupport();
        return activeSupport != null ? activeSupport.getBodyYawRadians(player) : Float.NaN;
    }

    public static boolean isLocalPlayerEyePass() {
        Support activeSupport = getSupport();
        return activeSupport != null && activeSupport.isLocalPlayerEyePass();
    }

    public static Vec3 getWorldRenderHeadPosition(Player player) {
        Support activeSupport = getSupport();
        return activeSupport != null ? activeSupport.getWorldRenderHeadPosition(player) : null;
    }

    public static Vec3 getLocalPlayerRenderOrigin(float partialTick) {
        Support activeSupport = getSupport();
        return activeSupport != null ? activeSupport.getLocalPlayerRenderOrigin(partialTick) : null;
    }

    public static void applyMmdRenderState(boolean active) {
        Support activeSupport = getSupport();
        if (activeSupport != null) {
            activeSupport.applyMmdRenderState(active);
        }
    }

    private static Support getSupport() {
        if (initialized) {
            return support;
        }

        synchronized (VivecraftReflectionBridge.class) {
            if (initialized) {
                return support;
            }

            support = Support.tryCreate();
            initialized = true;
            return support;
        }
    }

    private static final class Support {

        private static final float EPSILON = 1.0e-4f;

        private final Method vrApiInstanceMethod;
        private final Method vrApiIsVrPlayerMethod;
        private final Method vrApiGetVrPoseMethod;

        private final Method vrClientApiInstanceMethod;
        private final Method vrClientIsVrActiveMethod;
        private final Method vrClientGetPreTickWorldPoseMethod;
        private final Method vrClientGetWorldRenderPoseMethod;
        private final Method vrClientGetPostTickWorldPoseMethod;

        private final Method vrRenderingApiInstanceMethod;
        private final Method vrRenderingIsVanillaRenderPassMethod;
        private final Method vrRenderingGetCurrentRenderPassMethod;
        private final Method vrRenderingGetWorldRenderPoseMethod;
        private final Object renderPassLeft;
        private final Object renderPassRight;

        private final Method gameRendererGetRvePosMethod;

        private final Method vrPoseGetHeadMethod;
        private final Method vrPoseGetMainHandMethod;
        private final Method vrPoseGetOffHandMethod;
        private final Method vrPoseIsLeftHandedMethod;

        private final Method vrBodyPartDataGetPosMethod;
        private final Method vrBodyPartDataGetRotationMethod;

        private final Method clientDataHolderGetInstanceMethod;
        private final Field clientDataHolderVrPlayerField;
        private final Method gameplayVrPlayerGetVrDataWorldMethod;
        private final Method vrDataGetBodyYawRadMethod;

        private final VivecraftRenderStateController renderStateController;
        private final VivecraftTrackingDataReader trackingDataReader;

        private Support(Method vrApiInstanceMethod,
                        Method vrApiIsVrPlayerMethod,
                        Method vrApiGetVrPoseMethod,
                        Method vrClientApiInstanceMethod,
                        Method vrClientIsVrActiveMethod,
                        Method vrClientGetPreTickWorldPoseMethod,
                        Method vrClientGetWorldRenderPoseMethod,
                        Method vrClientGetPostTickWorldPoseMethod,
                        Method vrRenderingApiInstanceMethod,
                        Method vrRenderingIsVanillaRenderPassMethod,
                        Method vrRenderingGetCurrentRenderPassMethod,
                        Method vrRenderingGetWorldRenderPoseMethod,
                        Object renderPassLeft,
                        Object renderPassRight,
                        Method gameRendererGetRvePosMethod,
                        Method vrPoseGetHeadMethod,
                        Method vrPoseGetMainHandMethod,
                        Method vrPoseGetOffHandMethod,
                        Method vrPoseIsLeftHandedMethod,
                        Method vrBodyPartDataGetPosMethod,
                        Method vrBodyPartDataGetRotationMethod,
                        Method clientDataHolderGetInstanceMethod,
                        Field clientDataHolderVrPlayerField,
                        Method gameplayVrPlayerGetVrDataWorldMethod,
                        Method vrDataGetBodyYawRadMethod,
                        Field vrSettingsInstanceField,
                        Field showPlayerHandsField,
                        Field shouldRenderSelfField,
                        Field modelArmsModeField,
                        Object modelArmsModeOff) {
            this.vrApiInstanceMethod = vrApiInstanceMethod;
            this.vrApiIsVrPlayerMethod = vrApiIsVrPlayerMethod;
            this.vrApiGetVrPoseMethod = vrApiGetVrPoseMethod;
            this.vrClientApiInstanceMethod = vrClientApiInstanceMethod;
            this.vrClientIsVrActiveMethod = vrClientIsVrActiveMethod;
            this.vrClientGetPreTickWorldPoseMethod = vrClientGetPreTickWorldPoseMethod;
            this.vrClientGetWorldRenderPoseMethod = vrClientGetWorldRenderPoseMethod;
            this.vrClientGetPostTickWorldPoseMethod = vrClientGetPostTickWorldPoseMethod;
            this.vrRenderingApiInstanceMethod = vrRenderingApiInstanceMethod;
            this.vrRenderingIsVanillaRenderPassMethod = vrRenderingIsVanillaRenderPassMethod;
            this.vrRenderingGetCurrentRenderPassMethod = vrRenderingGetCurrentRenderPassMethod;
            this.vrRenderingGetWorldRenderPoseMethod = vrRenderingGetWorldRenderPoseMethod;
            this.renderPassLeft = renderPassLeft;
            this.renderPassRight = renderPassRight;
            this.gameRendererGetRvePosMethod = gameRendererGetRvePosMethod;
            this.vrPoseGetHeadMethod = vrPoseGetHeadMethod;
            this.vrPoseGetMainHandMethod = vrPoseGetMainHandMethod;
            this.vrPoseGetOffHandMethod = vrPoseGetOffHandMethod;
            this.vrPoseIsLeftHandedMethod = vrPoseIsLeftHandedMethod;
            this.vrBodyPartDataGetPosMethod = vrBodyPartDataGetPosMethod;
            this.vrBodyPartDataGetRotationMethod = vrBodyPartDataGetRotationMethod;
            this.clientDataHolderGetInstanceMethod = clientDataHolderGetInstanceMethod;
            this.clientDataHolderVrPlayerField = clientDataHolderVrPlayerField;
            this.gameplayVrPlayerGetVrDataWorldMethod = gameplayVrPlayerGetVrDataWorldMethod;
            this.vrDataGetBodyYawRadMethod = vrDataGetBodyYawRadMethod;
            this.renderStateController = new VivecraftRenderStateController(
                    vrSettingsInstanceField,
                    showPlayerHandsField,
                    shouldRenderSelfField,
                    modelArmsModeField,
                    modelArmsModeOff
            );
            this.trackingDataReader = new VivecraftTrackingDataReader(
                    vrPoseGetHeadMethod,
                    vrPoseGetMainHandMethod,
                    vrPoseGetOffHandMethod,
                    vrPoseIsLeftHandedMethod,
                    vrBodyPartDataGetPosMethod,
                    vrBodyPartDataGetRotationMethod
            );
        }

        @SuppressWarnings("unchecked")
        static Support tryCreate() {
            try {
                VivecraftBindings bindings = VivecraftBindings.load();

                LOGGER.info("Detected Vivecraft runtime API bridge");
                return new Support(
                        bindings.vrApiInstanceMethod(),
                        bindings.vrApiIsVrPlayerMethod(),
                        bindings.vrApiGetVrPoseMethod(),
                        bindings.vrClientApiInstanceMethod(),
                        bindings.vrClientIsVrActiveMethod(),
                        bindings.vrClientGetPreTickWorldPoseMethod(),
                        bindings.vrClientGetWorldRenderPoseMethod(),
                        bindings.vrClientGetPostTickWorldPoseMethod(),
                        bindings.vrRenderingApiInstanceMethod(),
                        bindings.vrRenderingIsVanillaRenderPassMethod(),
                        bindings.vrRenderingGetCurrentRenderPassMethod(),
                        bindings.vrRenderingGetWorldRenderPoseMethod(),
                        bindings.renderPassLeft(),
                        bindings.renderPassRight(),
                        bindings.gameRendererGetRvePosMethod(),
                        bindings.vrPoseGetHeadMethod(),
                        bindings.vrPoseGetMainHandMethod(),
                        bindings.vrPoseGetOffHandMethod(),
                        bindings.vrPoseIsLeftHandedMethod(),
                        bindings.vrBodyPartDataGetPosMethod(),
                        bindings.vrBodyPartDataGetRotationMethod(),
                        bindings.clientDataHolderGetInstanceMethod(),
                        bindings.clientDataHolderVrPlayerField(),
                        bindings.gameplayVrPlayerGetVrDataWorldMethod(),
                        bindings.vrDataGetBodyYawRadMethod(),
                        bindings.vrSettingsInstanceField(),
                        bindings.showPlayerHandsField(),
                        bindings.shouldRenderSelfField(),
                        bindings.modelArmsModeField(),
                        bindings.modelArmsModeOff()
                );
            } catch (Throwable t) {
                LOGGER.debug("Vivecraft runtime API bridge unavailable", t);
                return null;
            }
        }

        boolean isVRPlayer(Player player) {
            if (player == null) {
                return false;
            }

            try {
                if (isLocalPlayer(player)) {
                    return isLocalVrActive();
                }

                Object vrApi = vrApiInstanceMethod.invoke(null);
                return vrApi != null && (boolean) vrApiIsVrPlayerMethod.invoke(vrApi, player);
            } catch (Throwable t) {
                LOGGER.debug("Failed to query Vivecraft VR player state", t);
                return false;
            }
        }

        float getBodyYawRadians(Player player) {
            if (player == null || !isLocalPlayer(player)) {
                return Float.NaN;
            }

            try {
                if (!isLocalVrActive()) {
                    return Float.NaN;
                }

                Object clientDataHolder = clientDataHolderGetInstanceMethod.invoke(null);
                if (clientDataHolder == null) {
                    return Float.NaN;
                }

                Object vrPlayer = clientDataHolderVrPlayerField.get(clientDataHolder);
                if (vrPlayer == null) {
                    return Float.NaN;
                }

                Object vrDataWorld = gameplayVrPlayerGetVrDataWorldMethod.invoke(vrPlayer);
                if (vrDataWorld == null) {
                    return Float.NaN;
                }

                float yaw = ((Number) vrDataGetBodyYawRadMethod.invoke(vrDataWorld)).floatValue();
                return Float.isFinite(yaw) ? yaw : Float.NaN;
            } catch (Throwable t) {
                LOGGER.debug("Failed to query Vivecraft body yaw", t);
                return Float.NaN;
            }
        }

        boolean isLocalPlayerEyePass() {
            try {
                if (!isLocalVrActive() || vrRenderingApiInstanceMethod == null
                        || vrRenderingIsVanillaRenderPassMethod == null
                        || vrRenderingGetCurrentRenderPassMethod == null
                        || renderPassLeft == null
                        || renderPassRight == null) {
                    return false;
                }

                Object renderingApi = vrRenderingApiInstanceMethod.invoke(null);
                if (renderingApi == null) {
                    return false;
                }

                boolean vanillaRenderPass = (boolean) vrRenderingIsVanillaRenderPassMethod.invoke(renderingApi);
                if (vanillaRenderPass) {
                    return false;
                }

                Object currentPass = vrRenderingGetCurrentRenderPassMethod.invoke(renderingApi);
                return currentPass == renderPassLeft || currentPass == renderPassRight;
            } catch (Throwable t) {
                LOGGER.debug("Failed to query Vivecraft render pass", t);
                return false;
            }
        }

        Vec3 getWorldRenderHeadPosition(Player player) {
            if (player == null) {
                return null;
            }

            try {
                Object pose = null;
                if (vrRenderingApiInstanceMethod != null && vrRenderingGetWorldRenderPoseMethod != null) {
                    Object renderingApi = vrRenderingApiInstanceMethod.invoke(null);
                    if (renderingApi != null) {
                        pose = vrRenderingGetWorldRenderPoseMethod.invoke(renderingApi, player);
                    }
                }

                if (pose == null && isLocalPlayer(player)) {
                    Object clientApi = vrClientApiInstanceMethod.invoke(null);
                    if (clientApi != null) {
                        pose = vrClientGetWorldRenderPoseMethod.invoke(clientApi);
                    }
                }

                if (pose == null) {
                    Object vrApi = vrApiInstanceMethod.invoke(null);
                    if (vrApi != null) {
                        pose = vrApiGetVrPoseMethod.invoke(vrApi, player);
                    }
                }

                return trackingDataReader.extractHeadPosition(pose);
            } catch (Throwable t) {
                LOGGER.debug("Failed to read Vivecraft world-render head pose", t);
                return null;
            }
        }

        Vec3 getLocalPlayerRenderOrigin(float partialTick) {
            try {
                if (!isLocalVrActive() || gameRendererGetRvePosMethod == null || isVanillaRenderPass()) {
                    return null;
                }

                Object renderOrigin = gameRendererGetRvePosMethod.invoke(Minecraft.getInstance().gameRenderer, partialTick);
                return renderOrigin instanceof Vec3 vec3 ? vec3 : null;
            } catch (Throwable t) {
                LOGGER.debug("Failed to read Vivecraft render-view entity origin", t);
                return null;
            }
        }

        float[] getTrackingData(Player player) {
            if (player == null) {
                return null;
            }

            try {
                TrackingPoseResult result = resolveTrackingPose(player);
                if (result == null || result.pose == null) {
                    trackingDataReader.logMissingTracking("no_pose");
                    return null;
                }

                float[] data = trackingDataReader.poseToTrackingPacket(result.pose);
                if (!trackingDataReader.isPacketUsable(data)) {
                    trackingDataReader.logMissingTracking(result.source + "_empty");
                    return null;
                }

                if (!result.source.equals(trackingDataReader.lastTrackingSource())) {
                    LOGGER.info("Using Vivecraft tracking source: {}", result.source);
                    trackingDataReader.recordTrackingSource(result.source);
                }
                trackingDataReader.clearMissingTrackingFlag();
                return data;
            } catch (Throwable t) {
                LOGGER.debug("Failed to read Vivecraft tracking data", t);
                trackingDataReader.logMissingTracking("exception");
                return null;
            }
        }

        synchronized void applyMmdRenderState(boolean active) {
            renderStateController.apply(active);
        }

        private TrackingPoseResult resolveTrackingPose(Player player) throws Exception {
            if (isLocalPlayer(player)) {
                if (!isLocalVrActive()) {
                    return null;
                }

                Object clientApi = vrClientApiInstanceMethod.invoke(null);
                Object worldRenderPose = vrClientGetWorldRenderPoseMethod.invoke(clientApi);
                if (trackingDataReader.isPoseUsable(worldRenderPose)) {
                    return new TrackingPoseResult("world_render", worldRenderPose);
                }

                Object postTickWorldPose = vrClientGetPostTickWorldPoseMethod.invoke(clientApi);
                if (trackingDataReader.isPoseUsable(postTickWorldPose)) {
                    return new TrackingPoseResult("post_tick_world", postTickWorldPose);
                }

                Object preTickWorldPose = vrClientGetPreTickWorldPoseMethod.invoke(clientApi);
                if (trackingDataReader.isPoseUsable(preTickWorldPose)) {
                    return new TrackingPoseResult("pre_tick_world", preTickWorldPose);
                }
            }

            Object vrApi = vrApiInstanceMethod.invoke(null);
            Object sharedPose = vrApiGetVrPoseMethod.invoke(vrApi, player);
            if (trackingDataReader.isPoseUsable(sharedPose)) {
                return new TrackingPoseResult(isLocalPlayer(player) ? "shared_world_pose" : "remote_pose", sharedPose);
            }

            return null;
        }

        private boolean isLocalVrActive() throws Exception {
            Object clientApi = vrClientApiInstanceMethod.invoke(null);
            return clientApi != null && (boolean) vrClientIsVrActiveMethod.invoke(clientApi);
        }

        private boolean isVanillaRenderPass() throws Exception {
            if (vrRenderingApiInstanceMethod == null || vrRenderingIsVanillaRenderPassMethod == null) {
                return true;
            }

            Object renderingApi = vrRenderingApiInstanceMethod.invoke(null);
            return renderingApi == null || (boolean) vrRenderingIsVanillaRenderPassMethod.invoke(renderingApi);
        }

        private boolean isLocalPlayer(Player player) {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.player != null && minecraft.player.getUUID().equals(player.getUUID());
        }

        @SuppressWarnings("unchecked")
        static Object enumConstant(Class<?> enumClass, String name) {
            return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
        }
    }

    private record TrackingPoseResult(String source, Object pose) {
    }
}
