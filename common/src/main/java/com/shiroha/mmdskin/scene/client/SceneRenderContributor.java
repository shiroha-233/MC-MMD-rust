// 负责把已加载场景 Model Instance 转换为世界 Frame Queue 请求。
package com.shiroha.mmdskin.scene.client;

import com.shiroha.mmdskin.client.draw.FrameRenderQueue;
import com.shiroha.mmdskin.client.draw.MmdDrawRequest;
import com.shiroha.mmdskin.client.draw.PipelineVariant;
import com.shiroha.mmdskin.client.animation.MmdAnimationIntent;
import com.shiroha.mmdskin.client.frame.MmdEntityPose;
import com.shiroha.mmdskin.client.frame.MmdFrameUpdater;
import com.shiroha.mmdskin.client.frame.MmdModelMotion;
import com.shiroha.mmdskin.client.frame.MmdRenderSnapshot;
import com.shiroha.mmdskin.client.frame.ModelTransform;
import com.shiroha.mmdskin.client.model.MmdModelInstance;
import org.joml.Matrix4f;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class SceneRenderContributor {
    private final FrameRenderQueue queue;
    private final MmdFrameUpdater frameUpdater;

    public SceneRenderContributor(FrameRenderQueue queue, MmdFrameUpdater frameUpdater) {
        this.queue = queue;
        this.frameUpdater = frameUpdater;
    }

    public void contribute(ScenePlacement placement, MmdModelInstance model,
                           double cameraX, double cameraY, double cameraZ, int packedLight,
                           long frameId, float deltaSeconds) {
        // modelMat 使用相机相对坐标（与 26.2 官方例程一致，ModelView 只含视图旋转）
        double offsetX = placement.x() - cameraX;
        double offsetY = placement.y() - cameraY;
        double offsetZ = placement.z() - cameraZ;
        Matrix4f transform = new Matrix4f()
                .translation((float) offsetX, (float) offsetY, (float) offsetZ)
                .rotateY((float) Math.toRadians(-placement.yawDegrees()));
        double distanceSquared = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
        UUID sceneId = UUID.nameUUIDFromBytes(("mmdskin:scene:" + placement.modelName())
                .getBytes(StandardCharsets.UTF_8));
        MmdRenderSnapshot snapshot = new MmdRenderSnapshot(
                sceneId,
                model.key(),
                MmdEntityPose.standing(),
                MmdAnimationIntent.none(),
                MmdModelMotion.stationary(),
                ModelTransform.from(transform),
                MmdRenderSnapshot.Visibility.OPAQUE,
                false,
                packedLight,
                distanceSquared,
                MmdRenderSnapshot.Context.SCENE);
        frameUpdater.updateOnce(frameId, model, snapshot, deltaSeconds);
        queue.enqueue(new MmdDrawRequest(model, snapshot, PipelineVariant.OPAQUE, false));
    }
}
