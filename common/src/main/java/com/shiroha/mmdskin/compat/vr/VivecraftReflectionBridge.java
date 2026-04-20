package com.shiroha.mmdskin.compat.vr;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Quaternionf;

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

        private final Field vrSettingsInstanceField;
        private final Field showPlayerHandsField;
        private final Field shouldRenderSelfField;
        private final Field modelArmsModeField;
        private final Object modelArmsModeOff;

        private boolean loggedMissingTracking;
        private String lastTrackingSource;

        private boolean renderStateCaptured;
        private boolean renderStateApplied;
        private boolean originalShowPlayerHands;
        private boolean originalShouldRenderSelf;
        private Object originalModelArmsMode;

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
            this.vrSettingsInstanceField = vrSettingsInstanceField;
            this.showPlayerHandsField = showPlayerHandsField;
            this.shouldRenderSelfField = shouldRenderSelfField;
            this.modelArmsModeField = modelArmsModeField;
            this.modelArmsModeOff = modelArmsModeOff;
        }

        @SuppressWarnings("unchecked")
        static Support tryCreate() {
            try {
                Class<?> vrApiClass = Class.forName("org.vivecraft.api.VRAPI");
                Class<?> vrClientApiClass = Class.forName("org.vivecraft.api.client.VRClientAPI");
                Class<?> vrPoseClass = Class.forName("org.vivecraft.api.data.VRPose");
                Class<?> vrBodyPartDataClass = Class.forName("org.vivecraft.api.data.VRBodyPartData");
                Class<?> clientDataHolderClass = Class.forName("org.vivecraft.client_vr.ClientDataHolderVR");
                Class<?> gameplayVrPlayerClass = Class.forName("org.vivecraft.client_vr.gameplay.VRPlayer");
                Class<?> vrDataClass = Class.forName("org.vivecraft.client_vr.VRData");
                Class<?> vrSettingsClass = Class.forName("org.vivecraft.client_vr.settings.VRSettings");
                Class<?> modelArmsModeClass = Class.forName("org.vivecraft.client_vr.settings.VRSettings$ModelArmsMode");

                Method vrApiInstanceMethod = vrApiClass.getMethod("instance");
                Method vrApiIsVrPlayerMethod = vrApiClass.getMethod("isVRPlayer", Player.class);
                Method vrApiGetVrPoseMethod = vrApiClass.getMethod("getVRPose", Player.class);

                Method vrClientApiInstanceMethod = vrClientApiClass.getMethod("instance");
                Method vrClientIsVrActiveMethod = vrClientApiClass.getMethod("isVRActive");
                Method vrClientGetPreTickWorldPoseMethod = vrClientApiClass.getMethod("getPreTickWorldPose");
                Method vrClientGetWorldRenderPoseMethod = vrClientApiClass.getMethod("getWorldRenderPose");
                Method vrClientGetPostTickWorldPoseMethod = vrClientApiClass.getMethod("getPostTickWorldPose");

                Method vrRenderingApiInstanceMethod = null;
                Method vrRenderingIsVanillaRenderPassMethod = null;
                Method vrRenderingGetCurrentRenderPassMethod = null;
                Method vrRenderingGetWorldRenderPoseMethod = null;
                Object renderPassLeft = null;
                Object renderPassRight = null;
                Method gameRendererGetRvePosMethod = null;
                try {
                    Class<?> vrRenderingApiClass = Class.forName("org.vivecraft.api.client.VRRenderingAPI");
                    Class<?> renderPassClass = Class.forName("org.vivecraft.api.client.data.RenderPass");
                    vrRenderingApiInstanceMethod = vrRenderingApiClass.getMethod("instance");
                    vrRenderingIsVanillaRenderPassMethod = vrRenderingApiClass.getMethod("isVanillaRenderPass");
                    vrRenderingGetCurrentRenderPassMethod = vrRenderingApiClass.getMethod("getCurrentRenderPass");
                    vrRenderingGetWorldRenderPoseMethod = vrRenderingApiClass.getMethod("getWorldRenderPose", Player.class);
                    renderPassLeft = enumConstant(renderPassClass, "LEFT");
                    renderPassRight = enumConstant(renderPassClass, "RIGHT");
                    gameRendererGetRvePosMethod = Minecraft.getInstance().gameRenderer.getClass()
                            .getMethod("vivecraft$getRvePos", float.class);
                } catch (Throwable t) {
                    LOGGER.debug("Vivecraft render-pass API unavailable", t);
                }

                Method vrPoseGetHeadMethod = vrPoseClass.getMethod("getHead");
                Method vrPoseGetMainHandMethod = vrPoseClass.getMethod("getMainHand");
                Method vrPoseGetOffHandMethod = vrPoseClass.getMethod("getOffHand");
                Method vrPoseIsLeftHandedMethod = vrPoseClass.getMethod("isLeftHanded");

                Method vrBodyPartDataGetPosMethod = vrBodyPartDataClass.getMethod("getPos");
                Method vrBodyPartDataGetRotationMethod = vrBodyPartDataClass.getMethod("getRotation");

                Method clientDataHolderGetInstanceMethod = clientDataHolderClass.getMethod("getInstance");
                Field clientDataHolderVrPlayerField = clientDataHolderClass.getField("vrPlayer");
                Method gameplayVrPlayerGetVrDataWorldMethod = gameplayVrPlayerClass.getMethod("getVRDataWorld");
                Method vrDataGetBodyYawRadMethod = vrDataClass.getMethod("getBodyYawRad");

                Field vrSettingsInstanceField = vrSettingsClass.getField("INSTANCE");
                Field showPlayerHandsField = vrSettingsClass.getField("showPlayerHands");
                Field shouldRenderSelfField = vrSettingsClass.getField("shouldRenderSelf");
                Field modelArmsModeField = vrSettingsClass.getField("modelArmsMode");
                Object modelArmsModeOff = Enum.valueOf((Class<? extends Enum>) modelArmsModeClass.asSubclass(Enum.class), "OFF");

                LOGGER.info("Detected Vivecraft runtime API bridge");
                return new Support(
                        vrApiInstanceMethod,
                        vrApiIsVrPlayerMethod,
                        vrApiGetVrPoseMethod,
                        vrClientApiInstanceMethod,
                        vrClientIsVrActiveMethod,
                        vrClientGetPreTickWorldPoseMethod,
                        vrClientGetWorldRenderPoseMethod,
                        vrClientGetPostTickWorldPoseMethod,
                        vrRenderingApiInstanceMethod,
                        vrRenderingIsVanillaRenderPassMethod,
                        vrRenderingGetCurrentRenderPassMethod,
                        vrRenderingGetWorldRenderPoseMethod,
                        renderPassLeft,
                        renderPassRight,
                        gameRendererGetRvePosMethod,
                        vrPoseGetHeadMethod,
                        vrPoseGetMainHandMethod,
                        vrPoseGetOffHandMethod,
                        vrPoseIsLeftHandedMethod,
                        vrBodyPartDataGetPosMethod,
                        vrBodyPartDataGetRotationMethod,
                        clientDataHolderGetInstanceMethod,
                        clientDataHolderVrPlayerField,
                        gameplayVrPlayerGetVrDataWorldMethod,
                        vrDataGetBodyYawRadMethod,
                        vrSettingsInstanceField,
                        showPlayerHandsField,
                        shouldRenderSelfField,
                        modelArmsModeField,
                        modelArmsModeOff
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

                return extractHeadPosition(pose);
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
                    logMissingTracking("no_pose");
                    return null;
                }

                float[] data = poseToTrackingPacket(result.pose);
                if (!isPacketUsable(data)) {
                    logMissingTracking(result.source + "_empty");
                    return null;
                }

                if (!result.source.equals(lastTrackingSource)) {
                    LOGGER.info("Using Vivecraft tracking source: {}", result.source);
                    lastTrackingSource = result.source;
                }
                loggedMissingTracking = false;
                return data;
            } catch (Throwable t) {
                LOGGER.debug("Failed to read Vivecraft tracking data", t);
                logMissingTracking("exception");
                return null;
            }
        }

        synchronized void applyMmdRenderState(boolean active) {
            try {
                Object settings = vrSettingsInstanceField.get(null);
                if (settings == null) {
                    return;
                }

                if (active) {
                    if (!renderStateCaptured) {
                        originalShowPlayerHands = showPlayerHandsField.getBoolean(settings);
                        originalShouldRenderSelf = shouldRenderSelfField.getBoolean(settings);
                        originalModelArmsMode = modelArmsModeField.get(settings);
                        renderStateCaptured = true;
                    }

                    showPlayerHandsField.setBoolean(settings, false);
                    shouldRenderSelfField.setBoolean(settings, true);
                    modelArmsModeField.set(settings, modelArmsModeOff);

                    if (!renderStateApplied) {
                        LOGGER.info("Applied Vivecraft hand visibility override for MMD VR mode");
                        renderStateApplied = true;
                    }
                    return;
                }

                if (!renderStateCaptured) {
                    return;
                }

                showPlayerHandsField.setBoolean(settings, originalShowPlayerHands);
                shouldRenderSelfField.setBoolean(settings, originalShouldRenderSelf);
                modelArmsModeField.set(settings, originalModelArmsMode);

                renderStateCaptured = false;
                renderStateApplied = false;
                LOGGER.info("Restored Vivecraft hand visibility settings");
            } catch (Throwable t) {
                LOGGER.debug("Failed to update Vivecraft render settings", t);
            }
        }

        private TrackingPoseResult resolveTrackingPose(Player player) throws Exception {
            if (isLocalPlayer(player)) {
                if (!isLocalVrActive()) {
                    return null;
                }

                Object clientApi = vrClientApiInstanceMethod.invoke(null);
                Object worldRenderPose = vrClientGetWorldRenderPoseMethod.invoke(clientApi);
                if (isPoseUsable(worldRenderPose)) {
                    return new TrackingPoseResult("world_render", worldRenderPose);
                }

                Object postTickWorldPose = vrClientGetPostTickWorldPoseMethod.invoke(clientApi);
                if (isPoseUsable(postTickWorldPose)) {
                    return new TrackingPoseResult("post_tick_world", postTickWorldPose);
                }

                Object preTickWorldPose = vrClientGetPreTickWorldPoseMethod.invoke(clientApi);
                if (isPoseUsable(preTickWorldPose)) {
                    return new TrackingPoseResult("pre_tick_world", preTickWorldPose);
                }
            }

            Object vrApi = vrApiInstanceMethod.invoke(null);
            Object sharedPose = vrApiGetVrPoseMethod.invoke(vrApi, player);
            if (isPoseUsable(sharedPose)) {
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

        private Vec3 extractHeadPosition(Object pose) throws Exception {
            if (pose == null) {
                return null;
            }

            Object head = vrPoseGetHeadMethod.invoke(pose);
            if (head == null) {
                return null;
            }

            return (Vec3) vrBodyPartDataGetPosMethod.invoke(head);
        }

        private float[] poseToTrackingPacket(Object pose) throws Exception {
            float[] data = new float[21];

            Object head = vrPoseGetHeadMethod.invoke(pose);
            Object mainHand = vrPoseGetMainHandMethod.invoke(pose);
            Object offHand = vrPoseGetOffHandMethod.invoke(pose);
            boolean leftHanded = (boolean) vrPoseIsLeftHandedMethod.invoke(pose);

            Object rightHand = leftHanded ? offHand : mainHand;
            Object leftHand = leftHanded ? mainHand : offHand;

            writeTrackingPoint(head, data, 0);
            writeTrackingPoint(rightHand, data, 7);
            writeTrackingPoint(leftHand, data, 14);
            return data;
        }

        private boolean isPoseUsable(Object pose) throws Exception {
            if (pose == null) {
                return false;
            }
            return isPacketUsable(poseToTrackingPacket(pose));
        }

        private boolean isPacketUsable(float[] data) {
            return isPointUsable(data, 0) && (isPointUsable(data, 7) || isPointUsable(data, 14));
        }

        private boolean isPointUsable(float[] data, int offset) {
            float px = Math.abs(data[offset]);
            float py = Math.abs(data[offset + 1]);
            float pz = Math.abs(data[offset + 2]);
            float qx = Math.abs(data[offset + 3]);
            float qy = Math.abs(data[offset + 4]);
            float qz = Math.abs(data[offset + 5]);
            float qw = Math.abs(data[offset + 6]);

            boolean hasPosition = px > EPSILON || py > EPSILON || pz > EPSILON;
            boolean hasRotation = qx > EPSILON || qy > EPSILON || qz > EPSILON || Math.abs(qw - 1.0f) > EPSILON;
            return hasPosition || hasRotation;
        }

        private void writeTrackingPoint(Object bodyPartData, float[] out, int offset) throws Exception {
            if (bodyPartData == null) {
                return;
            }

            Vec3 pos = (Vec3) vrBodyPartDataGetPosMethod.invoke(bodyPartData);
            out[offset] = (float) pos.x;
            out[offset + 1] = (float) pos.y;
            out[offset + 2] = (float) pos.z;

            Quaternionf rotation = new Quaternionf();
            rotation.set((org.joml.Quaternionfc) vrBodyPartDataGetRotationMethod.invoke(bodyPartData));
            rotation.normalize();
            out[offset + 3] = rotation.x;
            out[offset + 4] = rotation.y;
            out[offset + 5] = rotation.z;
            out[offset + 6] = rotation.w;
        }

        private void logMissingTracking(String reason) {
            if (!loggedMissingTracking) {
                LOGGER.warn("Vivecraft VR is active but no usable tracking pose was available ({})", reason);
                loggedMissingTracking = true;
            }
        }

        @SuppressWarnings("unchecked")
        private static Object enumConstant(Class<?> enumClass, String name) {
            return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
        }
    }

    private record TrackingPoseResult(String source, Object pose) {
    }
}
