package com.shiroha.mmdskin.renderer.pipeline.shader;

import org.lwjgl.opengl.GL46C;

/**
 * SSBO 绑定状态保存/恢复工具。
 */
public class SSBOBindings {

    private static volatile int maxBindings = -1;

    public static int getMaxBindings() {
        int val = maxBindings;
        if (val < 0) {
            val = GL46C.glGetInteger(GL46C.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS);
            maxBindings = val;
        }
        return val;
    }

    private final int[]  bufferHandles;
    private final long[] bufferBindingOffsets;
    private final long[] bufferBindingSizes;

    public SSBOBindings() {
        int bindings = getMaxBindings();
        this.bufferHandles        = new int [bindings];
        this.bufferBindingOffsets  = new long[bindings];
        this.bufferBindingSizes    = new long[bindings];

        for (var bindingPoint = 0; bindingPoint < bindings; bindingPoint++) {
            this.bufferHandles       [bindingPoint] = GL46C.glGetIntegeri(GL46C.GL_SHADER_STORAGE_BUFFER_BINDING, bindingPoint);
            this.bufferBindingOffsets [bindingPoint] = GL46C.glGetInteger64i(GL46C.GL_SHADER_STORAGE_BUFFER_START, bindingPoint);
            this.bufferBindingSizes   [bindingPoint] = GL46C.glGetInteger64i(GL46C.GL_SHADER_STORAGE_BUFFER_SIZE, bindingPoint);
        }
    }

    public void restore() {
        for (var bindingPoint = 0; bindingPoint < bufferHandles.length; bindingPoint++) {
            var bufferHandle        = bufferHandles       [bindingPoint];
            var bufferBindingOffset = bufferBindingOffsets [bindingPoint];
            var bufferBindingSize   = bufferBindingSizes   [bindingPoint];

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
