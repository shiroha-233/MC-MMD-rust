/* 文件职责：记录渲染热路径耗时并按周期输出性能统计。 */
package com.shiroha.mmdskin.renderer.performance;

import com.shiroha.mmdskin.config.ConfigManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RenderPerformanceProfiler {
    public static final String SECTION_LIVING_STATE_SYNC = "livingStateSync";
    public static final String SECTION_NATIVE_MODEL_UPDATE = "nativeModelUpdate";
    public static final String SECTION_BONE_UPLOAD = "boneUpload";
    public static final String SECTION_MORPH_UPLOAD = "morphUpload";
    public static final String SECTION_MATERIAL_MORPH_FETCH = "materialMorphFetch";
    public static final String SECTION_COMPUTE_DISPATCH = "computeDispatch";
    public static final String SECTION_SUB_MESH_FETCH = "subMeshFetch";
    public static final String SECTION_DRAW = "draw";

    private static final Logger LOGGER = LogManager.getLogger();
    private static final RenderPerformanceProfiler INSTANCE = new RenderPerformanceProfiler();

    private final Map<String, Long> profilingTotalsNanos = new LinkedHashMap<>();
    private long profilingLastLogTimeMs = System.currentTimeMillis();
    private long profiledFrameCount;
    private long profiledVisibleModels;
    private long profiledPhysicsModels;

    private RenderPerformanceProfiler() {
        profilingTotalsNanos.put(SECTION_LIVING_STATE_SYNC, 0L);
        profilingTotalsNanos.put(SECTION_NATIVE_MODEL_UPDATE, 0L);
        profilingTotalsNanos.put(SECTION_BONE_UPLOAD, 0L);
        profilingTotalsNanos.put(SECTION_MORPH_UPLOAD, 0L);
        profilingTotalsNanos.put(SECTION_MATERIAL_MORPH_FETCH, 0L);
        profilingTotalsNanos.put(SECTION_COMPUTE_DISPATCH, 0L);
        profilingTotalsNanos.put(SECTION_SUB_MESH_FETCH, 0L);
        profilingTotalsNanos.put(SECTION_DRAW, 0L);
    }

    public static RenderPerformanceProfiler get() {
        return INSTANCE;
    }

    public long startTimer() {
        return ConfigManager.isPerformanceProfilingEnabled() ? System.nanoTime() : 0L;
    }

    public synchronized void endTimer(String section, long startTimeNanos) {
        if (startTimeNanos == 0L || !ConfigManager.isPerformanceProfilingEnabled()) {
            return;
        }
        profilingTotalsNanos.merge(section, System.nanoTime() - startTimeNanos, Long::sum);
    }

    public synchronized void completeFrame(int visibleModels, int physicsModels) {
        if (!ConfigManager.isPerformanceProfilingEnabled()) {
            resetProfiling();
            return;
        }

        profiledFrameCount++;
        profiledVisibleModels += visibleModels;
        profiledPhysicsModels += physicsModels;
        maybeLogProfiling();
    }

    private void maybeLogProfiling() {
        long now = System.currentTimeMillis();
        long intervalMs = ConfigManager.getPerformanceLogIntervalSeconds() * 1000L;
        if (profiledFrameCount <= 0L || now - profilingLastLogTimeMs < intervalMs) {
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
            message.append(", ")
                    .append(entry.getKey())
                    .append('=')
                    .append(String.format("%.3fms", avgMs));
        }

        LOGGER.info(message.toString());
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
    }
}
