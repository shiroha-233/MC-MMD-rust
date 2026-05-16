/* 文件职责：执行 CPU/OpenGL 模型的 Toon/普通渲染流程。 */
package com.shiroha.mmdskin.renderer.runtime.model.opengl;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.renderer.compat.IrisCompat;
import com.shiroha.mmdskin.renderer.compat.RenderSystemCompat;
import com.shiroha.mmdskin.renderer.performance.RenderPerformanceProfiler;
import com.shiroha.mmdskin.renderer.pipeline.shader.ToonShaderCpu;
import com.shiroha.mmdskin.renderer.pipeline.shader.ToonRenderHelper;
import com.shiroha.mmdskin.renderer.runtime.model.AbstractMMDModel;
import com.shiroha.mmdskin.renderer.runtime.model.helper.LightingHelper;
import com.shiroha.mmdskin.renderer.runtime.model.shared.MMDMaterial;
import com.shiroha.mmdskin.renderer.runtime.model.shared.SubMeshDrawHelper;
import com.mojang.blaze3d.opengl.GlTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

final class MMDModelOpenGLRenderer {
    private static final Logger logger = LogManager.getLogger();

    private MMDModelOpenGLRenderer() {
    }

    static void render(MMDModelOpenGL target, Entity entityIn, float entityYaw, float entityPitch,
                       Vector3f entityTrans, PoseStack deliverStack, int packedLight) {
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

        long materialMorphTimer = RenderPerformanceProfiler.get().startTimer();
        target.loadMaterialMorphResults();
        RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_MATERIAL_MORPH_FETCH, materialMorphTimer);

