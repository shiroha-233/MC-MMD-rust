package com.shiroha.mmdskin.stage.client.asset;

import com.shiroha.mmdskin.bridge.runtime.NativeAnimationPort;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.stage.client.StageClientRuntime;

import java.util.List;
import java.util.Objects;

/** 文件职责：扫描本地舞台资源包并读取动画能力摘要。 */
public final class LocalStagePackRepository {
    private final NativeAnimationPort animationPort;

    public LocalStagePackRepository(NativeAnimationPort animationPort) {
        this.animationPort = Objects.requireNonNull(animationPort, "animationPort");
    }

    public static LocalStagePackRepository getInstance() {
        return StageClientRuntime.get().stagePackRepository();
    }

    public List<StagePack> loadStagePacks() {
        PathConstants.ensureStageAnimDir();
        return StagePack.scan(PathConstants.getStageAnimDir(), path -> {
            long tempAnim = animationPort.loadAnimation(0, path);
            if (tempAnim == 0) {
                return null;
            }
            boolean[] result = {
                    animationPort.hasCameraData(tempAnim),
                    animationPort.hasBoneData(tempAnim),
                    animationPort.hasMorphData(tempAnim)
            };
            animationPort.deleteAnimation(tempAnim);
            return result;
        });
    }
}
