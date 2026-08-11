package com.shiroha.mmdskin.render.pipeline;

import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.render.policy.ConfigManagerRenderPerformanceConfig;
import com.shiroha.mmdskin.render.policy.RenderPerformanceConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 文件职责：记录渲染热路径耗时并按周期输出统计。 */
public final class RenderPerformanceProfiler {
    public static final String SECTION_LIVING_STATE_SYNC = "livingStateSync";
    public static final String SECTION_NATIVE_MODEL_UPDATE = "nativeModelUpdate";
    public static final String SECTION_BONE_UPLOAD = "boneUpload";
    public static final String SECTION_MORPH_UPLOAD = "morphUpload";
    public static final String SECTION_MATERIAL_MORPH_FETCH = "materialMorphFetch";
    public static final String SECTION_COMPUTE_DISPATCH = "computeDispatch";
    public static final String SECTION_SUB_MESH_FETCH = "subMeshFetch";
    public static final String SECTION_DRAW = "draw";
    public static final String GPU_SECTION_COMPUTE = "gpuCompute";
    public static final String GPU_SECTION_DRAW = "gpuDraw";

    /** 传输分类只在真实 JNI/OpenGL 数据搬运点记录，避免 HUD 展示推算值。 */
    public enum TransferKind {
        BONE,
        VERTEX_MORPH,
        UV_MORPH,
        MATERIAL_MORPH,
        SUB_MESH,
        CPU_VERTEX,
        FIRST_PERSON_INDEX,
        LIGHT,
        GPU_READBACK
    }

    private static final Logger logger = LogManager.getLogger();
    private static final RenderPerformanceProfiler INSTANCE = new RenderPerformanceProfiler();
    private final RenderPerformanceConfig config = ConfigManagerRenderPerformanceConfig.get();

    private final Map<String, Long> profilingTotalsNanos = new LinkedHashMap<>();
    private final Map<String, Long> currentFrameTotalsNanos = new LinkedHashMap<>();
    private final Map<String, Integer> currentFrameCalls = new LinkedHashMap<>();
    private final Map<String, Long> currentGpuTotalsNanos = new LinkedHashMap<>();
    private final Map<String, PerformanceSampleWindow> sectionWindows = new LinkedHashMap<>();
    private final PerformanceSampleWindow frameTimeWindow = new PerformanceSampleWindow(120);
    private final long[] currentTransferBytes = new long[TransferKind.values().length];
    private final long[] currentAvoidedUploadBytes = new long[TransferKind.values().length];
    private long lastPresentedFrameNanos;
    private volatile Snapshot latestSnapshot = Snapshot.empty();

    private long profilingLastLogTimeMs = System.currentTimeMillis();
    private long profiledFrameCount = 0L;
    private long profiledVisibleModels = 0L;
    private long profiledPhysicsModels = 0L;

    private RenderPerformanceProfiler() {
        profilingTotalsNanos.put(SECTION_LIVING_STATE_SYNC, 0L);
        profilingTotalsNanos.put(SECTION_NATIVE_MODEL_UPDATE, 0L);
        profilingTotalsNanos.put(SECTION_BONE_UPLOAD, 0L);
        profilingTotalsNanos.put(SECTION_MORPH_UPLOAD, 0L);
        profilingTotalsNanos.put(SECTION_MATERIAL_MORPH_FETCH, 0L);
        profilingTotalsNanos.put(SECTION_COMPUTE_DISPATCH, 0L);
        profilingTotalsNanos.put(SECTION_SUB_MESH_FETCH, 0L);
        profilingTotalsNanos.put(SECTION_DRAW, 0L);
        for (String section : profilingTotalsNanos.keySet()) {
            currentFrameTotalsNanos.put(section, 0L);
            currentFrameCalls.put(section, 0);
            sectionWindows.put(section, new PerformanceSampleWindow(120));
        }
        sectionWindows.put(GPU_SECTION_COMPUTE, new PerformanceSampleWindow(120));
        sectionWindows.put(GPU_SECTION_DRAW, new PerformanceSampleWindow(120));
        currentGpuTotalsNanos.put(GPU_SECTION_COMPUTE, 0L);
        currentGpuTotalsNanos.put(GPU_SECTION_DRAW, 0L);
    }

    public static RenderPerformanceProfiler get() {
        return INSTANCE;
    }

