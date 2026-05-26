package com.shiroha.mmdskin.stage.client.playback.port;

public interface StageLocalModelBindingPort {
    StageLocalModelBinding bindLocalModel(long mergedAnim);

    /**
     * 绑定本地模型到舞台动画
     * @param mergedAnim 合并后的动画句柄
     * @param puppetMode 纸娃娃模式：只播放纸娃娃动画，不设置玩家模型动画标志
     * @return 模型绑定结果
     */
    default StageLocalModelBinding bindLocalModel(long mergedAnim, boolean puppetMode) {
        return bindLocalModel(mergedAnim);
    }

    record StageLocalModelBinding(long modelHandle, String modelName) {
        public static StageLocalModelBinding empty() {
            return new StageLocalModelBinding(0, null);
        }
    }
}
