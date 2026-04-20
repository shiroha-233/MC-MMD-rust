package com.shiroha.mmdskin.renderer.runtime.bridge;

import java.nio.ByteBuffer;

import net.minecraft.world.entity.player.Player;

/**
 * 渲染层使用的模型运行时桥接接口。
 */
public interface ModelRuntimeBridge {

    boolean setLayerBoneMask(long modelHandle, int layer, String rootBoneName);

    boolean setLayerBoneExclude(long modelHandle, int layer, String rootBoneName);

    long getModelMemoryUsage(long modelHandle);

    void populateHandMatrix(long modelHandle, long handMatrixHandle, boolean mainHand);

    boolean copyMatrixToBuffer(long matrixHandle, ByteBuffer targetBuffer);

    void preRenderFirstPerson(long modelHandle, float combinedScale, boolean isLocalPlayer);

    void postRenderFirstPerson(long modelHandle, Player player, float tickDelta);

    boolean isAndroid();

    int getMaterialCount(long modelHandle);

    void setMaterialVisible(long modelHandle, int materialIndex, boolean visible);

    void deleteModel(long modelHandle);
}
