package com.shiroha.mmdskin.renderer.pipeline.shader;

import com.shiroha.mmdskin.util.AssetsUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL46C;

import java.nio.FloatBuffer;

/**
 * Toon 着色器抽象基类。
 */
public abstract class ToonShaderBase {
    protected static final Logger logger = LogManager.getLogger();

    protected int mainProgram = 0;
    protected int outlineProgram = 0;
    protected boolean initialized = false;

    protected static final String MAIN_FRAGMENT_SHADER_BODY =
            AssetsUtil.getAssetsAsString("shader/toon_main_body.frag.glsl");

    protected static final String OUTLINE_FRAGMENT_SHADER_BODY =
            AssetsUtil.getAssetsAsString("shader/toon_outline_body.frag.glsl");

    protected int projMatLocation = -1;
    protected int modelViewMatLocation = -1;
    protected int sampler0Location = -1;
    protected int lightIntensityLocation = -1;
    protected int toonLevelsLocation = -1;
    protected int rimPowerLocation = -1;
    protected int rimIntensityLocation = -1;
    protected int shadowColorLocation = -1;
    protected int specularPowerLocation = -1;
    protected int specularIntensityLocation = -1;

    protected int outlineProjMatLocation = -1;
    protected int outlineModelViewMatLocation = -1;
    protected int outlineWidthLocation = -1;
    protected int outlineColorLocation = -1;

    protected int positionLocation = -1;
    protected int normalLocation = -1;
    protected int uv0Location = -1;
    protected int outlinePositionLocation = -1;
    protected int outlineNormalLocation = -1;

    protected abstract String getMainVertexShader();

    protected abstract String getOutlineVertexShader();

    protected abstract void onInitialized();

    protected abstract String getShaderName();

    public boolean init() {
        if (initialized) return true;

        try {

            mainProgram = compileProgram(getMainVertexShader(), MAIN_FRAGMENT_SHADER_BODY,
                                        getShaderName() + "主着色器");
            if (mainProgram == 0) return false;

            outlineProgram = compileProgram(getOutlineVertexShader(), MAIN_FRAGMENT_SHADER_BODY,
                                           getShaderName() + "描边着色器");
            if (outlineProgram == 0) {
                GL46C.glDeleteProgram(mainProgram);
                mainProgram = 0;
                return false;
            }

            initCommonUniforms();

            initCommonAttributes();

            onInitialized();

            initialized = true;
            return true;

        } catch (Exception e) {
            logger.error("{} 初始化异常", getShaderName(), e);
            return false;
        }
    }

    private void initCommonUniforms() {

        projMatLocation = GL46C.glGetUniformLocation(mainProgram, "ProjMat");
        modelViewMatLocation = GL46C.glGetUniformLocation(mainProgram, "ModelViewMat");
        sampler0Location = GL46C.glGetUniformLocation(mainProgram, "Sampler0");
        lightIntensityLocation = GL46C.glGetUniformLocation(mainProgram, "LightIntensity");
        toonLevelsLocation = GL46C.glGetUniformLocation(mainProgram, "ToonLevels");
        rimPowerLocation = GL46C.glGetUniformLocation(mainProgram, "RimPower");
        rimIntensityLocation = GL46C.glGetUniformLocation(mainProgram, "RimIntensity");
        shadowColorLocation = GL46C.glGetUniformLocation(mainProgram, "ShadowColor");
        specularPowerLocation = GL46C.glGetUniformLocation(mainProgram, "SpecularPower");
        specularIntensityLocation = GL46C.glGetUniformLocation(mainProgram, "SpecularIntensity");

        outlineProjMatLocation = GL46C.glGetUniformLocation(outlineProgram, "ProjMat");
        outlineModelViewMatLocation = GL46C.glGetUniformLocation(outlineProgram, "ModelViewMat");
        outlineWidthLocation = GL46C.glGetUniformLocation(outlineProgram, "OutlineWidth");
        outlineColorLocation = GL46C.glGetUniformLocation(outlineProgram, "OutlineColor");
    }

