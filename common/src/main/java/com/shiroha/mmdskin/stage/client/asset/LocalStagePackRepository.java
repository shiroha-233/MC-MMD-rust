package com.shiroha.mmdskin.stage.client.asset;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationBridgeHolder;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.config.StagePack;

import java.util.List;

public final class LocalStagePackRepository {
    private static final LocalStagePackRepository INSTANCE = new LocalStagePackRepository();

    private LocalStagePackRepository() {
    }

    public static LocalStagePackRepository getInstance() {
        return INSTANCE;
    }

    public List<StagePack> loadStagePacks() {
        PathConstants.ensureStageAnimDir();
        return StagePack.scan(PathConstants.getStageAnimDir(), path -> {
            var animationBridge = NativeAnimationBridgeHolder.get();
            long tempAnim = animationBridge.loadAnimation(0, path);
            if (tempAnim == 0) {
                return null;
            }
            boolean[] result = {
                    animationBridge.hasCameraData(tempAnim),
                    animationBridge.hasBoneData(tempAnim),
                    animationBridge.hasMorphData(tempAnim)
            };
            animationBridge.deleteAnimation(tempAnim);
            return result;
        });
    }
}
