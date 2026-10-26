/* 职责：以 26.3 抽取式 GUI 渲染模型分发的一级提示页（条件说明 + 责任分离 + 带宽告知）。 */
package com.shiroha.mmdskin.ui.selector;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.distrib.ModelDistributionClient;
import com.shiroha.mmdskin.distrib.ModelDistributionManifest;
import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件职责：模型分发的一级提示页（26.3 移植版）。
 *
 * <p>房主端没有 UI（改配置文件），所以提示页放在接收方：
 * 谁要装这个模型，谁就要先看清楚条件与责任归属。
 *
 * <p>26.3 移植说明：1.20.1 用 {@code render(GuiGraphics, ...)} 自绘，
 * 26.3 的 GUI 改成"抽取式"（{@code extractRenderState} + {@code extractBackground}），
 * 鼠标事件也从 {@code (double, double, int)} 换成了 {@link MouseButtonEvent}。
 */
public class DistributionNoticeScreen extends Screen {

    private static final int WINDOW_MARGIN = 10;
    private static final int MIN_WINDOW_WIDTH = 180;
    private static final int MAX_WINDOW_WIDTH = 260;
    private static final int MIN_WINDOW_HEIGHT = 150;

    private static final int HEADER_HEIGHT = 30;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 4;
    private static final int LIST_PADDING = 6;
    private static final int LINE_HEIGHT = 10;

    private final Screen parentScreen;
    private final List<ModelDistributionManifest> models = new ArrayList<>();

    private int rateLimitKbPerSec;
    private float targetScroll;
    private float animatedScroll;
    private ButtonTarget hoveredButton = ButtonTarget.NONE;
    private Layout layout = Layout.empty();

    private enum ButtonTarget {
        NONE,
        ACCEPT,
        CANCEL
    }

    /** 自动下载模式：点"继续"表示接受自动下载的责任条款，而不是去选单个模型。 */
    private final boolean autoMode;

    public DistributionNoticeScreen(Screen parentScreen, List<ModelDistributionManifest> models, int rateLimitKbPerSec) {
        this(parentScreen, models, rateLimitKbPerSec, false);
    }

