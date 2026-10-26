// 负责在可复用原版 RenderState 上存取并显式清空 MMD 快照。
package com.shiroha.mmdskin.client.frame;

import org.jetbrains.annotations.Nullable;

public interface MmdRenderStateExtension {
    @Nullable
    MmdRenderSnapshot mmdskin$snapshot();

    void mmdskin$setSnapshot(@Nullable MmdRenderSnapshot snapshot);

    default void mmdskin$clearSnapshot() {
        mmdskin$setSnapshot(null);
    }

    /**
     * 该实体是否已被 MMD 尸体接管。
     *
     * <p>1.20.1 是在 {@code EntityRenderDispatcher.renderShadow} 里直接拿实体判定的；
     * 26.3 的影子提取换成了 {@code EntityRenderer.extractShadow(state, minecraft, level)}，
     * 只拿得到 render state、拿不到实体，所以在还能拿到实体的地方（extractRenderState 之前）
     * 把结论记在 state 上，影子提取时再读。
     *
     * <p>不抑制的话，尸体接管后地上会留一个"没有身体的原版影子"。
     */
    boolean mmdskin$suppressShadow();

    void mmdskin$setSuppressShadow(boolean suppressShadow);
}

