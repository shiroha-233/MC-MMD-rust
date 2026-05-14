/* 文件职责：验证 morph 目录缓存通过 query port 读取并支持失效后重载。 */
package com.shiroha.mmdskin.expression;

import com.shiroha.mmdskin.bridge.runtime.NativeModelQueryPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelMorphCatalogTest {

    @AfterEach
    void tearDown() {
        ModelMorphCatalog.configureRuntimeCollaborators(null);
        ModelMorphCatalog.invalidate(42L);
    }

    @Test
    void shouldLoadMorphEntriesViaQueryPortAndCacheUntilInvalidated() {
        ModelMorphCatalog.configureRuntimeCollaborators(new FakeQueryPort(
                Map.of(
                        42L, List.of("morph_smile", "face_angry")
                )
        ));

        ModelMorphCatalog first = ModelMorphCatalog.getOrCreate(42L);
        assertEquals(42L, first.modelHandle());
        assertEquals(0, first.findBest(ExpressionMatchRule.of(List.of("smile"), List.of()), List.of()));
        assertEquals("face_angry", first.getEntry(1).originalName());

        ModelMorphCatalog.configureRuntimeCollaborators(new FakeQueryPort(
                Map.of(
                        42L, List.of("expression_blink")
                )
        ));

        ModelMorphCatalog cached = ModelMorphCatalog.getOrCreate(42L);
        assertEquals(0, cached.findBest(ExpressionMatchRule.of(List.of("smile"), List.of()), List.of()));

        ModelMorphCatalog.invalidate(42L);
        ModelMorphCatalog reloaded = ModelMorphCatalog.getOrCreate(42L);
        assertEquals(0, reloaded.findBest(ExpressionMatchRule.of(List.of("blink"), List.of()), List.of()));
        assertNull(reloaded.getEntry(1));
    }

    private record FakeQueryPort(Map<Long, List<String>> morphsByHandle) implements NativeModelQueryPort {
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
            return 0L;
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
            return morphsByHandle.getOrDefault(modelHandle, List.of()).size();
        }

        @Override
        public String getMorphName(long modelHandle, int morphIndex) {
            return morphsByHandle.getOrDefault(modelHandle, List.of()).get(morphIndex);
        }

        @Override
        public String getMaterialName(long modelHandle, int materialIndex) {
            return "";
        }

        @Override
        public boolean isMaterialVisible(long modelHandle, int materialIndex) {
            return false;
        }
    }
}
