package com.shiroha.mmdskin.player.render;

import com.shiroha.mmdskin.compat.vr.VRDataProvider;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.render.scene.MutableRenderPose;
import net.minecraft.client.player.AbstractClientPlayer;
import java.util.Properties;
import org.joml.Vector3f;

/**
 * 玩家渲染辅助工具类（提取自 Fabric/Forge Mixin 的公共逻辑）。
 */
public final class PlayerRenderHelper {

    private PlayerRenderHelper() {}

    public static MutableRenderPose calculateMutableRenderPose(AbstractClientPlayer player, ManagedModel modelData, float tickDelta) {
        MutableRenderPose params = new MutableRenderPose();
        params.bodyYaw = VRDataProvider.getBodyYawDegrees(player, tickDelta);
        params.bodyPitch = 0.0f;
        params.translation.zero();

        if (player.isFallFlying()) {
            params.bodyPitch = player.getXRot() + getPropertyFloat(modelData, "flyingPitch", 0.0f);
            params.translation.set(getPropertyVector(modelData, "flyingTrans"));
        } else if (player.isSleeping()) {
            params.bodyYaw = player.getBedOrientation().toYRot() + 180.0f;
            params.bodyPitch = getPropertyFloat(modelData, "sleepingPitch", 0.0f);
            params.translation.set(getPropertyVector(modelData, "sleepingTrans"));
        } else if (player.isSwimming()) {
            params.bodyPitch = player.getXRot() + getPropertyFloat(modelData, "swimmingPitch", 0.0f);
            params.translation.set(getPropertyVector(modelData, "swimmingTrans"));
        } else if (player.isVisuallyCrawling()) {
            params.bodyPitch = getPropertyFloat(modelData, "crawlingPitch", 0.0f);
            params.translation.set(getPropertyVector(modelData, "crawlingTrans"));
        }

        return params;
    }

    public static float[] getModelSize(ManagedModel modelData) {
        return new float[] {
                modelData.renderProperties().modelScale(),
                modelData.renderProperties().inventoryScale()
        };
    }

    public static float getPropertyFloat(ManagedModel modelData, String key, float defaultValue) {
        String value = propertyValue(modelData.properties, key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public static Vector3f getPropertyVector(ManagedModel modelData, String key) {
        String value = propertyValue(modelData.properties, key);
        if (value == null) {
            return new Vector3f();
        }
        return com.shiroha.mmdskin.util.VectorParseUtil.parseVec3f(value);
    }

    private static String propertyValue(Properties properties, String key) {
        return properties != null ? properties.getProperty(key) : null;
    }
}
