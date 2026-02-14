package com.shiroha.mmdskin.api;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.render.PlayerModelResolver;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MMD Skin 公共 API（外部模组调用入口）
 *
 * 提供只读的模型信息查询接口，线程安全。
 * 所有方法在模型不存在或未加载时返回 null / 空集合，不会抛出异常。
 *
 * <pre>
 * 使用示例：
 *   ModelInfo info = MmdSkinApi.getModelInfo(player);
 *   if (info != null) {
 *       List&lt;String&gt; boneNames = info.getBoneNames();
 *       float[] positions = info.getBonePositions();
 *   }
 *
 *   float[] uvs = MmdSkinApi.getUV(player);
 * </pre>
 */
public final class MmdSkinApi {
    private static final Logger logger = LogManager.getLogger();

    private MmdSkinApi() {}

    // ==================== 模型信息 ====================

    /**
     * 获取玩家当前绑定的 MMD 模型信息
     *
     * @param player 目标玩家
     * @return 模型信息快照，模型未加载时返回 null
     */
    public static ModelInfo getModelInfo(Player player) {
        long handle = resolveModelHandle(player);
        if (handle == 0) return null;

        try {
            NativeFunc nf = NativeFunc.GetInst();
            int boneCount = nf.GetBoneCount(handle);
            int vertexCount = (int) nf.GetVertexCount(handle);
            int materialCount = (int) nf.GetMaterialCount(handle);

            // 骨骼名称
            List<String> boneNames = parseBoneNames(nf.GetBoneNames(handle));

            // 骨骼实时位置
            float[] bonePositions = null;
            if (boneCount > 0) {
                ByteBuffer buf = ByteBuffer.allocateDirect(boneCount * 12);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                int copied = nf.CopyBonePositionsToBuffer(handle, buf);
                if (copied > 0) {
                    bonePositions = new float[copied * 3];
                    buf.rewind();
                    buf.asFloatBuffer().get(bonePositions);
                }
            }

            return new ModelInfo(boneCount, vertexCount, materialCount, boneNames,
                    bonePositions != null ? bonePositions : new float[0]);
        } catch (Exception e) {
            logger.error("getModelInfo 异常", e);
            return null;
        }
    }

    // ==================== UV 数据 ====================

    /**
     * 获取玩家当前模型的实时 UV 坐标（经过 UV Morph 变形后）
     *
     * @param player 目标玩家
     * @return UV 数组 [u0, v0, u1, v1, ...]，模型未加载时返回 null
     */
    public static float[] getUV(Player player) {
        long handle = resolveModelHandle(player);
        if (handle == 0) return null;

        try {
            NativeFunc nf = NativeFunc.GetInst();
            int vertexCount = (int) nf.GetVertexCount(handle);
            if (vertexCount <= 0) return null;

            ByteBuffer buf = ByteBuffer.allocateDirect(vertexCount * 8);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            int copied = nf.CopyRealtimeUVsToBuffer(handle, buf);
            if (copied <= 0) return null;

            float[] uvs = new float[copied * 2];
            buf.rewind();
            buf.asFloatBuffer().get(uvs);
            return uvs;
        } catch (Exception e) {
            logger.error("getUV 异常", e);
            return null;
        }
    }

    // ==================== 内部工具 ====================

    /**
     * 解析玩家当前绑定模型的原生句柄
     * @return 模型句柄，不存在时返回 0
     */
    private static long resolveModelHandle(Player player) {
        if (player == null) return 0;
        try {
            PlayerModelResolver.Result result = PlayerModelResolver.resolve(player);
            if (result == null || result.model() == null || result.model().model == null) {
                return 0;
            }
            return result.model().model.getModelHandle();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 解析 JSON 数组格式的骨骼名称字符串
     */
    private static List<String> parseBoneNames(String json) {
        if (json == null || json.length() <= 2) {
            return Collections.emptyList();
        }
        // 去掉 [ ]
        String content = json.substring(1, json.length() - 1);
        if (content.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> names = new ArrayList<>();
        // 简单解析：按 "," 分割，去掉引号
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
}
