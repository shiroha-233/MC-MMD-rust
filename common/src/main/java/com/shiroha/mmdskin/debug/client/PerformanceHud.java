package com.shiroha.mmdskin.debug.client;

import com.shiroha.mmdskin.bridge.runtime.NativeModelQueryPort;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelInstance;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.texture.runtime.TextureRepository;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL46C;

/** 文件职责：绘制调试 HUD 并展示模型与纹理运行时资源占用。 */
public class PerformanceHud {

    private static final int BG_COLOR = 0xB0000000;
    private static final int TITLE_COLOR = 0xFF55FF55;
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int VALUE_COLOR = 0xFFFFFFFF;
    private static final int WARN_COLOR = 0xFFFFAA00;
    private static final int LINE_HEIGHT = 11;
    private static final int PADDING = 5;
    private static final int INNER_PAD = 3;

    private static final long REFRESH_INTERVAL_MS = 500L;
    private static long lastRefreshTime;

    private static final int GL_GPU_MEM_TOTAL_NVX = 0x9048;
    private static final int GL_GPU_MEM_AVAIL_NVX = 0x9049;
    private static final int GL_VBO_FREE_ATI = 0x87FB;


    private enum GpuVendor { NVIDIA, AMD, UNKNOWN }

    private static GpuVendor gpuVendor;
    private static long gpuTotalVram;

    private static final List<HudLine> cachedLines = new ArrayList<>();
    private static int cachedMaxWidth;
    private static volatile NativeModelQueryPort modelQueryPort = NativeModelQueryPort.noop();

    private PerformanceHud() {
    }

    public static void configureRuntimeCollaborators(NativeModelQueryPort modelQueryPort) {
        PerformanceHud.modelQueryPort = modelQueryPort != null ? modelQueryPort : NativeModelQueryPort.noop();
    }

    public static void render(GuiGraphics graphics) {
        if (!ConfigManager.isDebugHudEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.options.renderDebug) {
            return;
        }

        if (gpuVendor == null) {
            detectGpuVendor();
        }

        long now = System.currentTimeMillis();
        if (now - lastRefreshTime > REFRESH_INTERVAL_MS) {
            rebuildLines(minecraft.font);
            lastRefreshTime = now;
        }

        if (cachedLines.isEmpty()) {
            return;
        }

        Font font = minecraft.font;
        int totalHeight = cachedLines.size() * LINE_HEIGHT + INNER_PAD * 2;
        int totalWidth = cachedMaxWidth + INNER_PAD * 2;
        graphics.fill(PADDING, PADDING, PADDING + totalWidth, PADDING + totalHeight, BG_COLOR);

        int y = PADDING + INNER_PAD;
        for (HudLine line : cachedLines) {
            graphics.drawString(font, line.text(), PADDING + INNER_PAD, y, line.color(), true);
            y += LINE_HEIGHT;
        }
    }

