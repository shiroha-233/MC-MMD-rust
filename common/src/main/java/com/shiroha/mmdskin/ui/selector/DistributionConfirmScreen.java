/* 职责：以 26.3 抽取式 GUI 渲染模型分发的二级确认页（选择模型 + 责任确认 + 下载）。 */
package com.shiroha.mmdskin.ui.selector;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.distrib.ModelDistributionClient;
import com.shiroha.mmdskin.distrib.ModelDistributionManifest;
import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件职责：模型分发的二级确认页（26.3 移植版）。
 *
 * <p>玩家在这里挑选模型、看清体积与传输方式，并必须勾选"我确认拥有该模型的分发权"
 * 才能点下载 —— 这是接收方的责任分离。
 */
public class DistributionConfirmScreen extends Screen {

    private static final int WINDOW_MARGIN = 10;
    private static final int MIN_WINDOW_WIDTH = 180;
    private static final int MAX_WINDOW_WIDTH = 250;
    private static final int MIN_WINDOW_HEIGHT = 150;

    private static final int HEADER_HEIGHT = 28;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 4;
    private static final int LIST_PADDING = 5;
    private static final int CARD_HEIGHT = 14;
    private static final int CARD_GAP = 4;
    private static final int CHECKBOX_SIZE = 8;
    private static final int STATUS_LINE_HEIGHT = 10;
    private static final int STATUS_COLOR_DONE = 0xFF8CD98C;
    private static final int STATUS_COLOR_FAILED = 0xFFE06C75;

    /** 点击下载后多久还没收到数据，就判定"没传起来"。 */
    private static final long REQUEST_START_TIMEOUT_MS = 5000L;
    /** 传输中超过这个时间还没装好，就提示失败（可以重试）。 */
    private static final long DOWNLOAD_TIMEOUT_MS = 120000L;

    private final Screen parentScreen;
    private final List<ModelDistributionManifest> models = new ArrayList<>();

    private int selectedIndex = -1;
    private boolean acknowledged;
    private float targetScroll;
    private float animatedScroll;
    private int hoveredCard = -1;
    private ButtonTarget hoveredButton = ButtonTarget.NONE;
    private boolean checkboxHovered;
    private Layout layout = Layout.empty();

    /** 下载状态：请求中 → 传输中 → 完成 / 失败。 */
    private DownloadState downloadState = DownloadState.IDLE;
    private long downloadStartedAtMs;

    private enum DownloadState {
        IDLE,
        REQUESTING,
        DOWNLOADING,
        DONE,
        FAILED
    }

    private enum ButtonTarget {
        NONE,
        DOWNLOAD,
        CANCEL
    }

