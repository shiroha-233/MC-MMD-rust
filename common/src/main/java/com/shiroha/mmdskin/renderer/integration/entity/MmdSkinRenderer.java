/* 文件职责：MMD 自定义实体渲染器（1.21.11 占位版本，渲染管线待重写）。 */
package com.shiroha.mmdskin.renderer.integration.entity;

import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.renderer.api.RenderParams;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class MmdSkinRenderer<T extends Entity> extends EntityRenderer<T, EntityRenderState> {

    private static final Identifier PLACEHOLDER_TEXTURE =
            Identifier.fromNamespaceAndPath(MmdSkin.MOD_ID, "textures/entity/placeholder.png");

    protected final String modelName;

    private final RenderParams reusableParams = new RenderParams();
    private final Quaternionf reusableQuat = new Quaternionf();
    private final Vector3f reusableVec = new Vector3f();
    private final float[] reusableSize = new float[2];

    public MmdSkinRenderer(EntityRendererProvider.Context renderManager, String entityName) {
        super(renderManager);
        this.modelName = entityName.replace(':', '.');
    }

    // TODO_1.21.11: 渲染管线重写 - 1.21.5+ 改用 RenderState 抽取与 SubmitNodeCollector 提交
    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    // TODO_1.21.11: 渲染管线重写 - submit 占位实现，原 render 逻辑暂未迁移
    @Override
    public void submit(EntityRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        // 原渲染逻辑依赖 entity 实例与 RenderParams，需重写为 RenderState 驱动
    }
}
