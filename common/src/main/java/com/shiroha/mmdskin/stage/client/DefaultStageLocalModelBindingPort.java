package com.shiroha.mmdskin.stage.client;

import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import com.shiroha.mmdskin.stage.client.playback.port.StageLocalModelBindingPort;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import net.minecraft.client.Minecraft;

public final class DefaultStageLocalModelBindingPort implements StageLocalModelBindingPort {
    public static final DefaultStageLocalModelBindingPort INSTANCE = new DefaultStageLocalModelBindingPort();

    private DefaultStageLocalModelBindingPort() {
    }

    @Override
    public StageLocalModelBinding bindLocalModel(long mergedAnim) {
        return bindLocalModel(mergedAnim, false);
    }

    /**
     * 绑定本地模型到舞台动画
     * @param mergedAnim 合并后的动画句柄
     * @param puppetMode 纸娃娃模式：只播放纸娃娃动画，不设置玩家模型动画标志
     * @return 模型绑定结果
     */
    public StageLocalModelBinding bindLocalModel(long mergedAnim, boolean puppetMode) {
        Minecraft mc = StageClientContext.minecraft();
        if (mc.player == null) {
            return StageLocalModelBinding.empty();
        }

        String modelName = ModelSelectorConfig.getInstance().getSelectedModel();
        if (modelName == null || modelName.isEmpty()) {
            return StageLocalModelBinding.empty();
        }

        MMDModelManager.Model modelData = MMDModelManager.GetModel(
                modelName,
                PlayerModelResolver.getCacheKey(mc.player)
        );
        if (modelData == null) {
            return StageLocalModelBinding.empty();
        }

        long modelHandle = modelData.model.getModelHandle();
        
        // 纸娃娃模式下，将动画应用到玩家模型
        // 注意：模型不会在世界中渲染（MmdSkinRenderer 会跳过），但动画系统需要运行
        if (puppetMode) {
            if (mergedAnim != 0 && modelHandle != 0) {
                // 清除覆盖层动画
                modelData.model.setLayerLoop(1, true);
                modelData.model.changeAnim(0, 1);
                modelData.model.changeAnim(0, 2);
                // 重置物理
                modelData.model.resetPhysics();
                // 使状态层失效
                modelData.entityData.invalidateStateLayers();
                // 直接应用动画到第0层
                modelData.model.transitionAnim(mergedAnim, 0, 0.3f);
                // 设置两个标志：playCustomAnim 和 playStageAnim
                // playCustomAnim: 让 AnimationStateManager 跳过默认动画更新
                // playStageAnim: 防止 shouldStopCustomAnimation 在玩家移动时停止动画
                modelData.entityData.playCustomAnim = true;
                modelData.entityData.playStageAnim = true;
                // 注意：虽然设置了 playStageAnim，但 syncLocalStageFrame 会检查 state == PUPPET_MODE
                // 实际上不会同步，动画帧由 MMDCameraController.updatePuppetMode() 手动同步
            }
        } else {
            // 普通舞台模式，使用标准方法
            MmdSkinRendererPlayerHelper.startStageAnimation(modelData, mergedAnim);
        }
        
        return new StageLocalModelBinding(modelHandle, modelName);
    }
}