        long subMeshTimer = RenderPerformanceProfiler.get().startTimer();
        target.subMeshDataBuf.clear();
        nativeFunc.BatchGetSubMeshData(modelHandle, target.subMeshDataBuf);
        RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_SUB_MESH_FETCH, subMeshTimer);

        boolean useToon = initializeToonShaderIfNeeded();
        if (useToon) {
            renderToon(target, minecraft, light.intensity(), deliverStack);
            return;
        }

        renderStandard(target, minecraft, light, deliverStack);
    }

    private static boolean initializeToonShaderIfNeeded() {
        if (!ConfigManager.isToonRenderingEnabled()) {
            return false;
        }

        if (MMDModelOpenGL.toonShaderCpu == null) {
            synchronized (MMDModelOpenGL.class) {
                if (MMDModelOpenGL.toonShaderCpu == null) {
                    ToonShaderCpu shader = new ToonShaderCpu();
                    if (!shader.init()) {
                        logger.warn("ToonShaderCpu 初始化失败，回退到普通着色");
                        return false;
                    }
                    MMDModelOpenGL.toonShaderCpu = shader;
                }
            }
        }

        return MMDModelOpenGL.toonShaderCpu.isInitialized();
    }

    private static void renderStandard(MMDModelOpenGL target, Minecraft minecraft,
                                       LightingHelper.LightData light, PoseStack deliverStack) {
        boolean irisActive = IrisCompat.isIrisShaderActive();
        float colorFactor = irisActive ? 1.0f : light.intensity();
        RenderSystemCompat.setShaderColor(colorFactor, colorFactor, colorFactor, 1.0f);

        if (MMDModelOpenGL.MMDShaderProgram == 0) {
            RenderSystemCompat.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            return;
        }
        target.shaderProgram = MMDModelOpenGL.MMDShaderProgram;
        GlStateManager._glUseProgram(target.shaderProgram);

        target.updateLocation(target.shaderProgram);

        /* BufferUploader.reset() removed in 1.21.11 */
        GL46C.glBindVertexArray(target.vertexArrayObject);
        GlStateManager._enableBlend();
        GlStateManager._enableDepthTest();
        GL46C.glBlendEquation(GL46C.GL_FUNC_ADD);
        RenderSystemCompat.blendFuncSrcAlphaOneMinusSrcAlpha();

        uploadDynamicBuffers(target, light.blockLight(), light.skyLight(), light.skyDarken(), irisActive);
        uploadMatrixUniforms(target, deliverStack);
        bindStandardAttributes(target);
        bindCustomShaderAttributes(target);
        bindIrisAttributes(target);
        long drawTimer = RenderPerformanceProfiler.get().startTimer();
        try {
            drawSubMeshes(target, minecraft);
        } finally {
            RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_DRAW, drawTimer);
        }
        clearStandardRenderState(target);
    }

    private static void uploadDynamicBuffers(MMDModelOpenGL target, int blockLight, int skyLight,
                                             float skyDarken, boolean irisActive) {
        long currentRevision = target.nativeUpdateRevisionValue();
        if (target.lastPositionRevision != currentRevision) {
            var nativeFunc = target.nativeFunc();
            long modelHandle = target.nativeModelHandle();
            int posAndNorSize = target.vertexCount * 12;
            long posData = nativeFunc.GetPoss(modelHandle);
            nativeFunc.CopyDataToByteBuffer(target.posBuffer, posData, posAndNorSize);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.vertexBufferObject);
            GL46C.glBufferSubData(GL46C.GL_ARRAY_BUFFER, 0, target.posBuffer);

            long normalData = nativeFunc.GetNormals(modelHandle);
            nativeFunc.CopyDataToByteBuffer(target.norBuffer, normalData, posAndNorSize);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.normalBufferObject);
            GL46C.glBufferSubData(GL46C.GL_ARRAY_BUFFER, 0, target.norBuffer);

            if (target.hasUvMorph) {
                int uv0Size = target.vertexCount * 8;
                long uv0Data = nativeFunc.GetUVs(modelHandle);
                nativeFunc.CopyDataToByteBuffer(target.uv0Buffer, uv0Data, uv0Size);
                GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.texcoordBufferObject);
                GL46C.glBufferSubData(GL46C.GL_ARRAY_BUFFER, 0, target.uv0Buffer);
            }

            target.lastPositionRevision = currentRevision;
        }

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
    }

    private static void uploadMatrixUniforms(MMDModelOpenGL target, PoseStack deliverStack) {
        target.modelViewMatBuff.clear();
        target.projMatBuff.clear();
        AbstractMMDModel.computeModelViewMatrix(deliverStack).get(target.modelViewMatBuff);
        RenderSystemCompat.getProjectionMatrix().get(target.projMatBuff);

        GL46C.glUniformMatrix4fv(target.modelViewLocation, false, target.modelViewMatBuff);
        GL46C.glUniformMatrix4fv(target.projMatLocation, false, target.projMatBuff);

        if (target.light0Location != -1) {
            target.light0Buff.clear();
            target.light0Buff.put(target.light0Direction.x);
            target.light0Buff.put(target.light0Direction.y);
            target.light0Buff.put(target.light0Direction.z);
            target.light0Buff.flip();
            GL46C.glUniform3fv(target.light0Location, target.light0Buff);
        }
        if (target.light1Location != -1) {
            target.light1Buff.clear();
            target.light1Buff.put(target.light1Direction.x);
            target.light1Buff.put(target.light1Direction.y);
            target.light1Buff.put(target.light1Direction.z);
            target.light1Buff.flip();
            GL46C.glUniform3fv(target.light1Location, target.light1Buff);
        }
        if (target.sampler0Location != -1) {
            GL46C.glUniform1i(target.sampler0Location, 0);
        }
        if (target.sampler1Location != -1) {
            GlStateManager._activeTexture(GL46C.GL_TEXTURE1);
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, target.lightMapMaterial.tex);
            GL46C.glUniform1i(target.sampler1Location, 1);
        }
        if (target.sampler2Location != -1) {
            GlStateManager._activeTexture(GL46C.GL_TEXTURE2);
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, target.lightMapMaterial.tex);
            GL46C.glUniform1i(target.sampler2Location, 2);
        }
    }

    private static void bindStandardAttributes(MMDModelOpenGL target) {
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

    private static void bindCustomShaderAttributes(MMDModelOpenGL target) {
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
            GL46C.glUniformMatrix4fv(target.K_projMatLocation, false, target.projMatBuff);
        }
        if (target.K_modelViewLocation != -1) {
            target.modelViewMatBuff.position(0);
            GL46C.glUniformMatrix4fv(target.K_modelViewLocation, false, target.modelViewMatBuff);
        }
        if (target.K_sampler0Location != -1) {
            GL46C.glUniform1i(target.K_sampler0Location, 0);
        }
        if (target.K_sampler2Location != -1) {
            GlStateManager._activeTexture(GL46C.GL_TEXTURE2);
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, target.lightMapMaterial.tex);
            GL46C.glUniform1i(target.K_sampler2Location, 2);
        }
        if (target.KAIMyLocationV != -1) {
            GL46C.glUniform1i(target.KAIMyLocationV, 1);
        }
        if (target.KAIMyLocationF != -1) {
            GL46C.glUniform1i(target.KAIMyLocationF, 1);
        }
    }

    private static void bindIrisAttributes(MMDModelOpenGL target) {
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

    private static void drawSubMeshes(MMDModelOpenGL target, Minecraft minecraft) {
        int missingTextureId = resolveMissingTextureId(minecraft);
        SubMeshDrawHelper.draw(
                target.subMeshDataBuf,
                target.subMeshCount,
                target.indexElementSize,
                target.indexType,
                materialId -> target.mats[materialId].tex == 0 ? missingTextureId : target.mats[materialId].tex,
                target::effectiveMaterialAlpha);
    }

    private static int resolveMissingTextureId(Minecraft minecraft) {
        try {
            var abstractTexture = minecraft.getTextureManager().getTexture(MissingTextureAtlasSprite.getLocation());
            var gpuTexture = abstractTexture.getTexture();
            if (gpuTexture instanceof GlTexture glTexture) {
                return glTexture.glId();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static void clearStandardRenderState(MMDModelOpenGL target) {
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
        GlStateManager._activeTexture(GL46C.GL_TEXTURE0);

        /* BufferUploader.reset() removed in 1.21.11 */
        RenderSystemCompat.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void renderToon(MMDModelOpenGL target, Minecraft minecraft, float lightIntensity, PoseStack deliverStack) {
        /* BufferUploader.reset() removed in 1.21.11 */
        GL46C.glBindVertexArray(target.vertexArrayObject);
        GlStateManager._enableBlend();
        GlStateManager._enableDepthTest();
        GL46C.glBlendEquation(GL46C.GL_FUNC_ADD);
        RenderSystemCompat.blendFuncSrcAlphaOneMinusSrcAlpha();

        var nativeFunc = target.nativeFunc();
        long modelHandle = target.nativeModelHandle();
        int posAndNorSize = target.vertexCount * 12;

        long currentRevision = target.nativeUpdateRevisionValue();
        if (target.lastPositionRevision != currentRevision) {
            long posData = nativeFunc.GetPoss(modelHandle);
            nativeFunc.CopyDataToByteBuffer(target.posBuffer, posData, posAndNorSize);
            long normalData = nativeFunc.GetNormals(modelHandle);
            nativeFunc.CopyDataToByteBuffer(target.norBuffer, normalData, posAndNorSize);

            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.vertexBufferObject);
            GL46C.glBufferSubData(GL46C.GL_ARRAY_BUFFER, 0, target.posBuffer);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.normalBufferObject);
            GL46C.glBufferSubData(GL46C.GL_ARRAY_BUFFER, 0, target.norBuffer);
            if (target.hasUvMorph) {
                int uv0Size = target.vertexCount * 8;
                long uv0Data = nativeFunc.GetUVs(modelHandle);
                nativeFunc.CopyDataToByteBuffer(target.uv0Buffer, uv0Data, uv0Size);
                GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.texcoordBufferObject);
                GL46C.glBufferSubData(GL46C.GL_ARRAY_BUFFER, 0, target.uv0Buffer);
            }

            target.lastPositionRevision = currentRevision;
        }

        target.modelViewMatBuff.clear();
        target.projMatBuff.clear();
        AbstractMMDModel.computeModelViewMatrix(deliverStack).get(target.modelViewMatBuff);
        RenderSystemCompat.getProjectionMatrix().get(target.projMatBuff);
        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, target.indexBufferObject);

        long drawTimer = RenderPerformanceProfiler.get().startTimer();
        try {
            renderToonMainPass(target, minecraft, lightIntensity);
            if (MMDModelOpenGL.toonConfig.isOutlineEnabled()) {
                renderOutlinePass(target);
            }
        } finally {
            RenderPerformanceProfiler.get().endTimer(RenderPerformanceProfiler.SECTION_DRAW, drawTimer);
        }

        GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, 0);
        GL46C.glBindBuffer(GL46C.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL46C.glBindVertexArray(0);
        GL46C.glUseProgram(0);
        GlStateManager._activeTexture(GL46C.GL_TEXTURE0);
        /* BufferUploader.reset() removed in 1.21.11 */
    }

    private static void renderOutlinePass(MMDModelOpenGL target) {
        MMDModelOpenGL.toonShaderCpu.useOutline();
        int posLoc = MMDModelOpenGL.toonShaderCpu.getOutlinePositionLocation();
        int norLoc = MMDModelOpenGL.toonShaderCpu.getOutlineNormalLocation();
        int outlineUvLoc = MMDModelOpenGL.toonShaderCpu.getOutlineUv0Location();
        int missingTextureId = 0;

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

        MMDModelOpenGL.toonShaderCpu.setOutlineProjectionMatrix(target.projMatBuff);
        MMDModelOpenGL.toonShaderCpu.setOutlineModelViewMatrix(target.modelViewMatBuff);
        if (outlineUvLoc != -1) {
            GL46C.glEnableVertexAttribArray(outlineUvLoc);
            GL46C.glBindBuffer(GL46C.GL_ARRAY_BUFFER, target.texcoordBufferObject);
            GL46C.glVertexAttribPointer(outlineUvLoc, 2, GL46C.GL_FLOAT, false, 0, 0);
        }

        ToonRenderHelper.setupOutlineUniforms(MMDModelOpenGL.toonShaderCpu);

        GlStateManager._depthMask(false);
        GL46C.glCullFace(GL46C.GL_FRONT);
        GlStateManager._enableCull();
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
        GlStateManager._depthMask(true);

        if (posLoc != -1) GL46C.glDisableVertexAttribArray(posLoc);
        if (norLoc != -1) GL46C.glDisableVertexAttribArray(norLoc);
        if (outlineUvLoc != -1) GL46C.glDisableVertexAttribArray(outlineUvLoc);
    }

    private static void renderToonMainPass(MMDModelOpenGL target, Minecraft minecraft, float lightIntensity) {
        MMDModelOpenGL.toonShaderCpu.useMain();
        int posLoc = MMDModelOpenGL.toonShaderCpu.getPositionLocation();
        int norLoc = MMDModelOpenGL.toonShaderCpu.getNormalLocation();
        int uvLoc = MMDModelOpenGL.toonShaderCpu.getUv0Location();

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

        MMDModelOpenGL.toonShaderCpu.setProjectionMatrix(target.projMatBuff);
        MMDModelOpenGL.toonShaderCpu.setModelViewMatrix(target.modelViewMatBuff);
        ToonRenderHelper.setupToonUniforms(MMDModelOpenGL.toonShaderCpu, lightIntensity, target.light0Direction);

        drawSubMeshes(target, minecraft);

        if (posLoc != -1) GL46C.glDisableVertexAttribArray(posLoc);
        if (norLoc != -1) GL46C.glDisableVertexAttribArray(norLoc);
        if (uvLoc != -1) GL46C.glDisableVertexAttribArray(uvLoc);
    }
}
