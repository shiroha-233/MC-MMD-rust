package com.shiroha.mmdskin.renderer.shader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL46C;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * GPU Morph Compute Shader
 * 使用 Compute Shader 在 GPU 上并行计算顶点 Morph 变形
 * 
 * 原理：
 * - Morph 偏移数据以密集格式存储在 SSBO 中
 * - Morph 权重每帧从 Rust 引擎同步
 * - Compute Shader 并行计算每个顶点的 Morph 偏移累加
 * - 结果叠加到原始顶点位置上
 */
public class GpuMorphShader {
    private static final Logger logger = LogManager.getLogger();
    
    private int computeProgram = 0;
    private int morphOffsetsSSBO = 0;    // Morph 偏移数据（静态）
    private int morphWeightsSSBO = 0;    // Morph 权重（每帧更新）
    private int positionsSSBO = 0;       // 输入/输出顶点位置
    private boolean initialized = false;
    
    // 最大支持的 Morph 数量
    public static final int MAX_MORPHS = 64;
    
    private int vertexCount = 0;
    private int morphCount = 0;
    
    // Compute Shader 源码
    private static final String COMPUTE_SHADER = """
        #version 460 core
        
        layout(local_size_x = 256) in;
        
        // Morph 偏移数据：morphCount * vertexCount * 3 (xyz)
        layout(std430, binding = 0) readonly buffer MorphOffsets {
            float morphOffsets[];
        };
        
        // Morph 权重
        layout(std430, binding = 1) readonly buffer MorphWeights {
            float morphWeights[];
        };
        
        // 顶点位置（输入原始位置，输出变形后位置）
        layout(std430, binding = 2) buffer Positions {
            float positions[];
        };
        
        // 原始顶点位置（只读）
        layout(std430, binding = 3) readonly buffer OriginalPositions {
            float originalPositions[];
        };
        
        uniform int vertexCount;
        uniform int morphCount;
        
        void main() {
            uint vid = gl_GlobalInvocationID.x;
            if (vid >= vertexCount) return;
            
            // 从原始位置开始
            uint posIdx = vid * 3;
            float px = originalPositions[posIdx];
            float py = originalPositions[posIdx + 1];
            float pz = originalPositions[posIdx + 2];
            
            // 累加所有 Morph 的偏移
            for (int m = 0; m < morphCount; m++) {
                float weight = morphWeights[m];
                if (weight > 0.001) {
                    uint offsetIdx = m * vertexCount * 3 + vid * 3;
                    px += morphOffsets[offsetIdx] * weight;
                    py += morphOffsets[offsetIdx + 1] * weight;
                    pz += morphOffsets[offsetIdx + 2] * weight;
                }
            }
            
            // 写回结果
            positions[posIdx] = px;
            positions[posIdx + 1] = py;
            positions[posIdx + 2] = pz;
        }
        """;
    
    /**
     * 初始化 Compute Shader
     */
    public boolean init() {
        if (initialized) return true;
        
        try {
            // 编译 Compute Shader
            int shader = GL46C.glCreateShader(GL46C.GL_COMPUTE_SHADER);
            GL46C.glShaderSource(shader, COMPUTE_SHADER);
            GL46C.glCompileShader(shader);
            
            if (GL46C.glGetShaderi(shader, GL46C.GL_COMPILE_STATUS) == GL46C.GL_FALSE) {
                String log = GL46C.glGetShaderInfoLog(shader, 8192).trim();
                logger.error("GPU Morph Compute Shader 编译失败: {}", log);
                GL46C.glDeleteShader(shader);
                return false;
            }
            
            // 链接程序
            computeProgram = GL46C.glCreateProgram();
            GL46C.glAttachShader(computeProgram, shader);
            GL46C.glLinkProgram(computeProgram);
            
            if (GL46C.glGetProgrami(computeProgram, GL46C.GL_LINK_STATUS) == GL46C.GL_FALSE) {
                String log = GL46C.glGetProgramInfoLog(computeProgram, 8192);
                logger.error("GPU Morph Compute Shader 链接失败: {}", log);
                GL46C.glDeleteProgram(computeProgram);
                GL46C.glDeleteShader(shader);
                computeProgram = 0;
                return false;
            }
            
            GL46C.glDeleteShader(shader);
            
            // 创建 SSBO（使用 GL_COPY_WRITE_BUFFER 作为中性 target，避免修改 generic SSBO 绑定点导致 AMD 驱动与 Iris 光影冲突）
            morphOffsetsSSBO = GL46C.glGenBuffers();
            morphWeightsSSBO = GL46C.glGenBuffers();
            positionsSSBO = GL46C.glGenBuffers();
            
            initialized = true;
            logger.info("GPU Morph Compute Shader 初始化成功");
            return true;
            
        } catch (Exception e) {
            logger.error("GPU Morph Compute Shader 初始化异常", e);
            return false;
        }
    }
    
