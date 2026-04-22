package com.shiroha.mmdskin.render.backend.opengl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.compat.iris.IrisCompat;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.render.shader.ToonShaderCpu;
import com.shiroha.mmdskin.render.shader.ToonRenderHelper;
import com.shiroha.mmdskin.render.pipeline.LightingHelper;
import com.shiroha.mmdskin.render.pipeline.RenderPerformanceProfiler;
import com.shiroha.mmdskin.render.material.ModelMaterial;
import com.shiroha.mmdskin.render.material.SubMeshDrawHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL46C;

final class OpenGlModelRenderer {
    private static final Logger logger = LogManager.getLogger();

    private OpenGlModelRenderer() {
    }

    static void render(OpenGlModelInstance target, Entity entityIn, float entityYaw, float entityPitch,
                       Vector3f entityTrans, PoseStack deliverStack, int packedLight) {
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

        long materialMorphTimer = RenderPerformanceProfiler.get().startTimer();
        target.loadMaterialMorphResults();
        RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_MATERIAL_MORPH_FETCH, materialMorphTimer);

        long subMeshTimer = RenderPerformanceProfiler.get().startTimer();
        target.subMeshDataBuf.clear();
        nativeBackend.batchGetSubMeshData(modelHandle, target.subMeshDataBuf);
        RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_SUB_MESH_FETCH, subMeshTimer);

        boolean useToon = initializeToonShaderIfNeeded();
        if (useToon) {
            long drawTimer = RenderPerformanceProfiler.get().startTimer();
            try {
                renderToon(target, minecraft, light.intensity(), deliverStack);
            } finally {
                RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_DRAW, drawTimer);
            }
            return;
        }

        long drawTimer = RenderPerformanceProfiler.get().startTimer();
        try {
            renderStandard(target, minecraft, light, deliverStack);
        } finally {
            RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_DRAW, drawTimer);
        }
    }

    private static boolean initializeToonShaderIfNeeded() {
        if (!ConfigManager.isToonRenderingEnabled()) {
            return false;
        }

        if (OpenGlModelInstance.toonShaderCpu == null) {
            OpenGlModelInstance.toonShaderCpu = new ToonShaderCpu();
            if (!OpenGlModelInstance.toonShaderCpu.init()) {
                logger.warn("ToonShaderCpu ?????????????");
                OpenGlModelInstance.toonShaderCpu = null;
                return false;
            }
        }

        return OpenGlModelInstance.toonShaderCpu.isInitialized();
    }

    private static void renderStandard(OpenGlModelInstance target, Minecraft minecraft,
                                       LightingHelper.LightData light, PoseStack deliverStack) {
        boolean irisActive = IrisCompat.isIrisShaderActive();
        float colorFactor = irisActive ? 1.0f : light.intensity();
        RenderSystem.setShaderColor(colorFactor, colorFactor, colorFactor, 1.0f);

        if (!bindActiveShader(target, deliverStack)) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            return;
        }

        target.updateLocation(target.shaderProgram);

        BufferUploader.reset();
        GL46C.glBindVertexArray(target.vertexArrayObject);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendEquation(GL46C.GL_FUNC_ADD);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        uploadDynamicBuffers(target, light.blockLight(), light.skyLight(), light.skyDarken(), irisActive);
        uploadMatrixUniforms(target, deliverStack);
        bindStandardAttributes(target);
        bindCustomShaderAttributes(target);
        bindIrisAttributes(target);
        drawSubMeshes(target, minecraft);
        clearStandardRenderState(target);
    }

    private static boolean bindActiveShader(OpenGlModelInstance target, PoseStack deliverStack) {
        int shaderPipelineMode = ClientRenderRuntime.get().renderBackendRegistry().shaderPipelineMode();
        if (shaderPipelineMode == 0) {
            ShaderInstance mcShader = RenderSystem.getShader();
            if (mcShader == null) {
                return false;
            }
            target.shaderProgram = mcShader.getId();
            target.setUniforms(mcShader, deliverStack);
            mcShader.apply();
            return true;
        }

        if (shaderPipelineMode == 1) {
            target.shaderProgram = OpenGlModelInstance.MMDShaderProgram;
            GlStateManager._glUseProgram(target.shaderProgram);
            return true;
        }

        return false;
    }

    private static void uploadDynamicBuffers(OpenGlModelInstance target, int blockLight, int skyLight,
                                             float skyDarken, boolean irisActive) {
        var nativeBackend = target.nativeBackendPort();
        long modelHandle = target.nativeModelHandle();
        int posAndNorSize = target.vertexCount * 12;
        long posData = nativeBackend.getPositionDataAddress(modelHandle);
        nativeBackend.copyNativeDataToBuffer(target.posBuffer, posData, posAndNorSize);
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.vertexBufferObject);
        GL46C.glBufferSubData(GL46C.GL_ARRAY_BUFFER, 0, target.posBuffer);

        long normalData = nativeBackend.getNormalDataAddress(modelHandle);
        nativeBackend.copyNativeDataToBuffer(target.norBuffer, normalData, posAndNorSize);
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.normalBufferObject);
        GL46C.glBufferSubData(GL46C.GL_ARRAY_BUFFER, 0, target.norBuffer);

        if (target.hasUvMorph) {
            int uv0Size = target.vertexCount * 8;
            long uv0Data = nativeBackend.getUvDataAddress(modelHandle);
            nativeBackend.copyNativeDataToBuffer(target.uv0Buffer, uv0Data, uv0Size);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.texcoordBufferObject);
            GL46C.glBufferSubData(GL46C.GL_ARRAY_BUFFER, 0, target.uv0Buffer);
        }

        int blockBrightness = 16 * blockLight;
        int skyBrightness = irisActive ? (16 * skyLight)
                : Math.round((15.0f - skyDarken) * (skyLight / 15.0f) * 16);
        uploadLightBufferIfNeeded(target, blockBrightness, skyBrightness);
    }

    private static void uploadLightBufferIfNeeded(OpenGlModelInstance target, int blockBrightness, int skyBrightness) {
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

    private static void uploadMatrixUniforms(OpenGlModelInstance target, PoseStack deliverStack) {
        target.modelViewMatBuff.clear();
        target.projMatBuff.clear();
        deliverStack.last().pose().get(target.modelViewMatBuff);
        RenderSystem.getProjectionMatrix().get(target.projMatBuff);

        if (ClientRenderRuntime.get().renderBackendRegistry().shaderPipelineMode() != 1) {
            return;
        }

        RenderSystem.glUniformMatrix4(target.modelViewLocation, false, target.modelViewMatBuff);
        RenderSystem.glUniformMatrix4(target.projMatLocation, false, target.projMatBuff);

        if (target.light0Location != -1) {
            target.light0Buff.clear();
            target.light0Buff.put(target.light0Direction.x);
            target.light0Buff.put(target.light0Direction.y);
            target.light0Buff.put(target.light0Direction.z);
            target.light0Buff.flip();
            RenderSystem.glUniform3(target.light0Location, target.light0Buff);
        }
        if (target.light1Location != -1) {
            target.light1Buff.clear();
            target.light1Buff.put(target.light1Direction.x);
            target.light1Buff.put(target.light1Direction.y);
            target.light1Buff.put(target.light1Direction.z);
            target.light1Buff.flip();
            RenderSystem.glUniform3(target.light1Location, target.light1Buff);
        }
        if (target.sampler0Location != -1) {
            GL46C.glUniform1i(target.sampler0Location, 0);
        }
        if (target.sampler1Location != -1) {
            RenderSystem.activeTexture(GL46C.GL_TEXTURE1);
            RenderSystem.bindTexture(target.lightMapMaterial.tex);
            GL46C.glUniform1i(target.sampler1Location, 1);
        }
        if (target.sampler2Location != -1) {
            RenderSystem.activeTexture(GL46C.GL_TEXTURE2);
            RenderSystem.bindTexture(target.lightMapMaterial.tex);
            GL46C.glUniform1i(target.sampler2Location, 2);
        }
    }

    private static void bindStandardAttributes(OpenGlModelInstance target) {
        if (target.uv2Location != -1) {
            GL46C.glEnableVertexAttribArray(target.uv2Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.uv2BufferObject);
            GL46C.glVertexAttribIPointer(target.uv2Location, 2, GL46C.GL_INT, 0, 0);
        }
        if (target.colorLocation != -1) {
            GL46C.glEnableVertexAttribArray(target.colorLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.colorBufferObject);
            GL46C.glVertexAttribPointer(target.colorLocation, 4, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (target.positionLocation != -1) {
            GL46C.glEnableVertexAttribArray(target.positionLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.vertexBufferObject);
            GL46C.glVertexAttribPointer(target.positionLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (target.normalLocation != -1) {
            GL46C.glEnableVertexAttribArray(target.normalLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.normalBufferObject);
            GL46C.glVertexAttribPointer(target.normalLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (target.uv0Location != -1) {
            GL46C.glEnableVertexAttribArray(target.uv0Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.texcoordBufferObject);
            GL46C.glVertexAttribPointer(target.uv0Location, 2, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (target.uv1Location != -1) {
            GL46C.glEnableVertexAttribArray(target.uv1Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.uv1BufferObject);
            GL46C.glVertexAttribIPointer(target.uv1Location, 2, GL46C.GL_INT, 0, 0);
        }

        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, target.indexBufferObject);
    }

    private static void bindCustomShaderAttributes(OpenGlModelInstance target) {
        if (target.K_uv2Location != -1) {
            GL46C.glEnableVertexAttribArray(target.K_uv2Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.uv2BufferObject);
            GL46C.glVertexAttribIPointer(target.K_uv2Location, 2, GL46C.GL_INT, 0, 0);
        }
        if (target.K_positionLocation != -1) {
            GL46C.glEnableVertexAttribArray(target.K_positionLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.vertexBufferObject);
            GL46C.glVertexAttribPointer(target.K_positionLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (target.K_normalLocation != -1) {
            GL46C.glEnableVertexAttribArray(target.K_normalLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.normalBufferObject);
            GL46C.glVertexAttribPointer(target.K_normalLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (target.K_uv0Location != -1) {
            GL46C.glEnableVertexAttribArray(target.K_uv0Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.texcoordBufferObject);
            GL46C.glVertexAttribPointer(target.K_uv0Location, 2, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (target.K_projMatLocation != -1) {
            target.projMatBuff.position(0);
            RenderSystem.glUniformMatrix4(target.K_projMatLocation, false, target.projMatBuff);
        }
        if (target.K_modelViewLocation != -1) {
            target.modelViewMatBuff.position(0);
            RenderSystem.glUniformMatrix4(target.K_modelViewLocation, false, target.modelViewMatBuff);
        }
        if (target.K_sampler0Location != -1) {
            GL46C.glUniform1i(target.K_sampler0Location, 0);
        }
        if (target.K_sampler2Location != -1) {
            RenderSystem.activeTexture(GL46C.GL_TEXTURE2);
            RenderSystem.bindTexture(target.lightMapMaterial.tex);
            GL46C.glUniform1i(target.K_sampler2Location, 2);
        }
        if (target.KAIMyLocationV != -1) {
            GL46C.glUniform1i(target.KAIMyLocationV, 1);
        }
        if (target.KAIMyLocationF != -1) {
            GL46C.glUniform1i(target.KAIMyLocationF, 1);
        }
    }

    private static void bindIrisAttributes(OpenGlModelInstance target) {
        if (target.I_uv2Location != -1) {
            GL46C.glEnableVertexAttribArray(target.I_uv2Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.uv2BufferObject);
            GL46C.glVertexAttribIPointer(target.I_uv2Location, 2, GL46C.GL_INT, 0, 0);
        }
        if (target.I_colorLocation != -1) {
            GL46C.glEnableVertexAttribArray(target.I_colorLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.colorBufferObject);
            GL46C.glVertexAttribPointer(target.I_colorLocation, 4, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (target.I_positionLocation != -1) {
            GL46C.glEnableVertexAttribArray(target.I_positionLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.vertexBufferObject);
            GL46C.glVertexAttribPointer(target.I_positionLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (target.I_normalLocation != -1) {
            GL46C.glEnableVertexAttribArray(target.I_normalLocation);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.normalBufferObject);
            GL46C.glVertexAttribPointer(target.I_normalLocation, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (target.I_uv0Location != -1) {
            GL46C.glEnableVertexAttribArray(target.I_uv0Location);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.texcoordBufferObject);
            GL46C.glVertexAttribPointer(target.I_uv0Location, 2, GL46C.GL_FLOAT, false, 0, 0);
        }
    }

    private static void drawSubMeshes(OpenGlModelInstance target, Minecraft minecraft) {
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

    private static void clearStandardRenderState(OpenGlModelInstance target) {
        if (target.KAIMyLocationV != -1) GL46C.glUniform1i(target.KAIMyLocationV, 0);
        if (target.KAIMyLocationF != -1) GL46C.glUniform1i(target.KAIMyLocationF, 0);

        if (target.positionLocation != -1) GL46C.glDisableVertexAttribArray(target.positionLocation);
        if (target.normalLocation != -1) GL46C.glDisableVertexAttribArray(target.normalLocation);
        if (target.uv0Location != -1) GL46C.glDisableVertexAttribArray(target.uv0Location);
        if (target.uv1Location != -1) GL46C.glDisableVertexAttribArray(target.uv1Location);
        if (target.uv2Location != -1) GL46C.glDisableVertexAttribArray(target.uv2Location);
        if (target.colorLocation != -1) GL46C.glDisableVertexAttribArray(target.colorLocation);
        if (target.K_positionLocation != -1) GL46C.glDisableVertexAttribArray(target.K_positionLocation);
        if (target.K_normalLocation != -1) GL46C.glDisableVertexAttribArray(target.K_normalLocation);
        if (target.K_uv0Location != -1) GL46C.glDisableVertexAttribArray(target.K_uv0Location);
        if (target.K_uv2Location != -1) GL46C.glDisableVertexAttribArray(target.K_uv2Location);
        if (target.I_positionLocation != -1) GL46C.glDisableVertexAttribArray(target.I_positionLocation);
        if (target.I_normalLocation != -1) GL46C.glDisableVertexAttribArray(target.I_normalLocation);
        if (target.I_uv0Location != -1) GL46C.glDisableVertexAttribArray(target.I_uv0Location);
        if (target.I_uv2Location != -1) GL46C.glDisableVertexAttribArray(target.I_uv2Location);
        if (target.I_colorLocation != -1) GL46C.glDisableVertexAttribArray(target.I_colorLocation);

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

    private static void renderToon(OpenGlModelInstance target, Minecraft minecraft, float lightIntensity, PoseStack deliverStack) {
        BufferUploader.reset();
        GL46C.glBindVertexArray(target.vertexArrayObject);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendEquation(GL46C.GL_FUNC_ADD);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        if (IrisCompat.isIrisShaderActive()) {
            ShaderInstance irisShader = RenderSystem.getShader();
            if (irisShader != null) {
                target.setUniforms(irisShader, deliverStack);
                irisShader.apply();
            }
        }

        var nativeBackend = target.nativeBackendPort();
        long modelHandle = target.nativeModelHandle();
        int posAndNorSize = target.vertexCount * 12;
        long posData = nativeBackend.getPositionDataAddress(modelHandle);
        nativeBackend.copyNativeDataToBuffer(target.posBuffer, posData, posAndNorSize);
        long normalData = nativeBackend.getNormalDataAddress(modelHandle);
        nativeBackend.copyNativeDataToBuffer(target.norBuffer, normalData, posAndNorSize);

        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.vertexBufferObject);
        GL46C.glBufferSubData(GL46C.GL_ARRAY_BUFFER, 0, target.posBuffer);
        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.normalBufferObject);
        GL46C.glBufferSubData(GL46C.GL_ARRAY_BUFFER, 0, target.norBuffer);
        if (target.hasUvMorph) {
            int uv0Size = target.vertexCount * 8;
            long uv0Data = nativeBackend.getUvDataAddress(modelHandle);
            nativeBackend.copyNativeDataToBuffer(target.uv0Buffer, uv0Data, uv0Size);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.texcoordBufferObject);
            GL46C.glBufferSubData(GL46C.GL_ARRAY_BUFFER, 0, target.uv0Buffer);
        }

        target.modelViewMatBuff.clear();
        target.projMatBuff.clear();
        deliverStack.last().pose().get(target.modelViewMatBuff);
        RenderSystem.getProjectionMatrix().get(target.projMatBuff);
        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, target.indexBufferObject);

        renderToonMainPass(target, minecraft, lightIntensity);

        if (OpenGlModelInstance.toonConfig.isOutlineEnabled()) {
            renderOutlinePass(target, minecraft);
        }

        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, 0);
        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL46C.glBindVertexArray(0);
        GL46C.glUseProgram(0);
        RenderSystem.activeTexture(GL46C.GL_TEXTURE0);
        BufferUploader.reset();
    }

    private static void renderOutlinePass(OpenGlModelInstance target, Minecraft minecraft) {
        OpenGlModelInstance.toonShaderCpu.useOutline();
        int posLoc = OpenGlModelInstance.toonShaderCpu.getOutlinePositionLocation();
        int norLoc = OpenGlModelInstance.toonShaderCpu.getOutlineNormalLocation();
        int uvLoc = OpenGlModelInstance.toonShaderCpu.getOutlineUv0Location();

        if (posLoc != -1) {
            GL46C.glEnableVertexAttribArray(posLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.vertexBufferObject);
            GL46C.glVertexAttribPointer(posLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (norLoc != -1) {
            GL46C.glEnableVertexAttribArray(norLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.normalBufferObject);
            GL46C.glVertexAttribPointer(norLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (uvLoc != -1) {
            GL46C.glEnableVertexAttribArray(uvLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.texcoordBufferObject);
            GL46C.glVertexAttribPointer(uvLoc, 2, GL46C.GL_FLOAT, false, 0, 0);
        }

        OpenGlModelInstance.toonShaderCpu.setOutlineProjectionMatrix(target.projMatBuff);
        OpenGlModelInstance.toonShaderCpu.setOutlineModelViewMatrix(target.modelViewMatBuff);
        ToonRenderHelper.setupOutlineUniforms(OpenGlModelInstance.toonShaderCpu);
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
                (materialId, baseAlpha) -> effectiveOutlineAlpha(target, materialId, baseAlpha));
        GL46C.glCullFace(GL46C.GL_BACK);
        RenderSystem.depthMask(true);

        if (posLoc != -1) GL46C.glDisableVertexAttribArray(posLoc);
        if (norLoc != -1) GL46C.glDisableVertexAttribArray(norLoc);
        if (uvLoc != -1) GL46C.glDisableVertexAttribArray(uvLoc);
    }

    private static void renderToonMainPass(OpenGlModelInstance target, Minecraft minecraft, float lightIntensity) {
        OpenGlModelInstance.toonShaderCpu.useMain();
        int posLoc = OpenGlModelInstance.toonShaderCpu.getPositionLocation();
        int norLoc = OpenGlModelInstance.toonShaderCpu.getNormalLocation();
        int uvLoc = OpenGlModelInstance.toonShaderCpu.getUv0Location();

        if (posLoc != -1) {
            GL46C.glEnableVertexAttribArray(posLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.vertexBufferObject);
            GL46C.glVertexAttribPointer(posLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (norLoc != -1) {
            GL46C.glEnableVertexAttribArray(norLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.normalBufferObject);
            GL46C.glVertexAttribPointer(norLoc, 3, GL46C.GL_FLOAT, false, 0, 0);
        }
        if (uvLoc != -1) {
            GL46C.glEnableVertexAttribArray(uvLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.texcoordBufferObject);
            GL46C.glVertexAttribPointer(uvLoc, 2, GL46C.GL_FLOAT, false, 0, 0);
        }

        OpenGlModelInstance.toonShaderCpu.setProjectionMatrix(target.projMatBuff);
        OpenGlModelInstance.toonShaderCpu.setModelViewMatrix(target.modelViewMatBuff);
        ToonRenderHelper.setupToonUniforms(OpenGlModelInstance.toonShaderCpu, lightIntensity, target.light0Direction);

        drawSubMeshes(target, minecraft);

        if (posLoc != -1) GL46C.glDisableVertexAttribArray(posLoc);
        if (norLoc != -1) GL46C.glDisableVertexAttribArray(norLoc);
        if (uvLoc != -1) GL46C.glDisableVertexAttribArray(uvLoc);
    }

    private static float effectiveOutlineAlpha(OpenGlModelInstance target, int materialId, float baseAlpha) {
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

