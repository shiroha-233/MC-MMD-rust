/* 文件职责：MMD 自定义实体渲染所用的 RenderState，承载从 entity 抽取的瞬时数据。 */
package com.shiroha.mmdskin.renderer.integration.entity;

import com.shiroha.mmdskin.renderer.api.RenderParams;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

public class MmdSkinRenderState extends EntityRenderState {
    public Entity entity;
    public float partialTick;
    public float entityYaw;
    public float entityXRot;
    public boolean isBaby;
    public boolean isInInventoryScreen;
    public MMDModelManager.Model model;
    public final RenderParams params = new RenderParams();
    public final float[] size = new float[]{1.0f, 1.0f};
}
