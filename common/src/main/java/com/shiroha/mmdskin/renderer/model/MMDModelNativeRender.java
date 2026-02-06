package com.shiroha.mmdskin.renderer.model;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import com.shiroha.mmdskin.renderer.core.RenderContext;
import com.shiroha.mmdskin.renderer.resource.MMDTextureManager;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.vertex.MeshData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * 使用 Minecraft 原生渲染系统的 MMD 模型渲染器
 * 
 * 关键点：
 * 1. 蒙皮计算在 Rust 引擎完成（高性能）
 * 2. 渲染使用 Minecraft 的 BufferBuilder + VertexBuffer + ShaderInstance
 * 3. Iris 可以正确拦截 ShaderInstance，实现光影兼容
 */
public class MMDModelNativeRender implements IMMDModel {
    private static final Logger logger = LogManager.getLogger();
    static NativeFunc nf;
    
    // 模型数据
    long model;
    String modelDir;
    int vertexCount;
    
    // 顶点缓冲区（从 Rust 引擎获取蒙皮后的数据）
    ByteBuffer posBuffer;
    ByteBuffer norBuffer;
    ByteBuffer uv0Buffer;
    
    // Minecraft 原生 VertexBuffer（每个子网格一个）
    VertexBuffer[] subMeshVertexBuffers;
    
    // 索引数据
    int[] indices;
    int indexCount;
    int indexElementSize;
    
    // 材质
    Material[] mats;
    
    // 时间追踪
    private long lastUpdateTime = -1;
    private static final float MAX_DELTA_TIME = 0.05f; // 最大 50ms，防止暂停后跳跃
    
    MMDModelNativeRender() {}
    
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
            
            int vertexCount = (int) nf.GetVertexCount(model);
            
            // 分配缓冲区
            int posSize = vertexCount * 12;
            int norSize = vertexCount * 12;
            int uvSize = vertexCount * 8;
            
            ByteBuffer posBuffer = MemoryUtil.memAlloc(posSize);
            ByteBuffer norBuffer = MemoryUtil.memAlloc(norSize);
            ByteBuffer uv0Buffer = MemoryUtil.memAlloc(uvSize);
            
            // 加载索引
            long idxCount = nf.GetIndexCount(model);
            int indexElementSize = (int) nf.GetIndexElementSize(model);
            int[] indices = new int[(int) idxCount];
            
            ByteBuffer idxBuffer = MemoryUtil.memAlloc((int) (idxCount * indexElementSize));
            long idxData = nf.GetIndices(model);
            nf.CopyDataToByteBuffer(idxBuffer, idxData, (int) (idxCount * indexElementSize));
            
            if (indexElementSize == 2) {
                for (int i = 0; i < idxCount; i++) {
                    indices[i] = idxBuffer.getShort(i * 2) & 0xFFFF;
                }
            } else {
                for (int i = 0; i < idxCount; i++) {
                    indices[i] = idxBuffer.getInt(i * 4);
                }
            }
            MemoryUtil.memFree(idxBuffer);
            
            // 加载材质
            int matCount = (int) nf.GetMaterialCount(model);
            Material[] mats = new Material[matCount];
            for (int i = 0; i < matCount; i++) {
                mats[i] = new Material();
                String texPath = nf.GetMaterialTex(model, i);
                if (texPath != null && !texPath.isEmpty()) {
                    String fullPath = modelDirectory + "/" + texPath;
                    MMDTextureManager.Texture tex = MMDTextureManager.GetTexture(fullPath);
                    if (tex != null) {
                        mats[i].tex = tex.tex;
                        mats[i].hasAlpha = tex.hasAlpha;
                    }
                }
            }
            
            // 创建子网格 VertexBuffer
            long subMeshCount = nf.GetSubMeshCount(model);
            VertexBuffer[] subMeshVertexBuffers = new VertexBuffer[(int) subMeshCount];
            for (int i = 0; i < subMeshCount; i++) {
                subMeshVertexBuffers[i] = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            }
            
