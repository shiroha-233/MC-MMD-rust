package com.shiroha.mmdskin.compat.tacz;

import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.EnumSet;
import java.util.Optional;

/** 文件职责：保存 TaCZ 第一人称枪械渲染期间单帧有效的双手矩阵。 */
public final class TaczFirstPersonFrameSnapshot {
    private static final ThreadLocal<FrameState> ACTIVE_FRAME = new ThreadLocal<>();
    private static final java.util.concurrent.atomic.AtomicLong NEXT_FRAME_ID = new java.util.concurrent.atomic.AtomicLong();

    private TaczFirstPersonFrameSnapshot() {
    }

    /** 在枪械渲染入口建立新帧，并主动丢弃同线程未正常结束的旧帧。 */
    public static void beginFrame(Object localPlayer, Object gunStack, Matrix4f entryPose, boolean renderThread) {
        ACTIVE_FRAME.remove();
        if (!renderThread || localPlayer == null || gunStack == null || !isFinite(entryPose)) {
            return;
        }
        // 入口矩阵与手臂矩阵来自同一个 TaCZ 调用，可用于排除跨渲染 pass 的基准差异。
        ACTIVE_FRAME.set(new FrameState(NEXT_FRAME_ID.incrementAndGet(), Thread.currentThread(), localPlayer,
                gunStack, new Matrix4f(entryPose)));
    }

    /** 复制 TaCZ 在 Z 轴翻转后即将交给原版手臂的最终矩阵。 */
    public static boolean captureHand(Object localPlayer, Hand hand, Matrix4f pose, Matrix3f normal, boolean renderThread) {
        FrameState frame = ACTIVE_FRAME.get();
        if (!isCurrentFrame(frame, localPlayer, renderThread) || hand == null || !isFinite(pose) || !isFinite(normal)) {
            return false;
        }
        frame.set(hand, new HandMatrices(new Matrix4f(pose), new Matrix3f(normal)));
        return true;
    }

    /** MMD 已取得本帧枪械手臂所有权时隐藏原版手臂；矩阵缺帧不应触发方块手臂闪回。 */
    public static boolean shouldSuppressOriginalArm(Object localPlayer, Hand hand, boolean renderThread) {
        FrameState frame = ACTIVE_FRAME.get();
        return isCurrentFrame(frame, localPlayer, renderThread)
                && hand != null
                && TaczFirstPersonPostRenderer.ownsArmRendering(localPlayer, frame.gunStack);
    }

    /**
     * 结束本帧并清除线程局部状态；返回值是不可变副本，供后续切片在同一渲染回调中安全消费。
     * 当前切片故意丢弃返回值，绝不在此处绘制 MMD。
     */
    public static Optional<Snapshot> finishFrame(boolean renderThread) {
        FrameState frame = ACTIVE_FRAME.get();
        try {
            if (frame == null || !renderThread || frame.ownerThread != Thread.currentThread()) {
                return Optional.empty();
            }
            return Optional.of(new Snapshot(frame.frameId, frame.localPlayer, frame.gunStack,
                    frame.entryPose, frame.left, frame.right));
        } finally {
            ACTIVE_FRAME.remove();
        }
    }

    /** 测试与异常诊断使用：无论调用点如何都清空当前线程的暂存帧。 */
    static void clearCurrentThreadFrame() {
        ACTIVE_FRAME.remove();
    }

    private static boolean isCurrentFrame(FrameState frame, Object localPlayer, boolean renderThread) {
        return renderThread && frame != null && frame.ownerThread == Thread.currentThread() && frame.localPlayer == localPlayer;
    }

    /**
     * TaCZ 可能在开火时复制枪械栈并更新弹药等动态标签；同帧所有权只绑定物品类型。
     * 空栈和不同物品仍拒绝，非 Minecraft 对象只用于隔离测试并保持严格身份匹配。
     */
    static boolean matchesGunStack(Object expected, Object actual) {
        if (expected == actual) {
            return true;
        }
        if (!(expected instanceof ItemStack expectedStack)
                || !(actual instanceof ItemStack actualStack)
                || expectedStack.isEmpty()
                || actualStack.isEmpty()) {
            return false;
        }
        return expectedStack.is(actualStack.getItem());
    }

