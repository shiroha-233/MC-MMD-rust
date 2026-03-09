package com.shiroha.mmdskin.stage.client.playback.port;

public interface StageLocalModelBindingPort {
    StageLocalModelBinding bindLocalModel(long mergedAnim);

    record StageLocalModelBinding(long modelHandle, String modelName) {
        public static StageLocalModelBinding empty() {
            return new StageLocalModelBinding(0, null);
        }
    }
}
