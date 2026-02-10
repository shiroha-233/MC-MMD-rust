package com.shiroha.mmdskin.renderer.model;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import com.shiroha.mmdskin.renderer.core.EyeTrackingHelper;
import com.shiroha.mmdskin.renderer.camera.MMDCameraController;
import com.shiroha.mmdskin.renderer.core.RenderContext;
import com.shiroha.mmdskin.renderer.resource.MMDTextureManager;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 使用 Minecraft 原生渲染系统的 MMD 模型渲染器
 *
 * 关键点：
 * 1. 蒙皮计算在 Rust 引擎完成（高性能）
 * 2. P2-9: Rust 直接输出 MC NEW_ENTITY 顶点格式（含矩阵变换），消除 Java 逐顶点循环
 * 3. 使用自定义 VAO/VBO + MC ShaderInstance，Iris 可正确拦截
 */
public class MMDModelNativeRender implements IMMDModel {
    private static final Logger logger = LogManager.getLogger();
    static NativeFunc nf;
    
    // 模型数据
    long model;
    String modelDir;
    private String cachedModelName;
    int vertexCount;

    // P2-9: 自定义 VAO/VBO（替代 BufferBuilder 逐顶点循环）
    private int vao;
    private int[] subMeshVBOs;
    private int subMeshCount;

    // P2-9: Rust 顶点构建缓冲区（预分配，每帧复用）
    private ByteBuffer mcVertexBuf;    // 输出：MC NEW_ENTITY 交错顶点数据
    private ByteBuffer poseMatBuf;     // 4×4 pose 矩阵（64 字节）
    private ByteBuffer normalMatBuf;   // 3×3 normal 矩阵（36 字节）

    // 材质
    Material[] mats;

    // 材质 Morph 结果
    private FloatBuffer materialMorphResultsBuffer;
    private ByteBuffer materialMorphResultsByteBuffer;
    private int materialMorphResultCount = 0;

    // 预分配临时对象（避免每帧分配）
    private final Quaternionf tempQuat = new Quaternionf();
    
    // 时间追踪
    private long lastUpdateTime = -1;
    private static final float MAX_DELTA_TIME = 0.25f; // 最大 250ms（4FPS），防止暂停后跳跃
    
    MMDModelNativeRender() {}

    private void fetchMaterialMorphResults() {
        if (materialMorphResultCount <= 0 || materialMorphResultsBuffer == null) return;
        materialMorphResultsByteBuffer.clear();
        nf.CopyMaterialMorphResultsToBuffer(model, materialMorphResultsByteBuffer);
        materialMorphResultsBuffer.clear();
        materialMorphResultsByteBuffer.position(0);
        materialMorphResultsBuffer.put(materialMorphResultsByteBuffer.asFloatBuffer());
        materialMorphResultsBuffer.flip();
    }

    /**
     * 计算材质经 Morph 变形后的有效 alpha
     * 布局：每材质 56 float = mul(28) + add(28)，diffuse.w 在各组偏移 3
     * 计算：effective = baseAlpha * mul + add
     */
    private float getEffectiveMaterialAlpha(int materialIndex, float baseAlpha) {
        if (materialMorphResultsBuffer == null || materialIndex >= materialMorphResultCount) return baseAlpha;
        int mulOffset = materialIndex * 56 + 3;
        int addOffset = materialIndex * 56 + 28 + 3;
        float mulAlpha = (mulOffset < materialMorphResultsBuffer.capacity()) ? materialMorphResultsBuffer.get(mulOffset) : 1.0f;
        float addAlpha = (addOffset < materialMorphResultsBuffer.capacity()) ? materialMorphResultsBuffer.get(addOffset) : 0.0f;
        return baseAlpha * mulAlpha + addAlpha;
    }

    public static void Init(NativeFunc nativeFunc) {
        nf = nativeFunc;
    }
    
    /**
     * 从 PMX 文件加载模型（使用 Minecraft 原生渲染）
     */
    public static MMDModelNativeRender LoadModel(String pmxPath, String modelDirectory, long layerCount) {
        try {
            long model = nf.LoadModelPMX(pmxPath, modelDirectory, layerCount);
            if (model == 0) {
                logger.error("加载模型失败: {}", pmxPath);
                return null;
            }
            MMDModelNativeRender result = createFromHandle(model, modelDirectory);
            if (result == null) {
                nf.DeleteModel(model);
            }
            return result;
        } catch (Exception e) {
            logger.error("加载模型异常", e);
            return null;
        }
    }

