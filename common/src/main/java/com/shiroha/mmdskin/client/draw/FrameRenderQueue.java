// 负责收集、排序并在批处理边界统一提交 MMD 绘制请求。
package com.shiroha.mmdskin.client.draw;

import com.shiroha.mmdskin.client.metrics.FrameQueueMetricsPort;
import com.shiroha.mmdskin.config.ConfigManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class FrameRenderQueue implements FrameQueueMetricsPort {
    /**
     * 绘制顺序：不透明在前，透明按由远到近。
     *
     * <p>透明体必须最后画且由远及近（近的盖住远的），否则半透明混合会错。
     */
    private static final Comparator<QueuedRequest> DRAW_ORDER = Comparator
            .comparing((QueuedRequest queued) -> queued.request.transparent())
            .thenComparing((QueuedRequest queued) -> queued.request.transparent()
                    ? -queued.request.snapshot().cameraDistanceSquared()
                    : 0.0D)
            .thenComparingLong(QueuedRequest::sequence);

    /**
     * 截断用的排序：按距离升序（近的优先）。
     *
     * <p>单独一套排序键的原因：{@link #DRAW_ORDER} 里不透明模型的距离键是 0，
     * 直接"排序后取前 N 个"会按入队顺序砍掉不透明模型，而不是砍掉远处那些
     * 根本看不清的模型——那样既没省到开销又丢了近处的画面。
     */
    private static final Comparator<QueuedRequest> NEAREST_FIRST = Comparator
            .comparingDouble((QueuedRequest queued) -> queued.request.snapshot().cameraDistanceSquared())
            .thenComparingLong(QueuedRequest::sequence);

    private final List<QueuedRequest> requests = new ArrayList<>();
    private long nextSequence;

    public void enqueue(MmdDrawRequest request) {
        enqueue(request, null);
    }

    public void enqueue(MmdDrawRequest request, AutoCloseable frameLease) {
        requests.add(new QueuedRequest(
                Objects.requireNonNull(request, "request"), nextSequence++, frameLease));
    }

    @Override
    public int size() {
        return requests.size();
    }

    public void clear() {
        requests.forEach(QueuedRequest::closeLease);
        requests.clear();
        nextSequence = 0L;
    }

    public void flush(MmdDrawDevice device) {
        Objects.requireNonNull(device, "device");
        if (requests.isEmpty()) {
            return;
        }

        List<QueuedRequest> queued = new ArrayList<>(requests);
        List<MmdDrawRequest> ordered = selectVisible(queued).stream()
                .map(QueuedRequest::request)
                .toList();
        requests.clear();
        nextSequence = 0L;
        try {
            device.draw(ordered);
        } finally {
            queued.forEach(QueuedRequest::closeLease);
        }
    }

    /**
     * 每帧可见模型数上限（{@code maxVisibleModelsPerFrame}）：只提交最近的 N 个。
     *
     * <p>选取按距离升序（留下最近的），画的时候透明体仍按原有优先级处理
     * （不透明先画、透明由远到近）——截断不能把半透明模型挪到不透明模型前面，
     * 否则混合顺序就错了。
     */
    private static List<QueuedRequest> selectVisible(List<QueuedRequest> queued) {
        int limit = ConfigManager.getMaxVisibleModelsPerFrame();
        List<QueuedRequest> selected = queued;
        if (limit > 0 && queued.size() > limit) {
            List<QueuedRequest> nearest = new ArrayList<>(queued);
            nearest.sort(NEAREST_FIRST);
            selected = new ArrayList<>(nearest.subList(0, limit));
        }
        selected.sort(DRAW_ORDER);
        return selected;
    }

    private record QueuedRequest(MmdDrawRequest request, long sequence, AutoCloseable frameLease) {
        private void closeLease() {
            if (frameLease == null) {
                return;
            }
            try {
                frameLease.close();
            } catch (Exception exception) {
                throw new IllegalStateException("failed to release frame model lease", exception);
            }
        }
    }
}
