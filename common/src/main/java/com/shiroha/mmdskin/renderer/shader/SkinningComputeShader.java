package com.shiroha.mmdskin.renderer.shader;

import com.shiroha.mmdskin.util.AssetsUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL46C;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static com.shiroha.mmdskin.renderer.shader.ShaderConstants.MAX_BONES;

/**
 * GPU 蒙皮 Compute Shader
 * 
 * 在 GPU 上预计算蒙皮后的顶点位置和法线，输出到缓冲区。
 * 渲染阶段使用 Minecraft 标准 ShaderInstance 管线，确保 Iris 可以正确拦截。
 * 
 * 原理：
 * - 原始顶点数据（位置、法线、骨骼索引/权重）作为 SSBO 输入
 * - 骨骼矩阵和 Morph 数据作为 SSBO 输入
 * - Compute Shader 计算蒙皮后的位置和法线，写入输出 SSBO
 * - 输出缓冲区随后作为 VBO 绑定到标准渲染管线
 * 
 * SSBO 绑定布局：
 * - binding 0: 原始顶点位置（只读）
 * - binding 1: 原始顶点法线（只读）
 * - binding 2: 骨骼索引（只读）
 * - binding 3: 骨骼权重（只读）
 * - binding 4: 骨骼矩阵（只读，每帧更新）
 * - binding 5: Morph 偏移数据（只读，静态）
 * - binding 6: Morph 权重（只读，每帧更新）
 * - binding 7: 蒙皮后顶点位置（写入）
 * - binding 8: 蒙皮后顶点法线（写入）
 */
public class SkinningComputeShader {
    private static final Logger logger = LogManager.getLogger();
    
    private static final int LOCAL_SIZE_X = 256;
    
    // Compute Shader 程序
    private int program = 0;
    private boolean initialized = false;
    
    // Uniform locations
    private int vertexCountLocation = -1;
    private int morphCountLocation = -1;
    private int maxBonesLocation = -1;
    private int uvMorphCountLocation = -1;
    
    // SSBO 绑定点常量
    private static final int BINDING_ORIG_POSITIONS = 0;
    private static final int BINDING_ORIG_NORMALS = 1;
    private static final int BINDING_BONE_INDICES = 2;
    private static final int BINDING_BONE_WEIGHTS = 3;
    private static final int BINDING_BONE_MATRICES = 4;
    private static final int BINDING_MORPH_OFFSETS = 5;
    private static final int BINDING_MORPH_WEIGHTS = 6;
    private static final int BINDING_SKINNED_POSITIONS = 7;
    private static final int BINDING_SKINNED_NORMALS = 8;
    private static final int BINDING_ORIG_UVS = 9;
    private static final int BINDING_UV_MORPH_OFFSETS = 10;
    private static final int BINDING_UV_MORPH_WEIGHTS = 11;
    private static final int BINDING_SKINNED_UVS = 12;
    
    
    private static final String COMPUTE_SHADER_SOURCE =
            AssetsUtil.getAssetsAsString("shader/compute_skinning.comp.glsl");
    
    /**
     * 蒙皮 dispatch 参数对象，替代 16 个散装 int 参数
     */
    public record DispatchParams(
            // 输入缓冲区
            int origPosBuffer, int origNorBuffer,
            int boneIdxBuffer, int boneWgtBuffer, int origUvBuffer,
            // 输出缓冲区
            int outSkinnedPosBuffer, int outSkinnedNorBuffer, int outSkinnedUvBuffer,
            // 骨骼
            int boneMatrixSSBO,
            // 顶点 Morph
            int morphOffsetsSSBO, int morphWeightsSSBO, int morphCount,
            // UV Morph
            int uvMorphOffsetsSSBO, int uvMorphWeightsSSBO, int uvMorphCount,
            // 参数
            int vertexCount
    ) {
        /** 无 UV Morph 的便捷构造 */
        public static DispatchParams withoutUvMorph(
                int origPosBuffer, int origNorBuffer,
                int boneIdxBuffer, int boneWgtBuffer,
                int outSkinnedPosBuffer, int outSkinnedNorBuffer,
                int boneMatrixSSBO,
                int morphOffsetsSSBO, int morphWeightsSSBO,
                int vertexCount, int morphCount) {
            return new DispatchParams(
                    origPosBuffer, origNorBuffer,
                    boneIdxBuffer, boneWgtBuffer, 0,
                    outSkinnedPosBuffer, outSkinnedNorBuffer, 0,
                    boneMatrixSSBO,
                    morphOffsetsSSBO, morphWeightsSSBO, morphCount,
                    0, 0, -1,
                    vertexCount);
        }
    }
    