            // 组装结果
            MMDModelNativeRender result = new MMDModelNativeRender();
            result.model = model;
            result.modelDir = modelDirectory;
            result.vertexCount = vertexCount;
            result.posBuffer = posBuffer;
            result.norBuffer = norBuffer;
            result.uv0Buffer = uv0Buffer;
            result.indices = indices;
            result.indexCount = (int) idxCount;
            result.indexElementSize = indexElementSize;
            result.mats = mats;
            result.subMeshVertexBuffers = subMeshVertexBuffers;
            
            logger.info("原生渲染模型加载成功: 顶点={}, 索引={}, 子网格={}", vertexCount, idxCount, subMeshCount);
            return result;
            
        } catch (Exception e) {
            logger.error("加载模型异常", e);
            return null;
        }
    }
    
    @Override
    public void dispose() {
        if (posBuffer != null) {
            MemoryUtil.memFree(posBuffer);
            posBuffer = null;
        }
        if (norBuffer != null) {
            MemoryUtil.memFree(norBuffer);
            norBuffer = null;
        }
        if (uv0Buffer != null) {
            MemoryUtil.memFree(uv0Buffer);
            uv0Buffer = null;
        }
        if (subMeshVertexBuffers != null) {
            for (VertexBuffer vb : subMeshVertexBuffers) {
                if (vb != null) vb.close();
            }
            subMeshVertexBuffers = null;
        }
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
        RenderModel(entityIn, entityYaw, entityPitch, entityTrans, poseStack, packedLight);
    }
    
    private void renderLivingEntity(LivingEntity entityIn, float entityYaw, float entityPitch, Vector3f entityTrans, float tickDelta, PoseStack poseStack, int packedLight, RenderContext context) {
        // 头部角度处理
        float headAngleX = Mth.clamp(entityIn.getXRot(), -50.0f, 50.0f);
        float headAngleY = (entityYaw - Mth.lerp(tickDelta, entityIn.yHeadRotO, entityIn.yHeadRot)) % 360.0f;
        if (headAngleY < -180.0f) headAngleY += 360.0f;
        else if (headAngleY > 180.0f) headAngleY -= 360.0f;
        headAngleY = Mth.clamp(headAngleY, -80.0f, 80.0f);
        
        float pitchRad = headAngleX * ((float) Math.PI / 180F);
        float yawRad = context.isInventoryScene() ? -headAngleY * ((float) Math.PI / 180F) : headAngleY * ((float) Math.PI / 180F);
        nf.SetHeadAngle(model, pitchRad, yawRad, 0.0f, context.isWorldScene());
        
        // 传递实体位置和朝向给物理系统（用于人物移动时的惯性效果）
        final float MODEL_SCALE = 0.09f;
        float posX = (float)(Mth.lerp(tickDelta, entityIn.xo, entityIn.getX()) * MODEL_SCALE);
        float posY = (float)(Mth.lerp(tickDelta, entityIn.yo, entityIn.getY()) * MODEL_SCALE);
        float posZ = (float)(Mth.lerp(tickDelta, entityIn.zo, entityIn.getZ()) * MODEL_SCALE);
        float bodyYaw = Mth.lerp(tickDelta, entityIn.yBodyRotO, entityIn.yBodyRot) * ((float) Math.PI / 180F);
        nf.SetModelPositionAndYaw(model, posX, posY, posZ, bodyYaw);
        
        Update();
        RenderModel(entityIn, entityYaw, entityPitch, entityTrans, poseStack, packedLight);
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
    
    private void RenderModel(Entity entityIn, float entityYaw, float entityPitch, Vector3f entityTrans, PoseStack poseStack, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        
        // 变换矩阵
        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf().rotateY(-entityYaw * ((float) Math.PI / 180F)));
        poseStack.mulPose(new Quaternionf().rotateX(entityPitch * ((float) Math.PI / 180F)));
        poseStack.translate(entityTrans.x, entityTrans.y, entityTrans.z);
        poseStack.scale(0.09f, 0.09f, 0.09f);
        
        // 从 Rust 引擎获取蒙皮后的顶点数据
        int posSize = vertexCount * 12;
        int norSize = vertexCount * 12;
        int uvSize = vertexCount * 8;
        
        long posData = nf.GetPoss(model);
        long norData = nf.GetNormals(model);
        long uvData = nf.GetUVs(model);
        
        nf.CopyDataToByteBuffer(posBuffer, posData, posSize);
        nf.CopyDataToByteBuffer(norBuffer, norData, norSize);
        nf.CopyDataToByteBuffer(uv0Buffer, uvData, uvSize);
        
        posBuffer.rewind();
        norBuffer.rewind();
        uv0Buffer.rewind();
        
        // 启用混合和深度测试
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        
        // 按子网格渲染
        long subMeshCount = nf.GetSubMeshCount(model);
        
        for (int i = 0; i < subMeshCount; i++) {
            int materialID = nf.GetSubMeshMaterialID(model, i);
            
            if (!nf.IsMaterialVisible(model, materialID)) continue;
            float alpha = nf.GetMaterialAlpha(model, materialID);
            if (alpha == 0.0f) continue;
            
            int startIndex = nf.GetSubMeshBeginIndex(model, i);
            int vertCount = nf.GetSubMeshVertexCount(model, i);
            
            renderSubMesh(poseStack, packedLight, materialID, startIndex, vertCount, i, mc);
        }
        
        poseStack.popPose();
    }
    
    /**
     * 使用 Minecraft 原生系统渲染子网格
     */
    private void renderSubMesh(PoseStack poseStack, int packedLight, int materialID, int startIndex, int vertCount, int subMeshIndex, Minecraft mc) {
        // 设置纹理
        if (mats[materialID].tex != 0) {
            RenderSystem.setShaderTexture(0, mats[materialID].tex);
        } else {
            mc.getTextureManager().bindForSetup(TextureManager.INTENTIONAL_MISSING_TEXTURE);
        }
        
        // === 关键：使用 Minecraft 原生着色器，Iris 会正确拦截 ===
        RenderSystem.setShader(GameRenderer::getRendertypeEntityCutoutNoCullShader);
        
        // 双面渲染
        boolean bothFace = nf.GetMaterialBothFace(model, materialID);
        if (bothFace) {
            RenderSystem.disableCull();
        } else {
            RenderSystem.enableCull();
        }
        
        // 构建顶点数据
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);
        
        Matrix4f pose = poseStack.last().pose();
        
        // 添加顶点
        for (int i = 0; i < vertCount; i++) {
            int idx = indices[startIndex + i];
            
            // 位置
            float px = posBuffer.getFloat(idx * 12);
            float py = posBuffer.getFloat(idx * 12 + 4);
            float pz = posBuffer.getFloat(idx * 12 + 8);
            
            // 法线
            float nx = norBuffer.getFloat(idx * 12);
            float ny = norBuffer.getFloat(idx * 12 + 4);
            float nz = norBuffer.getFloat(idx * 12 + 8);
            
            // UV
            float u = uv0Buffer.getFloat(idx * 8);
            float v = uv0Buffer.getFloat(idx * 8 + 4);
            
            builder.addVertex(pose, px, py, pz)
                   .setColor(255, 255, 255, 255)
                   .setUv(u, v)
                   .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                   .setLight(packedLight)
                   .setNormal(poseStack.last(), nx, ny, nz);
        }
        
        // 上传并使用 Minecraft 原生渲染（Iris 兼容！）
        MeshData rendered = builder.buildOrThrow();
        VertexBuffer vb = subMeshVertexBuffers[subMeshIndex];
        vb.bind();
        vb.upload(rendered);
        
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        vb.drawWithShader(modelView, projection, RenderSystem.getShader());
        
        VertexBuffer.unbind();
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
    
    // 内部材质类
    static class Material {
        int tex = 0;
        boolean hasAlpha = false;
    }
}
