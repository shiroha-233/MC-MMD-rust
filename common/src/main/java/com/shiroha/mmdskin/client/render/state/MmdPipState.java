// 记录 26.2 画中画（PIP）渲染期间的离屏渲染状态。
// PIP（物品栏纸娃娃/GUI 实体预览）在 PictureInPictureRenderer.prepare 期间把
// RenderSystem.outputColorTextureOverride/DepthOverride 设为 PIP 离屏纹理，并用正交投影；
// 此时 MMD 模型应画到离屏纹理并使用 PIP 正交投影，而非世界透视投影。
package com.shiroha.mmdskin.client.render.state;

import org.joml.Matrix4f;

public final class MmdPipState {
    private static volatile boolean active;
    private static volatile Matrix4f projection = new Matrix4f();

    private MmdPipState() {
    }

    public static void begin(Matrix4f pipProjection) {
        active = true;
        if (pipProjection != null) {
            projection = new Matrix4f(pipProjection);
        }
    }

    public static void end() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static Matrix4f projection() {
        return projection;
    }
}
