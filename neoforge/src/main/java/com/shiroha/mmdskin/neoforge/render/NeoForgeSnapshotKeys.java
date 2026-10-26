// 负责定义 NeoForge 端 MMD 快照在 RenderState 上的存取位置。
//
// 26.3 适配：NeoForge 26.3 取消了 1.21.x 的
// {@code RegisterRenderStateModifiersEvent}（render state modifier 机制整个没了），
// 所以 MMD 快照不再挂在 NeoForge 的 {@code ContextKey} 上，
// 而是通过 common 的 {@link MmdRenderStateExtension} 直接挂在原版
// {@code LivingEntityRenderState} 上（见 LivingEntityRenderStateMixin）。
//
// 这里集中封装"从 RenderState 取/存快照"的入口，
// 供 LivingEntityRendererStateCaptureMixin（写入）与
// NeoForgeEntityBaseRenderMixin（读取）共用，避免两处各写一遍 instanceof 判断。
package com.shiroha.mmdskin.neoforge.render;

import com.shiroha.mmdskin.client.frame.MmdRenderSnapshot;
import com.shiroha.mmdskin.client.frame.MmdRenderStateExtension;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public final class NeoForgeSnapshotKeys {
    private NeoForgeSnapshotKeys() {
    }

    /** 读取该 RenderState 上的 MMD 快照；不是 MMD 接管的对象时返回 null。 */
    public static MmdRenderSnapshot snapshotOf(EntityRenderState state) {
        if (state instanceof MmdRenderStateExtension extension) {
            return extension.mmdskin$snapshot();
        }
        return null;
    }

    /** 把 MMD 快照写进该 RenderState；不是 MMD 接管的对象时什么都不做。 */
    public static void store(EntityRenderState state, MmdRenderSnapshot snapshot) {
        if (state instanceof MmdRenderStateExtension extension) {
            extension.mmdskin$setSnapshot(snapshot);
        }
    }
}
