package com.shiroha.mmdskin.compat.vr;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** 文件职责：装载 Vivecraft 反射绑定元数据。 */
record VivecraftBindings(
        Method vrApiInstanceMethod,
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
        Object modelArmsModeOff
) {
    private static final Logger LOGGER = LogManager.getLogger();

    @SuppressWarnings("unchecked")
    static VivecraftBindings load() throws Exception {
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

        return new VivecraftBindings(
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
    }

    @SuppressWarnings("unchecked")
    private static Object enumConstant(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
    }
}