    public DistributionConfirmScreen(Screen parentScreen, List<ModelDistributionManifest> models) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font,
                Component.translatable("gui.mmdskin.distribution.confirm.title"));
        this.parentScreen = parentScreen;
        if (models != null) {
            this.models.addAll(models);
        }
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
            LOGGER.error("模型分发确认页渲染失败", throwable);
            minecraft.gui.setScreen(parentScreen);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mouseX = event.x();
        double mouseY = event.y();
        if (layout.checkbox.contains(mouseX, mouseY)) {
            acknowledged = !acknowledged;
            return true;
        }
        // 用真实坐标判定，别用上一帧的 hover 状态（点之前光标可能刚挪过）
        if (layout.downloadButton.contains(mouseX, mouseY)) {
            ModelDistributionManifest selected = selectedModel();
            // 必须勾了"我确认拥有分发权"才允许下载 —— 这一条是责任分离的底线
            if (selected != null && acknowledged && canDownload()) {
                ModelDistributionClient.requestModel(selected.modelName);
                downloadState = DownloadState.REQUESTING;
                downloadStartedAtMs = System.currentTimeMillis();
            }
            return true;
        }
        if (layout.cancelButton.contains(mouseX, mouseY)) {
            minecraft.gui.setScreen(parentScreen);
            return true;
        }
        int index = cardIndexAt(mouseX, mouseY);
        if (index >= 0) {
            selectedIndex = index;
            acknowledged = false;
            // 换模型就换目标，之前那次下载的状态不再作数
            downloadState = DownloadState.IDLE;
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
        // 必须能 ESC 出去：这个流程是模态的，出不去就等于把玩家锁死在下载流程里
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

    /**
     * 下载状态轮询。
     *
     * <p>26.3 移植：分发核心没有对外暴露进度接口，这里只用它已有的公开方法判断：
     * 模型装好了（{@code hasLocalModel}）就是完成，临时目录里有数据就是在传，
     * 一直不落地就提示失败让玩家重试。
     */
    @Override
    public void tick() {
        super.tick();
        ModelDistributionManifest selected = selectedModel();
        if (selected == null || downloadState == DownloadState.IDLE || downloadState == DownloadState.DONE) {
            return;
        }
        if (ModelDistributionClient.hasLocalModel(selected.modelName)) {
            downloadState = DownloadState.DONE;
            return;
        }
        long elapsed = System.currentTimeMillis() - downloadStartedAtMs;
        if (downloadState == DownloadState.REQUESTING) {
            if (hasPartialData(selected.modelName)) {
                downloadState = DownloadState.DOWNLOADING;
            } else if (elapsed > REQUEST_START_TIMEOUT_MS) {
                downloadState = DownloadState.FAILED;
            }
        } else if (downloadState == DownloadState.DOWNLOADING && elapsed > DOWNLOAD_TIMEOUT_MS) {
            downloadState = DownloadState.FAILED;
        }
    }

    /** 临时目录里有没有收到数据（区分"没传起来"和"正在传"）。 */
    private static boolean hasPartialData(String modelName) {
        File partial = ModelDistributionClient.partialDir(modelName);
        if (partial == null || !partial.isDirectory()) {
            return false;
        }
        File[] children = partial.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            if (child.isFile() && child.length() > 0L) {
                return true;
            }
        }
        return false;
    }

    /** 能不能下载：选中了模型、并且勾了责任确认框、而且当前没有在下载。 */
    private boolean canDownload() {
        if (selectedModel() == null || !acknowledged) {
            return false;
        }
        return downloadState != DownloadState.REQUESTING
                && downloadState != DownloadState.DOWNLOADING
                && downloadState != DownloadState.DONE;
    }

    private ModelDistributionManifest selectedModel() {
        if (selectedIndex < 0 || selectedIndex >= models.size()) {
            return null;
        }
        return models.get(selectedIndex);
    }

    private int cardIndexAt(double mouseX, double mouseY) {
        if (!layout.listBox.contains(mouseX, mouseY)) {
            return -1;
        }
        int y = Math.round(layout.listBox.y + LIST_PADDING - animatedScroll);
        for (int i = 0; i < models.size(); i++) {
            if (mouseY >= y && mouseY < y + CARD_HEIGHT
                    && mouseX >= layout.listBox.x + 4 && mouseX < layout.listBox.x + layout.listBox.w - 4) {
                return i;
            }
            y += CARD_HEIGHT + CARD_GAP;
        }
        return -1;
    }

    private void updateHoverState(int mouseX, int mouseY) {
        hoveredButton = ButtonTarget.NONE;
        if (layout.downloadButton.contains(mouseX, mouseY) && canDownload()) {
            hoveredButton = ButtonTarget.DOWNLOAD;
        } else if (layout.cancelButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.CANCEL;
        }
        checkboxHovered = layout.checkbox.contains(mouseX, mouseY);
        hoveredCard = cardIndexAt(mouseX, mouseY);
    }

    private void updateScrollAnimation() {
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll());
        animatedScroll = animatedScroll + (targetScroll - animatedScroll) * 0.24f;
        if (Math.abs(targetScroll - animatedScroll) < 0.25f) {
            animatedScroll = targetScroll;
        }
    }

    private float maxScroll() {
        float contentHeight = LIST_PADDING * 2.0f + models.size() * CARD_HEIGHT
                + Math.max(0, models.size() - 1) * CARD_GAP;
        return Math.max(0.0f, contentHeight - layout.listBox.h);
    }

    private static int clampInt(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    private void renderContent(GuiGraphicsExtractor graphics) {
        TranslucentTrayChrome.drawOverlay(graphics, this.width, this.height);
        TranslucentTrayChrome.drawPanel(graphics, layout.panel.x, layout.panel.y, layout.panel.w, layout.panel.h);
        graphics.text(this.font, this.title.getString(), layout.header.x, layout.header.y + 1,
                TranslucentTrayChrome.TITLE_TEXT, false);

        TranslucentTrayChrome.fillListArea(graphics, layout.listBox.x, layout.listBox.y, layout.listBox.w, layout.listBox.h);
        if (models.isEmpty()) {
            graphics.centeredText(this.font,
                    Component.translatable("gui.mmdskin.distribution.empty").getString(),
                    layout.listBox.centerX(), layout.listBox.centerY() - 4, TranslucentTrayChrome.BODY_TEXT);
        } else {
            graphics.enableScissor(layout.listBox.x, layout.listBox.y,
                    layout.listBox.x + layout.listBox.w, layout.listBox.y + layout.listBox.h);
            int y = Math.round(layout.listBox.y + LIST_PADDING - animatedScroll);
            for (int i = 0; i < models.size(); i++) {
                if (y + CARD_HEIGHT < layout.listBox.y) {
                    y += CARD_HEIGHT + CARD_GAP;
                    continue;
                }
                if (y > layout.listBox.y + layout.listBox.h) {
                    break;
                }
                boolean selected = i == selectedIndex;
                boolean hovered = i == hoveredCard;
                int bg = TranslucentTrayChrome.cardBackground(selected, hovered);
                graphics.fill(layout.listBox.x + 4, y, layout.listBox.x + layout.listBox.w - 4, y + CARD_HEIGHT, bg);
                graphics.text(this.font, buildCardLabel(models.get(i)),
                        layout.listBox.x + 7, y + 3, TranslucentTrayChrome.BODY_TEXT, false);
                y += CARD_HEIGHT + CARD_GAP;
            }
            graphics.disableScissor();
            float max = maxScroll();
            if (max > 0.0f) {
                TranslucentTrayChrome.drawScrollbar(graphics, layout.listBox.x + layout.listBox.w - 3,
                        layout.listBox.y, layout.listBox.y + layout.listBox.h, animatedScroll, max);
            }
        }

        // 选中模型的详情 + 责任确认
        ModelDistributionManifest selected = selectedModel();
        int detailX = layout.detail.x;
        int detailY = layout.detail.y;
        // 这几行正好压在原版 HUD（血量条 / 快捷栏）上，面板是半透明的，
        // HUD 会直接透过来把文字冲到几乎看不见。垫一层较实的深色底再写文字。
        graphics.fill(layout.detail.x - 3, layout.detail.y - 3,
                layout.detail.x + layout.detail.w + 3, layout.checkbox.y + CHECKBOX_SIZE + 3,
                0xA00E1622);
        if (selected != null) {
            graphics.text(this.font, Component.translatable("gui.mmdskin.distribution.confirm.detail",
                    selected.modelName,
                    selected.fileCount > 0 ? selected.fileCount : 1,
                    String.format("%.1f", selected.totalSize / 1024.0 / 1024.0)).getString(),
                    detailX, detailY, TranslucentTrayChrome.BODY_TEXT, false);
            graphics.text(this.font, Component.translatable("gui.mmdskin.distribution.confirm.source",
                    selected.ossVerified
                            ? Component.translatable("gui.mmdskin.distribution.confirm.source.oss").getString()
                            : Component.translatable("gui.mmdskin.distribution.confirm.source.server").getString())
                    .getString(), detailX, detailY + 10, TranslucentTrayChrome.DETAIL_TEXT, false);
            graphics.text(this.font, Component.translatable("gui.mmdskin.distribution.confirm.liability").getString(),
                    detailX, detailY + 20, TranslucentTrayChrome.DETAIL_TEXT, false);
        }

        // 下载状态提示（下载中 / 完成 / 失败）
        String status = buildStatusText();
        if (!status.isEmpty()) {
            graphics.text(this.font, status, detailX, detailY + 30, statusColor(), false);
        }

        // 必须勾选的责任确认框
        int boxX = layout.checkbox.x;
        int boxY = layout.checkbox.y;
        // 未勾选时不能拿 CARD_BACKGROUND(0x24000000，透明度仅 14%) 画框：
        // 那是半透明卡片底色，压在同样半透明的面板上等于隐形，玩家根本看不到
        // 这里有个框、更不知道要点哪里。改成「深底 + 亮色描边」，勾选后填主色。
        int boxFill = acknowledged ? TranslucentTrayChrome.ACCENT : 0xD0101822;
        int boxBorder = acknowledged ? TranslucentTrayChrome.ACCENT
                : (checkboxHovered ? TranslucentTrayChrome.ACCENT : 0xCCBCD0DC);
        graphics.fill(boxX, boxY, boxX + CHECKBOX_SIZE, boxY + CHECKBOX_SIZE, boxFill);
        graphics.fill(boxX, boxY, boxX + CHECKBOX_SIZE, boxY + 1, boxBorder);
        graphics.fill(boxX, boxY + CHECKBOX_SIZE - 1, boxX + CHECKBOX_SIZE, boxY + CHECKBOX_SIZE, boxBorder);
        graphics.fill(boxX, boxY, boxX + 1, boxY + CHECKBOX_SIZE, boxBorder);
        graphics.fill(boxX + CHECKBOX_SIZE - 1, boxY, boxX + CHECKBOX_SIZE, boxY + CHECKBOX_SIZE, boxBorder);
        if (acknowledged) {
            graphics.text(this.font, "×", boxX + 2, boxY - 1, 0xFF10202C, false);
        }
        graphics.text(this.font, Component.translatable("gui.mmdskin.distribution.confirm.checkbox").getString(),
                boxX + CHECKBOX_SIZE + 4, boxY, TranslucentTrayChrome.BODY_TEXT, false);

        boolean canDownload = canDownload();
        TranslucentTrayChrome.drawButton(graphics, this.font, layout.downloadButton.x, layout.downloadButton.y,
                layout.downloadButton.w, layout.downloadButton.h,
                Component.translatable("gui.mmdskin.distribution.confirm.download"),
                hoveredButton == ButtonTarget.DOWNLOAD, canDownload);
        TranslucentTrayChrome.drawButton(graphics, this.font, layout.cancelButton.x, layout.cancelButton.y,
                layout.cancelButton.w, layout.cancelButton.h,
                Component.translatable("gui.mmdskin.distribution.confirm.cancel"),
                hoveredButton == ButtonTarget.CANCEL, true);
    }

    /** 下载状态提示文案。 */
    private String buildStatusText() {
        ModelDistributionManifest selected = selectedModel();
        if (selected == null) {
            return "";
        }
        switch (downloadState) {
            case REQUESTING:
            case DOWNLOADING:
                return Component.translatable("gui.mmdskin.distribution.confirm.status.downloading",
                        selected.modelName).getString();
            case DONE:
                return Component.translatable("gui.mmdskin.distribution.confirm.status.done",
                        selected.modelName).getString();
            case FAILED:
                return Component.translatable("gui.mmdskin.distribution.confirm.status.failed",
                        selected.modelName).getString();
            default:
                return "";
        }
    }

    /** 下载状态提示的颜色：进行中用强调色，完成用绿色，失败用红色。 */
    private int statusColor() {
        switch (downloadState) {
            case DONE:
                return STATUS_COLOR_DONE;
            case FAILED:
                return STATUS_COLOR_FAILED;
            default:
                return TranslucentTrayChrome.ACCENT;
        }
    }

    private String buildCardLabel(ModelDistributionManifest manifest) {
        int count = manifest.fileCount > 0 ? manifest.fileCount : 1;
        return Component.translatable("gui.mmdskin.distribution.card", manifest.modelName, count,
                String.format("%.1f", manifest.totalSize / 1024.0 / 1024.0)).getString();
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
        // 详情 3 行 + 状态 1 行 + 勾选框 1 行（勾选框正好贴在按钮行上方，不重叠）
        int detailY = buttonY - 48;
        int checkboxY = detailY + 40;
        int listHeight = Math.max(30, checkboxY - listY - 6);

        int buttonWidth = (panelWidth - BUTTON_GAP * 3) / 2;
        layout = new Layout(
                new UiRect(panelX, panelY, panelWidth, panelHeight),
                new UiRect(panelX + 6, headerY, panelWidth - 12, HEADER_HEIGHT - 4),
                new UiRect(panelX + 4, listY, panelWidth - 8, listHeight),
                new UiRect(panelX + 6, detailY, panelWidth - 12, 32 + STATUS_LINE_HEIGHT),
                new UiRect(panelX + 6, checkboxY, CHECKBOX_SIZE, CHECKBOX_SIZE),
                new UiRect(panelX + 6 + buttonWidth + BUTTON_GAP, buttonY, buttonWidth, BUTTON_HEIGHT),
                new UiRect(panelX + 6, buttonY, buttonWidth, BUTTON_HEIGHT));
    }

    private static final Logger LOGGER = LogManager.getLogger();

    record UiRect(int x, int y, int w, int h) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }

        int centerX() {
            return x + w / 2;
        }

        int centerY() {
            return y + h / 2;
        }
    }

    private record Layout(UiRect panel, UiRect header, UiRect listBox, UiRect detail, UiRect checkbox,
                          UiRect downloadButton, UiRect cancelButton) {
        static Layout empty() {
            return new Layout(new UiRect(0, 0, 0, 0), new UiRect(0, 0, 0, 0), new UiRect(0, 0, 0, 0),
                    new UiRect(0, 0, 0, 0), new UiRect(0, 0, 0, 0), new UiRect(0, 0, 0, 0), new UiRect(0, 0, 0, 0));
        }
    }
}
