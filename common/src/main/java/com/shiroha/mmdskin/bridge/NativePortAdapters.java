// 文件职责：将唯一的 JNI 声明集合适配为模型、动画、舞台、物理、Morph 与 VR 窄接口。
package com.shiroha.mmdskin.bridge;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelLoadPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelMatrixPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelQueryPort;
import com.shiroha.mmdskin.bridge.runtime.NativeMorphPort;
import com.shiroha.mmdskin.bridge.runtime.NativePhysicsPort;
import com.shiroha.mmdskin.bridge.runtime.NativeStagePort;
import com.shiroha.mmdskin.bridge.runtime.NativeVrPort;

import java.nio.ByteBuffer;

public final class NativePortAdapters {
    private static final Adapter ADAPTER = new Adapter();

    private NativePortAdapters() {
    }

    public static NativeModelLoadPort modelLoad() {
        return ADAPTER;
    }

    public static NativeModelPort model() {
        return ADAPTER;
    }

    public static NativeModelQueryPort modelQuery() {
        return ADAPTER;
    }

    public static NativeModelMatrixPort modelMatrix() {
        return ADAPTER;
    }

    public static NativeAnimationPort animation() {
        return ADAPTER;
    }

    public static NativeMorphPort morph() {
        return ADAPTER;
    }

    public static NativeStagePort stage() {
        return ADAPTER;
    }

    public static NativePhysicsPort physics() {
        return ADAPTER;
    }

    public static NativeVrPort vr() {
        return ADAPTER;
    }

    private static final class Adapter implements NativeModelLoadPort, NativeModelPort, NativeModelMatrixPort,
            NativeModelQueryPort, NativeAnimationPort, NativeMorphPort, NativeStagePort,
            NativePhysicsPort, NativeVrPort {
        @Override
        public long loadModel(String modelFile, String modelDirectory, Format format,
                              int animationLayers) {
            return switch (format) {
                case PMX -> NativeBindings.LoadModelPMX(modelFile, modelDirectory, animationLayers);
                case PMD -> NativeBindings.LoadModelPMD(modelFile, modelDirectory, animationLayers);
                case VRM -> NativeBindings.LoadModelVRM(modelFile, modelDirectory, animationLayers);
            };
        }

        @Override
        public boolean isVrm(long modelHandle) {
            return NativeBindings.IsVrmModel(modelHandle);
        }

        @Override
        public void updateModel(long modelHandle, float deltaSeconds) {
            NativeBindings.UpdateModel(modelHandle, deltaSeconds);
        }

        @Override
        public void resetPhysics(long modelHandle) {
            NativeBindings.ResetModelPhysics(modelHandle);
        }

        @Override
        public void setPhysicsEnabled(long modelHandle, boolean enabled) {
            NativeBindings.SetPhysicsEnabled(modelHandle, enabled);
        }

        @Override
        public void setModelPositionAndYaw(long modelHandle, float x, float y, float z,
                                           float yawRadians) {
            NativeBindings.SetModelPositionAndYaw(modelHandle, x, y, z, yawRadians);
        }

        @Override
        public long loadAnimation(long modelHandle, String path) {
            return NativeBindings.LoadAnimation(modelHandle, path);
        }

        @Override
        public void deleteAnimation(long animationHandle) {
            requireSuccess("releaseAnimation", NativeBindings.releaseAnimation(animationHandle));
        }

        @Override
        public void changeAnimation(long modelHandle, long animationHandle, int layer) {
            NativeBindings.ChangeModelAnim(modelHandle, animationHandle, layer);
        }

        @Override
        public void transitionAnimation(long modelHandle, int layer, long animationHandle,
                                        float transitionSeconds) {
            NativeBindings.TransitionLayerTo(modelHandle, layer, animationHandle, transitionSeconds);
        }

        @Override
        public void setLayerLoop(long modelHandle, int layer, boolean loop) {
            NativeBindings.SetLayerLoop(modelHandle, layer, loop);
        }

        @Override
        public boolean isLayerAnimationFinished(long modelHandle, int layer) {
            return NativeBindings.IsLayerAnimationFinished(modelHandle, layer);
        }

        @Override
        public void seekLayer(long modelHandle, int layer, float frame) {
            NativeBindings.SeekLayer(modelHandle, layer, frame);
        }

        @Override
        public boolean setLayerBoneMask(long modelHandle, int layer, String rootBoneName) {
            return NativeBindings.SetLayerBoneMask(modelHandle, layer, rootBoneName);
        }

        @Override
        public boolean setLayerBoneExclude(long modelHandle, int layer, String rootBoneName) {
            return NativeBindings.SetLayerBoneExclude(modelHandle, layer, rootBoneName);
        }

        @Override
        public long getModelMemoryUsage(long modelHandle) {
            return NativeBindings.GetModelMemoryUsage(modelHandle);
        }

        @Override
        public void setFirstPersonMode(long modelHandle, boolean enabled) {
            NativeBindings.SetFirstPersonMode(modelHandle, enabled);
        }

        @Override
        public boolean setGpuSkinningEnabled(long modelHandle, boolean enabled) {
            return NativeBindings.SetGpuSkinningEnabled(modelHandle, enabled);
        }

        @Override
        public void getEyeBonePosition(long modelHandle, float[] output) {
            NativeBindings.GetEyeBonePosition(modelHandle, output);
        }

        @Override
        public void setEyeTrackingEnabled(long modelHandle, boolean enabled) {
            NativeBindings.SetEyeTrackingEnabled(modelHandle, enabled);
        }

        @Override
        public void setEyeMaxAngle(long modelHandle, float maxAngle) {
            NativeBindings.SetEyeMaxAngle(modelHandle, maxAngle);
        }

        @Override
        public void setAutoBlinkEnabled(long modelHandle, boolean enabled) {
            NativeBindings.SetAutoBlinkEnabled(modelHandle, enabled);
        }

        @Override
        public int getMaterialCount(long modelHandle) {
            return Math.toIntExact(NativeBindings.GetMaterialCount(modelHandle));
        }

        @Override
        public void setMaterialVisible(long modelHandle, int materialIndex, boolean visible) {
            NativeBindings.SetMaterialVisible(modelHandle, materialIndex, visible);
        }

        @Override
        public void setAllMaterialsVisible(long modelHandle, boolean visible) {
            NativeBindings.SetAllMaterialsVisible(modelHandle, visible);
        }

        @Override
        public boolean copyHandMatrix(long modelHandle, NativeModelMatrixPort.Hand hand,
                                      ByteBuffer destination) {
            return NativeHandMatrixBridge.copy(modelHandle, hand, destination);
        }

        @Override
        public void deleteModel(long modelHandle) {
            requireSuccess("releaseModel", NativeBindings.releaseModel(modelHandle));
        }

        @Override
        public int getBoneCount(long modelHandle) {
            return NativeBindings.GetBoneCount(modelHandle);
        }

        @Override
        public long getVertexCount(long modelHandle) {
            return NativeBindings.GetVertexCount(modelHandle);
        }

        @Override
        public long getIndexCount(long modelHandle) {
            return NativeBindings.GetIndexCount(modelHandle);
        }

        @Override
        public String getBoneNames(long modelHandle) {
            return NativeBindings.GetBoneNames(modelHandle);
        }

        @Override
        public int copyBonePositionsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
            return NativeBindings.CopyBonePositionsToBuffer(modelHandle, targetBuffer);
        }

