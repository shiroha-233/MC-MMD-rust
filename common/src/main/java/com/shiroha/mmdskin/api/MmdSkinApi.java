/* 文件职责：提供外部模组访问当前玩家模型与替换配置的公共 API。 */
package com.shiroha.mmdskin.api;

import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.bridge.runtime.NativeModelQueryPort;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.renderer.integration.entity.MobReplacementService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MmdSkinApi {
    private static final int FLOATS_PER_BONE_POSITION = 3;
    private static final int FLOATS_PER_UV = 2;
    private static final Logger LOGGER = LogManager.getLogger();
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
    private static volatile NativeModelPort modelPort = NOOP_MODEL_PORT;
    private static volatile NativeModelQueryPort modelQueryPort = NativeModelQueryPort.noop();

    private MmdSkinApi() {
    }

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
        if (handle == 0 || queryPort == null) {
            return null;
        }
        try {
            int boneCount = sanitizeCount("bone", queryPort.getBoneCount(handle), handle);
            int vertexCount = sanitizeCount("vertex", queryPort.getVertexCount(handle), handle);
            int materialCount = sanitizeCount("material", queryPort.getMaterialCount(handle), handle);
            List<String> boneNames = clampBoneNames(parseBoneNames(queryPort.getBoneNames(handle)), boneCount, handle);
            float[] bonePositions = readBonePositions(handle, boneCount, queryPort);
            return new ModelInfo(boneCount, vertexCount, materialCount, boneNames, bonePositions);
        } catch (Exception e) {
            LOGGER.error("getModelInfo 异常，modelHandle={}", handle, e);
            return null;
        }
    }

    public static float[] getUV(Player player) {
        long handle = resolveModelHandle(player);
        return readRealtimeUvs(handle, modelQueryPort);
    }

    static float[] readRealtimeUvs(long handle, NativeModelQueryPort queryPort) {
        if (handle == 0 || queryPort == null) {
            return null;
        }
        try {
            int vertexCount = sanitizeCount("vertex", queryPort.getVertexCount(handle), handle);
            if (vertexCount <= 0) {
                return null;
            }
            ByteBuffer buffer = allocateFloatBuffer(handle, vertexCount, FLOATS_PER_UV, "uv");
            if (buffer == null) {
                return null;
            }
            int copied = clampCopiedCount("uv", vertexCount, queryPort.copyRealtimeUvsToBuffer(handle, buffer), handle);
            if (copied <= 0) {
                return null;
            }
            return extractFloats(buffer, copied * FLOATS_PER_UV, handle, "uv");
        } catch (Exception e) {
            LOGGER.error("getUV 异常，modelHandle={}", handle, e);
            return null;
        }
    }

    public static String getMobModelReplacement(LivingEntity entity) {
        return MobReplacementService.getReplacementModelName(entity);
    }

    public static String getConfiguredMobModelReplacement(String entityTypeId) {
        return ConfigManager.getMobModelReplacement(entityTypeId);
    }

    public static String getConfiguredMobModelReplacement(net.minecraft.world.entity.EntityType<?> entityType) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return id == null ? "" : ConfigManager.getMobModelReplacement(id.toString());
    }

    private static long resolveModelHandle(Player player) {
        if (player == null) return 0;
        try {
            PlayerModelResolver.Result result = PlayerModelResolver.resolve(player);
            if (result == null || result.model() == null || result.model().model == null) {
                return 0;
            }
            return result.model().model.getModelHandle();
        } catch (Exception e) {
            LOGGER.debug("resolveModelHandle 异常", e);
            return 0;
        }
    }

    private static float[] readBonePositions(long handle, int boneCount, NativeModelQueryPort queryPort) {
        if (boneCount <= 0) {
            return new float[0];
        }
        ByteBuffer buffer = allocateFloatBuffer(handle, boneCount, FLOATS_PER_BONE_POSITION, "bone positions");
        if (buffer == null) {
            return new float[0];
        }
        int copied = clampCopiedCount("bone positions", boneCount, queryPort.copyBonePositionsToBuffer(handle, buffer), handle);
        if (copied <= 0) {
            return new float[0];
        }
        float[] floats = extractFloats(buffer, copied * FLOATS_PER_BONE_POSITION, handle, "bone positions");
        return floats != null ? floats : new float[0];
    }

    static List<String> parseBoneNames(String json) {
        if (json == null || json.length() <= 2) {
            return Collections.emptyList();
        }
        if (json.charAt(0) != '[' || json.charAt(json.length() - 1) != ']') {
            LOGGER.debug("Ignored malformed bone-name payload: {}", json);
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
        LOGGER.warn("Native bone-name count exceeded reported bone count, modelHandle={}, reported={}, names={}",
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
            LOGGER.warn("Native {} count exceeded int range, modelHandle={}, count={}", label, handle, count);
            return 0;
        }
        return (int) count;
    }

    private static int clampCopiedCount(String label, int requestedCount, int copiedCount, long handle) {
        if (copiedCount <= 0) {
            return 0;
        }
        if (copiedCount > requestedCount) {
            LOGGER.warn("Native {} copy exceeded requested count, modelHandle={}, requested={}, copied={}",
                    label,
                    handle,
                    requestedCount,
                    copiedCount);
            return requestedCount;
        }
        return copiedCount;
    }

    private static ByteBuffer allocateFloatBuffer(long handle, int itemCount, int floatsPerItem, String label) {
        long floatCount = (long) itemCount * floatsPerItem;
        long byteCount = floatCount * Float.BYTES;
        if (itemCount <= 0 || floatCount <= 0L || byteCount <= 0L || byteCount > Integer.MAX_VALUE) {
            LOGGER.warn("Skipped {} buffer allocation because native count was unsafe, modelHandle={}, itemCount={}",
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
            LOGGER.warn("Native {} payload exceeded allocated buffer, modelHandle={}, requestedFloats={}, availableFloats={}",
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
