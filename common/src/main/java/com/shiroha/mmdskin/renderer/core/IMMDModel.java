package com.shiroha.mmdskin.renderer.core;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

/**
 * MMD 模型接口
 */
public interface IMMDModel {
    
    void render(Entity entityIn, float entityYaw, float entityPitch, 
            Vector3f entityTrans, float tickDelta, PoseStack mat, int packedLight,
            RenderContext context);

    void changeAnim(long anim, long layer);
    
    void transitionAnim(long anim, long layer, float transitionTime);

    /**
     * 设置动画层是否循环播放
     * @param layer 动画层
     * @param loop true=循环，false=播放到尾帧后停留
     */
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
    
    default boolean setLayerBoneMask(int layer, String rootBoneName) {
        return com.shiroha.mmdskin.NativeFunc.GetInst()
                .SetLayerBoneMask(getModelHandle(), layer, rootBoneName);
    }

    default boolean setLayerBoneExclude(int layer, String rootBoneName) {
        return com.shiroha.mmdskin.NativeFunc.GetInst()
                .SetLayerBoneExclude(getModelHandle(), layer, rootBoneName);
    }

    void dispose();
    
    default long getVramUsage() { return 0; }
    
    default long getRamUsage() {
        try {
            return com.shiroha.mmdskin.NativeFunc.GetInst().GetModelMemoryUsage(getModelHandle());
        } catch (Exception e) {
            return 0;
        }
    }
}
