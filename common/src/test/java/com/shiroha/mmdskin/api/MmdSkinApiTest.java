package com.shiroha.mmdskin.api;

import com.shiroha.mmdskin.bridge.runtime.NativeModelQueryPort;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MmdSkinApiTest {

    @Test
    void shouldClampCopiedBonePayloadToReportedCounts() {
        NativeModelQueryPort queryPort = new NativeModelQueryPort() {
            @Override
            public int getMaterialCount(long modelHandle) {
                return 4;
            }

            @Override
            public int getBoneCount(long modelHandle) {
                return 2;
            }

            @Override
            public long getVertexCount(long modelHandle) {
                return 3L;
            }

            @Override
            public long getIndexCount(long modelHandle) {
                return 0L;
            }

            @Override
            public String getBoneNames(long modelHandle) {
                return "[\"頭\",\"首\",\"余分\"]";
            }

            @Override
            public int copyBonePositionsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
                targetBuffer.asFloatBuffer().put(new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f});
                return 3;
            }

            @Override
            public int copyRealtimeUvsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
                return 0;
            }

            @Override
            public int getVertexMorphCount(long modelHandle) {
                return 0;
            }

            @Override
            public int getUvMorphCount(long modelHandle) {
                return 0;
            }

            @Override
            public long getGpuMorphOffsetsSize(long modelHandle) {
                return 0L;
            }

            @Override
            public long getGpuUvMorphOffsetsSize(long modelHandle) {
                return 0L;
            }

            @Override
            public int getMorphCount(long modelHandle) {
                return 0;
            }

            @Override
            public String getMorphName(long modelHandle, int morphIndex) {
                return "";
            }

            @Override
            public boolean isVrmModel(long modelHandle) {
                return false;
            }

            @Override
            public String getMaterialName(long modelHandle, int materialIndex) {
                return "";
            }

            @Override
            public boolean isMaterialVisible(long modelHandle, int materialIndex) {
                return false;
            }
        };

        ModelInfo info = MmdSkinApi.readModelInfo(42L, queryPort);

        assertEquals(2, info.getBoneCount());
        assertEquals(3, info.getVertexCount());
        assertEquals(4, info.getMaterialCount());
        assertEquals(2, info.getBoneNames().size());
        assertEquals("頭", info.getBoneNames().get(0));
        assertEquals("首", info.getBoneNames().get(1));
        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f}, info.getBonePositions());
    }

    @Test
    void shouldRefuseUnsafeUvAllocationWhenVertexCountExceedsIntRange() {
        NativeModelQueryPort queryPort = new NativeModelQueryPort() {
            @Override
            public int getMaterialCount(long modelHandle) {
                return 0;
            }

            @Override
            public int getBoneCount(long modelHandle) {
                return 0;
            }

            @Override
            public long getVertexCount(long modelHandle) {
                return (long) Integer.MAX_VALUE + 1L;
            }

            @Override
            public long getIndexCount(long modelHandle) {
                return 0L;
            }

            @Override
            public String getBoneNames(long modelHandle) {
                return "[]";
            }

            @Override
            public int copyBonePositionsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
                return 0;
            }

            @Override
            public int copyRealtimeUvsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
                throw new AssertionError("should not request UV copy when vertex count is unsafe");
            }

            @Override
            public int getVertexMorphCount(long modelHandle) {
                return 0;
            }

            @Override
            public int getUvMorphCount(long modelHandle) {
                return 0;
            }

            @Override
            public long getGpuMorphOffsetsSize(long modelHandle) {
                return 0L;
            }

            @Override
            public long getGpuUvMorphOffsetsSize(long modelHandle) {
                return 0L;
            }

            @Override
            public int getMorphCount(long modelHandle) {
                return 0;
            }

            @Override
            public String getMorphName(long modelHandle, int morphIndex) {
                return "";
            }

            @Override
            public boolean isVrmModel(long modelHandle) {
                return false;
            }

            @Override
            public String getMaterialName(long modelHandle, int materialIndex) {
                return "";
            }

            @Override
            public boolean isMaterialVisible(long modelHandle, int materialIndex) {
                return false;
            }
        };

        assertNull(MmdSkinApi.readRealtimeUvs(42L, queryPort));
    }

    @Test
    void shouldIgnoreMalformedBoneNamePayloads() {
        assertEquals(0, MmdSkinApi.parseBoneNames("not-json").size());
        assertEquals(2, MmdSkinApi.parseBoneNames("[\"Head\",\"Neck\"]").size());
    }
}
