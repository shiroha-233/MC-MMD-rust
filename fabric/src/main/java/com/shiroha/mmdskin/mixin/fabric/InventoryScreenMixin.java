// 文件职责：在 Fabric 物品栏实体绘制边界内开启并刷新 MMD 局部队列。
// 26.2 适配：renderEntityInInventory -> extractEntityInInventoryFollowsMouse（静态方法）。
package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Inject(method = "extractEntityInInventoryFollowsMouse", at = @At("HEAD"))
    private static void mmdskin$beginInventoryRender(CallbackInfo callback) {
        MmdClientRenderRuntime.currentIfInstalled()
                .ifPresent(runtime -> runtime.inventory().begin());
    }

    @Inject(method = "extractEntityInInventoryFollowsMouse", at = @At("RETURN"))
    private static void mmdskin$flushInventoryRender(CallbackInfo callback) {
        MmdClientRenderRuntime.currentIfInstalled()
                .ifPresent(runtime -> runtime.inventory().finish());
    }
}
