package com.shiroha.mmdskin.stage.client.asset;

import com.shiroha.mmdskin.NativeFunc;
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
            NativeFunc nativeFunc = NativeFunc.GetInst();
            long tempAnim = nativeFunc.LoadAnimation(0, path);
            if (tempAnim == 0) {
                return null;
            }
            boolean[] result = {
                    nativeFunc.HasCameraData(tempAnim),
                    nativeFunc.HasBoneData(tempAnim),
                    nativeFunc.HasMorphData(tempAnim)
            };
            nativeFunc.DeleteAnimation(tempAnim);
            return result;
        });
    }
}
