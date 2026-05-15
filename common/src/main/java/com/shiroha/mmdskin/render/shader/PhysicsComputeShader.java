package com.shiroha.mmdskin.render.shader;

import com.shiroha.mmdskin.util.AssetsUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL46C;

import java.nio.ByteBuffer;

/**
 * GPU 物理 Compute Shader（Verlet 弹簧 + 碰撞检测）。
 */
public class PhysicsComputeShader {
    private static final Logger logger = LogManager.getLogger();

    private static final int LOCAL_SIZE_X = 64;

    private int program = 0;
    private boolean initialized = false;

    private int bindingCountLocation = -1;
    private int colliderCountLocation = -1;
    private int deltaTimeLocation = -1;
    private int gravityYLocation = -1;
    private int dampingFactorLocation = -1;
    private int springFactorLocation = -1;

    private static final int BINDING_PRE_PHYSICS_BONES = 0;
    private static final int BINDING_RIGID_BODIES = 1;
    private static final int BINDING_JOINTS = 2;
    private static final int BINDING_BINDINGS = 3;
    private static final int BINDING_STATE_IN = 4;
    private static final int BINDING_STATE_OUT = 5;
    private static final int BINDING_BONE_MATRICES = 6;
    private static final int BINDING_COLLIDERS = 7;

    private static final String COMPUTE_SHADER_SOURCE =
            AssetsUtil.getAssetsAsString("shader/compute_physics.comp.glsl");

    public boolean init() {
        if (initialized) return true;

        try {
            program = ShaderCompiler.compileComputeProgram(COMPUTE_SHADER_SOURCE, "物理 Compute Shader");
            if (program == 0) return false;

            bindingCountLocation = GL43C.glGetUniformLocation(program, "BindingCount");
            colliderCountLocation = GL43C.glGetUniformLocation(program, "ColliderCount");
            deltaTimeLocation = GL43C.glGetUniformLocation(program, "DeltaTime");
            gravityYLocation = GL43C.glGetUniformLocation(program, "GravityY");
            dampingFactorLocation = GL43C.glGetUniformLocation(program, "DampingFactor");
            springFactorLocation = GL43C.glGetUniformLocation(program, "SpringFactor");

            initialized = true;
            return true;

        } catch (Exception e) {
            logger.error("物理 Compute Shader 初始化异常", e);
            return false;
        }
    }

    public void dispatch(int boneMatrixSSBO, int bindingsSSBO, int collidersSSBO,
                         int stateInSSBO, int stateOutSSBO,
                         int bindingCount, int colliderCount,
                         float deltaTime, float gravityY,
                         float dampingFactor, float springFactor) {
        if (!initialized || program == 0 || bindingCount <= 0) return;

        int savedProgram = GL46C.glGetInteger(GL46C.GL_CURRENT_PROGRAM);
        var savedSSBO = new SSBOBindings();

        GL43C.glUseProgram(program);

        if (bindingCountLocation >= 0) GL43C.glUniform1i(bindingCountLocation, bindingCount);
        if (colliderCountLocation >= 0) GL43C.glUniform1i(colliderCountLocation, colliderCount);
        if (deltaTimeLocation >= 0) GL43C.glUniform1f(deltaTimeLocation, deltaTime);
        if (gravityYLocation >= 0) GL43C.glUniform1f(gravityYLocation, gravityY);
        if (dampingFactorLocation >= 0) GL43C.glUniform1f(dampingFactorLocation, dampingFactor);
        if (springFactorLocation >= 0) GL43C.glUniform1f(springFactorLocation, springFactor);

        // binding 0: PrePhysicsBones（骨骼矩阵作为物理前输入）
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_PRE_PHYSICS_BONES, boneMatrixSSBO);
        // binding 3: Bindings
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_BINDINGS, bindingsSSBO);
        // binding 4: StateIn
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_STATE_IN, stateInSSBO);
        // binding 5: StateOut
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_STATE_OUT, stateOutSSBO);
        // binding 6: BoneMatrices（读写，物理结果直接覆写）
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_BONE_MATRICES, boneMatrixSSBO);
        // binding 7: Colliders
        if (collidersSSBO > 0) {
            GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, BINDING_COLLIDERS, collidersSSBO);
        }

        int groupCount = (bindingCount + LOCAL_SIZE_X - 1) / LOCAL_SIZE_X;
        GL43C.glDispatchCompute(groupCount, 1, 1);

        GL43C.glMemoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT);

        savedSSBO.restore();
        GL43C.glUseProgram(savedProgram);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void cleanup() {
        if (program > 0) {
            GL43C.glDeleteProgram(program);
            program = 0;
        }
        initialized = false;
    }

    /** 创建物理状态 SSBO（ping-pong 双缓冲） */
    public static int[] createStateBuffers(int bindingCount) {
        // StateData: vec4 position + vec4 velocity + vec4 prevPosition + vec4 pad + mat4 rotation = 128 bytes
        long bufferSize = (long) bindingCount * 128;
        int[] buffers = new int[2];
        for (int i = 0; i < 2; i++) {
            buffers[i] = GL46C.glGenBuffers();
            GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, buffers[i]);
            GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, bufferSize, GL46C.GL_DYNAMIC_COPY);
            // 零初始化
            ByteBuffer zeros = ByteBuffer.allocateDirect((int) bufferSize);
            GL46C.glBufferSubData(GL46C.GL_COPY_WRITE_BUFFER, 0, zeros);
        }
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
        return buffers;
    }

    /** 创建 Bindings SSBO */
    public static int createBindingsBuffer(ByteBuffer data) {
        int ssbo = GL46C.glGenBuffers();
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, ssbo);
        GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, data, GL46C.GL_STATIC_DRAW);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
        return ssbo;
    }

    /** 创建 Colliders SSBO */
    public static int createCollidersBuffer(ByteBuffer data) {
        int ssbo = GL46C.glGenBuffers();
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, ssbo);
        GL46C.glBufferData(GL46C.GL_COPY_WRITE_BUFFER, data, GL46C.GL_STATIC_DRAW);
        GL46C.glBindBuffer(GL46C.GL_COPY_WRITE_BUFFER, 0);
        return ssbo;
    }
}
