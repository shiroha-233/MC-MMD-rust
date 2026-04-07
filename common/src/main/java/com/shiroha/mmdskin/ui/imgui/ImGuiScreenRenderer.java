package com.shiroha.mmdskin.ui.imgui;

import imgui.ImFont;
import imgui.ImFontConfig;
import imgui.ImFontGlyphRangesBuilder;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ImGuiScreenRenderer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String GLSL_VERSION = "#version 150";
    private static final float BASE_FONT_SIZE = 8.5f;
    private static final float MIN_FONT_RASTER_SCALE = 1.0f;
    private static final float FONT_RASTER_SCALE_STEP = 0.25f;
    private static final String CJK_GLYPH_PROBE_TEXT =
            "\u821e\u53f0\u52a8\u4f5c\u955c\u5934\u65e5\u672c\u8a9e\u304b\u306a\u30ab\u30ca\u521d\u97f3\u672a\u6765";

    private final ImGuiImplGl3 gl3 = new ImGuiImplGl3();

    private boolean initialized;
    private boolean fontsInitialized;
    private long lastFrameNanos;
    private float appliedFontRasterScale = Float.NaN;
    private float appliedFontGlobalScale = 1.0f;
    private String pendingGlyphHintText = "";
    private String appliedGlyphHintText = "";
    private FontCandidate selectedFontCandidate;

    public void setGlyphHintTexts(List<String> hintTexts) {
        if (fontsInitialized) {
            return;
        }
        pendingGlyphHintText = mergeGlyphHintText(pendingGlyphHintText, hintTexts);
    }

    public void ensureInitialized() {
        if (initialized) {
            return;
        }

        ImGui.createContext();
        ImGui.styleColorsDark();
        applyCompactStyle();

        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);

        gl3.init(GLSL_VERSION);
        initialized = true;
        lastFrameNanos = System.nanoTime();
    }

    public void beginFrame(int guiWidth, int guiHeight, float framebufferScaleX, float framebufferScaleY,
                           double mouseX, double mouseY) {
        ensureInitialized();

        ImGuiIO io = ImGui.getIO();
        io.setDisplaySize(guiWidth, guiHeight);
        io.setDisplayFramebufferScale(framebufferScaleX, framebufferScaleY);
        configureFonts(io, framebufferScaleX, framebufferScaleY);

        long now = System.nanoTime();
        float deltaTime = (now - lastFrameNanos) / 1_000_000_000.0f;
        lastFrameNanos = now;
        io.setDeltaTime(deltaTime > 0.0f ? deltaTime : (1.0f / 60.0f));
        io.addMousePosEvent((float) mouseX, (float) mouseY);

        gl3.newFrame();
        ImGui.newFrame();
    }

    public void renderFrame() {
        if (!initialized) {
            return;
        }
        ImGui.render();
        gl3.renderDrawData(ImGui.getDrawData());
    }

    public void onMouseButton(int button, boolean down) {
        if (!initialized) {
            return;
        }
        ImGui.getIO().addMouseButtonEvent(button, down);
    }

    public void onMouseScroll(double horizontal, double vertical) {
        if (!initialized) {
            return;
        }
        ImGui.getIO().addMouseWheelEvent((float) horizontal, (float) vertical);
    }

    public void dispose() {
        if (!initialized) {
            return;
        }

        gl3.shutdown();
        ImGui.destroyContext();
        initialized = false;
        fontsInitialized = false;
        lastFrameNanos = 0L;
        appliedFontRasterScale = Float.NaN;
        appliedFontGlobalScale = 1.0f;
        pendingGlyphHintText = "";
        appliedGlyphHintText = "";
        selectedFontCandidate = null;
    }

    private void configureFonts(ImGuiIO io, float framebufferScaleX, float framebufferScaleY) {
        float desiredRasterScale = quantizeFontRasterScale(Math.max(framebufferScaleX, framebufferScaleY));
        if (fontsInitialized
                && Math.abs(desiredRasterScale - appliedFontRasterScale) < 0.001f
                && pendingGlyphHintText.equals(appliedGlyphHintText)) {
            io.setFontGlobalScale(appliedFontGlobalScale);
            return;
        }

        rebuildFonts(io, desiredRasterScale);
    }

    private void rebuildFonts(ImGuiIO io, float fontRasterScale) {
        short[] glyphRanges = buildGlyphRanges(io);
        float fontPixelSize = BASE_FONT_SIZE * fontRasterScale;
        boolean hintTextChanged = !pendingGlyphHintText.equals(appliedGlyphHintText);

        FontCandidate candidate = hintTextChanged ? null : selectedFontCandidate;
        if (candidate == null || !buildCandidateFontAtlas(io, candidate, glyphRanges, fontPixelSize)) {
            candidate = selectBestFontCandidate(io, glyphRanges, fontPixelSize);
            selectedFontCandidate = candidate;
        }

        boolean usingSystemFont = candidate != null
                && buildCandidateFontAtlas(io, candidate, glyphRanges, fontPixelSize);
        if (!usingSystemFont) {
            LOGGER.warn("[ImGuiScreenRenderer] No usable system UI font found for ImGui. Falling back to default atlas font.");
            io.getFonts().clear();
            io.getFonts().addFontDefault();
            io.getFonts().build();
        }

        appliedFontGlobalScale = usingSystemFont ? (1.0f / fontRasterScale) : 1.0f;
        io.setFontGlobalScale(appliedFontGlobalScale);
        refreshFontsTexture();
        fontsInitialized = true;
        appliedFontRasterScale = fontRasterScale;
        appliedGlyphHintText = pendingGlyphHintText;
    }

    private void applyCompactStyle() {
        var style = ImGui.getStyle();
        style.setAlpha(1.0f);
        style.setWindowPadding(4.0f, 4.0f);
        style.setFramePadding(3.0f, 2.0f);
        style.setCellPadding(1.0f, 1.0f);
        style.setItemSpacing(2.5f, 2.0f);
        style.setItemInnerSpacing(2.0f, 2.0f);
        style.setTouchExtraPadding(0.0f, 0.0f);
        style.setIndentSpacing(6.0f);
        style.setScrollbarSize(8.0f);
        style.setGrabMinSize(6.0f);
        style.setWindowRounding(3.0f);
        style.setFrameRounding(2.0f);
        style.setPopupRounding(2.0f);
        style.setScrollbarRounding(2.0f);
        style.setGrabRounding(2.0f);
        style.setTabRounding(2.0f);
        style.setWindowMinSize(96.0f, 72.0f);
        style.setWindowBorderSize(1.0f);
        style.setChildBorderSize(1.0f);
        style.setFrameBorderSize(1.0f);

        style.setColor(ImGuiCol.Text, 225, 228, 232, 255);
        style.setColor(ImGuiCol.TextDisabled, 140, 146, 158, 255);
        style.setColor(ImGuiCol.WindowBg, 19, 22, 29, 245);
        style.setColor(ImGuiCol.ChildBg, 22, 26, 35, 228);
        style.setColor(ImGuiCol.PopupBg, 20, 23, 31, 252);
        style.setColor(ImGuiCol.Border, 60, 67, 82, 255);
        style.setColor(ImGuiCol.FrameBg, 34, 40, 54, 255);
        style.setColor(ImGuiCol.FrameBgHovered, 47, 56, 73, 255);
        style.setColor(ImGuiCol.FrameBgActive, 58, 69, 89, 255);
        style.setColor(ImGuiCol.TitleBg, 25, 30, 40, 250);
        style.setColor(ImGuiCol.TitleBgActive, 34, 40, 54, 250);
        style.setColor(ImGuiCol.Button, 56, 79, 115, 210);
        style.setColor(ImGuiCol.ButtonHovered, 70, 97, 138, 235);
        style.setColor(ImGuiCol.ButtonActive, 83, 114, 160, 245);
        style.setColor(ImGuiCol.Header, 52, 73, 102, 175);
        style.setColor(ImGuiCol.HeaderHovered, 63, 88, 122, 225);
        style.setColor(ImGuiCol.HeaderActive, 77, 105, 145, 245);
        style.setColor(ImGuiCol.Separator, 67, 74, 88, 220);
    }

    private float quantizeFontRasterScale(float framebufferScale) {
        float scale = Math.max(MIN_FONT_RASTER_SCALE, framebufferScale);
        return Math.round(scale / FONT_RASTER_SCALE_STEP) * FONT_RASTER_SCALE_STEP;
    }

    private short[] buildGlyphRanges(ImGuiIO io) {
        ImFontGlyphRangesBuilder builder = new ImFontGlyphRangesBuilder();
        builder.addRanges(io.getFonts().getGlyphRangesDefault());
        builder.addRanges(io.getFonts().getGlyphRangesChineseFull());
        builder.addRanges(io.getFonts().getGlyphRangesJapanese());
        builder.addText("OK");
        builder.addText(CJK_GLYPH_PROBE_TEXT);
        if (!pendingGlyphHintText.isEmpty()) {
            builder.addText(pendingGlyphHintText);
        }
        return builder.buildRanges();
    }

    private FontCandidate selectBestFontCandidate(ImGuiIO io, short[] glyphRanges, float fontPixelSize) {
        FontCandidate bestCandidate = null;
        int bestScore = -1;
        String probeText = buildGlyphProbeText();
        int probeLength = probeText.codePointCount(0, probeText.length());

        for (FontCandidate candidate : getSystemFontCandidates()) {
            if (!Files.isRegularFile(candidate.path())) {
                continue;
            }

            ImFont font = buildFont(io, candidate, glyphRanges, fontPixelSize);
            if (font == null) {
                continue;
            }

            int score = countSupportedGlyphs(font, probeText);
            LOGGER.info("[ImGuiScreenRenderer] Font candidate {} (face {}) covers {}/{} probe glyphs",
                    candidate.path(), candidate.faceIndex(), score, probeLength);

            if (score > bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
            if (score >= probeLength) {
                break;
            }
        }

        if (bestCandidate != null && bestScore > 0) {
            LOGGER.info("[ImGuiScreenRenderer] Selected ImGui primary font {} (face {}) after glyph probe scoring",
                    bestCandidate.path(), bestCandidate.faceIndex());
            return bestCandidate;
        }
        return null;
    }

    private String buildGlyphProbeText() {
        return pendingGlyphHintText.isEmpty()
                ? CJK_GLYPH_PROBE_TEXT
                : CJK_GLYPH_PROBE_TEXT + pendingGlyphHintText;
    }

    private boolean buildCandidateFontAtlas(ImGuiIO io, FontCandidate candidate, short[] glyphRanges, float fontPixelSize) {
        ImFont font = buildFont(io, candidate, glyphRanges, fontPixelSize);
        if (font == null) {
            return false;
        }

        io.setFontDefault(font);
        return true;
    }

    private ImFont buildFont(ImGuiIO io, FontCandidate candidate, short[] glyphRanges, float fontPixelSize) {
        io.getFonts().clear();

        ImFontConfig fontConfig = new ImFontConfig();
        try {
            fontConfig.setPixelSnapH(true);
            fontConfig.setOversampleH(2);
            fontConfig.setOversampleV(2);
            fontConfig.setFontNo(candidate.faceIndex());
            ImFont font = io.getFonts().addFontFromFileTTF(
                    candidate.path().toString(),
                    fontPixelSize,
                    fontConfig,
                    glyphRanges
            );
            io.getFonts().build();
            return font;
        } catch (Throwable throwable) {
            LOGGER.warn("[ImGuiScreenRenderer] Failed to load ImGui primary font from {} (face {})",
                    candidate.path(), candidate.faceIndex(), throwable);
            return null;
        } finally {
            fontConfig.destroy();
        }
    }

    private int countSupportedGlyphs(ImFont font, String probeText) {
        int supported = 0;
        for (int offset = 0; offset < probeText.length(); ) {
            int codePoint = probeText.codePointAt(offset);
            if (font.findGlyphNoFallback(codePoint) != null) {
                supported++;
            }
            offset += Character.charCount(codePoint);
        }
        return supported;
    }

    private void refreshFontsTexture() {
        gl3.destroyFontsTexture();
        if (!gl3.createFontsTexture()) {
            LOGGER.warn("[ImGuiScreenRenderer] Failed to recreate ImGui font texture after atlas rebuild.");
        }
    }

    private String normalizeGlyphHintText(List<String> hintTexts) {
        if (hintTexts == null || hintTexts.isEmpty()) {
            return "";
        }

        Set<Integer> seenCodePoints = new LinkedHashSet<>();
        for (String text : hintTexts) {
            if (text == null || text.isEmpty()) {
                continue;
            }
            for (int offset = 0; offset < text.length(); ) {
                int codePoint = text.codePointAt(offset);
                if (!Character.isISOControl(codePoint)) {
                    seenCodePoints.add(codePoint);
                }
                offset += Character.charCount(codePoint);
            }
        }

        StringBuilder builder = new StringBuilder(seenCodePoints.size());
        for (Integer codePoint : seenCodePoints) {
            builder.appendCodePoint(codePoint);
        }
        return builder.toString();
    }

    private String mergeGlyphHintText(String existingHintText, List<String> hintTexts) {
        String normalized = normalizeGlyphHintText(hintTexts);
        if (normalized.isEmpty()) {
            return existingHintText == null ? "" : existingHintText;
        }
        if (existingHintText == null || existingHintText.isEmpty()) {
            return normalized;
        }

        Set<Integer> seenCodePoints = new LinkedHashSet<>();
        for (int offset = 0; offset < existingHintText.length(); ) {
            int codePoint = existingHintText.codePointAt(offset);
            seenCodePoints.add(codePoint);
            offset += Character.charCount(codePoint);
        }
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            seenCodePoints.add(codePoint);
            offset += Character.charCount(codePoint);
        }

        StringBuilder builder = new StringBuilder(seenCodePoints.size());
        for (Integer codePoint : seenCodePoints) {
            builder.appendCodePoint(codePoint);
        }
        return builder.toString();
    }

    private List<FontCandidate> getSystemFontCandidates() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<FontCandidate> candidates = new ArrayList<>();

        if (osName.contains("win")) {
            String windowsDir = System.getenv("WINDIR");
            if (windowsDir == null || windowsDir.isBlank()) {
                windowsDir = "C:\\Windows";
            }
            addCandidate(candidates, 0, windowsDir, "Fonts", "msyh.ttc");
            addCandidate(candidates, 0, windowsDir, "Fonts", "YuGothM.ttc");
            addCandidate(candidates, 0, windowsDir, "Fonts", "meiryo.ttc");
            addCandidate(candidates, 0, windowsDir, "Fonts", "simsun.ttc");
            addCandidate(candidates, 0, windowsDir, "Fonts", "simhei.ttf");
            addCandidate(candidates, 0, windowsDir, "Fonts", "msgothic.ttc");

            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                addCandidate(candidates, 0, localAppData, "Microsoft", "Windows", "Fonts", "msyh.ttf");
                addCandidate(candidates, 0, localAppData, "Microsoft", "Windows", "Fonts", "simhei.ttf");
            }
        } else if (osName.contains("mac")) {
            addCandidate(candidates, 0, "/System/Library/Fonts", "PingFang.ttc");
            addCandidate(candidates, 0, "/System/Library/Fonts", "Hiragino Sans GB.ttc");
            addCandidate(candidates, 0, "/System/Library/Fonts", "STHeiti Medium.ttc");
            addCandidate(candidates, 0, "/System/Library/Fonts/Supplemental", "Songti.ttc");
            addCandidate(candidates, 0, "/Library/Fonts", "Arial Unicode.ttf");
            addHomeFontCandidates(candidates, "Library", "Fonts");
        } else {
            addCandidate(candidates, 0, "/usr/share/fonts/opentype/noto", "NotoSansCJK-Regular.ttc");
            addCandidate(candidates, 1, "/usr/share/fonts/opentype/noto", "NotoSansCJK-Regular.ttc");
            addCandidate(candidates, 2, "/usr/share/fonts/opentype/noto", "NotoSansCJK-Regular.ttc");
            addCandidate(candidates, 0, "/usr/share/fonts/opentype/noto", "NotoSansCJKSC-Regular.otf");
            addCandidate(candidates, 0, "/usr/share/fonts/opentype/noto", "NotoSansCJKJP-Regular.otf");
            addCandidate(candidates, 0, "/usr/share/fonts/noto-cjk", "NotoSansCJK-Regular.ttc");
            addCandidate(candidates, 1, "/usr/share/fonts/noto-cjk", "NotoSansCJK-Regular.ttc");
            addCandidate(candidates, 2, "/usr/share/fonts/noto-cjk", "NotoSansCJK-Regular.ttc");
            addCandidate(candidates, 0, "/usr/share/fonts/truetype/noto", "NotoSansCJK-Regular.ttc");
            addCandidate(candidates, 1, "/usr/share/fonts/truetype/noto", "NotoSansCJK-Regular.ttc");
            addCandidate(candidates, 2, "/usr/share/fonts/truetype/noto", "NotoSansCJK-Regular.ttc");
            addCandidate(candidates, 0, "/usr/share/fonts/truetype/noto", "NotoSansCJKSC-Regular.otf");
            addCandidate(candidates, 0, "/usr/share/fonts/truetype/noto", "NotoSansCJKJP-Regular.otf");
            addCandidate(candidates, 0, "/usr/share/fonts/truetype/wqy", "wqy-zenhei.ttc");
            addCandidate(candidates, 0, "/usr/share/fonts/truetype/arphic", "ukai.ttc");
            addHomeFontCandidates(candidates, ".local", "share", "fonts");
        }

        return candidates;
    }

    private void addHomeFontCandidates(List<FontCandidate> candidates, String... parentSegments) {
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            return;
        }

        List<String[]> names = List.of(
                new String[]{"NotoSansCJK-Regular.ttc"},
                new String[]{"NotoSansCJKSC-Regular.otf"},
                new String[]{"NotoSansCJKJP-Regular.otf"},
                new String[]{"SourceHanSansSC-Regular.otf"},
                new String[]{"WenQuanYiZenHei.ttf"}
        );
        for (String[] name : names) {
            String[] segments = new String[parentSegments.length + name.length + 1];
            segments[0] = home;
            System.arraycopy(parentSegments, 0, segments, 1, parentSegments.length);
            System.arraycopy(name, 0, segments, parentSegments.length + 1, name.length);
            addCandidate(candidates, 0, segments);
        }
    }

    private void addCandidate(List<FontCandidate> candidates, int faceIndex, String... segments) {
        if (segments.length == 0) {
            return;
        }
        String first = segments[0];
        String[] remaining = new String[Math.max(0, segments.length - 1)];
        if (segments.length > 1) {
            System.arraycopy(segments, 1, remaining, 0, remaining.length);
        }
        candidates.add(new FontCandidate(Path.of(first, remaining), faceIndex));
    }

    private record FontCandidate(Path path, int faceIndex) {
    }
}
