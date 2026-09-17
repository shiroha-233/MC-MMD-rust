// 负责把 Render Metrics 与只读显存样本绘制为客户端调试 HUD。
package com.shiroha.mmdskin.debug.client;

import com.shiroha.mmdskin.bridge.graphics.OpenGlMemoryProbe;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.metrics.RenderMetrics;
import com.shiroha.mmdskin.bridge.NativePortAdapters;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.metrics.RenderMetrics;
import com.shiroha.mmdskin.client.model.MmdModelInstance;
import com.shiroha.mmdskin.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public final class PerformanceHud {
    private static final int BG_COLOR = 0xB0000000;
    private static final int TITLE_COLOR = 0xFF55FF55;
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int VALUE_COLOR = 0xFFFFFFFF;
    private static final int WARN_COLOR = 0xFFFFAA00;
    private static final int LINE_HEIGHT = 11;
    private static final int PADDING = 5;
    private static final int INNER_PAD = 3;
    private static final long REFRESH_INTERVAL_MILLIS = 500L;

    private static final List<HudLine> cachedLines = new ArrayList<>();
    private static long lastRefreshTime;
    private static int cachedMaxWidth;

    private PerformanceHud() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (!ConfigManager.isDebugHudEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameRenderer.gameRenderState().guiRenderState.isHudHidden
                || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRefreshTime >= REFRESH_INTERVAL_MILLIS) {
            rebuildLines(minecraft.font);
            lastRefreshTime = now;
        }
        if (cachedLines.isEmpty()) {
            return;
        }

        int height = cachedLines.size() * LINE_HEIGHT + INNER_PAD * 2;
        int width = cachedMaxWidth + INNER_PAD * 2;
        graphics.fill(PADDING, PADDING, PADDING + width, PADDING + height, BG_COLOR);
        int y = PADDING + INNER_PAD;
        for (HudLine line : cachedLines) {
            graphics.text(minecraft.font, line.text(), PADDING + INNER_PAD, y, line.color(), true);
            y += LINE_HEIGHT;
        }
    }

    private static void rebuildLines(Font font) {
        cachedLines.clear();
        addLine("▶ 系统资源", TITLE_COLOR);

        Runtime runtime = Runtime.getRuntime();
        addLine(String.format("  JVM   %s / %s",
                formatBytes(runtime.totalMemory() - runtime.freeMemory()), formatBytes(runtime.maxMemory())), VALUE_COLOR);

        OpenGlMemoryProbe.MemorySample gpuMemory = OpenGlMemoryProbe.sample();
        if (gpuMemory.totalBytes() > 0L) {
            addLine(String.format("  GPU   %s / %s",
                    formatBytes(gpuMemory.totalBytes() - gpuMemory.availableBytes()),
                    formatBytes(gpuMemory.totalBytes())), VALUE_COLOR);
        } else if (gpuMemory.availableBytes() > 0L) {
            addLine("  GPU   可用 " + formatBytes(gpuMemory.availableBytes()), VALUE_COLOR);
        } else {
            addLine("  GPU   N/A", LABEL_COLOR);
        }

        addLine("", VALUE_COLOR);
        addLine("▶ MMD 资源", TITLE_COLOR);
        try {
            MmdClientRenderRuntime runtimeState = MmdClientRenderRuntime.current();
            RenderMetrics.Snapshot metrics = runtimeState.metrics().snapshot();
            List<MmdModelInstance> models = runtimeState.models().loadedInstances();
            int pendingModels = metrics.pendingModels();
            addLine(pendingModels > 0
                    ? String.format("  模型   当前 %d  待释放 %d  累计 %d", models.size(), pendingModels, metrics.loadedModels())
                    : String.format("  模型   当前 %d  累计 %d", models.size(), metrics.loadedModels()), VALUE_COLOR);
            addLine(String.format("  纹理   %d 张  VRAM %s", metrics.textureCount(), formatBytes(metrics.textureBytes())), VALUE_COLOR);
            addLine("  RAM    N/A", VALUE_COLOR);
            addLine(String.format("  VRAM   %s (模型 %s + 纹理 %s)",
                    formatBytes(metrics.modelGpuBytes() + metrics.textureBytes()),
                    formatBytes(metrics.modelGpuBytes()), formatBytes(metrics.textureBytes())), VALUE_COLOR);

            if (!models.isEmpty()) {
                addLine("", VALUE_COLOR);
                addLine("▶ 模型详情", TITLE_COLOR);
                var query = NativePortAdapters.modelQuery();
                for (MmdModelInstance model : models) {
                    addLine("  ○ " + model.modelName(), VALUE_COLOR);
                    long handle = model.handle();
                    try {
                        int bones = query.getBoneCount(handle);
                        long vertices = query.getVertexCount(handle);
                        long faces = query.getIndexCount(handle) / 3L;
                        int materials = query.getMaterialCount(handle);
                        addLine(String.format("    RAM %-10s  VRAM %s", formatBytes(model.ramUsage()), formatBytes(metrics.modelGpuBytes())), LABEL_COLOR);
                        addLine(String.format("    面 %s  顶点 %s  骨骼 %d  材质 %d",
                                formatNumber(faces), formatNumber(vertices), bones, materials),
                                faces > 100_000 ? WARN_COLOR : LABEL_COLOR);
                    } catch (RuntimeException ignored) {
                        addLine("    模型数据读取失败", WARN_COLOR);
                    }
                }
            }
        } catch (IllegalStateException ignored) {
            addLine("  Runtime not installed", LABEL_COLOR);
        }

        cachedMaxWidth = cachedLines.stream().mapToInt(line -> font.width(line.text())).max().orElse(0);
    }

    private static void addLine(String text, int color) {
        cachedLines.add(new HudLine(text, color));
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format("%.1f KB", bytes / 1024.0D);
        if (bytes < 1024L * 1024L * 1024L) return String.format("%.1f MB", bytes / (1024.0D * 1024.0D));
        return String.format("%.2f GB", bytes / (1024.0D * 1024.0D * 1024.0D));
    }

    private static String formatNumber(long value) {
        if (value < 1_000L) return String.valueOf(value);
        if (value < 1_000_000L) return String.format("%.1fK", value / 1_000.0D);
        return String.format("%.2fM", value / 1_000_000.0D);
    }

    private record HudLine(String text, int color) {
    }
}