        @Override
        public int copyRealtimeUvsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
            return NativeBindings.CopyRealtimeUVsToBuffer(modelHandle, targetBuffer);
        }

        @Override
        public int getMorphCount(long modelHandle) {
            return Math.toIntExact(NativeBindings.GetMorphCount(modelHandle));
        }

        @Override
        public String getMorphName(long modelHandle, int morphIndex) {
            return NativeBindings.GetMorphName(modelHandle, morphIndex);
        }

        @Override
        public String getMaterialName(long modelHandle, int materialIndex) {
            return NativeBindings.GetMaterialName(modelHandle, materialIndex);
        }

        @Override
        public boolean isMaterialVisible(long modelHandle, int materialIndex) {
            return NativeBindings.IsMaterialVisible(modelHandle, materialIndex);
        }

        @Override
        public void resetAllMorphs(long modelHandle) {
            NativeBindings.ResetAllMorphs(modelHandle);
        }

        @Override
        public void setMorphWeight(long modelHandle, int morphIndex, float weight) {
            NativeBindings.SetMorphWeight(modelHandle, morphIndex, weight);
        }

        @Override
        public int applyVpdMorph(long modelHandle, String filePath) {
            return NativeBindings.ApplyVpdMorph(modelHandle, filePath);
        }

        @Override
        public boolean hasCameraData(long animationHandle) {
            return NativeBindings.HasCameraData(animationHandle);
        }

        @Override
        public boolean hasBoneData(long animationHandle) {
            return NativeBindings.HasBoneData(animationHandle);
        }

        @Override
        public boolean hasMorphData(long animationHandle) {
            return NativeBindings.HasMorphData(animationHandle);
        }

        @Override
        public float getAnimationMaxFrame(long animationHandle) {
            return NativeBindings.GetAnimMaxFrame(animationHandle);
        }

        @Override
        public void copyCameraTransform(long animationHandle, float frame,
                                        ByteBuffer destination) {
            NativeBindings.GetCameraTransform(animationHandle, frame, destination);
        }

        @Override
        public void mergeAnimation(long targetHandle, long sourceHandle) {
            NativeBindings.MergeAnimation(targetHandle, sourceHandle);
        }

        @Override
        public void configure(boolean enabled, float gravityY, float physicsFps,
                              int maxSubstepCount, float inertiaStrength,
                              float maxLinearVelocity, float maxAngularVelocity,
                              boolean jointsEnabled, boolean kinematicFilter,
                              boolean collisionEnabled, int collisionStabilityMode, boolean debugLog) {
            NativeBindings.SetPhysicsConfig(enabled, gravityY, physicsFps, maxSubstepCount,
                    inertiaStrength, maxLinearVelocity, maxAngularVelocity, jointsEnabled,
                    kinematicFilter, collisionEnabled, collisionStabilityMode, debugLog);
        }

        @Override
        public void applyTrackingInput(long modelHandle, float[] trackingData) {
            NativeBindings.SetVRTrackingData(modelHandle, trackingData);
        }

        @Override
        public void setEnabled(long modelHandle, boolean enabled) {
            NativeBindings.SetVREnabled(modelHandle, enabled);
        }

        @Override
        public void setIkParams(long modelHandle, float armIkStrength) {
            NativeBindings.SetVRIKParams(modelHandle, armIkStrength);
        }

        private static void requireSuccess(String operation, int status) {
            if (status < 0) {
                throw new NativeBridgeException(operation, status);
            }
        }
    }
}
