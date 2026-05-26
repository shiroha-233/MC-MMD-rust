/* 文件职责：以统一 bridge 适配器提供模型 native 读写能力。 */
package com.shiroha.mmdskin.bridge.runtime;

import com.shiroha.mmdskin.NativeFunc;

import java.nio.ByteBuffer;

public final class NativeModelBridgePorts {
    private static final NativeModelAdapter ADAPTER = new NativeModelAdapter();

    private NativeModelBridgePorts() {
    }

    public static NativeModelPort modelPort() {
        return ADAPTER;
    }

    public static NativeModelQueryPort queryPort() {
        return ADAPTER;
    }

    private static final class NativeModelAdapter implements NativeModelPort, NativeModelQueryPort {
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
        public void setFirstPersonMode(long modelHandle, boolean enabled) {
            nativeFunc().SetFirstPersonMode(modelHandle, enabled);
        }

        @Override
        public void getEyeBonePosition(long modelHandle, float[] output) {
            nativeFunc().GetEyeBonePosition(modelHandle, output);
        }

        @Override
        public void applyVrTrackingInput(long modelHandle, float[] trackingData) {
            nativeFunc().SetVRTrackingData(modelHandle, trackingData);
        }

        @Override
        public void setVrEnabled(long modelHandle, boolean enabled) {
            nativeFunc().SetVREnabled(modelHandle, enabled);
        }

        @Override
        public void setVrIkParams(long modelHandle, float armIkStrength) {
            nativeFunc().SetVRIKParams(modelHandle, armIkStrength);
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
        public void setAllMaterialsVisible(long modelHandle, boolean visible) {
            nativeFunc().SetAllMaterialsVisible(modelHandle, visible);
        }

        @Override
        public void deleteModel(long modelHandle) {
            nativeFunc().DeleteModel(modelHandle);
        }

        @Override
        public int getBoneCount(long modelHandle) {
            return nativeFunc().GetBoneCount(modelHandle);
        }

        @Override
        public long getVertexCount(long modelHandle) {
            return nativeFunc().GetVertexCount(modelHandle);
        }

        @Override
        public long getIndexCount(long modelHandle) {
            return nativeFunc().GetIndexCount(modelHandle);
        }

        @Override
        public String getBoneNames(long modelHandle) {
            return nativeFunc().GetBoneNames(modelHandle);
        }

        @Override
        public int copyBonePositionsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
            return nativeFunc().CopyBonePositionsToBuffer(modelHandle, targetBuffer);
        }

        @Override
        public int copyRealtimeUvsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
            return nativeFunc().CopyRealtimeUVsToBuffer(modelHandle, targetBuffer);
        }

        @Override
        public int getVertexMorphCount(long modelHandle) {
            return nativeFunc().GetVertexMorphCount(modelHandle);
        }

        @Override
        public int getUvMorphCount(long modelHandle) {
            return nativeFunc().GetUvMorphCount(modelHandle);
        }

        @Override
        public long getGpuMorphOffsetsSize(long modelHandle) {
            return nativeFunc().GetGpuMorphOffsetsSize(modelHandle);
        }

        @Override
        public long getGpuUvMorphOffsetsSize(long modelHandle) {
            return nativeFunc().GetGpuUvMorphOffsetsSize(modelHandle);
        }

        @Override
        public int getMorphCount(long modelHandle) {
            return (int) nativeFunc().GetMorphCount(modelHandle);
        }

        @Override
        public String getMorphName(long modelHandle, int morphIndex) {
            return nativeFunc().GetMorphName(modelHandle, morphIndex);
        }

        @Override
        public String getMaterialName(long modelHandle, int materialIndex) {
            return nativeFunc().GetMaterialName(modelHandle, materialIndex);
        }

        @Override
        public boolean isMaterialVisible(long modelHandle, int materialIndex) {
            return nativeFunc().IsMaterialVisible(modelHandle, materialIndex);
        }
    }
}
