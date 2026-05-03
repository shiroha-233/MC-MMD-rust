package com.shiroha.mmdskin.stage.client.playback.port;

/** 文件职责：绑定本地玩家舞台模型并提供舞台动画恢复操作。 */
public interface StageLocalModelBindingPort extends PlayerStageAnimationPort {
    StageLocalModelBinding bindLocalModel(long mergedAnim);

    record StageLocalModelBinding(long modelHandle, String modelName) {
        public static StageLocalModelBinding empty() {
            return new StageLocalModelBinding(0, null);
        }
    }
}
