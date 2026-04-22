package com.shiroha.mmdskin.render.backend.gpu;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.bridge.runtime.NativeRenderBackendPort;
import com.shiroha.mmdskin.compat.iris.IrisCompat;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.render.material.ModelMaterial;
import com.shiroha.mmdskin.render.material.SubMeshDrawHelper;
import com.shiroha.mmdskin.render.pipeline.LightingHelper;
import com.shiroha.mmdskin.render.pipeline.RenderPerformanceProfiler;
import com.shiroha.mmdskin.render.shader.SkinningComputeShader;
import com.shiroha.mmdskin.render.shader.ToonRenderHelper;
import com.shiroha.mmdskin.render.shader.ToonShaderCpu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL46C;

/** 文件职责：执行 GPU skinning 模型实例的渲染流程。 */
final class GpuSkinningModelRenderer {
    private static final Logger logger = LogManager.getLogger();

    private GpuSkinningModelRenderer() {
    }

    static void render(GpuSkinningModelInstance target,
                       Entity entityIn,
                       float entityYaw,
                       float entityPitch,
                       Vector3f entityTrans,
                       PoseStack deliverStack) {
        Minecraft minecraft = Minecraft.getInstance();
        LightingHelper.LightData light = LightingHelper.sampleLight(entityIn, minecraft);
        var workingQuat = target.workingQuaternion();
        var nativeBackend = target.nativeBackendPort();
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

        updateGpuStateIfDirty(target, nativeBackend, modelHandle);

        boolean useToon = initializeToonShaderIfNeeded();

        BufferUploader.reset();
        GL46C.glBindVertexArray(target.vertexArrayObject);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendEquation(GL46C.GL_FUNC_ADD);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        target.modelViewMatBuff.clear();
        target.projMatBuff.clear();
        deliverStack.last().pose().get(target.modelViewMatBuff);
        RenderSystem.getProjectionMatrix().get(target.projMatBuff);

        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, target.indexBufferObject);
        target.currentDeliverStack = deliverStack;

