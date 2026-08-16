// 负责保留旧实体目录注册行为并把通用实体提交到 Frame Queue。
package com.shiroha.mmdskin.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.client.draw.MmdRenderRouter;
import com.shiroha.mmdskin.client.animation.MmdAnimationIntent;
import com.shiroha.mmdskin.client.frame.MmdEntityPose;
import com.shiroha.mmdskin.client.frame.MmdModelMotion;
import com.shiroha.mmdskin.client.frame.MmdRenderSnapshot;
import com.shiroha.mmdskin.client.frame.ModelTransform;
import com.shiroha.mmdskin.client.model.ModelKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;

public final class LegacyEntityMmdRenderer<T extends Entity>
        extends EntityRenderer<T, LegacyEntityMmdRenderer.State> {
    private final String modelName;

    public LegacyEntityMmdRenderer(EntityRendererProvider.Context context, String modelName) {
        super(context);
        this.modelName = modelName;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.snapshot = new MmdRenderSnapshot(
                entity.getUUID(),
                new ModelKey(modelName, entity.getStringUUID(), ModelKey.Usage.ENTITY),
                MmdEntityPose.standing(),
                MmdAnimationIntent.none(),
                new MmdModelMotion(
                        (float) state.x,
                        (float) state.y,
                        (float) state.z,
                        0.0F,
                        true),
                ModelTransform.identity(),
                state.isInvisible ? MmdRenderSnapshot.Visibility.HIDDEN : MmdRenderSnapshot.Visibility.OPAQUE,
                false,
                0,
                Math.max(0.0D, state.distanceToCameraSq),
                MmdRenderSnapshot.Context.WORLD);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        MmdRenderRouter.route(state.snapshot, poseStack, state.lightCoords, false);
        super.submit(state, poseStack, collector, camera);
    }

    public static final class State extends EntityRenderState {
        private MmdRenderSnapshot snapshot;
    }
}
