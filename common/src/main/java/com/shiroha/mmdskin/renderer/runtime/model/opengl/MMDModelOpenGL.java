package com.shiroha.mmdskin.renderer.runtime.model.opengl;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.pipeline.shader.ShaderProvider;
import com.shiroha.mmdskin.renderer.pipeline.shader.ToonShaderCpu;
import com.shiroha.mmdskin.renderer.pipeline.shader.ToonConfig;
import com.shiroha.mmdskin.renderer.runtime.model.AbstractMMDModel;
import com.shiroha.mmdskin.renderer.runtime.model.shared.MMDMaterial;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * MMD模型OpenGL渲染实现。
 */
public class MMDModelOpenGL extends AbstractMMDModel {
    static boolean isShaderInited = false;
    static int MMDShaderProgram;
    public static boolean isMMDShaderEnabled = false;
    static ToonShaderCpu toonShaderCpu;
    static final ToonConfig toonConfig = ToonConfig.getInstance();
    int shaderProgram;

    int positionLocation;
    int normalLocation;
    int uv0Location, uv1Location, uv2Location;
    int colorLocation;
    int projMatLocation;
    int modelViewLocation;
    int sampler0Location, sampler1Location, sampler2Location;
    int light0Location, light1Location;

    int K_positionLocation;
    int K_normalLocation;
    int K_uv0Location, K_uv2Location;
    int K_projMatLocation;
    int K_modelViewLocation;
    int K_sampler0Location, K_sampler2Location;
    int KAIMyLocationV;
    int KAIMyLocationF;

    int I_positionLocation;
    int I_normalLocation;
    int I_uv0Location, I_uv2Location;
    int I_colorLocation;

    int vertexCount;
    ByteBuffer posBuffer, colorBuffer, norBuffer, uv0Buffer, uv1Buffer, uv2Buffer;
    int vertexArrayObject;
    int indexBufferObject;
    int vertexBufferObject;
    int colorBufferObject;
    int normalBufferObject;
    int texcoordBufferObject;
    int uv1BufferObject;
    int uv2BufferObject;
    int indexElementSize;
    int indexType;
    MMDMaterial[] mats;
    MMDMaterial lightMapMaterial;
    final Vector3f light0Direction = new Vector3f();
    final Vector3f light1Direction = new Vector3f();

    FloatBuffer modelViewMatBuff;
    FloatBuffer projMatBuff;
    FloatBuffer light0Buff;
    FloatBuffer light1Buff;

    int cachedShaderProgram = -1;

    boolean hasUvMorph = false;

    int subMeshCount;
    ByteBuffer subMeshDataBuf;

    MMDModelOpenGL() {
    }

    public static void InitShader() {

        ShaderProvider.Init();
        if (ShaderProvider.isReady()) {
            MMDShaderProgram = ShaderProvider.getProgram();
        } else {
            logger.warn("MMD Shader 初始化失败，已自动禁用自定义着色器");
            MMDShaderProgram = 0;
            isMMDShaderEnabled = false;
        }
        isShaderInited = true;
    }

    public static MMDModelOpenGL Create(String modelFilename, String modelDir, boolean isPMD, long layerCount) {
        return MMDModelOpenGLFactory.create(modelFilename, modelDir, isPMD, layerCount);
    }

    public static MMDModelOpenGL createFromHandle(long model, String modelDir) {
        return MMDModelOpenGLFactory.createFromHandle(model, modelDir);
    }

    @Override
    public void dispose() {
        MMDModelOpenGLLifecycle.dispose(this);
    }

    @Override
    public long getVramUsage() {
        return MMDModelOpenGLLifecycle.getVramUsage(this);
    }

    @Override
    public long getRamUsage() {
        return MMDModelOpenGLLifecycle.getRamUsage(this);
    }

    @Override
    protected void onUpdate(float deltaTime) {
        getNf().UpdateModel(model, deltaTime);
    }

    @Override
    protected void doRenderModel(Entity entityIn, float entityYaw, float entityPitch, Vector3f entityTrans, PoseStack deliverStack, int packedLight) {
        MMDModelOpenGLRenderer.render(this, entityIn, entityYaw, entityPitch, entityTrans, deliverStack, packedLight);
    }

