package com.shiroha.mmdskin.model.runtime;

import com.shiroha.mmdskin.animation.runtime.AnimationLibrary;
import com.shiroha.mmdskin.bridge.runtime.NativeAnimationPort;
import com.shiroha.mmdskin.bridge.runtime.NativeMatrixPort;
import com.shiroha.mmdskin.player.runtime.EntityAnimState;
import java.util.Properties;
import java.util.Objects;

/** 文件职责：聚合单个模型实例的动画库、状态和解析后的渲染属性。 */
public final class ManagedModel {
    private static final NativeAnimationPort NOOP_ANIMATION_PORT = new NativeAnimationPort() {
        @Override
        public long loadAnimation(long modelHandle, String animationPath) {
            return 0L;
        }

        @Override
        public void deleteAnimation(long animationHandle) {
        }

        @Override
        public void mergeAnimation(long mergedAnimationHandle, long sourceAnimationHandle) {
        }

        @Override
        public boolean hasCameraData(long animationHandle) {
            return false;
        }

        @Override
        public boolean hasBoneData(long animationHandle) {
            return false;
        }

        @Override
        public boolean hasMorphData(long animationHandle) {
            return false;
        }

        @Override
        public float getAnimationMaxFrame(long animationHandle) {
            return 0.0f;
        }

        @Override
        public void seekLayer(long modelHandle, long layer, float frame) {
        }

        @Override
        public void getCameraTransform(long animationHandle, float frame, java.nio.ByteBuffer targetBuffer) {
        }
    };
    private static final NativeMatrixPort NOOP_MATRIX_PORT = new NativeMatrixPort() {
        @Override
        public long createMatrix() {
            return 0L;
        }

        @Override
        public void deleteMatrix(long matrixHandle) {
        }

        @Override
        public void populateHandMatrix(long modelHandle, long handMatrixHandle, boolean mainHand) {
        }

        @Override
        public boolean copyMatrixToBuffer(long matrixHandle, java.nio.ByteBuffer targetBuffer) {
            return false;
        }
    };

    private static volatile NativeAnimationPort animationPort = NOOP_ANIMATION_PORT;
    private static volatile NativeMatrixPort matrixPort = NOOP_MATRIX_PORT;

    private final ModelRequestKey requestKey;
    private final String modelName;
    private final ModelInstance modelInstance;
    private final AnimationLibrary animationLibrary;
    private final EntityAnimState entityState;
    public final Properties properties;
    private volatile ModelRenderProperties renderProperties;

    public ManagedModel(
            ModelRequestKey requestKey,
            String modelName,
            ModelInstance modelInstance,
            Properties properties,
            ModelRenderProperties renderProperties) {
        this.requestKey = Objects.requireNonNull(requestKey, "requestKey");
        this.modelName = Objects.requireNonNull(modelName, "modelName");
        this.modelInstance = Objects.requireNonNull(modelInstance, "modelInstance");
        this.animationLibrary = new AnimationLibrary(modelInstance, animationPort);
        this.entityState = new EntityAnimState(3, matrixPort);
        this.properties = properties != null ? properties : new Properties();
        this.renderProperties = renderProperties != null ? renderProperties : ModelRenderProperties.DEFAULT;
    }

    public static void configureRuntimeCollaborators(NativeAnimationPort animationPort, NativeMatrixPort matrixPort) {
        ManagedModel.animationPort = animationPort != null ? animationPort : NOOP_ANIMATION_PORT;
        ManagedModel.matrixPort = matrixPort != null ? matrixPort : NOOP_MATRIX_PORT;
    }

    public ModelRequestKey requestKey() {
        return requestKey;
    }

    public String modelName() {
        return modelName;
    }

    public ModelInstance modelInstance() {
        return modelInstance;
    }

    public AnimationLibrary animationLibrary() {
        return animationLibrary;
    }

    public EntityAnimState entityState() {
        return entityState;
    }

    public ModelRenderProperties renderProperties() {
        return renderProperties;
    }

    public void replaceRenderProperties(ModelRenderProperties renderProperties) {
        this.renderProperties = renderProperties != null ? renderProperties : ModelRenderProperties.DEFAULT;
    }

    public void dispose() {
        animationLibrary.dispose();
        modelInstance.dispose();
        entityState.dispose();
    }
}