    /**
     * 从已加载的模型句柄创建渲染实例（Phase 2：GL 资源创建，必须在渲染线程调用）
     * Phase 1（nf.LoadModelPMX）已在后台线程完成
     */
    public static MMDModelNativeRender createFromHandle(long model, String modelDirectory) {
        if (nf == null) nf = NativeFunc.GetInst();

        // 资源追踪变量（用于异常时清理）
        FloatBuffer matMorphResultsBuf = null;
        ByteBuffer matMorphResultsByteBuf = null;
        int vao = 0;
        int[] vbos = null;
        ByteBuffer mcVertexBuf = null, poseMatBuf = null, normalMatBuf = null;

        try {
            int vertexCount = (int) nf.GetVertexCount(model);

            // 加载材质
            int matCount = (int) nf.GetMaterialCount(model);
            Material[] mats = new Material[matCount];
            for (int i = 0; i < matCount; i++) {
                mats[i] = new Material();
                String texPath = nf.GetMaterialTex(model, i);
                if (texPath != null && !texPath.isEmpty()) {
                    MMDTextureManager.Texture tex = MMDTextureManager.GetTexture(texPath);
                    if (tex != null) {
                        mats[i].tex = tex.tex;
                        mats[i].hasAlpha = tex.hasAlpha;
                    }
                }
            }

            // P2-9: 创建自定义 VAO + 每子网格 VBO（替代 MC VertexBuffer + BufferBuilder）
            BufferUploader.reset();
            int subMeshCount = (int) nf.GetSubMeshCount(model);
            vao = GL46C.glGenVertexArrays();
            vbos = new int[subMeshCount];
            int maxVertCount = 0;
            for (int i = 0; i < subMeshCount; i++) {
                vbos[i] = GL46C.glGenBuffers();
                int vc = nf.GetSubMeshVertexCount(model, i);
                maxVertCount = Math.max(maxVertCount, vc);
                // 预分配 VBO（后续使用 glBufferSubData 更新）
                GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, vbos[i]);
                GL46C.glBufferData(GL46C.GL_ARRAY_BUFFER, (long) vc * 36, GL46C.GL_DYNAMIC_DRAW);
            }
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, 0);

            // 预分配 Rust 顶点构建缓冲区
            mcVertexBuf = MemoryUtil.memAlloc(maxVertCount * 36);
            poseMatBuf = MemoryUtil.memAlloc(64);   // 4×4 float 矩阵
            normalMatBuf = MemoryUtil.memAlloc(36);  // 3×3 float 矩阵

            // 组装结果
            MMDModelNativeRender result = new MMDModelNativeRender();
            result.model = model;
            result.modelDir = modelDirectory;
            result.vertexCount = vertexCount;
            result.mats = mats;
            result.vao = vao;
            result.subMeshVBOs = vbos;
            result.subMeshCount = subMeshCount;
            result.mcVertexBuf = mcVertexBuf;
            result.poseMatBuf = poseMatBuf;
            result.normalMatBuf = normalMatBuf;

            // 初始化材质 Morph 结果缓冲区
            int matMorphCount = nf.GetMaterialMorphResultCount(model);
            if (matMorphCount > 0) {
                int floatCount = matMorphCount * 56;
                result.materialMorphResultCount = matMorphCount;
                matMorphResultsBuf = MemoryUtil.memAllocFloat(floatCount);
                matMorphResultsByteBuf = MemoryUtil.memAlloc(floatCount * 4);
                matMorphResultsByteBuf.order(ByteOrder.LITTLE_ENDIAN);
                result.materialMorphResultsBuffer = matMorphResultsBuf;
                result.materialMorphResultsByteBuffer = matMorphResultsByteBuf;
            }

            // 启用自动眨眼
            nf.SetAutoBlinkEnabled(model, true);