    public DistributionNoticeScreen(Screen parentScreen, List<ModelDistributionManifest> models,
                                    int rateLimitKbPerSec, boolean autoMode) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font,
                Component.translatable("gui.mmdskin.distribution.notice.title"));
        this.parentScreen = parentScreen;
        if (models != null) {
            this.models.addAll(models);
        }
        this.rateLimitKbPerSec = Math.max(0, rateLimitKbPerSec);
        this.autoMode = autoMode;
    }

    /**
     * 把本页面注册成"责任条款提示页"的打开方式。
     *
     * <p>26.3 移植：分发核心（{@link ModelDistributionClient}）不认识 UI 类，
     * 由 UI 层注入回调，自动下载第一次触发时才能弹出这张页面。
     */
    public static void installNoticeOpener() {
        ModelDistributionClient.setNoticeOpener((parent, models, rateLimitKbPerSec, autoDownload) ->
                Minecraft.getInstance().gui.setScreen(
                        new DistributionNoticeScreen(parent, models, rateLimitKbPerSec, autoDownload)));
    }

    @Override
    protected void init() {
        super.init();
        updateLayout();
    }

    /**
     * 26.3 的背景是独立入口：这里要的是"透出游戏画面 + 自己那层暗化蒙版"，
     * 所以把默认的暗化背景关掉，蒙版由 {@link TranslucentTrayChrome#drawOverlay} 自己画。
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        try {
            updateLayout();
            updateHoverState(mouseX, mouseY);
            updateScrollAnimation();
            renderContent(graphics);
        } catch (Throwable throwable) {
            closeAfterFailure(throwable);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mouseX = event.x();
        double mouseY = event.y();
        // 用真实坐标判定，别用上一帧的 hover 状态
        if (layout.acceptButton.contains(mouseX, mouseY)) {
            if (autoMode) {
                // 自动下载：接受条款后回到游戏，之后 nearby 玩家用什么就自动下什么
                ModelDistributionClient.acceptAutoDownload();
                minecraft.gui.setScreen(parentScreen);
            } else {
                minecraft.gui.setScreen(new DistributionConfirmScreen(parentScreen, models));
            }
            return true;
        }
        if (layout.cancelButton.contains(mouseX, mouseY)) {
            minecraft.gui.setScreen(parentScreen);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        targetScroll = Mth.clamp(targetScroll - (float) scrollY * 12.0f, 0.0f, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // 必须能 ESC 出去
        if (event.isEscape()) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int clampInt(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    private void updateHoverState(int mouseX, int mouseY) {
        hoveredButton = ButtonTarget.NONE;
        if (layout.acceptButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.ACCEPT;
        } else if (layout.cancelButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.CANCEL;
        }
    }

    private void updateScrollAnimation() {
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll());
        animatedScroll = animatedScroll + (targetScroll - animatedScroll) * 0.24f;
        if (Math.abs(targetScroll - animatedScroll) < 0.25f) {
            animatedScroll = targetScroll;
        }
    }

    private float maxScroll() {
        float contentHeight = contentLines().size() * LINE_HEIGHT + LIST_PADDING * 2.0f;
        return Math.max(0.0f, contentHeight - layout.listBox.h);
    }

    /** 正文行：条件说明 + 责任分离 + 带宽告知 + 好心提醒 + 可下载模型清单。 */
    private List<Component> contentLines() {
        List<Component> lines = new ArrayList<>();
        if (autoMode) {
            // 自动下载模式：先说清楚"自动"到底会自动到什么程度
            lines.add(Component.translatable("gui.mmdskin.distribution.notice.auto").withStyle(ChatFormatting.BOLD));
            lines.addAll(wrap(Component.translatable("gui.mmdskin.distribution.notice.what.desc")));
            lines.add(Component.empty());
        }
        lines.add(Component.translatable("gui.mmdskin.distribution.notice.what").withStyle(ChatFormatting.BOLD));
        lines.addAll(wrap(Component.translatable("gui.mmdskin.distribution.notice.what.desc")));
        lines.add(Component.empty());
        lines.add(Component.translatable("gui.mmdskin.distribution.notice.bandwidth").withStyle(ChatFormatting.BOLD));
        if (rateLimitKbPerSec > 0) {
            lines.addAll(wrap(Component.translatable("gui.mmdskin.distribution.notice.bandwidth.rate", rateLimitKbPerSec)));
        } else {
            lines.addAll(wrap(Component.translatable("gui.mmdskin.distribution.notice.bandwidth.unlimited")));
        }
        lines.addAll(wrap(Component.translatable("gui.mmdskin.distribution.notice.bandwidth.disk")));
        lines.addAll(wrap(Component.translatable("gui.mmdskin.distribution.notice.bandwidth.oss")));
        lines.add(Component.empty());
        lines.add(Component.translatable("gui.mmdskin.distribution.notice.liability").withStyle(ChatFormatting.BOLD));
        lines.addAll(wrap(Component.translatable("gui.mmdskin.distribution.notice.liability.source")));
        lines.addAll(wrap(Component.translatable("gui.mmdskin.distribution.notice.liability.copyright")));
        lines.addAll(wrap(Component.translatable("gui.mmdskin.distribution.notice.liability.mod")));
        lines.add(Component.empty());
        lines.addAll(wrap(Component.translatable("gui.mmdskin.distribution.notice.kind")));
        lines.add(Component.empty());
        // 可下载模型清单：让玩家在点"继续"之前就知道服务器到底提供了什么
        lines.add(Component.translatable("gui.mmdskin.distribution.entry", models.size()).withStyle(ChatFormatting.BOLD));
        if (models.isEmpty()) {
            lines.addAll(wrap(Component.translatable("gui.mmdskin.distribution.empty")));
        } else {
            for (ModelDistributionManifest manifest : models) {
                lines.addAll(wrap(buildModelLine(manifest)));
            }
        }
        return lines;
    }

    /** 单个模型的摘要行：名字 + 文件数 + 体积。 */
    private static Component buildModelLine(ModelDistributionManifest manifest) {
        int count = manifest.fileCount > 0 ? manifest.fileCount : 1;
        return Component.translatable("gui.mmdskin.distribution.card",
                manifest.modelName,
                count,
                String.format("%.1f", manifest.totalSize / 1024.0 / 1024.0));
    }

    /** 逐字换行：中文没有空格，按空格折行会整段溢出。 */
    private List<Component> wrap(Component text) {
        List<Component> result = new ArrayList<>();
        String value = text.getString();
        int maxWidth = Math.max(20, layout.listBox.w - LIST_PADDING * 2);
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n') {
                result.add(Component.literal(line.toString()));
                line.setLength(0);
                continue;
            }
            line.append(c);
            if (this.font.width(line.toString()) > maxWidth && line.length() > 1) {
                line.deleteCharAt(line.length() - 1);
                result.add(Component.literal(line.toString()));
                line.setLength(0);
                line.append(c);
            }
        }
        if (line.length() > 0) {
            result.add(Component.literal(line.toString()));
        }
        return result;
    }

    private void renderContent(GuiGraphicsExtractor graphics) {
        TranslucentTrayChrome.drawOverlay(graphics, this.width, this.height);
        TranslucentTrayChrome.drawPanel(graphics, layout.panel.x, layout.panel.y, layout.panel.w, layout.panel.h);
        graphics.text(this.font, this.title.getString(), layout.header.x, layout.header.y + 1,
                TranslucentTrayChrome.TITLE_TEXT, false);
        graphics.text(this.font, Component.translatable("gui.mmdskin.distribution.notice.subtitle").getString(),
                layout.header.x, layout.header.y + 11, TranslucentTrayChrome.SUBTITLE_TEXT, false);

        TranslucentTrayChrome.fillListArea(graphics, layout.listBox.x, layout.listBox.y, layout.listBox.w, layout.listBox.h);
        List<Component> lines = contentLines();
        graphics.enableScissor(layout.listBox.x, layout.listBox.y, layout.listBox.x + layout.listBox.w, layout.listBox.y + layout.listBox.h);
        int y = Math.round(layout.listBox.y + LIST_PADDING - animatedScroll);
        for (Component line : lines) {
            if (y + LINE_HEIGHT < layout.listBox.y) {
                y += LINE_HEIGHT;
                continue;
            }
            if (y > layout.listBox.y + layout.listBox.h) {
                break;
            }
            graphics.text(this.font, line, layout.listBox.x + LIST_PADDING, y, TranslucentTrayChrome.BODY_TEXT, false);
            y += LINE_HEIGHT;
        }
        graphics.disableScissor();
        float max = maxScroll();
        if (max > 0.0f) {
            TranslucentTrayChrome.drawScrollbar(graphics, layout.listBox.x + layout.listBox.w - 3,
                    layout.listBox.y, layout.listBox.y + layout.listBox.h, animatedScroll, max);
        }

        // 按钮行正好压在原版 HUD（血量条 / 快捷栏）上，半透明面板会让 HUD 透过来
        // 把按钮和文字冲淡。垫一层较实的深色底，按钮才有对比度。
        int buttonRowLeft = Math.min(layout.acceptButton.x, layout.cancelButton.x) - 3;
        int buttonRowRight = Math.max(layout.acceptButton.x + layout.acceptButton.w,
                layout.cancelButton.x + layout.cancelButton.w) + 3;
        graphics.fill(buttonRowLeft, layout.acceptButton.y - 3,
                buttonRowRight, layout.acceptButton.y + layout.acceptButton.h + 3,
                0xA00E1622);

        TranslucentTrayChrome.drawButton(graphics, this.font, layout.acceptButton.x, layout.acceptButton.y,
                layout.acceptButton.w, layout.acceptButton.h,
                Component.translatable("gui.mmdskin.distribution.notice.accept"), hoveredButton == ButtonTarget.ACCEPT, true);
        TranslucentTrayChrome.drawButton(graphics, this.font, layout.cancelButton.x, layout.cancelButton.y,
                layout.cancelButton.w, layout.cancelButton.h,
                Component.translatable("gui.mmdskin.distribution.notice.cancel"), hoveredButton == ButtonTarget.CANCEL, true);
    }

    private void updateLayout() {
        int panelWidth = clampInt(this.width - WINDOW_MARGIN * 2, MIN_WINDOW_WIDTH, MAX_WINDOW_WIDTH);
        // 屏幕比最小高度还矮时（高 GUI 缩放），面板不能超出屏幕，否则按钮会被挤出可视区
        int panelHeight = Math.min(this.height, Math.max(MIN_WINDOW_HEIGHT, this.height - WINDOW_MARGIN * 2));
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        int headerY = panelY + 4;
        int listY = headerY + HEADER_HEIGHT;
        int buttonY = panelY + panelHeight - BUTTON_HEIGHT - 4;
        int listHeight = Math.max(40, buttonY - listY - BUTTON_GAP - 4);

        int buttonWidth = (panelWidth - BUTTON_GAP * 3) / 2;
        layout = new Layout(
                new UiRect(panelX, panelY, panelWidth, panelHeight),
                new UiRect(panelX + 6, headerY, panelWidth - 12, HEADER_HEIGHT - 4),
                new UiRect(panelX + 4, listY, panelWidth - 8, listHeight),
                new UiRect(panelX + 6 + buttonWidth + BUTTON_GAP, buttonY, buttonWidth, BUTTON_HEIGHT),
                new UiRect(panelX + 6, buttonY, buttonWidth, BUTTON_HEIGHT));
    }

    private void closeAfterFailure(Throwable throwable) {
        LOGGER.error("模型分发提示页渲染失败", throwable);
        minecraft.gui.setScreen(parentScreen);
    }

    private static final Logger LOGGER = LogManager.getLogger();

    record UiRect(int x, int y, int w, int h) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }

        int centerX() {
            return x + w / 2;
        }
    }

    private record Layout(UiRect panel, UiRect header, UiRect listBox, UiRect acceptButton, UiRect cancelButton) {
        static Layout empty() {
            return new Layout(new UiRect(0, 0, 0, 0), new UiRect(0, 0, 0, 0), new UiRect(0, 0, 0, 0),
                    new UiRect(0, 0, 0, 0), new UiRect(0, 0, 0, 0));
        }
    }
}
