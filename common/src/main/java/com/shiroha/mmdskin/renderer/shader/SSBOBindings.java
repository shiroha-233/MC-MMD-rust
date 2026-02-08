package com.shiroha.mmdskin.renderer.shader;

import org.lwjgl.opengl.GL46C;

/**
 * SSBO 绑定状态保存/恢复工具
 * 
 * 在执行 Compute Shader 或自定义渲染时，需要保存当前所有 SSBO 绑定状态，
 * 完成后恢复原状，避免破坏光影 mod（如 Eclipse Shaders）的 SSBO 绑定。
 * 
 * 用法：
 *   var bindings = new SSBOBindings();   // 构造时自动保存当前状态
 *   // ... 执行 Compute Shader / 绑定自定义 SSBO ...
 *   bindings.restore();                  // 恢复之前的状态
 * 
 * 感谢 AR 提供此方案。
 */
public class SSBOBindings {
    
    public static final int MAX_BINDINGS = GL46C.glGetInteger(GL46C.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS);
    
    private final int[]  bufferHandles;
    private final long[] bufferBindingOffsets;
    private final long[] bufferBindingSizes;
    
    /**
     * 构造时自动记录当前所有 SSBO 绑定点的状态
     */
    public SSBOBindings() {
        this.bufferHandles        = new int [MAX_BINDINGS];
        this.bufferBindingOffsets  = new long[MAX_BINDINGS];
        this.bufferBindingSizes    = new long[MAX_BINDINGS];
        
        for (var bindingPoint = 0; bindingPoint < MAX_BINDINGS; bindingPoint++) {
            this.bufferHandles       [bindingPoint] = GL46C.glGetIntegeri(GL46C.GL_SHADER_STORAGE_BUFFER_BINDING, bindingPoint);
            this.bufferBindingOffsets [bindingPoint] = GL46C.glGetInteger64i(GL46C.GL_SHADER_STORAGE_BUFFER_START, bindingPoint);
            this.bufferBindingSizes   [bindingPoint] = GL46C.glGetInteger64i(GL46C.GL_SHADER_STORAGE_BUFFER_SIZE, bindingPoint);
        }
    }
    
    /**
     * 恢复之前保存的所有 SSBO 绑定状态
     */
    public void restore() {
        for (var bindingPoint = 0; bindingPoint < MAX_BINDINGS; bindingPoint++) {
            var bufferHandle        = bufferHandles       [bindingPoint];
            var bufferBindingOffset = bufferBindingOffsets [bindingPoint];
            var bufferBindingSize   = bufferBindingSizes   [bindingPoint];
            
            // 跳过已被删除的 buffer handle（光影 mod 可能在管线重建时删除旧 buffer，
            // AMD 驱动对绑定无效 handle 可能触发 EXCEPTION_ACCESS_VIOLATION）
            if (bufferHandle != 0 && !GL46C.glIsBuffer(bufferHandle)) {
                GL46C.glBindBufferBase(
                        GL46C.GL_SHADER_STORAGE_BUFFER,
                        bindingPoint,
                        0
                );
                continue;
            }
            
            if (        bufferBindingOffset == 0
                    &&  bufferBindingSize   == 0
            ) {
                GL46C.glBindBufferBase(
                        GL46C.GL_SHADER_STORAGE_BUFFER,
                        bindingPoint,
                        bufferHandle
                );
            } else {
                GL46C.glBindBufferRange(
                        GL46C.GL_SHADER_STORAGE_BUFFER,
                        bindingPoint,
                        bufferHandle,
                        bufferBindingOffset,
                        bufferBindingSize
                );
            }
        }
    }
}
