package com.shiroha.mmdskin.compat.vr;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 文件职责：提供 Vivecraft 运行时的反射兼容桥接。
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
        private final java.lang.reflect.Method vrApiInstanceMethod;
        private final java.lang.reflect.Method vrApiIsVrPlayerMethod;
        private final java.lang.reflect.Method vrApiGetVrPoseMethod;
        private final java.lang.reflect.Method vrClientApiInstanceMethod;
        private final java.lang.reflect.Method vrClientIsVrActiveMethod;
        private final java.lang.reflect.Method vrClientGetPreTickWorldPoseMethod;
        private final java.lang.reflect.Method vrClientGetWorldRenderPoseMethod;
        private final java.lang.reflect.Method vrClientGetPostTickWorldPoseMethod;
        private final java.lang.reflect.Method vrRenderingApiInstanceMethod;
        private final java.lang.reflect.Method vrRenderingIsVanillaRenderPassMethod;
        private final java.lang.reflect.Method vrRenderingGetCurrentRenderPassMethod;
        private final java.lang.reflect.Method vrRenderingGetWorldRenderPoseMethod;
        private final Object renderPassLeft;
        private final Object renderPassRight;
        private final java.lang.reflect.Method gameRendererGetRvePosMethod;
        private final java.lang.reflect.Method clientDataHolderGetInstanceMethod;
        private final java.lang.reflect.Field clientDataHolderVrPlayerField;
        private final java.lang.reflect.Method gameplayVrPlayerGetVrDataWorldMethod;
        private final java.lang.reflect.Method vrDataGetBodyYawRadMethod;
        private final VivecraftRenderStateController renderStateController;
        private final VivecraftTrackingDataReader trackingDataReader;

        private Support(VivecraftBindings bindings) {
            this.vrApiInstanceMethod = bindings.vrApiInstanceMethod();
            this.vrApiIsVrPlayerMethod = bindings.vrApiIsVrPlayerMethod();
            this.vrApiGetVrPoseMethod = bindings.vrApiGetVrPoseMethod();
            this.vrClientApiInstanceMethod = bindings.vrClientApiInstanceMethod();
            this.vrClientIsVrActiveMethod = bindings.vrClientIsVrActiveMethod();
            this.vrClientGetPreTickWorldPoseMethod = bindings.vrClientGetPreTickWorldPoseMethod();
            this.vrClientGetWorldRenderPoseMethod = bindings.vrClientGetWorldRenderPoseMethod();
            this.vrClientGetPostTickWorldPoseMethod = bindings.vrClientGetPostTickWorldPoseMethod();
            this.vrRenderingApiInstanceMethod = bindings.vrRenderingApiInstanceMethod();
            this.vrRenderingIsVanillaRenderPassMethod = bindings.vrRenderingIsVanillaRenderPassMethod();
            this.vrRenderingGetCurrentRenderPassMethod = bindings.vrRenderingGetCurrentRenderPassMethod();
            this.vrRenderingGetWorldRenderPoseMethod = bindings.vrRenderingGetWorldRenderPoseMethod();
            this.renderPassLeft = bindings.renderPassLeft();
            this.renderPassRight = bindings.renderPassRight();
            this.gameRendererGetRvePosMethod = bindings.gameRendererGetRvePosMethod();
            this.clientDataHolderGetInstanceMethod = bindings.clientDataHolderGetInstanceMethod();
            this.clientDataHolderVrPlayerField = bindings.clientDataHolderVrPlayerField();
            this.gameplayVrPlayerGetVrDataWorldMethod = bindings.gameplayVrPlayerGetVrDataWorldMethod();
            this.vrDataGetBodyYawRadMethod = bindings.vrDataGetBodyYawRadMethod();
            this.renderStateController = new VivecraftRenderStateController(
                    bindings.vrSettingsInstanceField(),
                    bindings.showPlayerHandsField(),
                    bindings.shouldRenderSelfField(),
                    bindings.modelArmsModeField(),
                    bindings.modelArmsModeOff()
            );
            this.trackingDataReader = new VivecraftTrackingDataReader(
                    bindings.vrPoseGetHeadMethod(),
                    bindings.vrPoseGetMainHandMethod(),
                    bindings.vrPoseGetOffHandMethod(),
                    bindings.vrPoseIsLeftHandedMethod(),
                    bindings.vrBodyPartDataGetPosMethod(),
                    bindings.vrBodyPartDataGetRotationMethod()
            );
        }

        static Support tryCreate() {
            try {
                VivecraftBindings bindings = VivecraftBindings.load();
                LOGGER.info("Detected Vivecraft runtime API bridge");
                return new Support(bindings);
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
                if (!isLocalVrActive()
                        || vrRenderingApiInstanceMethod == null
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

        void applyMmdRenderState(boolean active) {
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
    }

    private record TrackingPoseResult(String source, Object pose) {
    }
}
