package com.shiroha.mmdskin.model.runtime;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.render.scene.RenderScene;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

/** 文件职责：定义 MMD 运行时模型实例的统一能力边界。 */
public interface ModelInstance {

    void render(
            Entity entity,
            float entityYaw,
            float entityPitch,
            Vector3f entityTranslation,
            float tickDelta,
            PoseStack poseStack,
            int packedLight,
            RenderScene scene);

    void changeAnim(long animHandle, long layer);

    void transitionAnim(long animHandle, long layer, float transitionTime);

    default void setLayerLoop(long layer, boolean loop) {
    }

    void resetPhysics();

    long getModelHandle();

    String getModelDir();

    default String getModelName() {
        String dir = getModelDir();
        if (dir == null || dir.isEmpty()) {
            return "";
        }
        String normalized = dir.replace('\\', '/');
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    boolean setLayerBoneMask(int layer, String rootBoneName);

    boolean setLayerBoneExclude(int layer, String rootBoneName);

    void dispose();

    default long getVramUsage() {
        return 0L;
    }

    long getRamUsage();

    default long modelHandle() {
        return getModelHandle();
    }

    default String modelDir() {
        return getModelDir();
    }

    default String modelName() {
        return getModelName();
    }

    default long vramUsage() {
        return getVramUsage();
    }

    default long ramUsage() {
        return getRamUsage();
    }
}
