/* 文件职责：验证公开 MmdSkinApi 的 native 查询防御语义。 */
package com.shiroha.mmdskin.api;

import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelQueryPort;
import com.shiroha.mmdskin.config.AbstractMmdSkinConfig;
import com.shiroha.mmdskin.config.ConfigData;
import com.shiroha.mmdskin.config.ConfigManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MmdSkinApiTest {
    @AfterEach
    void tearDown() {
        MmdSkinApi.configureRuntimeCollaborators(null, null);
        ConfigManager.init(null);
    }

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
                return "[\"Head\",\"Neck\",\"Extra\"]";
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
        assertEquals("Head", info.getBoneNames().get(0));
        assertEquals("Neck", info.getBoneNames().get(1));
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
                throw new AssertionError("should not copy UV data when vertex count is unsafe");
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

    @Test
    void shouldResetRuntimeCollaboratorsToNoop() {
        NativeModelPort modelPort = new NativeModelPort() {
            @Override
            public boolean setLayerBoneMask(long modelHandle, int layer, String rootBoneName) {
                return false;
            }

            @Override
            public boolean setLayerBoneExclude(long modelHandle, int layer, String rootBoneName) {
                return false;
            }

            @Override
            public long getModelMemoryUsage(long modelHandle) {
                return 0L;
            }

            @Override
            public void setFirstPersonMode(long modelHandle, boolean enabled) {
            }

            @Override
            public void getEyeBonePosition(long modelHandle, float[] output) {
            }

            @Override
            public void applyVrTrackingInput(long modelHandle, float[] trackingData) {
            }

            @Override
            public void setVrEnabled(long modelHandle, boolean enabled) {
            }

            @Override
            public void setVrIkParams(long modelHandle, float armIkStrength) {
            }

            @Override
            public int getMaterialCount(long modelHandle) {
                return 0;
            }

            @Override
            public void setMaterialVisible(long modelHandle, int materialIndex, boolean visible) {
            }

            @Override
            public void setAllMaterialsVisible(long modelHandle, boolean visible) {
            }

            @Override
            public void deleteModel(long modelHandle) {
            }
        };
        NativeModelQueryPort queryPort = new NativeModelQueryPort() {
            @Override
            public int getMaterialCount(long modelHandle) {
                return 1;
            }

            @Override
            public int getBoneCount(long modelHandle) {
                return 1;
            }

            @Override
            public long getVertexCount(long modelHandle) {
                return 1L;
            }

            @Override
            public long getIndexCount(long modelHandle) {
                return 0L;
            }

            @Override
            public String getBoneNames(long modelHandle) {
                return "[\"Head\"]";
            }

            @Override
            public int copyBonePositionsToBuffer(long modelHandle, ByteBuffer targetBuffer) {
                return 0;
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
            public String getMaterialName(long modelHandle, int materialIndex) {
                return "";
            }

            @Override
            public boolean isMaterialVisible(long modelHandle, int materialIndex) {
                return false;
            }
        };

        MmdSkinApi.configureRuntimeCollaborators(modelPort, queryPort);
        MmdSkinApi.configureRuntimeCollaborators(null, null);

        try {
            Field modelPortField = MmdSkinApi.class.getDeclaredField("modelPort");
            Field modelQueryPortField = MmdSkinApi.class.getDeclaredField("modelQueryPort");
            modelPortField.setAccessible(true);
            modelQueryPortField.setAccessible(true);

            assertNotSame(modelPort, modelPortField.get(null));
            assertSame(NativeModelQueryPort.noop(), modelQueryPortField.get(null));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void shouldExposeConfiguredMobReplacementQueries() {
        ConfigData data = new ConfigData();
        data.mobModelReplacements.put("minecraft:zombie", "mmd/zombie");
        ConfigManager.init(new AbstractMmdSkinConfig(data) {
        });

        assertEquals("mmd/zombie", MmdSkinApi.getConfiguredMobModelReplacement("minecraft:zombie"));
    }
}
