// 负责在 NeoForge 完整实体状态提取返回点创建 MMD Render Snapshot。
package com.shiroha.mmdskin.mixin.neoforge;

import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.frame.MmdRenderSnapshot;
import com.shiroha.mmdskin.client.frame.MmdRenderStateExtension;
import com.shiroha.mmdskin.client.frame.MmdSnapshotFactory;
import com.shiroha.mmdskin.neoforge.compat.YsmCompat;
import com.shiroha.mmdskin.player.ragdoll.RagdollManager;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class LivingEntityRendererStateCaptureMixin {
    @Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("RETURN"))
    private void mmdskin$captureSnapshot(Entity entity, float partialTick,
                                         CallbackInfoReturnable<EntityRenderState> callback) {
        EntityRenderState state = callback.getReturnValue();
        if (!(state instanceof MmdRenderStateExtension extension)) {
            return;
        }
        extension.mmdskin$clearSnapshot();
        if (!(entity instanceof LivingEntity living) || !(state instanceof LivingEntityRenderState livingState)) {
            return;
        }
        if (living instanceof Player player && YsmCompat.isYsmActive(player)) {
            return;
        }
        MmdRenderSnapshot.Context context = MmdClientRenderRuntime.currentIfInstalled()
                .filter(runtime -> runtime.inventory().active())
                .map(runtime -> MmdRenderSnapshot.Context.INVENTORY)
                .orElse(MmdRenderSnapshot.Context.WORLD);
        extension.mmdskin$setSnapshot(MmdSnapshotFactory.capture(living, livingState, context));
        // 尸体接管后原版影子要灭掉：MMD 尸体自带影子通道，
        // 不抹的话地上会留一个"没有身体的原版影子"。
        // 26.3 的影子提取（EntityRenderer.extractShadow）只拿得到 render state，
        // 所以结论必须在这里（还拿得到实体对象的时候）先记到 state 上。
        extension.mmdskin$setSuppressShadow(
                RagdollManager.getInstance().shouldSuppressEntityRender(living));
    }
}
