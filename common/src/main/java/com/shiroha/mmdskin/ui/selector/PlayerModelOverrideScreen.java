/* 文件职责：本地「指定玩家模型」界面（只改我自己屏幕上的渲染）。 */
package com.shiroha.mmdskin.ui.selector;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.distrib.ModelDistributionClient;
import com.shiroha.mmdskin.player.sync.PlayerModelOverride;
import com.shiroha.mmdskin.player.sync.PlayerModelOverrideRegistry;
import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 「指定玩家模型」界面。
 *
 * <p>这里做的是一件<b>纯本地</b>的事：把某个玩家在我自己的屏幕上渲染成我指定的模型。
 * <ul>
 *   <li>不写进服务器、不广播，对方也不需要同意；</li>
 *   <li>服务器该怎么渲染怎么渲染、该怎么管怎么管，跟这个功能没关系；</li>
 *   <li>输入框填<b>玩家名</b>，按 TAB 可以补全当前在线的玩家名；</li>
 *   <li>离线玩家的名字也能手输——他什么时候上线，你的屏幕上就什么时候生效。</li>
 * </ul>
 */
public final class PlayerModelOverrideScreen extends Screen {

    private static final int WINDOW_MARGIN = 10;
    private static final int MIN_WINDOW_WIDTH = 252;
    private static final int MAX_WINDOW_WIDTH = 332;
    private static final int MIN_WINDOW_HEIGHT = 244;
    private static final int HEADER_HEIGHT = 34;
    private static final int INPUT_HEIGHT = 18;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_GAP = 4;
    private static final int LIST_PADDING = 5;
    private static final int CARD_HEIGHT = 16;
    private static final int CARD_GAP = 4;

    private final Screen parent;

    private final List<String> allModels = new ArrayList<>();

    private EditBox nameBox;
    private String selectedModel;
    private float targetScroll;
    private float animatedScroll;
    private int hoveredIndex = -1;
    private ButtonTarget hoveredButton = ButtonTarget.NONE;
    private Layout layout = Layout.empty();

    private enum ButtonTarget {
        NONE,
        ASSIGN,
        CLEAR,
        CLOSE
    }

