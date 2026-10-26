// 负责隔离只读 OpenGL 显存扩展探测并清理查询错误状态。
package com.shiroha.mmdskin.bridge.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL46C;

import java.util.Locale;

public final class OpenGlMemoryProbe {
    private static final int GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX = 0x9048;
    private static final int GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX = 0x9049;
    private static final int TEXTURE_FREE_MEMORY_ATI = 0x87FC;

    /**
     * 本进程是否真的存在可用的 OpenGL 上下文。
     * <p>null = 还没探测过。探测一次之后缓存住，避免每帧反复问驱动。
     */
    private static volatile Boolean openGlUsable;

    private OpenGlMemoryProbe() {
    }

    public static MemorySample sample() {
        // 26.3 起 MC 支持 Vulkan 后端，此时进程里**根本没有 OpenGL 上下文**。
        // 这种情况下调用任何 GL 函数，LWJGL 会在原生层直接 abort JVM：
        //   FATAL ERROR in native method: [LWJGL] No context is current ... The JVM will abort execution.
        // 这个是**不可捕获**的（不是 Java 异常，try/catch 拦不住），
        // 所以只能在调用之前判定后端，压根不去碰 GL。
        // 1.20.1 只有 OpenGL 后端，所以这段探测当年是安全的；26.3 移植时必须补上后端判断。
        if (!isOpenGlUsable()) {
            return MemorySample.unavailable();
        }
        try {
            String vendor = GL11.glGetString(GL11.GL_VENDOR);
            if (vendor == null) {
                return MemorySample.unavailable();
            }
            String normalized = vendor.toLowerCase(Locale.ROOT);
            if (normalized.contains("nvidia")) {
                return new MemorySample(
                        kilobytesToBytes(GL46C.glGetInteger(GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX)),
                        kilobytesToBytes(GL46C.glGetInteger(GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX)));
            }
            if (normalized.contains("amd") || normalized.contains("ati")) {
                int[] values = new int[4];
                GL46C.glGetIntegerv(TEXTURE_FREE_MEMORY_ATI, values);
                return new MemorySample(0L, kilobytesToBytes(values[0]));
            }
            return MemorySample.unavailable();
        } catch (Throwable exception) {
            return MemorySample.unavailable();
        } finally {
            clearErrors();
        }
    }

    /**
     * 判断当前进程能不能安全地调用 OpenGL 函数。
     * <p>两道检查都必须是"纯 Java"判定，任何一道都不能触发原生 GL 调用。
     */
    static boolean isOpenGlUsable() {
        Boolean cached = openGlUsable;
        if (cached != null) {
            return cached;
        }
        boolean usable = probeOpenGl();
        openGlUsable = usable;
        return usable;
    }

    private static boolean probeOpenGl() {
        // 第一道：后端名。明确是 Vulkan / Direct3D / Metal 之类的后端就直接放弃。
        try {
            String backend = RenderSystem.getDevice().getDeviceInfo().backendName();
            if (backend != null) {
                String lower = backend.toLowerCase(Locale.ROOT);
                if (lower.contains("vulkan") || lower.contains("direct3d")
                        || lower.contains("d3d") || lower.contains("metal")) {
                    return false;
                }
            }
        } catch (Throwable ignored) {
            // 取不到设备信息时交给第二道检查。
        }
        // 第二道：LWJGL 是否真的创建过 GL 上下文。
        // getCapabilities() 是纯 Java 判定，没有上下文时抛 IllegalStateException（可捕获），
        // 不会走到原生层。
        try {
            return GL.getCapabilities() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static long kilobytesToBytes(int kilobytes) {
        return kilobytes > 0 ? (long) kilobytes * 1024L : 0L;
    }

    private static void clearErrors() {
        if (!isOpenGlUsable()) {
            return;
        }
        try {
            while (GL11.glGetError() != GL11.GL_NO_ERROR) {
                // 查询扩展不应污染后续渲染命令的错误状态。
            }
        } catch (Throwable ignored) {
            // 清错误本身失败就算了，不能因为它把整帧带崩。
        }
    }

    public record MemorySample(long totalBytes, long availableBytes) {
        public static MemorySample unavailable() {
            return new MemorySample(0L, 0L);
        }
    }
}
