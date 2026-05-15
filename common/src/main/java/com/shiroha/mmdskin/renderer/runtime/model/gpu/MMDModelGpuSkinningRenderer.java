/* 文件职责：执行 GPU skinning 模型的 Toon/普通渲染流程。 */
package com.shiroha.mmdskin.renderer.runtime.model.gpu;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.renderer.compat.IrisCompat;
import com.shiroha.mmdskin.renderer.performance.RenderPerformanceProfiler;
import com.shiroha.mmdskin.renderer.pipeline.shader.SkinningComputeShader;
import com.shiroha.mmdskin.renderer.pipeline.shader.ToonShaderCpu;
import com.shiroha.mmdskin.renderer.pipeline.shader.ToonRenderHelper;
import com.shiroha.mmdskin.renderer.runtime.model.AbstractMMDModel;
import com.shiroha.mmdskin.renderer.runtime.model.helper.LightingHelper;
import com.shiroha.mmdskin.renderer.runtime.model.shared.MMDMaterial;
import com.shiroha.mmdskin.renderer.runtime.model.shared.SubMeshDrawHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

final class MMDModelGpuSkinningRenderer {
    private static final Logger logger = LogManager.getLogger();

    private MMDModelGpuSkinningRenderer() {
    }

    static void render(MMDModelGpuSkinning target, Entity entityIn, float entityYaw, float entityPitch,
                       Vector3f entityTrans, PoseStack deliverStack) {
        Minecraft minecraft = Minecraft.getInstance();
        LightingHelper.LightData light = LightingHelper.sampleLight(entityIn, minecraft);
        var workingQuat = target.workingQuaternion();
        var nativeFunc = target.nativeFunc();
        long modelHandle = target.nativeModelHandle();

        target.light0Direction.set(1.0f, 0.75f, 0.0f).normalize();
        target.light1Direction.set(-1.0f, 0.75f, 0.0f).normalize();
        float yawRad = entityYaw * ((float) Math.PI / 180F);
        target.light0Direction.rotate(workingQuat.identity().rotateY(yawRad));
        target.light1Direction.rotate(workingQuat.identity().rotateY(yawRad));

        deliverStack.mulPose(workingQuat.identity().rotateY(-yawRad));
        deliverStack.mulPose(workingQuat.identity().rotateX(entityPitch * ((float) Math.PI / 180F)));
        deliverStack.translate(entityTrans.x, entityTrans.y, entityTrans.z);
        float baseScale = target.modelScaleValue();
        deliverStack.scale(baseScale, baseScale, baseScale);

        long boneTimer = RenderPerformanceProfiler.get().startTimer();
        MMDModelGpuSkinningUploader.uploadBoneMatrices(target);
        RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_BONE_UPLOAD, boneTimer);