    private static boolean isFinite(Matrix4f matrix) {
        return matrix != null
                && Float.isFinite(matrix.m00()) && Float.isFinite(matrix.m01()) && Float.isFinite(matrix.m02()) && Float.isFinite(matrix.m03())
                && Float.isFinite(matrix.m10()) && Float.isFinite(matrix.m11()) && Float.isFinite(matrix.m12()) && Float.isFinite(matrix.m13())
                && Float.isFinite(matrix.m20()) && Float.isFinite(matrix.m21()) && Float.isFinite(matrix.m22()) && Float.isFinite(matrix.m23())
                && Float.isFinite(matrix.m30()) && Float.isFinite(matrix.m31()) && Float.isFinite(matrix.m32()) && Float.isFinite(matrix.m33());
    }

    private static boolean isFinite(Matrix3f matrix) {
        return matrix != null
                && Float.isFinite(matrix.m00()) && Float.isFinite(matrix.m01()) && Float.isFinite(matrix.m02())
                && Float.isFinite(matrix.m10()) && Float.isFinite(matrix.m11()) && Float.isFinite(matrix.m12())
                && Float.isFinite(matrix.m20()) && Float.isFinite(matrix.m21()) && Float.isFinite(matrix.m22());
    }

    public enum Hand { LEFT, RIGHT }

    /** 已完成帧的只读数据；矩阵访问器每次返回副本，避免后续调用污染采样值。 */
    public static final class Snapshot {
        private final long frameId;
        private final Object localPlayer;
        private final Object gunStack;
        private final Matrix4f entryPose;
        private final HandMatrices left;
        private final HandMatrices right;
        private final EnumSet<Hand> consumedHands = EnumSet.noneOf(Hand.class);

        private Snapshot(long frameId, Object localPlayer, Object gunStack, Matrix4f entryPose,
                         HandMatrices left, HandMatrices right) {
            this.frameId = frameId;
            this.localPlayer = localPlayer;
            this.gunStack = gunStack;
            this.entryPose = new Matrix4f(entryPose);
            this.left = left;
            this.right = right;
        }

        public long frameId() {
            return frameId;
        }

        public Object localPlayer() {
            return localPlayer;
        }

        public Object gunStack() {
            return gunStack;
        }

        public Matrix4f entryPose() {
            return new Matrix4f(entryPose);
        }

        /** 只读查看捕获矩阵，不消耗本帧目标。 */
        public Optional<HandMatrices> capturedHand(Hand hand) {
            HandMatrices matrices = hand == Hand.LEFT ? left : hand == Hand.RIGHT ? right : null;
            return Optional.ofNullable(matrices == null ? null : matrices.copy());
        }

        /** 返回本帧实际捕获到的左右手位掩码，供低频运行时诊断使用。 */
        public int capturedMask() {
            int mask = 0;
            if (left != null) mask |= 0b01;
            if (right != null) mask |= 0b10;
            return mask;
        }

        /** 每只手只能由同一本地玩家和同一枪械标识安全消费一次。 */
        public Optional<HandMatrices> consume(Object expectedPlayer, Object expectedGunStack, Hand hand) {
            if (expectedPlayer != localPlayer || !matchesGunStack(expectedGunStack, gunStack) || hand == null) {
                return Optional.empty();
            }
            if (!consumedHands.add(hand)) {
                return Optional.empty();
            }
            HandMatrices matrices = hand == Hand.LEFT ? left : hand == Hand.RIGHT ? right : null;
            return Optional.ofNullable(matrices == null ? null : matrices.copy());
        }
    }

    /** 左右手的 pose/normal 成对矩阵。 */
    public record HandMatrices(Matrix4f pose, Matrix3f normal) {
        public HandMatrices {
            pose = new Matrix4f(pose);
            normal = new Matrix3f(normal);
        }

        private HandMatrices copy() {
            return new HandMatrices(pose, normal);
        }
    }

    private static final class FrameState {
        private final long frameId;
        private final Thread ownerThread;
        private final Object localPlayer;
        private final Object gunStack;
        private final Matrix4f entryPose;
        private HandMatrices left;
        private HandMatrices right;

        private FrameState(long frameId, Thread ownerThread, Object localPlayer, Object gunStack,
                           Matrix4f entryPose) {
            this.frameId = frameId;
            this.ownerThread = ownerThread;
            this.localPlayer = localPlayer;
            this.gunStack = gunStack;
            this.entryPose = entryPose;
        }

        private HandMatrices get(Hand hand) {
            return hand == Hand.LEFT ? left : right;
        }

        private void set(Hand hand, HandMatrices matrices) {
            if (hand == Hand.LEFT) {
                left = matrices;
            } else {
                right = matrices;
            }
        }
    }
}