    /**
     * 上传 Morph 偏移数据到 GPU（模型加载时调用一次）
     * @param offsetData 偏移数据（morphCount * vertexCount * 3 个 float）
     * @param vertexCount 顶点数量
     * @param morphCount Morph 数量
     */
    public void uploadMorphOffsets(ByteBuffer offsetData, int vertexCount, int morphCount) {
        if (!initialized) return;
        
        this.vertexCount = vertexCount;
        this.morphCount = morphCount;
        
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, morphOffsetsSSBO);
        GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, offsetData, GL46C.GL_STATIC_DRAW);
        
        // 预分配权重缓冲区
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, morphWeightsSSBO);
        GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, morphCount * 4L, GL46C.GL_DYNAMIC_DRAW);
        
        logger.debug("GPU Morph 数据上传: {} 顶点, {} Morph, 数据大小 {:.2f} MB",
            vertexCount, morphCount, (offsetData.remaining()) / 1024.0 / 1024.0);
    }
    
    /**
     * 更新 Morph 权重（每帧调用）
     * @param weights 权重数据
     */
    public void updateMorphWeights(FloatBuffer weights) {
        if (!initialized || morphWeightsSSBO == 0) return;
        
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, morphWeightsSSBO);
        weights.position(0);
        GL46C.glBufferSubData(GL46C.GL_COPY_WRITE_BUFFER, 0, weights);
    }
    
    /**
     * 执行 Morph 计算
     * @param originalPositionsSSBO 原始顶点位置 SSBO
     * @param outputPositionsSSBO 输出顶点位置 SSBO
     */
    public void dispatch(int originalPositionsSSBO, int outputPositionsSSBO) {
        if (!initialized || computeProgram == 0 || vertexCount == 0 || morphCount == 0) {
            return;
        }
        
        int savedProgram = GL46C.glGetInteger(GL46C.GL_CURRENT_PROGRAM);
        // 保存当前所有 SSBO 绑定状态（避免破坏光影 mod 的 SSBO）
        var savedSSBO = new SSBOBindings();
        
        GL46C.glUseProgram(computeProgram);
        
        // 设置 uniform
        int vertexCountLoc = GL46C.glGetUniformLocation(computeProgram, "vertexCount");
        int morphCountLoc = GL46C.glGetUniformLocation(computeProgram, "morphCount");
        GL46C.glUniform1i(vertexCountLoc, vertexCount);
        GL46C.glUniform1i(morphCountLoc, morphCount);
        
        // 绑定 SSBO
        GL46C.glBindBufferBase(GL46C.GL_SHADER_STORAGE_BUFFER, 0, morphOffsetsSSBO);
        GL46C.glBindBufferBase(GL46C.GL_SHADER_STORAGE_BUFFER, 1, morphWeightsSSBO);
        GL46C.glBindBufferBase(GL46C.GL_SHADER_STORAGE_BUFFER, 2, outputPositionsSSBO);
        GL46C.glBindBufferBase(GL46C.GL_SHADER_STORAGE_BUFFER, 3, originalPositionsSSBO);
        
        // 计算工作组数量（每组 256 个线程）
        int workGroupCount = (vertexCount + 255) / 256;
        GL46C.glDispatchCompute(workGroupCount, 1, 1);
        
        // 内存屏障：输出顶点后续作为顶点属性用于 draw call，需要 VERTEX_ATTRIB_ARRAY_BARRIER_BIT
        GL46C.glMemoryBarrier(GL46C.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT | GL46C.GL_SHADER_STORAGE_BARRIER_BIT);
        
        // 恢复之前的 SSBO 绑定状态
        savedSSBO.restore();
        GL46C.glUseProgram(savedProgram);
    }
    
    /**
     * 获取或创建内部位置 SSBO
     */
    public int getPositionsSSBO() {
        return positionsSSBO;
    }
    
    /**
     * 初始化位置 SSBO（用于存储 Morph 后的顶点位置）
     */
    public void initPositionsSSBO(int vertexCount) {
        if (!initialized) return;
        
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, positionsSSBO);
        GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, vertexCount * 12L, GL46C.GL_DYNAMIC_DRAW);
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    public int getVertexCount() {
        return vertexCount;
    }
    
    public int getMorphCount() {
        return morphCount;
    }
    
    public void cleanup() {
        if (computeProgram > 0) {
            GL46C.glDeleteProgram(computeProgram);
            computeProgram = 0;
        }
        if (morphOffsetsSSBO > 0) {
            GL46C.glDeleteBuffers(morphOffsetsSSBO);
            morphOffsetsSSBO = 0;
        }
        if (morphWeightsSSBO > 0) {
            GL46C.glDeleteBuffers(morphWeightsSSBO);
            morphWeightsSSBO = 0;
        }
        if (positionsSSBO > 0) {
            GL46C.glDeleteBuffers(positionsSSBO);
            positionsSSBO = 0;
        }
        initialized = false;
    }
}