        long morphTimer = RenderPerformanceProfiler.get().startTimer();
        if (target.vertexMorphCount > 0) {
            MMDModelGpuSkinningUploader.uploadMorphData(target);
        }
        if (target.uvMorphCount > 0) {
            MMDModelGpuSkinningUploader.uploadUvMorphData(target);
        }
        RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_MORPH_UPLOAD, morphTimer);

        if (target.materialMorphResultCountValue() > 0) {
            long materialMorphTimer = RenderPerformanceProfiler.get().startTimer();
            target.loadMaterialMorphResults();
            RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_MATERIAL_MORPH_FETCH, materialMorphTimer);
        }

        long computeTimer = RenderPerformanceProfiler.get().startTimer();
        MMDModelGpuSkinning.computeShader.dispatch(target.cachedDispatchParams);
        RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_COMPUTE_DISPATCH, computeTimer);

        long subMeshTimer = RenderPerformanceProfiler.get().startTimer();
        target.subMeshDataBuf.clear();
        nativeFunc.BatchGetSubMeshData(modelHandle, target.subMeshDataBuf);
        RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_SUB_MESH_FETCH, subMeshTimer);

        boolean useToon = initializeToonShaderIfNeeded();

        BufferUploader.reset();
        GL46C.glBindVertexArray(target.vertexArrayObject);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendEquation(GL46C.GL_FUNC_ADD);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        target.modelViewMatBuff.clear();
        target.projMatBuff.clear();
        AbstractMMDModel.computeModelViewMatrix(deliverStack).get(target.modelViewMatBuff);
        RenderSystem.getProjectionMatrix().get(target.projMatBuff);

        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, target.indexBufferObject);
        target.currentDeliverStack = deliverStack;

        if (useToon && MMDModelGpuSkinning.toonShaderCpu != null && MMDModelGpuSkinning.toonShaderCpu.isInitialized()) {
            renderToon(target, minecraft, light.intensity());
        } else {
            renderNormal(target, minecraft, light.intensity(), light.blockLight(), light.skyLight(), light.skyDarken());
        }

        cleanupVertexAttributes(target);
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, 0);
        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL46C.glBindVertexArray(0);
        RenderSystem.activeTexture(GL46C.GL_TEXTURE0);

        ShaderInstance currentShader = RenderSystem.getShader();
        if (currentShader != null) {
            currentShader.clear();
        }
        BufferUploader.reset();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static boolean initializeToonShaderIfNeeded() {
        if (!ConfigManager.isToonRenderingEnabled()) {
            return false;
        }
        if (MMDModelGpuSkinning.toonShaderCpu == null) {
            synchronized (MMDModelGpuSkinning.class) {
                if (MMDModelGpuSkinning.toonShaderCpu == null) {
                    ToonShaderCpu shader = new ToonShaderCpu();
                    if (!shader.init()) {
                        logger.warn("ToonShaderCpu 初始化失败，回退到普通着色");
                        return false;
                    }
                    MMDModelGpuSkinning.toonShaderCpu = shader;
                }
            }
        }
        return true;
    }

    private static void cleanupVertexAttributes(MMDModelGpuSkinning target) {
        if (target.positionLocation != -1) GL46C.glDisableVertexAttribArray(target.positionLocation);
        if (target.normalLocation != -1) GL46C.glDisableVertexAttribArray(target.normalLocation);
        if (target.uv0Location != -1) GL46C.glDisableVertexAttribArray(target.uv0Location);
        if (target.uv1Location != -1) GL46C.glDisableVertexAttribArray(target.uv1Location);
        if (target.uv2Location != -1) GL46C.glDisableVertexAttribArray(target.uv2Location);
        if (target.colorLocation != -1) GL46C.glDisableVertexAttribArray(target.colorLocation);
        if (target.I_positionLocation != -1) GL46C.glDisableVertexAttribArray(target.I_positionLocation);
        if (target.I_normalLocation != -1) GL46C.glDisableVertexAttribArray(target.I_normalLocation);
        if (target.I_uv0Location != -1) GL46C.glDisableVertexAttribArray(target.I_uv0Location);
        if (target.I_uv2Location != -1) GL46C.glDisableVertexAttribArray(target.I_uv2Location);
        if (target.I_colorLocation != -1) GL46C.glDisableVertexAttribArray(target.I_colorLocation);
    }

    private static void renderNormal(MMDModelGpuSkinning target, Minecraft minecraft,
                                     float lightIntensity, int blockLight, int skyLight, float skyDarken) {
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            logger.error("[GPU蒙皮] RenderSystem.getShader() 返回 null，跳过渲染");
            return;
        }
        target.shaderProgram = shader.getId();

        boolean irisActive = IrisCompat.isIrisShaderActive();
        float colorFactor = irisActive ? 1.0f : lightIntensity;
        RenderSystem.setShaderColor(colorFactor, colorFactor, colorFactor, 1.0f);

        target.setUniforms(shader, target.currentDeliverStack);
        shader.apply();

        GL46C.glUseProgram(target.shaderProgram);
        target.updateLocation(target.shaderProgram);

        int blockBrightness = LightingHelper.computeBlockBrightness(blockLight);
        int skyBrightness = LightingHelper.computeSkyBrightness(skyLight, skyDarken, irisActive);
        target.uv2Buffer.clear();
        long addr = MemoryUtil.memAddress(target.uv2Buffer);
        for (int i = 0; i < target.vertexCount; i++) {
            MemoryUtil.memPutInt(addr + (long) i * 8, blockBrightness);
            MemoryUtil.memPutInt(addr + (long) i * 8 + 4, skyBrightness);
        }
        target.uv2Buffer.position(target.vertexCount * 8);
        target.uv2Buffer.flip();
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.uv2BufferObject);
        GL46C.glBufferSubData(GL46C.GL_ARRAY_BUFFER, 0, target.uv2Buffer);

        if (target.uv2Location != -1) {
            GL46C.glEnableVertexAttribArray(target.uv2Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.uv2BufferObject);
            GL46C.glVertexAttribIPointer(target.uv2Location, 2, GL46C.GL_INT, 0, 0);
        }
        if (target.I_uv2Location != -1) {
            GL46C.glEnableVertexAttribArray(target.I_uv2Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.uv2BufferObject);
            GL46C.glVertexAttribIPointer(target.I_uv2Location, 2, GL46C.GL_INT, 0, 0);
        }
        if (target.colorLocation != -1) {
            GL46C.glEnableVertexAttribArray(target.colorLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.colorBufferObject);
            GL46C.glVertexAttribPointer(target.colorLocation, 4, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (target.I_colorLocation != -1) {
            GL46C.glEnableVertexAttribArray(target.I_colorLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.colorBufferObject);
            GL46C.glVertexAttribPointer(target.I_colorLocation, 4, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (target.positionLocation != -1) {
            GL46C.glEnableVertexAttribArray(target.positionLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.skinnedPositionsBuffer);
            GL46C.glVertexAttribPointer(target.positionLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (target.normalLocation != -1) {
            GL46C.glEnableVertexAttribArray(target.normalLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.skinnedNormalsBuffer);
            GL46C.glVertexAttribPointer(target.normalLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        int activeUvBuffer = target.skinnedUvBuffer > 0 ? target.skinnedUvBuffer : target.uv0BufferObject;
        if (target.uv0Location != -1) {
            GL46C.glEnableVertexAttribArray(target.uv0Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, activeUvBuffer);
            GL46C.glVertexAttribPointer(target.uv0Location, 2, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (target.uv1Location != -1) {
            GL46C.glEnableVertexAttribArray(target.uv1Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.uv1BufferObject);
            GL46C.glVertexAttribIPointer(target.uv1Location, 2, GL46C.GL_INT, 0, 0);
        }
        if (target.I_positionLocation != -1) {
            GL46C.glEnableVertexAttribArray(target.I_positionLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.skinnedPositionsBuffer);
            GL46C.glVertexAttribPointer(target.I_positionLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (target.I_normalLocation != -1) {
            GL46C.glEnableVertexAttribArray(target.I_normalLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.skinnedNormalsBuffer);
            GL46C.glVertexAttribPointer(target.I_normalLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (target.I_uv0Location != -1) {
            GL46C.glEnableVertexAttribArray(target.I_uv0Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, activeUvBuffer);
            GL46C.glVertexAttribPointer(target.I_uv0Location, 2, GL46C.GL_FLOAT, false, 0, 0);
        }

        long drawTimer = RenderPerformanceProfiler.get().startTimer();
        try {
            drawAllSubMeshes(target, minecraft);
        } finally {
            RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_DRAW, drawTimer);
        }
    }

    private static void renderToon(MMDModelGpuSkinning target, Minecraft minecraft, float lightIntensity) {
        boolean irisActive = IrisCompat.isIrisShaderActive();
        if (irisActive) {
            ShaderInstance irisShader = RenderSystem.getShader();
            if (irisShader != null) {
                target.setUniforms(irisShader, target.currentDeliverStack);
                irisShader.apply();
            }
        }

        int missingTextureId = minecraft.getTextureManager()
                .getTexture(TextureManager.INTENTIONAL_MISSING_TEXTURE)
                .getId();

        MMDModelGpuSkinning.toonShaderCpu.useMain();
        int toonPosLoc = MMDModelGpuSkinning.toonShaderCpu.getPositionLocation();
        int toonNorLoc = MMDModelGpuSkinning.toonShaderCpu.getNormalLocation();
        int uvLoc = MMDModelGpuSkinning.toonShaderCpu.getUv0Location();

        if (toonPosLoc != -1) {
            GL46C.glEnableVertexAttribArray(toonPosLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.skinnedPositionsBuffer);
            GL46C.glVertexAttribPointer(toonPosLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (toonNorLoc != -1) {
            GL46C.glEnableVertexAttribArray(toonNorLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.skinnedNormalsBuffer);
            GL46C.glVertexAttribPointer(toonNorLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (uvLoc != -1) {
            GL46C.glEnableVertexAttribArray(uvLoc);
            int toonUvBuffer = target.skinnedUvBuffer > 0 ? target.skinnedUvBuffer : target.uv0BufferObject;
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, toonUvBuffer);
            GL46C.glVertexAttribPointer(uvLoc, 2, GL46C.GL_FLOAT, false, 0, 0);
        }

        MMDModelGpuSkinning.toonShaderCpu.setProjectionMatrix(target.projMatBuff);
        MMDModelGpuSkinning.toonShaderCpu.setModelViewMatrix(target.modelViewMatBuff);
        ToonRenderHelper.setupToonUniforms(MMDModelGpuSkinning.toonShaderCpu, lightIntensity, target.light0Direction);

        long drawTimer = RenderPerformanceProfiler.get().startTimer();
        try {
            drawAllSubMeshes(target, minecraft);
        } finally {
            RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_DRAW, drawTimer);
        }

        if (toonPosLoc != -1) GL46C.glDisableVertexAttribArray(toonPosLoc);
        if (toonNorLoc != -1) GL46C.glDisableVertexAttribArray(toonNorLoc);
        if (uvLoc != -1) GL46C.glDisableVertexAttribArray(uvLoc);

        if (MMDModelGpuSkinning.toonConfig.isOutlineEnabled()) {
            MMDModelGpuSkinning.toonShaderCpu.useOutline();

            int posLoc = MMDModelGpuSkinning.toonShaderCpu.getOutlinePositionLocation();
            int norLoc = MMDModelGpuSkinning.toonShaderCpu.getOutlineNormalLocation();

            if (posLoc != -1) {
                GL46C.glEnableVertexAttribArray(posLoc);
                GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.skinnedPositionsBuffer);
                GL46C.glVertexAttribPointer(posLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
            }
            if (norLoc != -1) {
                GL46C.glEnableVertexAttribArray(norLoc);
                GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.skinnedNormalsBuffer);
                GL46C.glVertexAttribPointer(norLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
            }

            MMDModelGpuSkinning.toonShaderCpu.setOutlineProjectionMatrix(target.projMatBuff);
            MMDModelGpuSkinning.toonShaderCpu.setOutlineModelViewMatrix(target.modelViewMatBuff);
            int outlineUvLoc = MMDModelGpuSkinning.toonShaderCpu.getOutlineUv0Location();
            if (outlineUvLoc != -1) {
                GL46C.glEnableVertexAttribArray(outlineUvLoc);
                int outlineUvBuffer = target.skinnedUvBuffer > 0 ? target.skinnedUvBuffer : target.uv0BufferObject;
                GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, outlineUvBuffer);
                GL46C.glVertexAttribPointer(outlineUvLoc, 2, GL46C.GL_FLOAT, false, 0, 0);
            }

            ToonRenderHelper.setupOutlineUniforms(MMDModelGpuSkinning.toonShaderCpu);

            RenderSystem.depthMask(false);
            GL46C.glCullFace(GL46C.GL_FRONT);
            RenderSystem.enableCull();
            SubMeshDrawHelper.drawOutline(
                    target.subMeshDataBuf,
                    target.subMeshCount,
                    target.indexElementSize,
                    target.indexType,
                    materialId -> target.mats[materialId].tex == 0 ? missingTextureId : target.mats[materialId].tex,
                    (materialId, baseAlpha) -> {
                        MMDMaterial material = target.mats[materialId];
                        if (material != null && material.isFacialFeature()) {
                            return 0.0f;
                        }
                        return target.effectiveMaterialAlpha(materialId, baseAlpha);
                    });
            GL46C.glCullFace(GL46C.GL_BACK);
            RenderSystem.depthMask(true);
            if (posLoc != -1) GL46C.glDisableVertexAttribArray(posLoc);
            if (norLoc != -1) GL46C.glDisableVertexAttribArray(norLoc);
            if (outlineUvLoc != -1) GL46C.glDisableVertexAttribArray(outlineUvLoc);
        }

        GL46C.glUseProgram(0);
    }

    private static void drawAllSubMeshes(MMDModelGpuSkinning target, Minecraft minecraft) {
        int missingTextureId = minecraft.getTextureManager()
                .getTexture(TextureManager.INTENTIONAL_MISSING_TEXTURE)
                .getId();
        SubMeshDrawHelper.draw(
                target.subMeshDataBuf,
                target.subMeshCount,
                target.indexElementSize,
                target.indexType,
                materialId -> target.mats[materialId].tex == 0 ? missingTextureId : target.mats[materialId].tex,
                target::effectiveMaterialAlpha);
    }
}
