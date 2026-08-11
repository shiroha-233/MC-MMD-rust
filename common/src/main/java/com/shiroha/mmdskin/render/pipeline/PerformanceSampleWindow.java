package com.shiroha.mmdskin.render.pipeline;

import java.util.Arrays;

/**
 * 文件职责：保存固定数量的性能样本，并在低频读取时生成统计快照。
 *
 * <p>记录样本只修改预分配的数组和几个计数器，适合放在渲染热路径上。
 * 快照生成会复用排序缓冲区，因此不会因为计算分位数而持续扩大内存。</p>
 */
public final class PerformanceSampleWindow {
    private final long[] samples;
    private final long[] sortedSamples;
    private int nextIndex;
    private int count;

    /**
     * 创建固定容量的样本窗口。
     *
     * @param capacity 能够保留的最近样本数量，必须大于零
     */
    public PerformanceSampleWindow(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be greater than zero");
        }
        this.samples = new long[capacity];
        this.sortedSamples = new long[capacity];
    }

    /**
     * 记录一个纳秒样本。窗口已满时覆盖最早的样本。
     *
     * @param sampleNanos 样本值，通常是非负的纳秒耗时
     */
    public synchronized void record(long sampleNanos) {
        samples[nextIndex] = sampleNanos;
        nextIndex = (nextIndex + 1) % samples.length;
        if (count < samples.length) {
            count++;
        }
    }

    /**
     * 清空当前窗口。数组内容无需清零，因为它们受 count 约束且会在后续写入前覆盖。
     */
    public synchronized void clear() {
        nextIndex = 0;
        count = 0;
    }

    /**
     * 生成当前窗口的不可变统计快照。
     * 分位数只在这里排序计算，不会增加 record 热路径的工作量。
     */
    public synchronized Snapshot snapshot() {
        if (count == 0) {
            return Snapshot.empty();
        }

        long average = 0L;
        long remainder = 0L;
        for (int i = 0; i < count; i++) {
            long sample = samples[i];
            sortedSamples[i] = sample;
            // 用商和余数计算平均值，避免大量纳秒样本累加时发生 long 溢出。
            average += sample / count;
            remainder += sample % count;
        }
        average += remainder / count;
        Arrays.sort(sortedSamples, 0, count);

        return new Snapshot(
                samples[(nextIndex + samples.length - 1) % samples.length],
                average,
                percentile(sortedSamples, count, 95),
                percentile(sortedSamples, count, 99),
                count);
    }

    private static long percentile(long[] sortedValues, int size, int percentile) {
        // 使用 nearest-rank 定义，保证 p95/p99 始终返回窗口中真实存在的样本。
        int rank = (percentile * size + 99) / 100;
        return sortedValues[rank - 1];
    }

    /** 返回窗口的固定容量。 */
    public int capacity() {
        return samples.length;
    }

    /** 不可变的性能统计快照。所有时间值单位均为纳秒。 */
    public static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(0L, 0L, 0L, 0L, 0);

        private final long currentNanos;
        private final long averageNanos;
        private final long p95Nanos;
        private final long p99Nanos;
        private final int count;

        private Snapshot(long currentNanos, long averageNanos, long p95Nanos, long p99Nanos, int count) {
            this.currentNanos = currentNanos;
            this.averageNanos = averageNanos;
            this.p95Nanos = p95Nanos;
            this.p99Nanos = p99Nanos;
            this.count = count;
        }

        public static Snapshot empty() {
            return EMPTY;
        }

        public long currentNanos() {
            return currentNanos;
        }

        public long averageNanos() {
            return averageNanos;
        }

        public long p95Nanos() {
            return p95Nanos;
        }

        public long p99Nanos() {
            return p99Nanos;
        }

        public int count() {
            return count;
        }
    }
}
