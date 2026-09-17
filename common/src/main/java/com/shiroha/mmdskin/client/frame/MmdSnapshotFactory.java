// 负责在原版状态提取完成后复制不可变的 MMD Render Snapshot。
package com.shiroha.mmdskin.client.frame;

import com.shiroha.mmdskin.client.animation.MmdAnimationIntent;
import com.shiroha.mmdskin.client.entity.EntityModelSelection;
import com.shiroha.mmdskin.client.model.ModelKey;
import com.shiroha.mmdskin.player.animation.PlayerAnimationIntentExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

public final class MmdSnapshotFactory {
    private MmdSnapshotFactory() {
    }

    public static MmdRenderSnapshot capture(LivingEntity entity, LivingEntityRenderState state) {
        return capture(entity, state, MmdRenderSnapshot.Context.WORLD);
    }

    public static MmdRenderSnapshot capture(LivingEntity entity, LivingEntityRenderState state,
                                            MmdRenderSnapshot.Context context) {
        String modelName = EntityModelSelection.resolveModelName(entity);
        if (modelName == null) {
            return null;
        }
        MmdRenderSnapshot.Visibility visibility = resolveVisibility(state);
        ModelKey key = new ModelKey(modelName, entity.getStringUUID(), usageFor(context));
        MmdEntityPose pose = new MmdEntityPose(
                state.bodyRot,
                state.yRot,
                state.xRot,
                state.ageInTicks,
                state.walkAnimationPos,
                state.walkAnimationSpeed,
                state.deathTime,
                state.isBaby,
                state.isInWater,
                state.isAutoSpinAttack,
                state.pose);
        MmdAnimationIntent animationIntent = entity instanceof AbstractClientPlayer player
                ? PlayerAnimationIntentExtractor.capture(player)
                : MmdAnimationIntent.none();
        MmdModelMotion motion = new MmdModelMotion(
                (float) state.x,
                (float) state.y,
                (float) state.z,
                (float) Math.toRadians(state.bodyRot),
                context == MmdRenderSnapshot.Context.WORLD);
        return new MmdRenderSnapshot(
                entity.getUUID(),
                key,
                pose,
                animationIntent,
                motion,
                ModelTransform.identity(),
                visibility,
                state.appearsGlowing(),
                0,
                Math.max(0.0D, state.distanceToCameraSq),
                context);
    }

    static ModelKey.Usage usageFor(MmdRenderSnapshot.Context context) {
        return switch (context) {
            case WORLD -> ModelKey.Usage.ENTITY;
            case FIRST_PERSON -> ModelKey.Usage.FIRST_PERSON;
            case INVENTORY -> ModelKey.Usage.INVENTORY;
            case SCENE -> ModelKey.Usage.SCENE;
        };
    }

    private static MmdRenderSnapshot.Visibility resolveVisibility(LivingEntityRenderState state) {
        if (!state.isInvisible) {
            return MmdRenderSnapshot.Visibility.OPAQUE;
        }
        return state.isInvisibleToPlayer
                ? MmdRenderSnapshot.Visibility.HIDDEN
                : MmdRenderSnapshot.Visibility.TRANSLUCENT;
    }
}