            logger.info("原生渲染模型加载成功 (P2-9): 顶点={}, 子网格={}, 最大子网格顶点={}", vertexCount, subMeshCount, maxVertCount);
            return result;

        } catch (Exception e) {
            logger.error("原生渲染模型创建失败，清理资源", e);

            if (matMorphResultsBuf != null) MemoryUtil.memFree(matMorphResultsBuf);
            if (matMorphResultsByteBuf != null) MemoryUtil.memFree(matMorphResultsByteBuf);
            if (mcVertexBuf != null) MemoryUtil.memFree(mcVertexBuf);
            if (poseMatBuf != null) MemoryUtil.memFree(poseMatBuf);
            if (normalMatBuf != null) MemoryUtil.memFree(normalMatBuf);
            if (vao != 0) GL46C.glDeleteVertexArrays(vao);
            if (vbos != null) {
                for (int vbo : vbos) {
                    if (vbo != 0) GL46C.glDeleteBuffers(vbo);
                }
            }

            return null;
        }
    }
    
    @Override
    public void dispose() {
        if (mcVertexBuf != null) { MemoryUtil.memFree(mcVertexBuf); mcVertexBuf = null; }
        if (poseMatBuf != null) { MemoryUtil.memFree(poseMatBuf); poseMatBuf = null; }
        if (normalMatBuf != null) { MemoryUtil.memFree(normalMatBuf); normalMatBuf = null; }
        if (materialMorphResultsBuffer != null) {
            MemoryUtil.memFree(materialMorphResultsBuffer);
            materialMorphResultsBuffer = null;
        }
        if (materialMorphResultsByteBuffer != null) {
            MemoryUtil.memFree(materialMorphResultsByteBuffer);
            materialMorphResultsByteBuffer = null;
        }
        if (subMeshVBOs != null) {
            for (int vbo : subMeshVBOs) {
                if (vbo != 0) GL46C.glDeleteBuffers(vbo);
            }
            subMeshVBOs = null;
        }
        if (vao != 0) { GL46C.glDeleteVertexArrays(vao); vao = 0; }
        if (model != 0) {
            nf.DeleteModel(model);
            model = 0;
        }
    }
    
    @Override
    public void render(Entity entityIn, float entityYaw, float entityPitch, Vector3f entityTrans, float tickDelta, PoseStack poseStack, int packedLight, RenderContext context) {
        if (entityIn instanceof LivingEntity && tickDelta != 1.0f) {
            renderLivingEntity((LivingEntity) entityIn, entityYaw, entityPitch, entityTrans, tickDelta, poseStack, packedLight, context);
            return;
        }
        Update();
        RenderModel(entityIn, entityYaw, entityPitch, entityTrans, poseStack, packedLight, context);
    }
    
    private void renderLivingEntity(LivingEntity entityIn, float entityYaw, float entityPitch, Vector3f entityTrans, float tickDelta, PoseStack poseStack, int packedLight, RenderContext context) {
        // 头部角度处理（舞台播放时归零，由 VMD 动画控制）
        boolean stagePlaying = MMDCameraController.getInstance().isStagePlayingModel(model);
        if (stagePlaying) {
            nf.SetHeadAngle(model, 0.0f, 0.0f, 0.0f, context.isWorldScene());
        } else {
            float headAngleX = Mth.clamp(entityIn.getXRot(), -50.0f, 50.0f);
            float headAngleY = (entityYaw - Mth.lerp(tickDelta, entityIn.yHeadRotO, entityIn.yHeadRot)) % 360.0f;
            if (headAngleY < -180.0f) headAngleY += 360.0f;
            else if (headAngleY > 180.0f) headAngleY -= 360.0f;
            headAngleY = Mth.clamp(headAngleY, -80.0f, 80.0f);

            float pitchRad = headAngleX * ((float) Math.PI / 180F);
            float yawRad = context.isInventoryScene() ? -headAngleY * ((float) Math.PI / 180F) : headAngleY * ((float) Math.PI / 180F);
            nf.SetHeadAngle(model, pitchRad, yawRad, 0.0f, context.isWorldScene());
        }

        // 传递实体位置和朝向给物理系统（用于人物移动时的惯性效果）
        final float MODEL_SCALE = 0.09f;
        float posX = (float)(Mth.lerp(tickDelta, entityIn.xo, entityIn.getX()) * MODEL_SCALE);
        float posY = (float)(Mth.lerp(tickDelta, entityIn.yo, entityIn.getY()) * MODEL_SCALE);
        float posZ = (float)(Mth.lerp(tickDelta, entityIn.zo, entityIn.getZ()) * MODEL_SCALE);
        float bodyYaw = Mth.lerp(tickDelta, entityIn.yBodyRotO, entityIn.yBodyRot) * ((float) Math.PI / 180F);
        nf.SetModelPositionAndYaw(model, posX, posY, posZ, bodyYaw);

        if (!stagePlaying) {
            EyeTrackingHelper.updateEyeTracking(nf, model, entityIn, entityYaw, tickDelta, getModelName());
        }
        
        Update();
        RenderModel(entityIn, entityYaw, entityPitch, entityTrans, poseStack, packedLight, context);
    }
    
    private void Update() {
        long currentTime = System.currentTimeMillis();
        if (lastUpdateTime < 0) {
            lastUpdateTime = currentTime;
            return;
        }
        
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;
        
        // 限制 deltaTime 上限，防止暂停后物理爆炸
        // 注意：不设下限，避免高帧率下动画加速
        if (deltaTime > MAX_DELTA_TIME) {
            deltaTime = MAX_DELTA_TIME;
        }
        
        // Rust 引擎更新动画和蒙皮
        nf.UpdateModel(model, deltaTime);
    }
    
    private void RenderModel(Entity entityIn, float entityYaw, float entityPitch, Vector3f entityTrans, PoseStack poseStack, int packedLight, RenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        
        // 变换矩阵
        poseStack.pushPose();
        poseStack.mulPose(tempQuat.identity().rotateY(-entityYaw * ((float) Math.PI / 180F)));
        poseStack.mulPose(tempQuat.identity().rotateX(entityPitch * ((float) Math.PI / 180F)));
        poseStack.translate(entityTrans.x, entityTrans.y, entityTrans.z);
        float baseScale = 0.09f * com.shiroha.mmdskin.config.ModelConfigManager.getConfig(getModelName()).modelScale;
        poseStack.scale(baseScale, baseScale, baseScale);
        
        // MC 1.21.1 相机修复：重建模型视图矩阵
        // pushPose 内修改会在 popPose 时恢复，不影响原始 PoseStack
        if (context.isWorldScene()) {
            boolean isShadowPass = false;
            try {
                net.minecraft.client.renderer.ShaderInstance shader = RenderSystem.getShader();
                if (shader != null && shader.getName().contains("shadow")) {
                    isShadowPass = true;
                }
            } catch (Exception ignored) {}
            
            if (!isShadowPass) {
                Matrix4f fullMatrix = new Matrix4f(poseStack.last().pose());
                // 重置 pose 和 normal 矩阵为单位矩阵
                poseStack.last().pose().identity();
                poseStack.last().normal().identity();
                // 应用相机旋转（mulPose 会同时更新 pose 和 normal）
                poseStack.mulPose(new Quaternionf().rotateX(
                    mc.gameRenderer.getMainCamera().getXRot() * ((float)Math.PI / 180F)));
                poseStack.mulPose(new Quaternionf().rotateY(
                    mc.gameRenderer.getMainCamera().getYRot() * ((float)Math.PI / 180F)));
                poseStack.mulPose(new Quaternionf().rotateY((float)Math.PI));
                // 应用原始完整矩阵（PoseStack.mulPose(Matrix4fc) 会处理法线矩阵）
                poseStack.mulPose(fullMatrix);
            }
        }
        
        // 获取材质 Morph 结果
        fetchMaterialMorphResults();

        // P2-9: 将 pose/normal 矩阵写入 ByteBuffer，供 Rust 侧矩阵变换使用
        poseMatBuf.clear();
        poseStack.last().pose().get(poseMatBuf);
        normalMatBuf.clear();
        poseStack.last().normal().get(normalMatBuf);

        // 启用混合和深度测试
        BufferUploader.reset();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();

        // 按子网格渲染
        for (int i = 0; i < subMeshCount; i++) {
            int materialID = nf.GetSubMeshMaterialID(model, i);

            if (!nf.IsMaterialVisible(model, materialID)) continue;
            float alpha = nf.GetMaterialAlpha(model, materialID);
            if (getEffectiveMaterialAlpha(materialID, alpha) < 0.001f) continue;

            renderSubMesh(poseStack, packedLight, materialID, i, mc);
        }

        poseStack.popPose();
    }
    
    /**
     * P2-9: 使用 Rust 构建的 MC 顶点数据 + 自定义 VAO/VBO 渲染子网格
     *
     * 替代旧的 BufferBuilder 逐顶点循环：
     * - Rust 侧完成 SoA→AoS 转换 + 矩阵变换 + 格式打包（1 次 JNI 调用）
     * - Java 侧仅上传 + 绑定着色器 + 绘制
     */
    private void renderSubMesh(PoseStack poseStack, int packedLight, int materialID, int subMeshIndex, Minecraft mc) {
        // 设置纹理
        if (mats[materialID].tex != 0) {
            RenderSystem.setShaderTexture(0, mats[materialID].tex);
        } else {
            RenderSystem.setShaderTexture(0, mc.getTextureManager().getTexture(TextureManager.INTENTIONAL_MISSING_TEXTURE).getId());
        }

        // 使用 Minecraft 原生着色器（Iris 会正确拦截）
        RenderSystem.setShader(GameRenderer::getRendertypeEntityCutoutNoCullShader);

        // 双面渲染
        boolean bothFace = nf.GetMaterialBothFace(model, materialID);
        if (bothFace) {
            RenderSystem.disableCull();
        } else {
            RenderSystem.enableCull();
        }

        // P2-9 核心：Rust 直接构建 MC NEW_ENTITY 格式的交错顶点数据
        int overlayPacked = 0 | (10 << 16); // OverlayTexture.pack(0, 10)
        mcVertexBuf.clear();
        int written = nf.BuildMCVertexBuffer(
            model, subMeshIndex, mcVertexBuf,
            poseMatBuf, normalMatBuf,
            0xFFFFFFFF, overlayPacked, packedLight
        );
        if (written <= 0) return;
        mcVertexBuf.position(0).limit(written * 36);

        // 上传到 VBO
        GL46C.glBindVertexArray(vao);
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, subMeshVBOs[subMeshIndex]);
        GL46C.glBufferSubData(GL46C.GL_ARRAY_BUFFER, 0, mcVertexBuf);

        // 设置 NEW_ENTITY 格式的顶点属性指针（stride = 36 bytes）
        DefaultVertexFormat.NEW_ENTITY.setupBufferState();

        // 获取着色器并设置 uniform
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            DefaultVertexFormat.NEW_ENTITY.clearBufferState();
            GL46C.glBindVertexArray(0);
            return;
        }

        // 设置 uniform（复制自 VertexBuffer.drawWithShader 的逻辑）
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        for (int i = 0; i < 12; ++i) {
            int j = RenderSystem.getShaderTexture(i);
            shader.setSampler("Sampler" + i, j);
        }
        if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(modelView);
        if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(projection);
        // MC 1.21.1: INVERSE_VIEW_ROTATION_MATRIX 已移除
        if (shader.COLOR_MODULATOR != null) shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        if (shader.FOG_START != null) shader.FOG_START.set(RenderSystem.getShaderFogStart());
        if (shader.FOG_END != null) shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        if (shader.FOG_COLOR != null) shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        if (shader.FOG_SHAPE != null) shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        if (shader.TEXTURE_MATRIX != null) shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        if (shader.GAME_TIME != null) shader.GAME_TIME.set(RenderSystem.getShaderGameTime());

        shader.apply();

        // 绘制（不使用索引，顶点已由 Rust 展开）
        GL46C.glDrawArrays(GL46C.GL_TRIANGLES, 0, written);

        // 清理
        shader.clear();
        DefaultVertexFormat.NEW_ENTITY.clearBufferState();
        GL46C.glBindVertexArray(0);
    }
    
    @Override
    public void ChangeAnim(long anim, long layer) {
        if (model != 0) {
            nf.ChangeModelAnim(model, anim, layer);
        }
    }
    
    @Override
    public void TransitionAnim(long anim, long layer, float transitionTime) {
        if (model != 0) {
            nf.TransitionLayerTo(model, layer, anim, transitionTime);
        }
    }
    
    @Override
    public void ResetPhysics() {
        if (model != 0) {
            nf.ResetModelPhysics(model);
        }
    }
    
    @Override
    public long GetModelLong() {
        return model;
    }
    
    @Override
    public String GetModelDir() {
        return modelDir;
    }
    
    @Override
    public String getModelName() {
        if (cachedModelName == null) {
            cachedModelName = IMMDModel.super.getModelName();
        }
        return cachedModelName;
    }
    
    // 内部材质类
    static class Material {
        int tex = 0;
        boolean hasAlpha = false;
    }
}
