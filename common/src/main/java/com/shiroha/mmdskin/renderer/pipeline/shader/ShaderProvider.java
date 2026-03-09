package com.shiroha.mmdskin.renderer.pipeline.shader;

import com.shiroha.mmdskin.config.PathConstants;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 外部 MMD 着色器加载器。
 */
public class ShaderProvider {
    private static int program = 0;
    private static final Logger logger = LogManager.getLogger();

    public static void Init() {
        if (program > 0) return;

        File shaderDir = PathConstants.getShaderDir();
        File vertexFile = new File(shaderDir, "MMDShader.vsh");
        File fragFile = new File(shaderDir, "MMDShader.fsh");

        try {
            String vertexSource = Files.readString(vertexFile.toPath());
            String fragSource = Files.readString(fragFile.toPath());

            program = ShaderCompiler.compileRenderProgram(vertexSource, fragSource, "MMDShader");
            if (program <= 0) {
                logger.error("MMD Shader 编译/链接失败");
            }
        } catch (IOException e) {
            logger.error("MMD Shader 文件读取失败: {}", e.getMessage());
            program = 0;
        }
    }

    public static boolean isReady() {
        return program > 0;
    }

    public static int getProgram() {
        if (program <= 0)
            throw new RuntimeException("MMD Shader 未初始化或初始化失败");
        return program;
    }
}
