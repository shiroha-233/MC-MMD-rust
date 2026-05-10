/* 职责：以原生 GuiGraphics 渲染语音包绑定界面。 */
package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.compat.maid.runtime.MaidMMDModelManager;
import com.shiroha.mmdskin.voice.VoiceUsageMode;
import com.shiroha.mmdskin.voice.config.VoicePackBindingsConfig;
import com.shiroha.mmdskin.voice.pack.LocalVoicePackRepository;
import com.shiroha.mmdskin.voice.pack.VoicePackDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 文件职责：提供语音包绑定原生界面。 */
public class VoicePackBindingScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final int WINDOW_MARGIN = 10;
    private static final int MIN_PANEL_WIDTH = 236;
    private static final int MAX_PANEL_WIDTH = 292;
    private static final int MIN_PANEL_HEIGHT = 228;
    private static final int HEADER_HEIGHT = 34;
    private static final int ROW_HEIGHT = 34;
    private static final int ROW_GAP = 5;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 6;

    private final Screen parent;
    private final BindingTarget target;
    private final String modelName;
    private final List<Option> options = new ArrayList<>();
    private final Map<RowKind, String> values = new LinkedHashMap<>();

    private int hoveredRow = -1;
    private HoverTarget hoveredTarget = HoverTarget.NONE;
    private boolean pendingClose;
    private Layout layout = Layout.empty();

    public static VoicePackBindingScreen createForPlayer(Screen parent, String modelName) {
        return new VoicePackBindingScreen(parent, BindingTarget.PLAYER, normalizeModelName(modelName), null);
    }

    public static VoicePackBindingScreen createForMaid(Screen parent, UUID maidUuid) {
        return new VoicePackBindingScreen(parent, BindingTarget.MAID, normalizeModelName(MaidMMDModelManager.getBindingModelName(maidUuid)), maidUuid);
    }

    private VoicePackBindingScreen(Screen parent, BindingTarget target, String modelName, UUID maidUuid) {
        super(Component.translatable(target == BindingTarget.PLAYER
                ? "gui.mmdskin.voice.title.player"
                : "gui.mmdskin.voice.title.maid"));
        this.parent = parent;
        this.target = target;
        this.modelName = modelName;
    }

    @Override
    protected void init() {
        super.init();
        loadOptions();
        loadValues();
        updateLayout();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            updateLayout();
            updateHoverState(mouseX, mouseY);
            renderFallback(guiGraphics);
            flushPendingClose(minecraft);
        } catch (Throwable throwable) {
            closeAfterFailure(minecraft, throwable);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (!layout.panel.contains(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (layout.doneButton.contains(mouseX, mouseY) || layout.cancelButton.contains(mouseX, mouseY)) {
            requestCloseAfterFrame();
            return true;
        }

        List<RowKind> rows = visibleRows();
        if (hoveredRow >= 0 && hoveredRow < rows.size()) {
            cycleValue(rows.get(hoveredRow));
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return layout.panel.contains(mouseX, mouseY) || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            requestCloseAfterFrame();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        requestCloseAfterFrame();
    }

    @Override
    public void removed() {
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void updateLayout() {
        List<RowKind> rows = visibleRows();
        int panelWidth = Mth.clamp(Math.round(this.width * 0.22f), MIN_PANEL_WIDTH, MAX_PANEL_WIDTH);
        int contentWidth = panelWidth - 20;
        int rowsHeight = rows.size() * ROW_HEIGHT + Math.max(0, rows.size() - 1) * ROW_GAP;
        int panelHeight = Math.max(MIN_PANEL_HEIGHT, HEADER_HEIGHT + rowsHeight + BUTTON_HEIGHT + 46);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = Mth.clamp((this.height - panelHeight) / 2, WINDOW_MARGIN, Math.max(WINDOW_MARGIN, this.height - panelHeight - WINDOW_MARGIN));

        UiRect panel = new UiRect(panelX, panelY, panelWidth, panelHeight);
        UiRect header = new UiRect(panelX + 10, panelY + 10, contentWidth, HEADER_HEIGHT);

        UiRect[] rowRects = new UiRect[rows.size()];
        int rowY = header.y + header.h + 10;
        for (int i = 0; i < rows.size(); i++) {
            rowRects[i] = new UiRect(header.x, rowY + i * (ROW_HEIGHT + ROW_GAP), header.w, ROW_HEIGHT);
        }

        int buttonY = panel.y + panel.h - 10 - BUTTON_HEIGHT;
        int buttonWidth = (contentWidth - BUTTON_GAP) / 2;
        UiRect doneButton = new UiRect(header.x, buttonY, buttonWidth, BUTTON_HEIGHT);
        UiRect cancelButton = new UiRect(header.x + buttonWidth + BUTTON_GAP, buttonY, contentWidth - buttonWidth - BUTTON_GAP, BUTTON_HEIGHT);
        layout = new Layout(panel, header, rowRects, doneButton, cancelButton);
    }

    private void updateHoverState(int mouseX, int mouseY) {
        hoveredTarget = HoverTarget.NONE;
        hoveredRow = -1;

        if (layout.doneButton.contains(mouseX, mouseY)) {
            hoveredTarget = HoverTarget.DONE;
            return;
        }
        if (layout.cancelButton.contains(mouseX, mouseY)) {
            hoveredTarget = HoverTarget.CANCEL;
            return;
        }

        for (int i = 0; i < layout.rowRects.length; i++) {
            UiRect rowRect = layout.rowRects[i];
            if (rowRect != null && rowRect.contains(mouseX, mouseY)) {
                hoveredRow = i;
                return;
            }
        }
    }

    private void renderFallback(GuiGraphics guiGraphics) {
        List<RowKind> rows = visibleRows();
        guiGraphics.fill(0, 0, this.width, this.height, 0x22000000);
        guiGraphics.fill(layout.panel.x, layout.panel.y, layout.panel.x + layout.panel.w, layout.panel.y + layout.panel.h, 0x2A000000);
        guiGraphics.fill(layout.panel.x + 1, layout.panel.y + 1, layout.panel.x + layout.panel.w - 1, layout.panel.y + layout.panel.h - 1, 0x20000000);

        guiGraphics.drawString(this.font, this.title.getString(), layout.header.x, layout.header.y + 2, 0xFFF1F5FB, false);
        guiGraphics.drawString(
                this.font,
                Component.translatable("gui.mmdskin.voice.current_model", modelName == null ? UIConstants.DEFAULT_MODEL_NAME : modelName).getString(),
                layout.header.x,
                layout.header.y + 12,
                0xC8D5DFEC,
                false
        );

        for (int i = 0; i < rows.size() && i < layout.rowRects.length; i++) {
            RowKind rowKind = rows.get(i);
            UiRect rect = layout.rowRects[i];
            boolean assigned = values.get(rowKind) != null;
            boolean hovered = hoveredRow == i;
            int bg = hovered ? 0x36FFFFFF : (assigned ? 0x28000000 : 0x1A000000);
            guiGraphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, bg);
            guiGraphics.drawString(this.font, Component.translatable(rowKind.translationKey()).getString(), rect.x + 6, rect.y + 4, 0xFFE9F1FA, false);
            guiGraphics.drawString(this.font, buildValueLabel(rowKind), rect.x + 6, rect.y + 18, assigned ? 0xFFF1F6FD : 0xC8D5DFEC, false);
            guiGraphics.drawString(this.font, ">", rect.x + rect.w - 10, rect.y + 12, 0xD8E6F4FF, false);
        }

        drawFallbackButton(guiGraphics, layout.doneButton, Component.translatable("gui.done").getString(), hoveredTarget == HoverTarget.DONE);
        drawFallbackButton(guiGraphics, layout.cancelButton, Component.translatable("gui.cancel").getString(), hoveredTarget == HoverTarget.CANCEL);
    }

    private void drawFallbackButton(GuiGraphics guiGraphics, UiRect rect, String text, boolean hovered) {
        int bg = hovered ? 0x4AFFFFFF : 0x30000000;
        guiGraphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, bg);
        guiGraphics.drawCenteredString(this.font, text, rect.centerX(), rect.y + 4, 0xFFF1F6FD);
    }

    private void closeAfterFailure(Minecraft minecraft, Throwable throwable) {
        LOGGER.error("[VoicePackBinding] Native binding render failed and will close", throwable);
        if (minecraft.screen == this) {
            minecraft.setScreen(parent);
        }
    }

    private void requestCloseAfterFrame() {
        pendingClose = true;
    }

    private void flushPendingClose(Minecraft minecraft) {
        if (!pendingClose || minecraft.screen != this) {
            return;
        }
        pendingClose = false;
        minecraft.setScreen(parent);
    }

    private void loadOptions() {
        options.clear();
        options.add(new Option(null, Component.translatable("gui.mmdskin.voice.pack.none").getString()));
        for (VoicePackDefinition definition : LocalVoicePackRepository.getInstance().refresh()) {
            options.add(new Option(definition.getId(), definition.getDisplayName()));
        }
    }

    private void loadValues() {
        VoicePackBindingsConfig config = VoicePackBindingsConfig.getInstance();
        values.clear();
        if (target == BindingTarget.PLAYER) {
            values.put(RowKind.DEFAULT_PACK, config.getPlayerDefaultPackId());
            values.put(RowKind.MODEL_PACK, config.getPlayerModelPackId(modelName));
            values.put(RowKind.NORMAL_MODE, config.getPlayerUsagePackId(VoiceUsageMode.NORMAL));
            values.put(RowKind.ACTION_MODE, config.getPlayerUsagePackId(VoiceUsageMode.CUSTOM_ACTION));
            values.put(RowKind.STAGE_MODE, config.getPlayerUsagePackId(VoiceUsageMode.STAGE));
        } else {
            values.put(RowKind.DEFAULT_PACK, config.getMaidDefaultPackId());
            values.put(RowKind.MODEL_PACK, config.getMaidModelPackId(modelName));
            values.put(RowKind.NORMAL_MODE, config.getMaidUsagePackId(VoiceUsageMode.NORMAL));
            values.put(RowKind.ACTION_MODE, config.getMaidUsagePackId(VoiceUsageMode.CUSTOM_ACTION));
            values.put(RowKind.STAGE_MODE, config.getMaidUsagePackId(VoiceUsageMode.STAGE));
        }
    }

    private List<RowKind> visibleRows() {
        List<RowKind> rows = new ArrayList<>();
        rows.add(RowKind.DEFAULT_PACK);
        if (modelName != null) {
            rows.add(RowKind.MODEL_PACK);
        }
        rows.add(RowKind.NORMAL_MODE);
        rows.add(RowKind.ACTION_MODE);
        if (target == BindingTarget.PLAYER) {
            rows.add(RowKind.STAGE_MODE);
        }
        return rows;
    }

    private void cycleValue(RowKind rowKind) {
        String current = values.get(rowKind);
        int currentIndex = 0;
        for (int i = 0; i < options.size(); i++) {
            if ((options.get(i).packId == null && current == null)
                    || (options.get(i).packId != null && options.get(i).packId.equals(current))) {
                currentIndex = i;
                break;
            }
        }
        Option next = options.get((currentIndex + 1) % options.size());
        values.put(rowKind, next.packId);
        applyValue(rowKind, next.packId);
    }

    private void applyValue(RowKind rowKind, String packId) {
        VoicePackBindingsConfig config = VoicePackBindingsConfig.getInstance();
        if (target == BindingTarget.PLAYER) {
            switch (rowKind) {
                case DEFAULT_PACK -> config.setPlayerDefaultPackId(packId);
                case MODEL_PACK -> config.setPlayerModelPackId(modelName, packId);
                case NORMAL_MODE -> config.setPlayerUsagePackId(VoiceUsageMode.NORMAL, packId);
                case ACTION_MODE -> config.setPlayerUsagePackId(VoiceUsageMode.CUSTOM_ACTION, packId);
                case STAGE_MODE -> config.setPlayerUsagePackId(VoiceUsageMode.STAGE, packId);
            }
            return;
        }

        switch (rowKind) {
            case DEFAULT_PACK -> config.setMaidDefaultPackId(packId);
            case MODEL_PACK -> config.setMaidModelPackId(modelName, packId);
            case NORMAL_MODE -> config.setMaidUsagePackId(VoiceUsageMode.NORMAL, packId);
            case ACTION_MODE -> config.setMaidUsagePackId(VoiceUsageMode.CUSTOM_ACTION, packId);
            case STAGE_MODE -> config.setMaidUsagePackId(VoiceUsageMode.STAGE, packId);
        }
    }

    private String buildValueLabel(RowKind rowKind) {
        String packId = values.get(rowKind);
        for (Option option : options) {
            if ((packId == null && option.packId == null)
                    || (packId != null && packId.equals(option.packId))) {
                return option.displayName;
            }
        }
        return Component.translatable("gui.mmdskin.voice.pack.none").getString();
    }

    private static String normalizeModelName(String modelName) {
        if (modelName == null || modelName.isBlank() || UIConstants.DEFAULT_MODEL_NAME.equals(modelName)) {
            return null;
        }
        return modelName;
    }

    private enum HoverTarget {
        NONE,
        DONE,
        CANCEL
    }

    private enum BindingTarget {
        PLAYER,
        MAID
    }

    private enum RowKind {
        DEFAULT_PACK("gui.mmdskin.voice.row.default"),
        MODEL_PACK("gui.mmdskin.voice.row.model"),
        NORMAL_MODE("gui.mmdskin.voice.row.normal"),
        ACTION_MODE("gui.mmdskin.voice.row.action"),
        STAGE_MODE("gui.mmdskin.voice.row.stage");

        private final String translationKey;

        RowKind(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    record UiRect(int x, int y, int w, int h) {
        static UiRect empty() {
            return new UiRect(0, 0, 0, 0);
        }

        boolean contains(double px, double py) {
            return px >= x && py >= y && px <= x + w && py <= y + h;
        }

        int centerX() {
            return x + w / 2;
        }

        int centerY() {
            return y + h / 2;
        }
    }

    private record Layout(
            UiRect panel,
            UiRect header,
            UiRect[] rowRects,
            UiRect doneButton,
            UiRect cancelButton
    ) {
        static Layout empty() {
            UiRect empty = UiRect.empty();
            return new Layout(empty, empty, new UiRect[0], empty, empty);
        }
    }

    private record Option(String packId, String displayName) {
    }
}
