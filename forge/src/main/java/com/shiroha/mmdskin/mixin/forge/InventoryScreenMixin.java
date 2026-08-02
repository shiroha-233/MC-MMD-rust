package com.shiroha.mmdskin.mixin.forge;

import com.shiroha.mmdskin.player.render.InventoryRenderScope;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将背包人物预览限制在明确的渲染调用作用域内。 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    private static final String RENDER_ENTITY_METHOD =
            "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;"
                    + "Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V";

    @Inject(method = RENDER_ENTITY_METHOD, at = @At("HEAD"))
    private static void mmdskin$enterInventoryRender(CallbackInfo callbackInfo) {
        InventoryRenderScope.enter();
    }

    @Inject(method = RENDER_ENTITY_METHOD, at = @At("RETURN"))
    private static void mmdskin$exitInventoryRender(CallbackInfo callbackInfo) {
        InventoryRenderScope.exit();
    }
}
