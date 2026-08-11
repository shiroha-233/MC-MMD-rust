package com.shiroha.mmdskin.render.pipeline;

import com.shiroha.mmdskin.config.ConfigManager;
import java.util.ArrayDeque;
import java.util.Deque;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;

/** 文件职责：用固定数量的异步 OpenGL 查询采集 GPU 阶段耗时，绝不等待查询完成。 */
public final class GpuTimerQueryPool {
    private static final int MAX_QUERIES = 32;
    private static final GpuTimerQueryPool COMPUTE = new GpuTimerQueryPool(
            RenderPerformanceProfiler.GPU_SECTION_COMPUTE);
    private static final GpuTimerQueryPool DRAW = new GpuTimerQueryPool(
            RenderPerformanceProfiler.GPU_SECTION_DRAW);

    private final String section;
    private final Deque<Integer> available = new ArrayDeque<>(MAX_QUERIES);
    private final Deque<Integer> pending = new ArrayDeque<>(MAX_QUERIES);
    private boolean initialized;
    private boolean supported = true;
    private int activeQuery;

    private GpuTimerQueryPool(String section) {
        this.section = section;
    }

    public static GpuTimerQueryPool compute() {
        return COMPUTE;
    }

    public static GpuTimerQueryPool draw() {
        return DRAW;
    }

    /** 查询池耗尽时跳过样本，避免为了调试信息阻塞渲染线程。 */
    public void begin() {
        if (!ConfigManager.isDebugHudEnabled() || activeQuery != 0 || !ensureInitialized()) {
            return;
        }
        collectAvailableResults();
        Integer query = available.pollFirst();
        if (query == null) {
            return;
        }
        activeQuery = query;
        GL33C.glBeginQuery(GL33C.GL_TIME_ELAPSED, query);
    }

    public void end() {
        if (activeQuery == 0) {
            return;
        }
        GL33C.glEndQuery(GL33C.GL_TIME_ELAPSED);
        pending.addLast(activeQuery);
        activeQuery = 0;
    }

    private boolean ensureInitialized() {
        if (initialized || !supported) {
            return supported;
        }
        initialized = true;
        try {
            if (GL.getCapabilities() == null || !GL.getCapabilities().OpenGL33) {
                supported = false;
                return false;
            }
            for (int i = 0; i < MAX_QUERIES; i++) {
                available.addLast(GL33C.glGenQueries());
            }
            return true;
        } catch (RuntimeException exception) {
            supported = false;
            return false;
        }
    }

    private void collectAvailableResults() {
        while (!pending.isEmpty()) {
            int query = pending.peekFirst();
            if (GL33C.glGetQueryObjecti(query, GL33C.GL_QUERY_RESULT_AVAILABLE) == 0) {
                break;
            }
            pending.removeFirst();
            long elapsedNanos = GL33C.glGetQueryObjecti64(query, GL33C.GL_QUERY_RESULT);
            RenderPerformanceProfiler.get().recordGpuTime(section, elapsedNanos);
            available.addLast(query);
        }
    }
}
