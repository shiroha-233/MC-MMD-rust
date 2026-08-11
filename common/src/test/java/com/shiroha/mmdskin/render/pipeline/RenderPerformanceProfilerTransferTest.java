package com.shiroha.mmdskin.render.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderPerformanceProfilerTransferTest {

    @Test
    void transferSnapshotShouldClassifyAndSumBytes() {
        long[] uploaded = new long[RenderPerformanceProfiler.TransferKind.values().length];
        long[] avoided = new long[RenderPerformanceProfiler.TransferKind.values().length];
        uploaded[RenderPerformanceProfiler.TransferKind.BONE.ordinal()] = 64L;
        uploaded[RenderPerformanceProfiler.TransferKind.GPU_READBACK.ordinal()] = 32L;
        avoided[RenderPerformanceProfiler.TransferKind.CPU_VERTEX.ordinal()] = 128L;
        avoided[RenderPerformanceProfiler.TransferKind.MATERIAL_MORPH.ordinal()] = 16L;

        RenderPerformanceProfiler.TransferSnapshot snapshot =
                new RenderPerformanceProfiler.TransferSnapshot(uploaded, avoided);

        assertEquals(64L, snapshot.totalGpuUploaded());
        assertEquals(32L, snapshot.uploaded(RenderPerformanceProfiler.TransferKind.GPU_READBACK));
        assertEquals(128L, snapshot.totalGpuUploadAvoided());
        assertEquals(16L, snapshot.totalCpuTransferAvoided());
    }

    @Test
    void transferSnapshotShouldNotExposeMutableArrays() {
        long[] uploaded = new long[RenderPerformanceProfiler.TransferKind.values().length];
        long[] avoided = new long[RenderPerformanceProfiler.TransferKind.values().length];
        uploaded[RenderPerformanceProfiler.TransferKind.BONE.ordinal()] = 64L;
        RenderPerformanceProfiler.TransferSnapshot snapshot =
                new RenderPerformanceProfiler.TransferSnapshot(uploaded, avoided);

        uploaded[RenderPerformanceProfiler.TransferKind.BONE.ordinal()] = 1L;
        snapshot.uploadedBytes()[RenderPerformanceProfiler.TransferKind.BONE.ordinal()] = 2L;

        assertEquals(64L, snapshot.uploaded(RenderPerformanceProfiler.TransferKind.BONE));
    }
}
