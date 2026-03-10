package com.shiroha.mmdskin.renderer.runtime.bridge;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;

import java.nio.ByteBuffer;

final class NativeModelRuntimeBridge implements ModelRuntimeBridge {

    private NativeFunc nativeFunc() {
        return NativeFunc.GetInst();
    }

    @Override
    public boolean setLayerBoneMask(long modelHandle, int layer, String rootBoneName) {
        return nativeFunc().SetLayerBoneMask(modelHandle, layer, rootBoneName);
    }

    @Override
    public boolean setLayerBoneExclude(long modelHandle, int layer, String rootBoneName) {
        return nativeFunc().SetLayerBoneExclude(modelHandle, layer, rootBoneName);
    }

    @Override
    public long getModelMemoryUsage(long modelHandle) {
        return nativeFunc().GetModelMemoryUsage(modelHandle);
    }

    @Override
    public void populateHandMatrix(long modelHandle, long handMatrixHandle, boolean mainHand) {
        if (mainHand) {
            nativeFunc().GetRightHandMat(modelHandle, handMatrixHandle);
            return;
        }
        nativeFunc().GetLeftHandMat(modelHandle, handMatrixHandle);
    }

    @Override
    public boolean copyMatrixToBuffer(long matrixHandle, ByteBuffer targetBuffer) {
        return nativeFunc().CopyMatToBuffer(matrixHandle, targetBuffer);
    }

    @Override
    public void preRenderFirstPerson(long modelHandle, float combinedScale, boolean isLocalPlayer) {
        FirstPersonManager.preRender(nativeFunc(), modelHandle, combinedScale, isLocalPlayer);
    }

    @Override
    public void postRenderFirstPerson(long modelHandle) {
        FirstPersonManager.postRender(nativeFunc(), modelHandle);
    }

    @Override
    public boolean isAndroid() {
        return NativeFunc.isAndroid();
    }

    @Override
    public int getMaterialCount(long modelHandle) {
        return (int) nativeFunc().GetMaterialCount(modelHandle);
    }

    @Override
    public void setMaterialVisible(long modelHandle, int materialIndex, boolean visible) {
        nativeFunc().SetMaterialVisible(modelHandle, materialIndex, visible);
    }

    @Override
    public void deleteModel(long modelHandle) {
        nativeFunc().DeleteModel(modelHandle);
    }
}