        long drawTimer = RenderPerformanceProfiler.get().startTimer();
        try {
            if (useToon && GpuSkinningModelInstance.toonShaderCpu != null && GpuSkinningModelInstance.toonShaderCpu.isInitialized()) {
                renderToon(target, minecraft, light.intensity());
            } else {
                renderNormal(target, minecraft, light.intensity(), light.blockLight(), light.skyLight(), light.skyDarken());
            }
        } finally {
            RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_DRAW, drawTimer);
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
        if (GpuSkinningModelInstance.toonShaderCpu == null) {
            GpuSkinningModelInstance.toonShaderCpu = new ToonShaderCpu();
            if (!GpuSkinningModelInstance.toonShaderCpu.init()) {
                logger.warn("ToonShaderCpu initialization failed, falling back to standard shading");
                GpuSkinningModelInstance.toonShaderCpu = null;
                return false;
            }
        }
        return true;
    }

    private static void updateGpuStateIfDirty(GpuSkinningModelInstance target,
                                              NativeRenderBackendPort nativeBackend,
                                              long modelHandle) {
        long currentRevision = target.nativeUpdateRevisionValue();
        if (target.lastGpuUploadRevision == currentRevision) {
            return;
        }

        long boneTimer = RenderPerformanceProfiler.get().startTimer();
        GpuSkinningModelUploader.uploadBoneMatrices(target);
        RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_BONE_UPLOAD, boneTimer);

        if (target.vertexMorphCount > 0 || target.uvMorphCount > 0) {
            long morphTimer = RenderPerformanceProfiler.get().startTimer();
            if (target.vertexMorphCount > 0) {
                GpuSkinningModelUploader.uploadMorphData(target);
            }
            if (target.uvMorphCount > 0) {
                GpuSkinningModelUploader.uploadUvMorphData(target);
            }
            RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_MORPH_UPLOAD, morphTimer);
        }

        if (target.materialMorphResultCountValue() > 0) {
            long materialMorphTimer = RenderPerformanceProfiler.get().startTimer();
            target.loadMaterialMorphResults();
            RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_MATERIAL_MORPH_FETCH, materialMorphTimer);
        }

        long computeTimer = RenderPerformanceProfiler.get().startTimer();
        GpuSkinningModelInstance.computeShader.dispatch(new SkinningComputeShader.DispatchParams(
                target.positionBufferObject,
                target.normalBufferObject,
                target.boneIndicesBufferObject,
                target.boneWeightsBufferObject,
                target.uv0BufferObject,
                target.skinnedPositionsBuffer,
                target.skinnedNormalsBuffer,
                target.skinnedUvBuffer,
                target.boneMatrixSSBO,
                target.morphOffsetsSSBO,
                target.morphWeightsSSBO,
                target.vertexMorphCount,
                target.uvMorphOffsetsSSBO,
                target.uvMorphWeightsSSBO,
                target.uvMorphCount,
                target.vertexCount
        ));
        RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_COMPUTE_DISPATCH, computeTimer);

        long subMeshTimer = RenderPerformanceProfiler.get().startTimer();
        target.subMeshDataBuf.clear();
        nativeBackend.batchGetSubMeshData(modelHandle, target.subMeshDataBuf);
        RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_SUB_MESH_FETCH, subMeshTimer);

        target.lastGpuUploadRevision = currentRevision;
    }

    private static void cleanupVertexAttributes(GpuSkinningModelInstance target) {
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

    private static void renderNormal(GpuSkinningModelInstance target,
                                     Minecraft minecraft,
                                     float lightIntensity,
                                     int blockLight,
                                     int skyLight,
                                     float skyDarken) {
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            logger.error("[GPU skinning] RenderSystem.getShader() returned null; skipping render");
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

        int blockBrightness = 16 * blockLight;
        int skyBrightness = irisActive ? (16 * skyLight) : Math.round((15.0f - skyDarken) * (skyLight / 15.0f) * 16);
        uploadLightBufferIfNeeded(target, blockBrightness, skyBrightness);

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

        drawAllSubMeshes(target, minecraft);
    }

    private static void uploadLightBufferIfNeeded(GpuSkinningModelInstance target, int blockBrightness, int skyBrightness) {
        if (target.lastBlockBrightness == blockBrightness && target.lastSkyBrightness == skyBrightness) {
            return;
        }

        target.uv2Buffer.clear();
        for (int i = 0; i < target.vertexCount; i++) {
            target.uv2Buffer.putInt(blockBrightness);
            target.uv2Buffer.putInt(skyBrightness);
        }
        target.uv2Buffer.flip();
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.uv2BufferObject);
        GL46C.glBufferSubData(GL46C.GL_ARRAY_BUFFER, 0, target.uv2Buffer);
        target.lastBlockBrightness = blockBrightness;
        target.lastSkyBrightness = skyBrightness;
    }

    private static void renderToon(GpuSkinningModelInstance target, Minecraft minecraft, float lightIntensity) {
        if (IrisCompat.isIrisShaderActive()) {
            ShaderInstance irisShader = RenderSystem.getShader();
            if (irisShader != null) {
                target.setUniforms(irisShader, target.currentDeliverStack);
                irisShader.apply();
            }
        }

        GpuSkinningModelInstance.toonShaderCpu.useMain();
        int toonPosLoc = GpuSkinningModelInstance.toonShaderCpu.getPositionLocation();
        int toonNorLoc = GpuSkinningModelInstance.toonShaderCpu.getNormalLocation();
        int uvLoc = GpuSkinningModelInstance.toonShaderCpu.getUv0Location();

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

        GpuSkinningModelInstance.toonShaderCpu.setProjectionMatrix(target.projMatBuff);
        GpuSkinningModelInstance.toonShaderCpu.setModelViewMatrix(target.modelViewMatBuff);
        ToonRenderHelper.setupToonUniforms(GpuSkinningModelInstance.toonShaderCpu, lightIntensity, target.light0Direction);

        drawAllSubMeshes(target, minecraft);

        if (toonPosLoc != -1) GL46C.glDisableVertexAttribArray(toonPosLoc);
        if (toonNorLoc != -1) GL46C.glDisableVertexAttribArray(toonNorLoc);
        if (uvLoc != -1) GL46C.glDisableVertexAttribArray(uvLoc);

        if (GpuSkinningModelInstance.toonConfig.isOutlineEnabled()) {
            renderOutlinePass(target, minecraft);
        }

        GL46C.glUseProgram(0);
    }

    private static void renderOutlinePass(GpuSkinningModelInstance target, Minecraft minecraft) {
        GpuSkinningModelInstance.toonShaderCpu.useOutline();

        int posLoc = GpuSkinningModelInstance.toonShaderCpu.getOutlinePositionLocation();
        int norLoc = GpuSkinningModelInstance.toonShaderCpu.getOutlineNormalLocation();
        int uvLoc = GpuSkinningModelInstance.toonShaderCpu.getOutlineUv0Location();
        int outlineUvBuffer = target.skinnedUvBuffer > 0 ? target.skinnedUvBuffer : target.uv0BufferObject;

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
        if (uvLoc != -1) {
            GL46C.glEnableVertexAttribArray(uvLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, outlineUvBuffer);
            GL46C.glVertexAttribPointer(uvLoc, 2, GL46C.GL_FLOAT, false, 0, 0);
        }

        GpuSkinningModelInstance.toonShaderCpu.setOutlineProjectionMatrix(target.projMatBuff);
        GpuSkinningModelInstance.toonShaderCpu.setOutlineModelViewMatrix(target.modelViewMatBuff);
        ToonRenderHelper.setupOutlineUniforms(GpuSkinningModelInstance.toonShaderCpu);
        int missingTextureId = minecraft.getTextureManager()
                .getTexture(TextureManager.INTENTIONAL_MISSING_TEXTURE)
                .getId();

        RenderSystem.depthMask(false);
        GL46C.glCullFace(GL46C.GL_FRONT);
        RenderSystem.enableCull();
        SubMeshDrawHelper.drawOutline(
                target.subMeshDataBuf,
                target.subMeshCount,
                target.indexElementSize,
                target.indexType,
                materialId -> target.mats[materialId].tex == 0 ? missingTextureId : target.mats[materialId].tex,
                (materialId, baseAlpha) -> effectiveOutlineAlpha(target, materialId, baseAlpha)
        );
        GL46C.glCullFace(GL46C.GL_BACK);
        RenderSystem.depthMask(true);

        if (posLoc != -1) GL46C.glDisableVertexAttribArray(posLoc);
        if (norLoc != -1) GL46C.glDisableVertexAttribArray(norLoc);
        if (uvLoc != -1) GL46C.glDisableVertexAttribArray(uvLoc);
    }

    private static void drawAllSubMeshes(GpuSkinningModelInstance target, Minecraft minecraft) {
        int missingTextureId = minecraft.getTextureManager()
                .getTexture(TextureManager.INTENTIONAL_MISSING_TEXTURE)
                .getId();
        SubMeshDrawHelper.draw(
                target.subMeshDataBuf,
                target.subMeshCount,
                target.indexElementSize,
                target.indexType,
                materialId -> target.mats[materialId].tex == 0 ? missingTextureId : target.mats[materialId].tex,
                target::effectiveMaterialAlpha
        );
    }

    private static float effectiveOutlineAlpha(GpuSkinningModelInstance target, int materialId, float baseAlpha) {
        if (materialId < 0 || materialId >= target.mats.length) {
            return 0.0f;
        }

        ModelMaterial material = target.mats[materialId];
        if (material == null || !material.outlineEnabled) {
            return 0.0f;
        }

        return target.effectiveMaterialAlpha(materialId, baseAlpha);
    }
}