    public long startTimer() {
        return isSamplingEnabled() ? System.nanoTime() : 0L;
    }

    public synchronized void endTimer(String section, long startTimeNanos) {
        if (startTimeNanos == 0L || !isSamplingEnabled()) {
            return;
        }

        long elapsed = System.nanoTime() - startTimeNanos;
        profilingTotalsNanos.merge(section, elapsed, Long::sum);
        currentFrameTotalsNanos.merge(section, elapsed, Long::sum);
        currentFrameCalls.merge(section, 1, Integer::sum);
    }

    /** GPU 查询结果会延迟若干帧返回，因此独立写入阶段窗口。 */
    public synchronized void recordGpuTime(String section, long elapsedNanos) {
        if (currentGpuTotalsNanos.containsKey(section) && elapsedNanos >= 0L) {
            currentGpuTotalsNanos.merge(section, elapsedNanos, Long::sum);
        }
    }

    public synchronized void recordTransfer(TransferKind kind, long bytes) {
        if (isSamplingEnabled() && bytes > 0L) {
            currentTransferBytes[kind.ordinal()] += bytes;
        }
    }

    public synchronized void recordAvoidedUpload(TransferKind kind, long bytes) {
        if (isSamplingEnabled() && bytes > 0L) {
            currentAvoidedUploadBytes[kind.ordinal()] += bytes;
        }
    }

    /** HUD 绘制边界用于采样实际呈现帧间隔，不参与 MMD 世界帧阶段累加。 */
    public synchronized void markPresentedFrame() {
        if (!ConfigManager.isDebugHudEnabled()) {
            lastPresentedFrameNanos = 0L;
            return;
        }
        long now = System.nanoTime();
        if (lastPresentedFrameNanos != 0L) {
            frameTimeWindow.record(now - lastPresentedFrameNanos);
        }
        lastPresentedFrameNanos = now;
    }

    public Snapshot snapshot() {
        return latestSnapshot;
    }

    public synchronized void completeFrame(int visibleModels, int physicsModels) {
        if (!isSamplingEnabled()) {
            resetProfiling();
            return;
        }

        profiledFrameCount++;
        profiledVisibleModels += visibleModels;
        profiledPhysicsModels += physicsModels;
        for (Map.Entry<String, Long> entry : currentFrameTotalsNanos.entrySet()) {
            sectionWindows.get(entry.getKey()).record(entry.getValue());
            entry.setValue(0L);
        }
        for (Map.Entry<String, Long> entry : currentGpuTotalsNanos.entrySet()) {
            if (entry.getValue() > 0L) {
                sectionWindows.get(entry.getKey()).record(entry.getValue());
            }
            entry.setValue(0L);
        }
        Map<String, Integer> frameCalls = Map.copyOf(currentFrameCalls);
        currentFrameCalls.replaceAll((section, count) -> 0);
        long[] frameTransferBytes = currentTransferBytes.clone();
        long[] frameAvoidedUploadBytes = currentAvoidedUploadBytes.clone();
        java.util.Arrays.fill(currentTransferBytes, 0L);
        java.util.Arrays.fill(currentAvoidedUploadBytes, 0L);
        rebuildSnapshot(visibleModels, physicsModels, frameCalls, frameTransferBytes, frameAvoidedUploadBytes);
        if (config.isPerformanceProfilingEnabled()) {
            maybeLogProfiling();
        }
    }

    private void rebuildSnapshot(int visibleModels, int physicsModels, Map<String, Integer> frameCalls,
                                 long[] transferBytes, long[] avoidedUploadBytes) {
        Map<String, PerformanceSampleWindow.Snapshot> sections = new LinkedHashMap<>();
        for (Map.Entry<String, PerformanceSampleWindow> entry : sectionWindows.entrySet()) {
            sections.put(entry.getKey(), entry.getValue().snapshot());
        }
        latestSnapshot = new Snapshot(
                frameTimeWindow.snapshot(), Map.copyOf(sections), frameCalls, visibleModels, physicsModels,
                new TransferSnapshot(transferBytes, avoidedUploadBytes));
    }

    private boolean isSamplingEnabled() {
        return config.isPerformanceProfilingEnabled() || ConfigManager.isDebugHudEnabled();
    }

