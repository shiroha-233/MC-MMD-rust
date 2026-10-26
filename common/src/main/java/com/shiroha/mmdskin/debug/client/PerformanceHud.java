// 负责把 Render Metrics 与只读显存样本绘制为客户端调试 HUD。
package com.shiroha.mmdskin.debug.client;

import com.shiroha.mmdskin.bridge.graphics.OpenGlMemoryProbe;
import com.shiroha.mmdskin.client.MmdClientRenderRuntime;
import com.shiroha.mmdskin.client.metrics.RenderMetrics;
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
    private static final int LINE_HEIGHT = 11;
    private static final int PADDING = 5;
    private static final int INNER_PAD = 3;
    private static final long REFRESH_INTERVAL_MILLIS = 500L;

    private static final List<HudLine> cachedLines = new ArrayList<>();
    private static long lastRefreshTime;
    private static int cachedMaxWidth;
    private static boolean reportedFailure;

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger(PerformanceHud.class);

    private PerformanceHud() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (!ConfigManager.isDebugHudEnabled()) {
            return;
        }
        try {
            renderInternal(graphics);
        } catch (Throwable exception) {
            // 调试面板绝对不能把整帧带崩（它只是给开发者看的）。
            cachedLines.clear();
            if (!reportedFailure) {
                reportedFailure = true;
                LOGGER.warn("性能调试 HUD 采集失败，已停用本帧绘制", exception);
            }
        }
    }

    private static void renderInternal(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        // 26.3 把 F1「隐藏界面」的状态从 Options.hideGui 挪到了 Hud.isHidden()
        if (minecraft.gui.hud.isHidden() || minecraft.getDebugOverlay().showDebugScreen()) {
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
        addLine("MMD Render", TITLE_COLOR);

        Runtime runtime = Runtime.getRuntime();
        addLine("JVM  " + formatBytes(runtime.totalMemory() - runtime.freeMemory())
                + " / " + formatBytes(runtime.maxMemory()), VALUE_COLOR);

        OpenGlMemoryProbe.MemorySample gpuMemory = OpenGlMemoryProbe.sample();
        if (gpuMemory.totalBytes() > 0L) {
            addLine("GPU  " + formatBytes(gpuMemory.totalBytes() - gpuMemory.availableBytes())
                    + " / " + formatBytes(gpuMemory.totalBytes()), VALUE_COLOR);
        } else if (gpuMemory.availableBytes() > 0L) {
            addLine("GPU available " + formatBytes(gpuMemory.availableBytes()), VALUE_COLOR);
        } else {
            addLine("GPU memory N/A", LABEL_COLOR);
        }

        try {
            RenderMetrics.Snapshot metrics = MmdClientRenderRuntime.current().metrics().snapshot();
            addLine("Models " + metrics.loadedModels() + "  loading " + metrics.pendingModels(), VALUE_COLOR);
            addLine("Queue " + metrics.queuedDraws() + "  uploads " + metrics.uploadCount(), VALUE_COLOR);
            addLine("Vertex uploaded " + formatBytes(metrics.uploadedVertexBytes()), LABEL_COLOR);
            addLine("GPU tracked " + formatBytes(metrics.modelGpuBytes() + metrics.textureBytes())
                    + "  textures " + metrics.textureCount(), LABEL_COLOR);
        } catch (IllegalStateException ignored) {
            addLine("Runtime not installed", LABEL_COLOR);
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

    private record HudLine(String text, int color) {
    }
}
