/* 文件职责：以统一 bridge 适配器提供 morph native 能力。 */
package com.shiroha.mmdskin.bridge.runtime;

import com.shiroha.mmdskin.NativeFunc;

public final class NativeMorphBridgePorts {
    private static final NativeMorphPort PORT = new NativeMorphPort() {
        @Override
        public void resetAllMorphs(long modelHandle) {
            NativeFunc.GetInst().ResetAllMorphs(modelHandle);
        }

        @Override
        public void setMorphWeight(long modelHandle, int morphIndex, float weight) {
            NativeFunc.GetInst().SetMorphWeight(modelHandle, morphIndex, weight);
        }

        @Override
        public void syncGpuMorphWeights(long modelHandle) {
            NativeFunc.GetInst().SyncGpuMorphWeights(modelHandle);
        }

        @Override
        public int applyVpdMorph(long modelHandle, String filePath) {
            return NativeFunc.GetInst().ApplyVpdMorph(modelHandle, filePath);
        }
    };

    private NativeMorphBridgePorts() {
    }

    public static NativeMorphPort morphPort() {
        return PORT;
    }
}
