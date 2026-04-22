package com.shiroha.mmdskin.render.entity;

import net.minecraft.world.entity.Entity;

/** 文件职责：作为平台实体渲染注册的公开工厂入口。 */
public final class EntityRenderFactory<T extends Entity> extends MmdSkinRenderFactory<T> {
    public EntityRenderFactory(String entityName) {
        super(entityName);
    }
}
