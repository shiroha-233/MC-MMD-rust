package com.shiroha.mmdskin.renderer.integration.entity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;

public class MmdSkinRenderFactory<T extends Entity> implements EntityRendererProvider<T> {
    String entityName;

    public MmdSkinRenderFactory(String entityName) {
        this.entityName = entityName;
    }

    @Override
    public EntityRenderer<T> create(Context manager) {
        return new MmdSkinRenderer<>(manager, entityName);
    }
}
