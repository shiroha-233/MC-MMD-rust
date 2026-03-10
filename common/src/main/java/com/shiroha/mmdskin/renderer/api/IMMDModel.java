package com.shiroha.mmdskin.renderer.api;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

/**
 * MMD 模型接口。
 */
public interface IMMDModel {

    void render(Entity entityIn, float entityYaw, float entityPitch,
            Vector3f entityTrans, float tickDelta, PoseStack mat, int packedLight,
            RenderContext context);

    void changeAnim(long anim, long layer);

    void transitionAnim(long anim, long layer, float transitionTime);

    default void setLayerLoop(long layer, boolean loop) {}

    void resetPhysics();

    long getModelHandle();

    String getModelDir();

    default String getModelName() {
        String dir = getModelDir();
        if (dir == null || dir.isEmpty()) return "";
        dir = dir.replace('\\', '/');
        if (dir.endsWith("/")) dir = dir.substring(0, dir.length() - 1);
        int lastSlash = dir.lastIndexOf('/');
        return lastSlash >= 0 ? dir.substring(lastSlash + 1) : dir;
    }

    boolean setLayerBoneMask(int layer, String rootBoneName);

    boolean setLayerBoneExclude(int layer, String rootBoneName);

    void dispose();

    default long getVramUsage() { return 0; }

    long getRamUsage();
}
