package com.shiroha.mmdskin.renderer.runtime.model.helper;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;

/**
 * 光照计算工具类。
 */
public final class LightingHelper {

    private LightingHelper() {}

    public record LightData(int blockLight, int skyLight, float skyDarken, float intensity) {}

    private static final LightData DEFAULT_LIGHT = new LightData(0, 15, 0, 1.0f);

    public static LightData sampleLight(Entity entity, Minecraft mc) {
        if (mc.level == null) return DEFAULT_LIGHT;
        mc.level.updateSkyBrightness();
        int eyeHeight = (int) (entity.getEyeY() - entity.getBlockY());
        int blockLight = entity.level().getBrightness(LightLayer.BLOCK, entity.blockPosition().above(eyeHeight));
        int skyLight = entity.level().getBrightness(LightLayer.SKY, entity.blockPosition().above(eyeHeight));
        float skyDarken = mc.level.getSkyDarken();

        float blockLightFactor = blockLight / 15.0f;
        float skyLightFactor = (skyLight / 15.0f) * ((15.0f - skyDarken) / 15.0f);
        float lightIntensity = Math.max(blockLightFactor, skyLightFactor);

        lightIntensity = 0.1f + lightIntensity * 0.9f;

        return new LightData(blockLight, skyLight, skyDarken, lightIntensity);
    }
}
