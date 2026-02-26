package com.shiroha.mmdskin.renderer.shader;

import com.shiroha.mmdskin.util.AssetsUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL46C;

import java.nio.FloatBuffer;

/**
 * Toon 着色器抽象基类
 * 
 * 提供 Toon 渲染的公共逻辑（片段着色器、uniform 管理），
 * 子类负责提供各自的顶点着色器（蒙皮方式不同）。
 */
public abstract class ToonShaderBase {
    protected static final Logger logger = LogManager.getLogger();
    
    // 着色器程序
    protected int mainProgram = 0;
    protected int outlineProgram = 0;
    protected boolean initialized = false;
    
    // ==================== 共享的片段着色器逻辑 ====================
    
    protected static final String MAIN_FRAGMENT_SHADER_BODY =
            AssetsUtil.getAssetsAsString("shader/toon_main_body.frag.glsl");
    
    protected static final String OUTLINE_FRAGMENT_SHADER_BODY =
            AssetsUtil.getAssetsAsString("shader/toon_outline_body.frag.glsl");
    
    // ==================== 主着色器 Uniform locations ====================
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
    
    // ==================== 描边着色器 Uniform locations ====================
    protected int outlineProjMatLocation = -1;
    protected int outlineModelViewMatLocation = -1;
    protected int outlineWidthLocation = -1;
    protected int outlineColorLocation = -1;
    
    // ==================== Attribute locations ====================
    protected int positionLocation = -1;
    protected int normalLocation = -1;
    protected int uv0Location = -1;
    protected int outlinePositionLocation = -1;
    protected int outlineNormalLocation = -1;
    
    // ==================== 抽象方法（子类实现） ====================
    
    /** 获取主着色器的顶点着色器源码 */
    protected abstract String getMainVertexShader();
    
    /**
     * 获取描边着色器的顶点着色器源码
     */
    protected abstract String getOutlineVertexShader();
    
    /**
     * 子类初始化后的额外处理（如获取特有的 uniform/attribute locations）
     */
    protected abstract void onInitialized();
    
    /**
     * 获取着色器名称（用于日志）
     */
    protected abstract String getShaderName();
    
    // ==================== 公共初始化逻辑 ====================
    
    public boolean init() {
        if (initialized) return true;
        
        try {
            // 编译主着色器
            mainProgram = compileProgram(getMainVertexShader(), MAIN_FRAGMENT_SHADER_BODY, 
                                        getShaderName() + "主着色器");
            if (mainProgram == 0) return false;
            
            // 编译描边着色器
            outlineProgram = compileProgram(getOutlineVertexShader(), OUTLINE_FRAGMENT_SHADER_BODY, 
                                           getShaderName() + "描边着色器");
            if (outlineProgram == 0) {
                GL46C.glDeleteProgram(mainProgram);
                mainProgram = 0;
                return false;
            }
            
            // 获取公共 uniform 位置
            initCommonUniforms();
            
            // 获取公共 attribute 位置
            initCommonAttributes();
            
            // 子类额外初始化
            onInitialized();
            
            initialized = true;
            logger.info("{} 初始化成功", getShaderName());
            return true;
            
        } catch (Exception e) {
            logger.error("{} 初始化异常", getShaderName(), e);
            return false;
        }
    }
    
    private void initCommonUniforms() {
        // 主着色器 uniform
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
        
        // 描边着色器 uniform
        outlineProjMatLocation = GL46C.glGetUniformLocation(outlineProgram, "ProjMat");
        outlineModelViewMatLocation = GL46C.glGetUniformLocation(outlineProgram, "ModelViewMat");
        outlineWidthLocation = GL46C.glGetUniformLocation(outlineProgram, "OutlineWidth");
        outlineColorLocation = GL46C.glGetUniformLocation(outlineProgram, "OutlineColor");
    }
    
    private void initCommonAttributes() {
        // 主着色器 attribute
        positionLocation = GL46C.glGetAttribLocation(mainProgram, "Position");
        normalLocation = GL46C.glGetAttribLocation(mainProgram, "Normal");
        uv0Location = GL46C.glGetAttribLocation(mainProgram, "UV0");
        
        // 描边着色器 attribute
        outlinePositionLocation = GL46C.glGetAttribLocation(outlineProgram, "Position");
        outlineNormalLocation = GL46C.glGetAttribLocation(outlineProgram, "Normal");
    }
    
    // ==================== 着色器编译（委托 ShaderCompiler） ====================
    
    protected int compileProgram(String vertexSource, String fragmentSource, String name) {
        return ShaderCompiler.compileRenderProgram(vertexSource, fragmentSource, name);
    }
    
    // ==================== 公共 Uniform 设置方法 ====================
    
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
    
    // ==================== 描边 Uniform 设置方法 ====================
    
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
    
    // ==================== Getters ====================
    
    public int getMainProgram() { return mainProgram; }
    public int getOutlineProgram() { return outlineProgram; }
    
    public int getPositionLocation() { return positionLocation; }
    public int getNormalLocation() { return normalLocation; }
    public int getUv0Location() { return uv0Location; }
    
    public int getOutlinePositionLocation() { return outlinePositionLocation; }
    public int getOutlineNormalLocation() { return outlineNormalLocation; }
    
    public boolean isInitialized() { return initialized; }
    
    // ==================== 资源释放 ====================
    
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
