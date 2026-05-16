/* 文件职责：在 1.21.11 渲染状态化 API 下，将 LivingEntity 与其 LivingEntityRenderState 在 extract→submit 之间桥接，便于 MMD 替换层访问原始实体与 partialTick。 */
package com.shiroha.mmdskin.renderer.runtime.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class LivingEntityRenderStateBinder {
    private static final Map<LivingEntityRenderState, Binding> BINDINGS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LivingEntityRenderStateBinder() {
    }

    public static void bind(LivingEntityRenderState state, LivingEntity entity, float partialTick) {
        if (state == null || entity == null) {
            return;
        }
        BINDINGS.put(state, new Binding(entity, partialTick));
    }

    public static Binding get(LivingEntityRenderState state) {
        if (state == null) {
            return null;
        }
        return BINDINGS.get(state);
    }

    public record Binding(LivingEntity entity, float partialTick) {
    }
}
