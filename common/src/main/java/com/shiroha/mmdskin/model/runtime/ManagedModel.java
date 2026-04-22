package com.shiroha.mmdskin.model.runtime;

import com.shiroha.mmdskin.animation.runtime.AnimationLibrary;
import com.shiroha.mmdskin.player.runtime.EntityAnimState;
import java.util.Properties;
import java.util.Objects;

/** 文件职责：聚合单个模型实例的动画库、状态和解析后的渲染属性。 */
public final class ManagedModel {
    private final ModelRequestKey requestKey;
    private final String modelName;
    private final ModelInstance modelInstance;
    private final AnimationLibrary animationLibrary;
    private final EntityAnimState entityState;
    public final Properties properties;
    private ModelRenderProperties renderProperties;

    public ManagedModel(
            ModelRequestKey requestKey,
            String modelName,
            ModelInstance modelInstance,
            Properties properties,
            ModelRenderProperties renderProperties) {
        this.requestKey = Objects.requireNonNull(requestKey, "requestKey");
        this.modelName = Objects.requireNonNull(modelName, "modelName");
        this.modelInstance = Objects.requireNonNull(modelInstance, "modelInstance");
        this.animationLibrary = new AnimationLibrary(modelInstance);
        this.entityState = new EntityAnimState(3);
        this.properties = properties != null ? properties : new Properties();
        this.renderProperties = renderProperties != null ? renderProperties : ModelRenderProperties.DEFAULT;
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