    private void initCommonAttributes() {

        positionLocation = GL46C.glGetAttribLocation(mainProgram, "Position");
        normalLocation = GL46C.glGetAttribLocation(mainProgram, "Normal");
        uv0Location = GL46C.glGetAttribLocation(mainProgram, "UV0");

        outlinePositionLocation = GL46C.glGetAttribLocation(outlineProgram, "Position");
        outlineNormalLocation = GL46C.glGetAttribLocation(outlineProgram, "Normal");
    }

    protected int compileProgram(String vertexSource, String fragmentSource, String name) {
        return ShaderCompiler.compileRenderProgram(vertexSource, fragmentSource, name);
    }

    public void useMain() {
        if (mainProgram > 0) {
            GL46C.glUseProgram(mainProgram);
        }
    }

    public void useOutline() {
        if (outlineProgram > 0) {
            GL46C.glUseProgram(outlineProgram);
        }
    }

    public void setProjectionMatrix(FloatBuffer matrix) {
        if (projMatLocation >= 0) {
            matrix.position(0);
            GL46C.glUniformMatrix4fv(projMatLocation, false, matrix);
        }
    }

    public void setModelViewMatrix(FloatBuffer matrix) {
        if (modelViewMatLocation >= 0) {
            matrix.position(0);
            GL46C.glUniformMatrix4fv(modelViewMatLocation, false, matrix);
        }
    }

    public void setSampler0(int textureUnit) {
        if (sampler0Location >= 0) {
            GL46C.glUniform1i(sampler0Location, textureUnit);
        }
    }

    public void setLightIntensity(float intensity) {
        if (lightIntensityLocation >= 0) {
            GL46C.glUniform1f(lightIntensityLocation, intensity);
        }
    }

    public void setToonLevels(int levels) {
        if (toonLevelsLocation >= 0) {
            GL46C.glUniform1i(toonLevelsLocation, Math.max(2, Math.min(5, levels)));
        }
    }

    public void setRimLight(float power, float intensity) {
        if (rimPowerLocation >= 0) {
            GL46C.glUniform1f(rimPowerLocation, power);
        }
        if (rimIntensityLocation >= 0) {
            GL46C.glUniform1f(rimIntensityLocation, intensity);
        }
    }

    public void setShadowColor(float r, float g, float b) {
        if (shadowColorLocation >= 0) {
            GL46C.glUniform3f(shadowColorLocation, r, g, b);
        }
    }

    public void setSpecular(float power, float intensity) {
        if (specularPowerLocation >= 0) {
            GL46C.glUniform1f(specularPowerLocation, power);
        }
        if (specularIntensityLocation >= 0) {
            GL46C.glUniform1f(specularIntensityLocation, intensity);
        }
    }

    public void setOutlineProjectionMatrix(FloatBuffer matrix) {
        if (outlineProjMatLocation >= 0) {
            matrix.position(0);
            GL46C.glUniformMatrix4fv(outlineProjMatLocation, false, matrix);
        }
    }

    public void setOutlineModelViewMatrix(FloatBuffer matrix) {
        if (outlineModelViewMatLocation >= 0) {
            matrix.position(0);
            GL46C.glUniformMatrix4fv(outlineModelViewMatLocation, false, matrix);
        }
    }

    public void setOutlineWidth(float width) {
        if (outlineWidthLocation >= 0) {
            GL46C.glUniform1f(outlineWidthLocation, width);
        }
    }

    public void setOutlineColor(float r, float g, float b) {
        if (outlineColorLocation >= 0) {
            GL46C.glUniform3f(outlineColorLocation, r, g, b);
        }
    }

    public int getMainProgram() { return mainProgram; }
    public int getOutlineProgram() { return outlineProgram; }

    public int getPositionLocation() { return positionLocation; }
    public int getNormalLocation() { return normalLocation; }
    public int getUv0Location() { return uv0Location; }

    public int getOutlinePositionLocation() { return outlinePositionLocation; }
    public int getOutlineNormalLocation() { return outlineNormalLocation; }

    public boolean isInitialized() { return initialized; }

    public void cleanup() {
        if (mainProgram > 0) {
            GL46C.glDeleteProgram(mainProgram);
            mainProgram = 0;
        }
        if (outlineProgram > 0) {
            GL46C.glDeleteProgram(outlineProgram);
            outlineProgram = 0;
        }
        initialized = false;
    }
}
