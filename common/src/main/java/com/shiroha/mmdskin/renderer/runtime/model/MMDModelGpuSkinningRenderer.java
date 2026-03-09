package com.shiroha.mmdskin.renderer.runtime.model;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.renderer.compat.IrisCompat;
import com.shiroha.mmdskin.renderer.pipeline.shader.SkinningComputeShader;
import com.shiroha.mmdskin.renderer.pipeline.shader.ToonShaderCpu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL46C;

final class MMDModelGpuSkinningRenderer {
    private MMDModelGpuSkinningRenderer() {
    }

    static void render(MMDModelGpuSkinning target, Entity entityIn, float entityYaw, float entityPitch,
                       Vector3f entityTrans, PoseStack deliverStack) {
        Minecraft minecraft = Minecraft.getInstance();
        LightingHelper.LightData light = LightingHelper.sampleLight(entityIn, minecraft);

        target.light0Direction.set(1.0f, 0.75f, 0.0f).normalize();
        target.light1Direction.set(-1.0f, 0.75f, 0.0f).normalize();
        float yawRad = entityYaw * ((float) Math.PI / 180F);
        target.light0Direction.rotate(target.tempQuat.identity().rotateY(yawRad));
        target.light1Direction.rotate(target.tempQuat.identity().rotateY(yawRad));

        deliverStack.mulPose(target.tempQuat.identity().rotateY(-yawRad));
        deliverStack.mulPose(target.tempQuat.identity().rotateX(entityPitch * ((float) Math.PI / 180F)));
        deliverStack.translate(entityTrans.x, entityTrans.y, entityTrans.z);
        float baseScale = target.getModelScale();
        deliverStack.scale(baseScale, baseScale, baseScale);

        MMDModelGpuSkinningUploader.uploadBoneMatrices(target);
        if (target.vertexMorphCount > 0) {
            MMDModelGpuSkinningUploader.uploadMorphData(target);
        }
        if (target.uvMorphCount > 0) {
            MMDModelGpuSkinningUploader.uploadUvMorphData(target);
        }
        if (target.materialMorphResultCount > 0) {
            target.fetchMaterialMorphResults();
        }

        MMDModelGpuSkinning.computeShader.dispatch(new SkinningComputeShader.DispatchParams(
                target.positionBufferObject, target.normalBufferObject,
                target.boneIndicesBufferObject, target.boneWeightsBufferObject, target.uv0BufferObject,
                target.skinnedPositionsBuffer, target.skinnedNormalsBuffer, target.skinnedUvBuffer,
                target.boneMatrixSSBO,
                target.morphOffsetsSSBO, target.morphWeightsSSBO, target.vertexMorphCount,
                target.uvMorphOffsetsSSBO, target.uvMorphWeightsSSBO, target.uvMorphCount,
                target.vertexCount
        ));

        target.subMeshDataBuf.clear();
        target.nf.BatchGetSubMeshData(target.model, target.subMeshDataBuf);

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
            MMDModelGpuSkinning.toonShaderCpu = new ToonShaderCpu();
            if (!MMDModelGpuSkinning.toonShaderCpu.init()) {
                AbstractMMDModel.logger.warn("ToonShaderCpu 初始化失败，回退到普通着色");
                MMDModelGpuSkinning.toonShaderCpu = null;
                return false;
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
            AbstractMMDModel.logger.error("[GPU蒙皮] RenderSystem.getShader() 返回 null，跳过渲染");
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
        target.uv2Buffer.clear();
        for (int i = 0; i < target.vertexCount; i++) {
            target.uv2Buffer.putInt(blockBrightness);
            target.uv2Buffer.putInt(skyBrightness);
        }
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

        drawAllSubMeshes(target, minecraft);
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
            MMDModelGpuSkinning.toonShaderCpu.setOutlineWidth(MMDModelGpuSkinning.toonConfig.getOutlineWidth());
            MMDModelGpuSkinning.toonShaderCpu.setOutlineColor(
                    MMDModelGpuSkinning.toonConfig.getOutlineColorR(),
                    MMDModelGpuSkinning.toonConfig.getOutlineColorG(),
                    MMDModelGpuSkinning.toonConfig.getOutlineColorB()
            );

            GL46C.glCullFace(GL46C.GL_FRONT);
            RenderSystem.enableCull();
            for (int i = 0; i < target.subMeshCount; ++i) {
                int base = i * 20;
                int materialID = target.subMeshDataBuf.getInt(base);
                int beginIndex = target.subMeshDataBuf.getInt(base + 4);
                int count = target.subMeshDataBuf.getInt(base + 8);
                float edgeAlpha = target.subMeshDataBuf.getFloat(base + 12);
                boolean visible = target.subMeshDataBuf.get(base + 16) != 0;

                if (!visible) continue;
                if (target.getEffectiveMaterialAlpha(materialID, edgeAlpha) < 0.001f) continue;

                long startPos = (long) beginIndex * target.indexElementSize;
                GL46C.glDrawElements(GL46C.GL_TRIANGLES, count, target.indexType, startPos);
            }
            GL46C.glCullFace(GL46C.GL_BACK);
            if (posLoc != -1) GL46C.glDisableVertexAttribArray(posLoc);
            if (norLoc != -1) GL46C.glDisableVertexAttribArray(norLoc);
        }

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
        MMDModelGpuSkinning.toonShaderCpu.setSampler0(0);
        MMDModelGpuSkinning.toonShaderCpu.setLightIntensity(lightIntensity);
        MMDModelGpuSkinning.toonShaderCpu.setToonLevels(MMDModelGpuSkinning.toonConfig.getToonLevels());
        MMDModelGpuSkinning.toonShaderCpu.setRimLight(
                MMDModelGpuSkinning.toonConfig.getRimPower(),
                MMDModelGpuSkinning.toonConfig.getRimIntensity()
        );
        MMDModelGpuSkinning.toonShaderCpu.setShadowColor(
                MMDModelGpuSkinning.toonConfig.getShadowColorR(),
                MMDModelGpuSkinning.toonConfig.getShadowColorG(),
                MMDModelGpuSkinning.toonConfig.getShadowColorB()
        );
        MMDModelGpuSkinning.toonShaderCpu.setSpecular(
                MMDModelGpuSkinning.toonConfig.getSpecularPower(),
                MMDModelGpuSkinning.toonConfig.getSpecularIntensity()
        );

        drawAllSubMeshes(target, minecraft);

        if (toonPosLoc != -1) GL46C.glDisableVertexAttribArray(toonPosLoc);
        if (toonNorLoc != -1) GL46C.glDisableVertexAttribArray(toonNorLoc);
        if (uvLoc != -1) GL46C.glDisableVertexAttribArray(uvLoc);
        GL46C.glUseProgram(0);
    }