    void updateLocation(int shaderProgram){
        if (shaderProgram == cachedShaderProgram) return;
        cachedShaderProgram = shaderProgram;
        positionLocation = GlStateManager._glGetAttribLocation(shaderProgram, "Position");
        normalLocation = GlStateManager._glGetAttribLocation(shaderProgram, "Normal");
        uv0Location = GlStateManager._glGetAttribLocation(shaderProgram, "UV0");
        uv1Location = GlStateManager._glGetAttribLocation(shaderProgram, "UV1");
        uv2Location = GlStateManager._glGetAttribLocation(shaderProgram, "UV2");
        colorLocation = GlStateManager._glGetAttribLocation(shaderProgram, "Color");
        projMatLocation = GlStateManager._glGetUniformLocation(shaderProgram, "ProjMat");
        modelViewLocation = GlStateManager._glGetUniformLocation(shaderProgram, "ModelViewMat");
        sampler0Location = GlStateManager._glGetUniformLocation(shaderProgram, "Sampler0");
        sampler1Location = GlStateManager._glGetUniformLocation(shaderProgram, "Sampler1");
        sampler2Location = GlStateManager._glGetUniformLocation(shaderProgram, "Sampler2");
        light0Location = GlStateManager._glGetUniformLocation(shaderProgram, "Light0_Direction");
        light1Location = GlStateManager._glGetUniformLocation(shaderProgram, "Light1_Direction");

        K_positionLocation = GlStateManager._glGetAttribLocation(shaderProgram, "K_Position");
        K_normalLocation = GlStateManager._glGetAttribLocation(shaderProgram, "K_Normal");
        K_uv0Location = GlStateManager._glGetAttribLocation(shaderProgram, "K_UV0");
        K_uv2Location = GlStateManager._glGetAttribLocation(shaderProgram, "K_UV2");
        K_projMatLocation = GlStateManager._glGetUniformLocation(shaderProgram, "K_ProjMat");
        K_modelViewLocation = GlStateManager._glGetUniformLocation(shaderProgram, "K_ModelViewMat");
        K_sampler0Location = GlStateManager._glGetUniformLocation(shaderProgram, "K_Sampler0");
        K_sampler2Location = GlStateManager._glGetUniformLocation(shaderProgram, "K_Sampler2");
        KAIMyLocationV = GlStateManager._glGetUniformLocation(shaderProgram, "MMDShaderV");
        KAIMyLocationF = GlStateManager._glGetUniformLocation(shaderProgram, "MMDShaderF");

        I_positionLocation = GlStateManager._glGetAttribLocation(shaderProgram, "iris_Position");
        I_normalLocation = GlStateManager._glGetAttribLocation(shaderProgram, "iris_Normal");
        I_uv0Location = GlStateManager._glGetAttribLocation(shaderProgram, "iris_UV0");
        I_uv2Location = GlStateManager._glGetAttribLocation(shaderProgram, "iris_UV2");
        I_colorLocation = GlStateManager._glGetAttribLocation(shaderProgram, "iris_Color");
    }

    public void setUniforms(ShaderInstance shader, PoseStack deliverStack) {
        setupShaderUniforms(shader, deliverStack, light0Direction, light1Direction, lightMapMaterial.tex);
    }

    NativeFunc nativeFunc() {
        return getNf();
    }

    long nativeModelHandle() {
        return model;
    }

    Quaternionf workingQuaternion() {
        return tempQuat;
    }

    float modelScaleValue() {
        return getModelScale();
    }

    void loadMaterialMorphResults() {
        fetchMaterialMorphResults();
    }

    float effectiveMaterialAlpha(int materialIndex, float baseAlpha) {
        return getEffectiveMaterialAlpha(materialIndex, baseAlpha);
    }

    void releaseBaseResources() {
        releaseTextures();
        disposeModelHandle();
        disposeMaterialMorphBuffers();
    }

    int materialMorphResultCountValue() {
        return materialMorphResultCount;
    }

    void applyBaseState(long modelHandle, String modelDirectory, List<String> loadedTextureKeys) {
        this.model = modelHandle;
        this.modelDir = modelDirectory;
        this.textureKeys = loadedTextureKeys;
    }

    void applyMaterialMorphState(int resultCount, ByteBuffer resultBuffer) {
        this.materialMorphResultCount = resultCount;
        this.materialMorphResultsByteBuffer = resultBuffer;
    }
}