    public PlayerModelOverrideScreen(Screen parent) {
        super(Component.translatable("gui.mmdskin.override.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        updateLayout();
        rebuildNameBox();
        refreshModels();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateLayout();
        updateHoverState(mouseX, mouseY);
        updateScrollAnimation();

        drawFrame(guiGraphics);
        drawModelList(guiGraphics);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (layout.assignButton.contains(mouseX, mouseY)) {
            submitAssign();
            return true;
        }
        if (layout.clearButton.contains(mouseX, mouseY)) {
            submitClear();
            return true;
        }
        if (layout.closeButton.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (layout.listBox.contains(mouseX, mouseY) && hoveredIndex >= 0 && hoveredIndex < allModels.size()) {
            selectedModel = allModels.get(hoveredIndex);
            return true;
        }
        return layout.panel.contains(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!layout.listBox.contains(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        targetScroll = Mth.clamp(targetScroll - (float) scrollY * 12.0f, 0.0f, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            onClose();
            return true;
        }
        // TAB：补全在线玩家名（Shift+TAB 留给原生的焦点切换）。
        if (isTabKey(event) && !(event.hasShiftDown())) {
            if (nameBox != null && nameBox.isFocused()) {
                completePlayerName();
                return true;
            }
        }
        if (event.isConfirmation()) {
            submitAssign();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ========================================================================
    // 玩家名补全
    // ========================================================================

    /** TAB 是不是被按下了。 */
    private static boolean isTabKey(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_TAB) {
            return true;
        }
        return InputConstants.Type.KEYBOARD.getOrCreate(key).getValue() == InputConstants.KEY_TAB;
    }

    /**
     * 用当前输入去补全在线玩家名。
     *
     * <p>只有一个候选时直接补全；多个候选时补到它们的公共前缀。
     */
    private void completePlayerName() {
        String query = nameBox == null ? "" : nameBox.getValue().trim().toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            for (Player player : minecraft.level.players()) {
                String name = player.getName().getString();
                if (name != null && !name.isBlank() && name.toLowerCase(Locale.ROOT).startsWith(query)) {
                    candidates.add(name);
                }
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        candidates.sort(Comparator.comparing(String::length).thenComparing(String::compareTo));
        String chosen = candidates.get(0);
        if (candidates.size() > 1) {
            String prefix = commonPrefix(candidates);
            if (prefix.length() > query.length()) {
                chosen = prefix;
            }
        }
        if (nameBox != null) {
            nameBox.setValue(chosen);
        }
    }

    /** 一组字符串的最长公共前缀。 */
    private static String commonPrefix(List<String> values) {
        if (values.isEmpty()) {
            return "";
        }
        String prefix = values.get(0);
        for (String value : values) {
            int limit = Math.min(prefix.length(), value.length());
            int i = 0;
            while (i < limit && prefix.charAt(i) == value.charAt(i)) {
                i++;
            }
            prefix = prefix.substring(0, i);
            if (prefix.isEmpty()) {
                break;
            }
        }
        return prefix;
    }

    /** 当前输入的名字对应的在线玩家 UUID（可能为 null：允许填离线玩家的名字）。 */
    private UUID resolveTargetUuid() {
        String name = nameBox == null ? "" : nameBox.getValue().trim();
        if (name.isEmpty()) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        for (Player player : minecraft.level.players()) {
            if (player.getName().getString().equalsIgnoreCase(name)) {
                return player.getUUID();
            }
        }
        return null;
    }

    // ========================================================================
    // 提交
    // ========================================================================

    private void submitAssign() {
        String name = nameBox == null ? "" : nameBox.getValue().trim();
        if (name.isEmpty() || selectedModel == null || selectedModel.isBlank()) {
            return;
        }
        PlayerModelOverrideRegistry.setOverride(name, resolveTargetUuid(), selectedModel);
    }

    private void submitClear() {
        String name = nameBox == null ? "" : nameBox.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        PlayerModelOverrideRegistry.removeOverride(name);
    }

    // ========================================================================
    // 布局与绘制
    // ========================================================================

    private void rebuildNameBox() {
        String query = nameBox == null ? "" : nameBox.getValue();
        nameBox = new EditBox(this.font, layout.nameBox.x, layout.nameBox.y, layout.nameBox.w, layout.nameBox.h,
                Component.translatable("gui.mmdskin.override.player_name"));
        nameBox.setMaxLength(64);
        nameBox.setBordered(false);
        nameBox.setTextColor(TranslucentTrayChrome.BODY_TEXT);
        nameBox.setTextColorUneditable(TranslucentTrayChrome.MUTED_TEXT);
        nameBox.setSuggestion(Component.translatable("gui.mmdskin.override.player_name.hint").getString());
        nameBox.setResponder(value -> {
            selectedModel = null;
            targetScroll = 0.0f;
            animatedScroll = 0.0f;
        });
        nameBox.setValue(query);
        this.addRenderableWidget(nameBox);
        this.setInitialFocus(nameBox);
    }

    private void updateLayout() {
        int panelWidth = Mth.clamp(Math.round(this.width * 0.24f), MIN_WINDOW_WIDTH, MAX_WINDOW_WIDTH);
        int panelHeight = Math.max(MIN_WINDOW_HEIGHT, this.height - WINDOW_MARGIN * 2);
        int panelX = this.width - panelWidth - WINDOW_MARGIN;
        int panelY = WINDOW_MARGIN;

        UiRect panel = new UiRect(panelX, panelY, panelWidth, panelHeight);
        UiRect header = new UiRect(panelX + 8, panelY + 6, panelWidth - 16, HEADER_HEIGHT);

        int inputY = header.y + header.h + 2;
        UiRect nameBoxRect = new UiRect(header.x, inputY, header.w, INPUT_HEIGHT);

        int buttonY = panel.y + panel.h - BUTTON_HEIGHT - 6;
        int buttonWidth = (header.w - BUTTON_GAP * 2) / 3;
        UiRect assignButton = new UiRect(header.x, buttonY, buttonWidth, BUTTON_HEIGHT);
        UiRect clearButton = new UiRect(header.x + buttonWidth + BUTTON_GAP, buttonY, buttonWidth, BUTTON_HEIGHT);
        UiRect closeButton = new UiRect(header.x + (buttonWidth + BUTTON_GAP) * 2, buttonY, buttonWidth, BUTTON_HEIGHT);

        int listY = inputY + INPUT_HEIGHT + 6;
        int listHeight = Math.max(72, buttonY - listY - 6);
        UiRect listBox = new UiRect(header.x, listY, header.w, listHeight);
        layout = new Layout(panel, header, nameBoxRect, listBox, assignButton, clearButton, closeButton);

        if (nameBox != null) {
            nameBox.setX(nameBoxRect.x + 4);
            nameBox.setY(nameBoxRect.y + 1);
            nameBox.setWidth(Math.max(8, nameBoxRect.w - 8));
            nameBox.setHeight(nameBoxRect.h - 2);
        }

        float maxScroll = maxScroll();
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll);
        animatedScroll = Mth.clamp(animatedScroll, 0.0f, maxScroll);
    }

    /** 候选模型：服务器授权分发的 + 本机已有的。 */
    private void refreshModels() {
        Set<String> names = new LinkedHashSet<>();
        for (String name : ModelDistributionClient.distributableModelNames()) {
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        try {
            for (String name : ModelInfo.scanModels().stream().map(ModelInfo::getDisplayName).toList()) {
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        } catch (Exception e) {
            // 本地模型目录读不到就算了，候选列表少几个不影响主流程。
        }
        allModels.clear();
        allModels.addAll(names);
        allModels.sort(Comparator.comparing(value -> value.toLowerCase(Locale.ROOT)));
        if (selectedModel != null && !allModels.contains(selectedModel)) {
            allModels.add(selectedModel);
            allModels.sort(Comparator.comparing(value -> value.toLowerCase(Locale.ROOT)));
        }
    }

    private void drawFrame(GuiGraphicsExtractor guiGraphics) {
        TranslucentTrayChrome.drawOverlay(guiGraphics, this.width, this.height);
        TranslucentTrayChrome.drawPanel(guiGraphics, layout.panel.x, layout.panel.y, layout.panel.w, layout.panel.h);

        guiGraphics.text(this.font, this.title.getString(), layout.header.x, layout.header.y + 1,
                TranslucentTrayChrome.TITLE_TEXT, false);
        guiGraphics.text(this.font, buildStatusText(), layout.header.x, layout.header.y + 11,
                TranslucentTrayChrome.SUBTITLE_TEXT, false);
        guiGraphics.text(this.font, buildTargetText(), layout.header.x, layout.header.y + 21,
                TranslucentTrayChrome.DETAIL_TEXT, false);

        drawInputBackplate(guiGraphics);
        drawButton(guiGraphics, layout.assignButton, Component.translatable("gui.mmdskin.override.assign").getString(),
                hoveredButton == ButtonTarget.ASSIGN, canAssign());
        drawButton(guiGraphics, layout.clearButton, Component.translatable("gui.mmdskin.override.clear").getString(),
                hoveredButton == ButtonTarget.CLEAR, canClear());
        drawButton(guiGraphics, layout.closeButton, Component.translatable("gui.cancel").getString(),
                hoveredButton == ButtonTarget.CLOSE, true);
    }

    private void drawInputBackplate(GuiGraphicsExtractor guiGraphics) {
        boolean focused = nameBox != null && nameBox.isFocused();
        int fill = focused ? 0x30FFFFFF : TranslucentTrayChrome.LIST_BACKGROUND;
        guiGraphics.fill(layout.nameBox.x, layout.nameBox.y,
                layout.nameBox.x + layout.nameBox.w, layout.nameBox.y + layout.nameBox.h, fill);
    }

    private void drawModelList(GuiGraphicsExtractor guiGraphics) {
        UiRect list = layout.listBox;
        TranslucentTrayChrome.fillListArea(guiGraphics, list.x, list.y, list.w, list.h);
        if (allModels.isEmpty()) {
            guiGraphics.centeredText(this.font,
                    Component.translatable("gui.mmdskin.override.no_models").getString(),
                    list.centerX(), list.centerY() - 4, TranslucentTrayChrome.DIM_TEXT);
            return;
        }

        guiGraphics.enableScissor(list.x, list.y, list.x + list.w, list.y + list.h);
        int y = Math.round(list.y + LIST_PADDING - animatedScroll);
        for (int i = 0; i < allModels.size(); i++) {
            if (y + CARD_HEIGHT < list.y) {
                y += CARD_HEIGHT + CARD_GAP;
                continue;
            }
            if (y > list.y + list.h) {
                break;
            }
            String modelName = allModels.get(i);
            boolean selected = modelName.equals(selectedModel);
            boolean hovered = i == hoveredIndex;
            guiGraphics.fill(list.x + 4, y, list.x + list.w - 4, y + CARD_HEIGHT,
                    TranslucentTrayChrome.cardBackground(selected, hovered));
            guiGraphics.text(this.font, shorten(modelName, 24), list.x + 7, y + 4,
                    TranslucentTrayChrome.BODY_TEXT, false);
            y += CARD_HEIGHT + CARD_GAP;
        }
        guiGraphics.disableScissor();
        TranslucentTrayChrome.drawScrollbar(guiGraphics, list.x + list.w - 3, list.y, list.y + list.h,
                animatedScroll, maxScroll());
    }

    private void drawButton(GuiGraphicsExtractor guiGraphics, UiRect rect, String text, boolean hovered, boolean enabled) {
        TranslucentTrayChrome.drawButton(guiGraphics, this.font, rect.x, rect.y, rect.w, rect.h, text, hovered, enabled);
    }

    private void updateHoverState(int mouseX, int mouseY) {
        hoveredButton = ButtonTarget.NONE;
        hoveredIndex = -1;

        if (layout.assignButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.ASSIGN;
            return;
        }
        if (layout.clearButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.CLEAR;
            return;
        }
        if (layout.closeButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.CLOSE;
            return;
        }
        if (!layout.listBox.contains(mouseX, mouseY) || allModels.isEmpty()) {
            return;
        }

        float localY = (float) mouseY - (layout.listBox.y + LIST_PADDING) + animatedScroll;
        if (localY < 0.0f) {
            return;
        }
        int index = (int) (localY / (CARD_HEIGHT + CARD_GAP));
        if (index < 0 || index >= allModels.size()) {
            return;
        }
        if (localY - index * (CARD_HEIGHT + CARD_GAP) <= CARD_HEIGHT) {
            hoveredIndex = index;
        }
    }

    private void updateScrollAnimation() {
        animatedScroll = Mth.lerp(0.24f, animatedScroll, targetScroll);
        if (Math.abs(animatedScroll - targetScroll) < 0.25f) {
            animatedScroll = targetScroll;
        }
    }

    private float maxScroll() {
        int count = allModels.size();
        float contentHeight = count <= 0
                ? 0.0f
                : LIST_PADDING * 2.0f + count * CARD_HEIGHT + Math.max(0, count - 1) * CARD_GAP;
        return Math.max(0.0f, contentHeight - layout.listBox.h);
    }

    // ========================================================================
    // 文案
    // ========================================================================

    private String buildStatusText() {
        List<PlayerModelOverride> overrides = PlayerModelOverrideRegistry.getAllOverrides();
        return Component.translatable("gui.mmdskin.override.stats",
                allModels.size(), overrides.size()).getString();
    }

    private String buildTargetText() {
        String name = nameBox == null ? "" : nameBox.getValue().trim();
        if (name.isEmpty()) {
            return Component.translatable("gui.mmdskin.override.target.none").getString();
        }
        PlayerModelOverride override = PlayerModelOverrideRegistry.findOverride(name, resolveTargetUuid());
        if (override == null) {
            return Component.translatable("gui.mmdskin.override.target.unassigned", name).getString();
        }
        return Component.translatable("gui.mmdskin.override.target.assigned", name, override.modelName()).getString();
    }

    private boolean canAssign() {
        String name = nameBox == null ? "" : nameBox.getValue().trim();
        return !name.isEmpty() && selectedModel != null && !selectedModel.isBlank();
    }

    private boolean canClear() {
        String name = nameBox == null ? "" : nameBox.getValue().trim();
        if (name.isEmpty()) {
            return false;
        }
        return PlayerModelOverrideRegistry.isOverridden(name, resolveTargetUuid());
    }

    private static String shorten(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        if (maxChars <= 3) {
            return value.substring(0, Math.max(0, maxChars));
        }
        return value.substring(0, maxChars - 2) + "..";
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
            UiRect nameBox,
            UiRect listBox,
            UiRect assignButton,
            UiRect clearButton,
            UiRect closeButton
    ) {
        static Layout empty() {
            UiRect empty = UiRect.empty();
            return new Layout(empty, empty, empty, empty, empty, empty, empty);
        }
    }
}
