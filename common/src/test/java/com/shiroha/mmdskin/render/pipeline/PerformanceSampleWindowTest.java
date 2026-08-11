package com.shiroha.mmdskin.render.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PerformanceSampleWindowTest {

    @Test
    void emptyWindowShouldReturnSafeSnapshot() {
        PerformanceSampleWindow window = new PerformanceSampleWindow(4);

        PerformanceSampleWindow.Snapshot snapshot = window.snapshot();

        assertEquals(0, snapshot.count());
        assertEquals(0L, snapshot.currentNanos());
        assertEquals(0L, snapshot.averageNanos());
        assertEquals(0L, snapshot.p95Nanos());
        assertEquals(0L, snapshot.p99Nanos());
    }

    @Test
    void fullWindowShouldKeepOnlyMostRecentSamples() {
        PerformanceSampleWindow window = new PerformanceSampleWindow(3);
        window.record(10L);
        window.record(20L);
        window.record(30L);
        window.record(40L);

        PerformanceSampleWindow.Snapshot snapshot = window.snapshot();

        assertEquals(3, snapshot.count());
        assertEquals(40L, snapshot.currentNanos());
        assertEquals(30L, snapshot.averageNanos());
        assertEquals(40L, snapshot.p95Nanos());
        assertEquals(40L, snapshot.p99Nanos());
    }

    @Test
    void percentileShouldUseNearestRankAndSingleSampleShouldBeStable() {
        PerformanceSampleWindow window = new PerformanceSampleWindow(100);
        for (int i = 1; i <= 100; i++) {
            window.record(i);
        }

        PerformanceSampleWindow.Snapshot snapshot = window.snapshot();

        assertEquals(50L, snapshot.averageNanos());
        assertEquals(95L, snapshot.p95Nanos());
        assertEquals(99L, snapshot.p99Nanos());

        window.clear();
        window.record(123L);
        PerformanceSampleWindow.Snapshot single = window.snapshot();
        assertEquals(123L, single.currentNanos());
        assertEquals(123L, single.averageNanos());
        assertEquals(123L, single.p95Nanos());
        assertEquals(123L, single.p99Nanos());
    }

    @Test
    void snapshotsShouldBeIndependentImmutableValues() {
        PerformanceSampleWindow window = new PerformanceSampleWindow(2);
        window.record(10L);
        PerformanceSampleWindow.Snapshot first = window.snapshot();
        window.record(20L);

        PerformanceSampleWindow.Snapshot second = window.snapshot();

        assertNotSame(first, second);
        assertEquals(1, first.count());
        assertEquals(10L, first.currentNanos());
        assertEquals(2, second.count());
        assertEquals(20L, second.currentNanos());
    }

    @Test
    void capacityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new PerformanceSampleWindow(0));
        assertThrows(IllegalArgumentException.class, () -> new PerformanceSampleWindow(-1));
    }
}
