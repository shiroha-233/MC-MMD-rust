// 负责用 NeoForge 26.3 的渲染事件驱动 MMD 的世界帧生命周期。
//
// 26.3 适配要点：Fabric 端的三个世界渲染钩子在 NeoForge 26.3 里有一一对应的事件：
// <ul>
//   <li>Fabric {@code LevelExtractionEvents.END_EXTRACTION}（开帧）
//       → NeoForge {@code ExtractLevelRenderStateEvent}；
//       但本实现改用 {@code GameRendererMixin} 在 {@code GameRenderer.renderLevel} 的 HEAD
//       开帧（语义等价：都早于实体提交）。</li>
//   <li>Fabric {@code LevelRenderEvents.COLLECT_SUBMITS}（第一人称本地模型提交）
//       → NeoForge {@code SubmitCustomGeometryEvent}：
//       该事件在粒子提交之后、不透明提交渲染之前触发，此时
//       {@code SubmitNodeCollector} 还没被消费，手持物仍可提交。</li>
//   <li>Fabric {@code LevelRenderEvents.END_MAIN}（冲刷世界帧）
//       → NeoForge {@code RenderLevelStageEvent.AfterLevel}：
//       该事件在 {@code LevelRenderer.render} 返回之后触发（主 Render Pass 已关闭、
//       没有别的 Pass 处于打开状态），地形与实体已经画进主目标（深度缓冲有效）。</li>
// </ul>
package com.shiroha.mmdskin.neoforge.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.compat.iris.IrisCompatibility;
import com.shiroha.mmdskin.neoforge.compat.YsmCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

public final class NeoForgeRenderAdapter {
    /**
     * 26.3 的 {@code Lightmap} 不再暴露 {@code FULL_BRIGHT}，这里直接用 MC 的全亮 packed light 常量
     * （block = 0xF000，sky = 0xF000）。
     */
    private static final int FULL_BRIGHT_PACKED_LIGHT = 0xF000F0;

    /**
     * 第一人称本地模型提交：与 Fabric 端 {@code LevelRenderEvents.COLLECT_SUBMITS} 同位置。
     */
    @SubscribeEvent
    public void onCollectSubmits(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        MmdClientRenderRuntime runtime = MmdClientRenderRuntime.current();
        LocalPlayer player = minecraft.player;
        // 26.3 的 LevelRenderState.worldPartialTicks 就是 1.21.5 的
        // tickCounter().getGameTimeDeltaPartialTick(false)。
        float partialTick = event.getLevelRenderState().worldPartialTicks;
        runtime.firstPerson().localModels().contribute(
                minecraft, minecraft.gameRenderer.mainCamera(), partialTick,
                runtime.frameId(), runtime.frameDeltaSeconds(),
                player != null && YsmCompat.isYsmActive(player),
                event.getPoseStack(), event.getSubmitNodeCollector());
    }

    /**
     * 冲刷世界帧：与 Fabric 端 {@code LevelRenderEvents.END_MAIN} 同位置。
     */
    @SubscribeEvent
    public void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        if (IrisCompatibility.isShadowPass()) {
            MmdClientRenderRuntime.current().frameQueue().clear();
            return;
        }
        MmdClientRenderRuntime runtime = MmdClientRenderRuntime.current();
        var camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        // 26.3 的世界视图矩阵（相机旋转）是在 LevelRenderer.render 期间压进
        // RenderSystem.getModelViewStack() 的，LevelRenderer 返回时弹栈。
        //
        // 两端触发时机不同：
        //   * Fabric 的 LevelRenderEvents.END_MAIN 在主 Pass 的 lambda 里触发
        //     （LevelRenderer.render 还没返回，压栈还在）→ MMD 读到的是相机旋转矩阵；
        //   * NeoForge 的 RenderLevelStageEvent.AfterLevel 在 LevelRenderer.render
        //     返回之后触发（压栈已弹）→ MMD 读到的是单位阵。
        //
        // MMD 的模型矩阵是「相机相对」的（实体位置 - 相机位置），必须再乘上相机旋转
        // 才是视空间坐标。少了这一步，相机相对偏移就被当成世界轴向偏移消费：
        // 转视角时模型会绕着玩家转圈，连远近都会变（深度分量跟着视角改）。
        //
        // 所以这里按 LevelRenderer 的做法把同一份视图旋转矩阵重新压栈，
        // 让两端的模型矩阵语义完全一致（Fabric 端代码与行为零变化）。
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try {
            modelViewStack.identity();
            modelViewStack.mul(viewRotationMatrix(event));
            runtime.flushWorldFrame(
                    camera.x, camera.y, camera.z, FULL_BRIGHT_PACKED_LIGHT);
        } finally {
            modelViewStack.popMatrix();
        }
    }

    /**
     * 本帧世界渲染实际使用的视图旋转矩阵。
     *
     * <p>优先取 {@code LevelRenderState.cameraRenderState.viewRotationMatrix}
     * ——它就是 {@code LevelRenderer.render} 压进 modelViewStack 的那一份，
     * 保证和地形、实体用的是同一个矩阵（相机横滚也带上）。
     * 拿不到时退回相机自己算的 {@code Camera.getViewRotationMatrix}。
     */
    private static Matrix4f viewRotationMatrix(RenderLevelStageEvent.AfterLevel event) {
        LevelRenderState levelState = event.getLevelRenderState();
        if (levelState != null && levelState.cameraRenderState != null
                && levelState.cameraRenderState.viewRotationMatrix != null) {
            return levelState.cameraRenderState.viewRotationMatrix;
        }
        return Minecraft.getInstance().gameRenderer.mainCamera()
                .getViewRotationMatrix(new Matrix4f());
    }
}