    private void maybeLogProfiling() {
        long now = System.currentTimeMillis();
        long intervalMs = config.getPerformanceLogIntervalSeconds() * 1000L;
        if (profiledFrameCount <= 0 || now - profilingLastLogTimeMs < intervalMs) {
            return;
        }

        StringBuilder message = new StringBuilder("[MMD性能] frames=")
                .append(profiledFrameCount)
                .append(", avgVisible=")
                .append(String.format("%.2f", profiledVisibleModels / (double) profiledFrameCount))
                .append(", avgPhysics=")
                .append(String.format("%.2f", profiledPhysicsModels / (double) profiledFrameCount));

        for (Map.Entry<String, Long> entry : profilingTotalsNanos.entrySet()) {
            double avgMs = entry.getValue() / 1_000_000.0d / profiledFrameCount;
            message.append(", ").append(entry.getKey()).append('=')
                    .append(String.format("%.3fms", avgMs));
        }

        logger.info(message.toString());
        resetProfiling();
    }

    private void resetProfiling() {
        profilingLastLogTimeMs = System.currentTimeMillis();
        profiledFrameCount = 0L;
        profiledVisibleModels = 0L;
        profiledPhysicsModels = 0L;
        for (Map.Entry<String, Long> entry : profilingTotalsNanos.entrySet()) {
            entry.setValue(0L);
        }
        java.util.Arrays.fill(currentTransferBytes, 0L);
        java.util.Arrays.fill(currentAvoidedUploadBytes, 0L);
    }

    /** HUD 只读取不可变快照，避免在绘制阶段持有 Profiler 锁。 */
    public record Snapshot(PerformanceSampleWindow.Snapshot frameTime,
                           Map<String, PerformanceSampleWindow.Snapshot> sections,
                           Map<String, Integer> sectionCalls,
                           int visibleModels,
                           int physicsModels,
                           TransferSnapshot transfers) {
        private static Snapshot empty() {
            return new Snapshot(PerformanceSampleWindow.Snapshot.empty(), Map.of(), Map.of(), 0, 0,
                    TransferSnapshot.empty());
        }

        public PerformanceSampleWindow.Snapshot section(String name) {
            return sections.getOrDefault(name, PerformanceSampleWindow.Snapshot.empty());
        }

        public int calls(String name) {
            return sectionCalls.getOrDefault(name, 0);
        }
    }

    /** 数组在构造时复制，HUD 无需持有 profiler 锁即可安全读取。 */
    public record TransferSnapshot(long[] uploadedBytes, long[] avoidedUploadBytes) {
        public TransferSnapshot {
            uploadedBytes = uploadedBytes.clone();
            avoidedUploadBytes = avoidedUploadBytes.clone();
        }

        private static TransferSnapshot empty() {
            return new TransferSnapshot(new long[TransferKind.values().length],
                    new long[TransferKind.values().length]);
        }

        @Override
        public long[] uploadedBytes() {
            return uploadedBytes.clone();
        }

        @Override
        public long[] avoidedUploadBytes() {
            return avoidedUploadBytes.clone();
        }

        public long uploaded(TransferKind kind) {
            return uploadedBytes[kind.ordinal()];
        }

        public long avoided(TransferKind kind) {
            return avoidedUploadBytes[kind.ordinal()];
        }

        public long totalGpuUploaded() {
            long total = 0L;
            return uploaded(TransferKind.BONE)
                    + uploaded(TransferKind.VERTEX_MORPH)
                    + uploaded(TransferKind.UV_MORPH)
                    + uploaded(TransferKind.CPU_VERTEX)
                    + uploaded(TransferKind.FIRST_PERSON_INDEX)
                    + uploaded(TransferKind.LIGHT);
        }

        public long totalGpuUploadAvoided() {
            return avoided(TransferKind.BONE)
                    + avoided(TransferKind.VERTEX_MORPH)
                    + avoided(TransferKind.UV_MORPH)
                    + avoided(TransferKind.CPU_VERTEX)
                    + avoided(TransferKind.FIRST_PERSON_INDEX)
                    + avoided(TransferKind.LIGHT);
        }

        public long totalCpuTransfer() {
            return uploaded(TransferKind.MATERIAL_MORPH) + uploaded(TransferKind.SUB_MESH);
        }

        public long totalCpuTransferAvoided() {
            return avoided(TransferKind.MATERIAL_MORPH) + avoided(TransferKind.SUB_MESH);
        }
    }
}