    private static void rebuildLines(Font font) {
        cachedLines.clear();

        addLine("System", TITLE_COLOR);
        Runtime runtime = Runtime.getRuntime();
        long jvmUsed = runtime.totalMemory() - runtime.freeMemory();
        addLine(String.format("  JVM   %s / %s", fmtB(jvmUsed), fmtB(runtime.maxMemory())), VALUE_COLOR);

        long gpuAvail = queryAvailableVram();
        if (gpuTotalVram > 0) {
            addLine(String.format("  GPU   %s / %s", fmtB(gpuTotalVram - gpuAvail), fmtB(gpuTotalVram)), VALUE_COLOR);
        } else if (gpuAvail > 0) {
            addLine(String.format("  GPU   available %s", fmtB(gpuAvail)), VALUE_COLOR);
        } else {
            addLine("  GPU   N/A", LABEL_COLOR);
        }

        addLine("", VALUE_COLOR);
        addLine("MMD", TITLE_COLOR);

        List<ManagedModel> models = ClientRenderRuntime.get().modelDiagnostics().loadedModels();
        int totalLoaded = ClientRenderRuntime.get().modelDiagnostics().totalModelsLoaded();
        int pendingModels = ClientRenderRuntime.get().modelDiagnostics().pendingReleaseCount();
        int textureCount = TextureRepository.getTextureCount();
        long textureVram = TextureRepository.getTotalTextureVram();

        if (pendingModels > 0) {
            addLine(String.format("  Models   current %d  pending %d  total %d", models.size(), pendingModels, totalLoaded), VALUE_COLOR);
        } else {
            addLine(String.format("  Models   current %d  total %d", models.size(), totalLoaded), VALUE_COLOR);
        }

        int pendingTextures = TextureRepository.getPendingReleaseCount();
        long pendingTextureVram = TextureRepository.getPendingReleaseVram();
        if (pendingTextures > 0) {
            addLine(String.format("  Textures %d  VRAM %s (pending %d %s)", textureCount, fmtB(textureVram), pendingTextures, fmtB(pendingTextureVram)), VALUE_COLOR);
        } else {
            addLine(String.format("  Textures %d  VRAM %s", textureCount, fmtB(textureVram)), VALUE_COLOR);
        }

        long totalRam = 0L;
        long totalVram = 0L;
        for (ManagedModel managedModel : models) {
            totalRam += managedModel.modelInstance().getRamUsage();
            totalVram += managedModel.modelInstance().getVramUsage();
        }
        addLine(String.format("  RAM    %s", fmtB(totalRam)), VALUE_COLOR);
        addLine(String.format("  VRAM   %s (models %s + textures %s)", fmtB(totalVram + textureVram), fmtB(totalVram), fmtB(textureVram)), VALUE_COLOR);

        if (!models.isEmpty()) {
            NativeModelQueryPort nativeBridge = modelQueryPort;
            addLine("", VALUE_COLOR);
            addLine("Model Details", TITLE_COLOR);
            for (ManagedModel managedModel : models) {
                ModelInstance model = managedModel.modelInstance();
                long handle = model.getModelHandle();
                if (handle == 0L) {
                    continue;
                }

                String modelName = model.getModelName();
                if (modelName.length() > 24) {
                    modelName = modelName.substring(0, 22) + "..";
                }
                addLine("  " + modelName, VALUE_COLOR);

                long ram = model.getRamUsage();
                long vram = model.getVramUsage();
                addLine(String.format("    RAM %-10s  VRAM %s", fmtB(ram), fmtB(vram)), vram > 50 * 1024 * 1024 ? WARN_COLOR : LABEL_COLOR);

                long indexCount = nativeBridge.getIndexCount(handle);
                long vertexCount = nativeBridge.getVertexCount(handle);
                int boneCount = nativeBridge.getBoneCount(handle);
                int materialCount = nativeBridge.getMaterialCount(handle);
                int vertexMorphCount = nativeBridge.getVertexMorphCount(handle);
                int uvMorphCount = nativeBridge.getUvMorphCount(handle);
                long faceCount = indexCount / 3L;
                addLine(String.format("    Faces %s  Vertices %s  Bones %d  Materials %d", fmtNum(faceCount), fmtNum(vertexCount), boneCount, materialCount),
                        faceCount > 100_000 ? WARN_COLOR : LABEL_COLOR);
                if (vertexMorphCount > 0 || uvMorphCount > 0) {
                    addLine(String.format("    Morphs vertex %d  uv %d", vertexMorphCount, uvMorphCount), LABEL_COLOR);
                }
            }
        }

        cachedMaxWidth = 0;
        for (HudLine line : cachedLines) {
            cachedMaxWidth = Math.max(cachedMaxWidth, font.width(line.text()));
        }
    }

    private static void addLine(String text, int color) {
        cachedLines.add(new HudLine(text, color));
    }

    private static void detectGpuVendor() {
        try {
            String vendor = GL11.glGetString(GL11.GL_VENDOR);
            if (vendor == null) {
                gpuVendor = GpuVendor.UNKNOWN;
                return;
            }
            String value = vendor.toLowerCase();
            if (value.contains("nvidia")) {
                gpuVendor = GpuVendor.NVIDIA;
                int kb = GL46C.glGetInteger(GL_GPU_MEM_TOTAL_NVX);
                if (kb > 0) {
                    gpuTotalVram = (long) kb * 1024L;
                }
            } else if (value.contains("ati") || value.contains("amd")) {
                gpuVendor = GpuVendor.AMD;
            } else {
                gpuVendor = GpuVendor.UNKNOWN;
            }
        } catch (Exception ignored) {
            gpuVendor = GpuVendor.UNKNOWN;
        }
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
        }
    }

    private static long queryAvailableVram() {
        try {
            long result = 0L;
            if (gpuVendor == GpuVendor.NVIDIA) {
                int kb = GL46C.glGetInteger(GL_GPU_MEM_AVAIL_NVX);
                if (kb > 0) {
                    result = (long) kb * 1024L;
                }
            } else if (gpuVendor == GpuVendor.AMD) {
                int[] info = new int[4];
                GL46C.glGetIntegerv(GL_VBO_FREE_ATI, info);
                if (info[0] > 0) {
                    result = (long) info[0] * 1024L;
                }
            }
            while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            }
            return result;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String fmtB(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format("%.1f KB", bytes / 1024.0d);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format("%.1f MB", bytes / (1024.0d * 1024.0d));
        }
        return String.format("%.2f GB", bytes / (1024.0d * 1024.0d * 1024.0d));
    }

    private static String fmtNum(long value) {
        if (value < 1_000L) {
            return Long.toString(value);
        }
        if (value < 1_000_000L) {
            return String.format("%.1fK", value / 1_000.0d);
        }
        return String.format("%.2fM", value / 1_000_000.0d);
    }

    private record HudLine(String text, int color) {
    }
}
