// 负责保证每个模型实例在一个渲染帧内最多推进一次，并按距离做动画 LOD 降频。
package com.shiroha.mmdskin.client.frame;

import com.shiroha.mmdskin.config.ConfigManager;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class MmdFrameUpdater {
    private final FrameUpdatePreparation preparation;
    private final Map<FrameUpdateTarget, Boolean> updatedTargets = new IdentityHashMap<>();
    /**
     * 动画 LOD 状态（每个目标一份）：连续没推进的帧数 + 这些帧累计的 deltaSeconds。
     *
     * <p>用 WeakHashMap 而不是 IdentityHashMap：键是模型实例，实例被模型池回收销毁后
     * 条目会自动消失，不会因为"曾经渲染过"而把一批已销毁的实例一直攥在 Java 侧。
     */
    private final Map<FrameUpdateTarget, AnimationLodState> lodStates = new WeakHashMap<>();
    private long currentFrame = Long.MIN_VALUE;

    public MmdFrameUpdater() {
        this(FrameUpdatePreparation.NONE);
    }

    public MmdFrameUpdater(FrameUpdatePreparation preparation) {
        this.preparation = Objects.requireNonNull(preparation, "preparation");
    }

    public boolean updateOnce(long frameId, FrameUpdateTarget target,
                              MmdRenderSnapshot snapshot, float deltaSeconds) {
        return updateOnce(frameId, target, snapshot, deltaSeconds, false);
    }

    /**
     * 推进一个模型。
     *
     * @param animationLodEnabled 是否按距离降频推进。实体路径开启；尸体 / 场景 / 第一人称
     *                             保持每帧推进——尸体的布娃娃交接依赖"骨骼真的被更新过"
     *                             （见 {@code RagdollManager} 里"远离视线的尸体不能停在 T-pose"）。
     */
    public boolean updateOnce(long frameId, FrameUpdateTarget target,
                              MmdRenderSnapshot snapshot, float deltaSeconds,
                              boolean animationLodEnabled) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0.0F) {
            throw new IllegalArgumentException("deltaSeconds must be finite and non-negative");
        }
        beginFrame(frameId);
        if (updatedTargets.put(target, Boolean.TRUE) != null) {
            return false;
        }
        if (!animationLodEnabled) {
            preparation.prepare(target, snapshot);
            target.update(snapshot, deltaSeconds);
            return true;
        }
        return advanceWithLod(target, snapshot, deltaSeconds);
    }

    /**
     * 按距离 LOD 推进：跳过的帧把 delta 攒起来，下次一次性补上。
     *
     * <p>必须攒 delta：直接把跳过的帧丢掉的话，动画会"变慢"而不是"降频"
     * （远处的模型动作会明显滞后），这是这类降频实现最容易做错的地方。
     */
    private boolean advanceWithLod(FrameUpdateTarget target, MmdRenderSnapshot snapshot, float deltaSeconds) {
        int interval = animationLodInterval(snapshot.cameraDistanceSquared());
        AnimationLodState state = lodStates.computeIfAbsent(target, ignored -> new AnimationLodState());
        state.pendingFrames++;
        state.pendingDelta += deltaSeconds;
        if (state.pendingFrames < interval) {
            // 本帧不推进：delta 先存着，等攒够一个间隔再一次性喂给引擎。
            return false;
        }
        float accumulatedDelta = state.pendingDelta;
        state.pendingFrames = 0;
        state.pendingDelta = 0.0F;
        preparation.prepare(target, snapshot);
        target.update(snapshot, accumulatedDelta);
        return true;
    }

    /**
     * 距离 → 推进间隔（帧）。
     *
     * <p>近（{@code animationLodMediumDistance} 以内）每帧推进；
     * 中（medium..far）每 {@code animationLodMediumUpdateInterval} 帧推进一次；
     * 远（{@code animationLodFarDistance} 以外）每 {@code animationLodFarUpdateInterval} 帧一次。
     */
    private static int animationLodInterval(double cameraDistanceSquared) {
        float mediumDistance = ConfigManager.getAnimationLodMediumDistance();
        if (cameraDistanceSquared <= (double) mediumDistance * mediumDistance) {
            return 1;
        }
        float farDistance = ConfigManager.getAnimationLodFarDistance();
        if (cameraDistanceSquared <= (double) farDistance * farDistance) {
            return Math.max(1, ConfigManager.getAnimationLodMediumUpdateInterval());
        }
        return Math.max(1, ConfigManager.getAnimationLodFarUpdateInterval());
    }

    private void beginFrame(long frameId) {
        if (currentFrame == frameId) {
            return;
        }
        currentFrame = frameId;
        updatedTargets.clear();
    }

    private static final class AnimationLodState {
        /** 连续没真正推进的帧数。 */
        private int pendingFrames;
        /** 这些帧累计的秒数（下次推进时一次性传入）。 */
        private float pendingDelta;
    }
}