    /**
     * 初始化 Compute Shader
     */
    public boolean init() {
        if (initialized) return true;
        
        try {
            program = ShaderCompiler.compileComputeProgram(COMPUTE_SHADER_SOURCE, "蒙皮 Compute Shader");
            if (program == 0) return false;
            
            // 获取 Uniform locations
            vertexCountLocation = GL43C.glGetUniformLocation(program, "VertexCount");
            morphCountLocation = GL43C.glGetUniformLocation(program, "MorphCount");
            maxBonesLocation = GL43C.glGetUniformLocation(program, "MaxBones");
            uvMorphCountLocation = GL43C.glGetUniformLocation(program, "UvMorphCount");
            
            initialized = true;
            logger.info("蒙皮 Compute Shader 初始化成功");
            return true;
            
        } catch (Exception e) {
            logger.error("蒙皮 Compute Shader 初始化异常", e);
            return false;
        }
    }
    
    /**
     * 创建一对输出缓冲区（蒙皮后的顶点位置和法线）
     * 返回 int[2]：{skinnedPositionsBuffer, skinnedNormalsBuffer}
     * 调用方负责管理这些缓冲区的生命周期
     */
    public static int[] createOutputBuffers(int vertexCount) {
        long bufferSize = (long) vertexCount * 3 * 4; // vec3 * sizeof(float)
        
        int posBuffer = GL46C.glGenBuffers();
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, posBuffer);
        GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, bufferSize, GL46C.GL_DYNAMIC_COPY);
        
        int norBuffer = GL46C.glGenBuffers();
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, norBuffer);
        GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, bufferSize, GL46C.GL_DYNAMIC_COPY);
        
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
        return new int[]{posBuffer, norBuffer};
    }
    
    /**
     * 执行蒙皮计算
     */
    public void dispatch(DispatchParams p) {
        if (!initialized || program == 0) return;
        
        int savedProgram = GL46C.glGetInteger(GL46C.GL_CURRENT_PROGRAM);
        var savedSSBO = new SSBOBindings();
        
        GL43C.glUseProgram(program);
        
        if (vertexCountLocation >= 0) GL43C.glUniform1i(vertexCountLocation, p.vertexCount());
        if (morphCountLocation >= 0) GL43C.glUniform1i(morphCountLocation, p.morphCount());
        if (maxBonesLocation >= 0) GL43C.glUniform1i(maxBonesLocation, MAX_BONES);
        if (uvMorphCountLocation >= 0) GL43C.glUniform1i(uvMorphCountLocation, p.uvMorphCount());
        
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_ORIG_POSITIONS, p.origPosBuffer());
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_ORIG_NORMALS, p.origNorBuffer());
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_BONE_INDICES, p.boneIdxBuffer());
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_BONE_WEIGHTS, p.boneWgtBuffer());
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_BONE_MATRICES, p.boneMatrixSSBO());
        if (p.morphCount() > 0 && p.morphOffsetsSSBO() != 0) {
            GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_MORPH_OFFSETS, p.morphOffsetsSSBO());
        }
        if (p.morphCount() > 0 && p.morphWeightsSSBO() != 0) {
            GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_MORPH_WEIGHTS, p.morphWeightsSSBO());
        }
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_SKINNED_POSITIONS, p.outSkinnedPosBuffer());
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_SKINNED_NORMALS, p.outSkinnedNorBuffer());
        
        // UV 相关 SSBO
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_ORIG_UVS, p.origUvBuffer());
        if (p.uvMorphCount() > 0 && p.uvMorphOffsetsSSBO() != 0) {
            GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_UV_MORPH_OFFSETS, p.uvMorphOffsetsSSBO());
        }
        if (p.uvMorphCount() > 0 && p.uvMorphWeightsSSBO() != 0) {
            GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_UV_MORPH_WEIGHTS, p.uvMorphWeightsSSBO());
        }
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_SKINNED_UVS, p.outSkinnedUvBuffer());
        
        int groupCount = (p.vertexCount() + LOCAL_SIZE_X - 1) / LOCAL_SIZE_X;
        GL43C.glDispatchCompute(groupCount, 1, 1);
        
        GL43C.glMemoryBarrier(GL43C.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT | GL43C.GL_SHADER_STORAGE_BARRIER_BIT);
        
        savedSSBO.restore();
        GL43C.glUseProgram(savedProgram);
    }
    
    /**
     * 上传骨骼矩阵到指定 SSBO（每实例独立）
     */
    public void uploadBoneMatrices(int boneMatrixSSBO, FloatBuffer matrices, int boneCount) {
        if (!initialized || boneMatrixSSBO == 0) return;
        
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, boneMatrixSSBO);
        int actualBones = Math.min(boneCount, ShaderConstants.MAX_BONES);
        int savedLimit = matrices.limit();
        matrices.limit(actualBones * 16);
        matrices.position(0);
        GL46C.glBufferSubData(GL46C.GL_COPY_WRITE_BUFFER, 0, matrices);
        matrices.limit(savedLimit);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
    }
    
    /**
     * 上传 Morph 偏移数据到指定 SSBO（静态，只需上传一次）
     */
    public void uploadMorphOffsets(int morphOffsetsSSBO, ByteBuffer data) {
        if (!initialized || morphOffsetsSSBO == 0) return;
        
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, morphOffsetsSSBO);
        GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, data, GL46C.GL_STATIC_DRAW);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
    }
    
    /**
     * 更新 Morph 权重到指定 SSBO（每帧调用）
     */
    public void updateMorphWeights(int morphWeightsSSBO, FloatBuffer weights) {
        if (!initialized || morphWeightsSSBO == 0) return;
        
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, morphWeightsSSBO);
        weights.position(0);
        GL46C.glBufferSubData(GL46C.GL_COPY_WRITE_BUFFER, 0, weights);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
    }
    
    /**
     * 创建一对 Morph SSBO（每模型实例独立）
     * 返回 int[2]：{morphOffsetsSSBO, morphWeightsSSBO}
     * 调用方负责管理生命周期
     */
    public static int[] createMorphBuffers(int morphCount) {
        int offsetsSSBO = GL46C.glGenBuffers();
        int weightsSSBO = GL46C.glGenBuffers();
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, weightsSSBO);
        GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, (long) morphCount * 4, GL46C.GL_DYNAMIC_DRAW);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
        return new int[]{offsetsSSBO, weightsSSBO};
    }
    
    /**
     * 创建 UV Morph SSBO（每模型实例独立）
     * 返回 int[2]：{uvMorphOffsetsSSBO, uvMorphWeightsSSBO}
     */
    public static int[] createUvMorphBuffers(int uvMorphCount) {
        int offsetsSSBO = GL46C.glGenBuffers();
        int weightsSSBO = GL46C.glGenBuffers();
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, weightsSSBO);
        GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, (long) uvMorphCount * 4, GL46C.GL_DYNAMIC_DRAW);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
        return new int[]{offsetsSSBO, weightsSSBO};
    }
    
    /**
     * 创建蒙皮后 UV 输出缓冲区
     */
    public static int createSkinnedUvBuffer(int vertexCount) {
        int buffer = GL46C.glGenBuffers();
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, buffer);
        GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, (long) vertexCount * 2 * 4, GL46C.GL_DYNAMIC_COPY);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
        return buffer;
    }
    
    /**
     * 上传 UV Morph 偏移数据到指定 SSBO
     */
    public void uploadUvMorphOffsets(int uvMorphOffsetsSSBO, java.nio.ByteBuffer data) {
        if (!initialized || uvMorphOffsetsSSBO == 0) return;
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, uvMorphOffsetsSSBO);
        GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, data, GL46C.GL_STATIC_DRAW);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
    }
    
    /**
     * 更新 UV Morph 权重到指定 SSBO
     */
    public void updateUvMorphWeights(int uvMorphWeightsSSBO, FloatBuffer weights) {
        if (!initialized || uvMorphWeightsSSBO == 0) return;
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, uvMorphWeightsSSBO);
        weights.position(0);
        GL46C.glBufferSubData(GL46C.GL_COPY_WRITE_BUFFER, 0, weights);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * 创建骨骼矩阵 SSBO（每模型实例独立）
     * 调用方负责管理生命周期
     */
    public static int createBoneMatrixBuffer() {
        int ssbo = GL46C.glGenBuffers();
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, ssbo);
        GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, (long) ShaderConstants.MAX_BONES * 64, GL46C.GL_DYNAMIC_DRAW);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
        return ssbo;
    }
    
    /**
     * 释放资源
     */
    public void cleanup() {
        if (program > 0) {
            GL43C.glDeleteProgram(program);
            program = 0;
        }
        initialized = false;
    }
}
