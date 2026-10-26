// 文件职责：在 NeoForge 物品栏实体绘制边界内开启并刷新 MMD 局部队列。
package com.shiroha.mmdskin.mixin.neoforge;

import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    // 26.3 里物品栏实体的绘制入口由 renderEntityInInventory 改名为 extractEntityInInventoryFollowsMouse，
    // 并且不再有 GuiGraphicsExtractor.flush()，因此收尾点改为方法返回。
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