    private static void drawAllSubMeshes(MMDModelGpuSkinning target, Minecraft minecraft) {
        RenderSystem.activeTexture(GL46C.GL_TEXTURE0);

        for (int i = 0; i < target.subMeshCount; ++i) {
            int base = i * 20;
            int materialID = target.subMeshDataBuf.getInt(base);
            int beginIndex = target.subMeshDataBuf.getInt(base + 4);
            int vertCount = target.subMeshDataBuf.getInt(base + 8);
            float alpha = target.subMeshDataBuf.getFloat(base + 12);
            boolean visible = target.subMeshDataBuf.get(base + 16) != 0;
            boolean bothFace = target.subMeshDataBuf.get(base + 17) != 0;

            if (!visible) continue;
            if (target.getEffectiveMaterialAlpha(materialID, alpha) < 0.001f) continue;

            if (bothFace) {
                RenderSystem.disableCull();
            } else {
                RenderSystem.enableCull();
            }

            int texId = target.mats[materialID].tex == 0
                    ? minecraft.getTextureManager().getTexture(TextureManager.INTENTIONAL_MISSING_TEXTURE).getId()
                    : target.mats[materialID].tex;
            RenderSystem.setShaderTexture(0, texId);
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, texId);

            long startPos = (long) beginIndex * target.indexElementSize;
            GL46C.glDrawElements(GL46C.GL_TRIANGLES, vertCount, target.indexType, startPos);
        }
    }
}
