package com.shiroha.mmdskin.renderer.pipeline.shader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL46C;

/**
 * 着色器编译/链接工具类。
 */
public final class ShaderCompiler {

    private static final Logger logger = LogManager.getLogger();

    private ShaderCompiler() {}

    public static int compileRenderProgram(String vertexSource, String fragmentSource, String name) {
        int vertexShader = compileShader(GL46C.GL_VERTEX_SHADER, vertexSource, name + " 顶点");
        if (vertexShader == 0) return 0;

        int fragShader = compileShader(GL46C.GL_FRAGMENT_SHADER, fragmentSource, name + " 片段");
        if (fragShader == 0) {
            GL46C.glDeleteShader(vertexShader);
            return 0;
        }

        int program = linkProgram(new int[]{vertexShader, fragShader}, name);
        GL46C.glDeleteShader(vertexShader);
        GL46C.glDeleteShader(fragShader);
        return program;
    }

    public static int compileComputeProgram(String source, String name) {
        int shader = compileShader(GL43C.GL_COMPUTE_SHADER, source, name);
        if (shader == 0) return 0;

        int program = linkProgram(new int[]{shader}, name);
        GL43C.glDeleteShader(shader);
        return program;
    }

    private static int compileShader(int type, String source, String name) {
        int shader = GL46C.glCreateShader(type);
        GL46C.glShaderSource(shader, source);
        GL46C.glCompileShader(shader);

        if (GL46C.glGetShaderi(shader, GL46C.GL_COMPILE_STATUS) == GL46C.GL_FALSE) {
            String log = GL46C.glGetShaderInfoLog(shader, 8192).trim();
            logger.error("{} 着色器编译失败: {}", name, log);
            GL46C.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static int linkProgram(int[] shaders, String name) {
        int program = GL46C.glCreateProgram();
        for (int s : shaders) {
            GL46C.glAttachShader(program, s);
        }
        GL46C.glLinkProgram(program);

        if (GL46C.glGetProgrami(program, GL46C.GL_LINK_STATUS) == GL46C.GL_FALSE) {
            String log = GL46C.glGetProgramInfoLog(program, 8192).trim();
            logger.error("{} 着色器链接失败: {}", name, log);
            GL46C.glDeleteProgram(program);
            return 0;
        }
        return program;
    }
}
