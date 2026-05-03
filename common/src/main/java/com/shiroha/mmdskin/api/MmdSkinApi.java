package com.shiroha.mmdskin.api;

import com.shiroha.mmdskin.bridge.runtime.NativeModelQueryPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 文件职责：提供外部模组访问当前玩家模型信息的公共 API。 */
public final class MmdSkinApi {
    private static final int FLOATS_PER_BONE_POSITION = 3;
    private static final int FLOATS_PER_UV = 2;
    private static final Logger logger = LogManager.getLogger();
    private static final NativeModelPort NOOP_MODEL_PORT = new NativeModelPort() {
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

    private static volatile NativeModelQueryPort modelQueryPort = NativeModelQueryPort.noop();
    private static volatile NativeModelPort modelPort = NOOP_MODEL_PORT;

    private MmdSkinApi() {}

    public static void configureRuntimeCollaborators(NativeModelPort modelPort,
                                                     NativeModelQueryPort modelQueryPort) {
        MmdSkinApi.modelPort = modelPort != null ? modelPort : NOOP_MODEL_PORT;
        MmdSkinApi.modelQueryPort = modelQueryPort != null ? modelQueryPort : NativeModelQueryPort.noop();
    }

    public static ModelInfo getModelInfo(Player player) {
        long handle = resolveModelHandle(player);
        return readModelInfo(handle, modelQueryPort);
    }

    static ModelInfo readModelInfo(long handle, NativeModelQueryPort queryPort) {
        if (handle == 0 || queryPort == null) return null;
        try {
            int boneCount = sanitizeCount("bone", queryPort.getBoneCount(handle), handle);
            int vertexCount = sanitizeCount("vertex", queryPort.getVertexCount(handle), handle);
            int materialCount = sanitizeCount("material", queryPort.getMaterialCount(handle), handle);

            List<String> boneNames = clampBoneNames(parseBoneNames(queryPort.getBoneNames(handle)), boneCount, handle);

            float[] bonePositions = readBonePositions(handle, boneCount, queryPort);

            return new ModelInfo(boneCount, vertexCount, materialCount, boneNames,
                    bonePositions);
        } catch (Exception e) {
            logger.error("getModelInfo 异常，modelHandle={}", handle, e);
            return null;
        }
    }

    public static float[] getUV(Player player) {
        long handle = resolveModelHandle(player);
        return readRealtimeUvs(handle, modelQueryPort);
    }

    static float[] readRealtimeUvs(long handle, NativeModelQueryPort queryPort) {
        if (handle == 0 || queryPort == null) return null;
        try {
            int vertexCount = sanitizeCount("vertex", queryPort.getVertexCount(handle), handle);
            if (vertexCount <= 0) return null;

            ByteBuffer buf = allocateFloatBuffer(vertexCount, FLOATS_PER_UV, handle, "uv");
            if (buf == null) {
                return null;
            }
            int copied = clampCopiedCount("uv", vertexCount, queryPort.copyRealtimeUvsToBuffer(handle, buf), handle);
            if (copied <= 0) return null;

            return extractFloats(buf, copied * FLOATS_PER_UV, handle, "uv");
        } catch (Exception e) {
            logger.error("getUV 异常，modelHandle={}", handle, e);
            return null;
        }
    }

    private static float[] readBonePositions(long handle, int boneCount, NativeModelQueryPort queryPort) {
        if (boneCount <= 0) {
            return new float[0];
        }

        ByteBuffer buf = allocateFloatBuffer(boneCount, FLOATS_PER_BONE_POSITION, handle, "bone positions");
        if (buf == null) {
            return new float[0];
        }

        int copied = clampCopiedCount("bone positions", boneCount, queryPort.copyBonePositionsToBuffer(handle, buf), handle);
        if (copied <= 0) {
            return new float[0];
        }
        float[] floats = extractFloats(buf, copied * FLOATS_PER_BONE_POSITION, handle, "bone positions");
        return floats != null ? floats : new float[0];
    }

    private static long resolveModelHandle(Player player) {
        if (player == null) return 0;
        try {
            PlayerModelResolver.Result result = PlayerModelResolver.resolve(player);
            if (result == null || result.model() == null || result.model().modelInstance() == null) {
                return 0;
            }
            return result.model().modelInstance().getModelHandle();
        } catch (Exception e) {
            logger.debug("resolveModelHandle 异常", e);
            return 0;
        }
    }

    static List<String> parseBoneNames(String json) {
        if (json == null || json.length() <= 2) {
            return Collections.emptyList();
        }
        if (json.charAt(0) != '[' || json.charAt(json.length() - 1) != ']') {
            logger.debug("Ignored malformed bone-name payload: {}", json);
            return Collections.emptyList();
        }

        String content = json.substring(1, json.length() - 1);
        if (content.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> names = new ArrayList<>();

        boolean inQuote = false;
        boolean escaped = false;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (c == ',' && !inQuote) {
                names.add(sb.toString());
                sb.setLength(0);
                continue;
            }
            if (inQuote) {
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            names.add(sb.toString());
        }
        return Collections.unmodifiableList(names);
    }

    private static List<String> clampBoneNames(List<String> boneNames, int boneCount, long handle) {
        if (boneCount <= 0 || boneNames.isEmpty()) {
            return Collections.emptyList();
        }
        if (boneNames.size() <= boneCount) {
            return boneNames;
        }
        logger.warn("Native bone-name count exceeded reported bone count, modelHandle={}, reported={}, names={}",
                handle,
                boneCount,
                boneNames.size());
        return List.copyOf(boneNames.subList(0, boneCount));
    }

    private static int sanitizeCount(String label, long count, long handle) {
        if (count <= 0L) {
            return 0;
        }
        if (count > Integer.MAX_VALUE) {
            logger.warn("Native {} count exceeded int range, modelHandle={}, count={}", label, handle, count);
            return 0;
        }
        return (int) count;
    }

    private static int clampCopiedCount(String label, int requestedCount, int copiedCount, long handle) {
        if (copiedCount <= 0) {
            return 0;
        }
        if (copiedCount > requestedCount) {
            logger.warn("Native {} copy exceeded requested count, modelHandle={}, requested={}, copied={}",
                    label,
                    handle,
                    requestedCount,
                    copiedCount);
            return requestedCount;
        }
        return copiedCount;
    }

    private static ByteBuffer allocateFloatBuffer(int itemCount, int floatsPerItem, long handle, String label) {
        long floatCount = (long) itemCount * floatsPerItem;
        long byteCount = floatCount * Float.BYTES;
        if (itemCount <= 0 || floatCount <= 0L || byteCount <= 0L || byteCount > Integer.MAX_VALUE) {
            logger.warn("Skipped {} buffer allocation because native count was unsafe, modelHandle={}, itemCount={}",
                    label,
                    handle,
                    itemCount);
            return null;
        }
        return ByteBuffer.allocateDirect((int) byteCount).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static float[] extractFloats(ByteBuffer buffer, int floatCount, long handle, String label) {
        if (buffer == null || floatCount <= 0) {
            return null;
        }
        int availableFloatCount = buffer.capacity() / Float.BYTES;
        if (floatCount > availableFloatCount) {
            logger.warn("Native {} payload exceeded allocated buffer, modelHandle={}, requestedFloats={}, availableFloats={}",
                    label,
                    handle,
                    floatCount,
                    availableFloatCount);
            floatCount = availableFloatCount;
        }
        float[] values = new float[floatCount];
        buffer.rewind();
        buffer.asFloatBuffer().get(values);
        return values;
    }
}
