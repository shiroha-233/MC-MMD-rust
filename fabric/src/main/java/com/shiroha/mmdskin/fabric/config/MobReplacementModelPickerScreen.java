/* 文件职责：提供生物模型替换选择器的托盘式界面与交互。 */
package com.shiroha.mmdskin.fabric.config;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.render.entity.MobReplacementTargets;
import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** 文件职责：为生物替换配置提供搜索、滚动和确认交互。 */
public class MobReplacementModelPickerScreen extends Screen {
    private static final int WINDOW_MARGIN = 10;
    private static final int MIN_WINDOW_WIDTH = 252;
    private static final int MAX_WINDOW_WIDTH = 332;
    private static final int MIN_WINDOW_HEIGHT = 244;
    private static final int HEADER_HEIGHT = 34;
    private static final int SEARCH_HEIGHT = 18;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_GAP = 4;
    private static final int LIST_PADDING = 5;
    private static final int CARD_HEIGHT = 16;
    private static final int CARD_GAP = 4;
    private static final int REFRESH_BUTTON_WIDTH = 52;

    private final Screen parent;
    private final MobReplacementTargets.Target target;
    private final String currentValue;
    private final Consumer<String> selectionConsumer;

    private final List<String> allModels = new ArrayList<>();
    private final List<String> filteredModels = new ArrayList<>();

    private EditBox searchBox;
    private ChromeButton refreshButton;
    private ChromeButton chooseButton;
    private ChromeButton defaultButton;
    private ChromeButton cancelButton;

    private Layout layout = Layout.empty();
    private int hoveredIndex = -1;
    private float targetScroll;
    private float animatedScroll;
    private String selectedModel;

    public MobReplacementModelPickerScreen(Screen parent, MobReplacementTargets.Target target,
                                           String currentValue, Consumer<String> selectionConsumer) {
        super(Component.translatable("gui.mmdskin.mod_settings.category.mob_replacement"));
        this.parent = parent;
        this.target = target;
        this.currentValue = currentValue;
        this.selectionConsumer = selectionConsumer;
        this.selectedModel = UIConstants.DEFAULT_MODEL_NAME.equals(currentValue) ? null : currentValue;
    }

    @Override
    protected void init() {
        super.init();
        updateLayout();

        this.searchBox = new EditBox(this.font, layout.searchBox.x + 4, layout.searchBox.y + 1,
            Math.max(8, layout.searchBox.w - 8), layout.searchBox.h - 2,
            Component.translatable("gui.mmdskin.model_selector.search"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(TranslucentTrayChrome.BODY_TEXT);
        this.searchBox.setTextColorUneditable(TranslucentTrayChrome.MUTED_TEXT);
        this.searchBox.setSuggestion(Component.translatable("gui.mmdskin.model_selector.search").getString());
        this.searchBox.setResponder(value -> {
            refreshFilter();
            targetScroll = 0.0f;
            animatedScroll = 0.0f;
        });
        this.addRenderableWidget(this.searchBox);
        this.setInitialFocus(this.searchBox);

        this.refreshButton = this.addRenderableWidget(new ChromeButton(
            layout.refreshButton,
            Component.translatable("gui.mmdskin.refresh"),
            this::refreshModels
        ));
        this.chooseButton = this.addRenderableWidget(new ChromeButton(
            layout.doneButton,
            Component.translatable("gui.done"),
            this::applySelection
        ));
        this.defaultButton = this.addRenderableWidget(new ChromeButton(
            layout.defaultButton,
            Component.translatable("gui.mmdskin.mod_settings.mob_replacement.vanilla"),
            this::applyDefault
        ));
        this.cancelButton = this.addRenderableWidget(new ChromeButton(
            layout.cancelButton,
            Component.translatable("gui.cancel"),
            this::onClose
        ));

        refreshModels();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateLayout();
        updateHoverState(mouseX, mouseY);
        updateScrollAnimation();

        drawFrame(guiGraphics);
        drawModelList(guiGraphics, mouseX, mouseY);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && hoveredIndex >= 0 && hoveredIndex < filteredModels.size()) {
            selectedModel = filteredModels.get(hoveredIndex);
            updateChooseButtonState();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!layout.listBox.contains(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        targetScroll = Mth.clamp(targetScroll - (float) delta * 12.0f, 0.0f, maxScroll());
        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void refreshModels() {
        String search = this.searchBox == null ? "" : this.searchBox.getValue();
        allModels.clear();
        for (String modelName : ModConfigScreen.createModelSelections()) {
            if (!UIConstants.DEFAULT_MODEL_NAME.equals(modelName) && !allModels.contains(modelName)) {
                allModels.add(modelName);
            }
        }
        if (selectedModel != null && !selectedModel.isBlank() && !allModels.contains(selectedModel)) {
            allModels.add(selectedModel);
        }
        if (!UIConstants.DEFAULT_MODEL_NAME.equals(currentValue) && !currentValue.isBlank() && !allModels.contains(currentValue)) {
            allModels.add(currentValue);
        }
        allModels.sort(Comparator.comparing(value -> value.toLowerCase(Locale.ROOT)));
        if (this.searchBox != null && !search.equals(this.searchBox.getValue())) {
            this.searchBox.setValue(search);
        }
        refreshFilter();
        targetScroll = 0.0f;
        animatedScroll = 0.0f;
    }

    private void refreshFilter() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        filteredModels.clear();
        for (String modelName : allModels) {
            if (query.isEmpty() || modelName.toLowerCase(Locale.ROOT).contains(query)) {
                filteredModels.add(modelName);
            }
        }
        if (selectedModel != null && !filteredModels.contains(selectedModel)) {
            selectedModel = filteredModels.isEmpty() ? null : filteredModels.get(0);
        }
        float maxScroll = maxScroll();
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll);
        animatedScroll = Mth.clamp(animatedScroll, 0.0f, maxScroll);
        updateChooseButtonState();
    }

    private void applySelection() {
        if (selectedModel == null || selectedModel.isBlank()) {
            return;
        }
        selectionConsumer.accept(selectedModel);
        Minecraft.getInstance().setScreen(parent);
    }

    private void applyDefault() {
        selectionConsumer.accept(UIConstants.DEFAULT_MODEL_NAME);
        Minecraft.getInstance().setScreen(parent);
    }

    private void updateChooseButtonState() {
        if (chooseButton != null) {
            chooseButton.active = selectedModel != null;
        }
    }

    private void drawFrame(GuiGraphics guiGraphics) {
        TranslucentTrayChrome.drawOverlay(guiGraphics, this.width, this.height);
        TranslucentTrayChrome.drawPanel(guiGraphics, layout.panel.x, layout.panel.y, layout.panel.w, layout.panel.h);

        guiGraphics.drawString(this.font, this.title.getString(), layout.header.x, layout.header.y + 1, TranslucentTrayChrome.TITLE_TEXT, false);
        guiGraphics.drawString(this.font, truncate(target.displayName().getString(), 24), layout.header.x, layout.header.y + 11, TranslucentTrayChrome.SUBTITLE_TEXT, false);
        guiGraphics.drawString(this.font, buildStatusText(), layout.header.x, layout.header.y + 21, TranslucentTrayChrome.DETAIL_TEXT, false);
        drawSearchBoxBackplate(guiGraphics);
    }

    private void drawSearchBoxBackplate(GuiGraphics guiGraphics) {
        boolean focused = searchBox != null && searchBox.isFocused();
        int fill = focused ? 0x30FFFFFF : TranslucentTrayChrome.LIST_BACKGROUND;
        guiGraphics.fill(layout.searchBox.x, layout.searchBox.y,
            layout.searchBox.x + layout.searchBox.w, layout.searchBox.y + layout.searchBox.h, fill);
    }

    private void drawModelList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        hoveredIndex = -1;
        UiRect list = layout.listBox;
        TranslucentTrayChrome.fillListArea(guiGraphics, list.x, list.y, list.w, list.h);
        if (filteredModels.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "-", list.centerX(), list.centerY() - 4, TranslucentTrayChrome.DIM_TEXT);
            return;
        }

        guiGraphics.enableScissor(list.x, list.y, list.x + list.w, list.y + list.h);
        for (int i = 0; i < filteredModels.size(); i++) {
            int rowY = Math.round(list.y + LIST_PADDING + i * (CARD_HEIGHT + CARD_GAP) - animatedScroll);
            if (rowY + CARD_HEIGHT < list.y || rowY > list.y + list.h) {
                continue;
            }

            String modelName = filteredModels.get(i);
            int rowX = list.x + 4;
            int rowWidth = list.w - 8;
            boolean hovered = mouseX >= rowX && mouseX <= rowX + rowWidth
                && mouseY >= Math.max(rowY, list.y) && mouseY <= Math.min(rowY + CARD_HEIGHT, list.y + list.h);
            boolean selected = modelName.equals(selectedModel);
            if (hovered) {
                hoveredIndex = i;
            }

            guiGraphics.fill(rowX, rowY, rowX + rowWidth, rowY + CARD_HEIGHT,
                TranslucentTrayChrome.cardBackground(selected, hovered));
            guiGraphics.drawString(this.font, truncate(modelName, 24), rowX + 3, rowY + 4, TranslucentTrayChrome.BODY_TEXT, false);
        }
        guiGraphics.disableScissor();
        TranslucentTrayChrome.drawScrollbar(guiGraphics, list.x + list.w - 3, list.y, list.y + list.h, animatedScroll, maxScroll());
    }

    private void updateLayout() {
        int panelWidth = Mth.clamp(Math.round(this.width * 0.24f), MIN_WINDOW_WIDTH, MAX_WINDOW_WIDTH);
        int panelHeight = Math.max(MIN_WINDOW_HEIGHT, this.height - WINDOW_MARGIN * 2);
        int panelX = this.width - panelWidth - WINDOW_MARGIN;
        int panelY = WINDOW_MARGIN;

        UiRect panel = new UiRect(panelX, panelY, panelWidth, panelHeight);
        UiRect header = new UiRect(panelX + 8, panelY + 6, panelWidth - 16, HEADER_HEIGHT);

        int searchY = header.y + header.h + 2;
        int searchWidth = header.w - REFRESH_BUTTON_WIDTH - BUTTON_GAP;
        UiRect searchBoxRect = new UiRect(header.x, searchY, searchWidth, SEARCH_HEIGHT);
        UiRect refreshButtonRect = new UiRect(searchBoxRect.x + searchBoxRect.w + BUTTON_GAP, searchY, REFRESH_BUTTON_WIDTH, SEARCH_HEIGHT);

        int buttonY = panel.y + panel.h - BUTTON_HEIGHT - 6;
        int buttonWidth = (header.w - BUTTON_GAP * 2) / 3;
        UiRect doneButtonRect = new UiRect(header.x, buttonY, buttonWidth, BUTTON_HEIGHT);
        UiRect defaultButtonRect = new UiRect(header.x + buttonWidth + BUTTON_GAP, buttonY, buttonWidth, BUTTON_HEIGHT);
        UiRect cancelButtonRect = new UiRect(header.x + (buttonWidth + BUTTON_GAP) * 2, buttonY, buttonWidth, BUTTON_HEIGHT);

        int listY = searchY + SEARCH_HEIGHT + 6;
        int listHeight = Math.max(72, buttonY - listY - 6);
        UiRect listBox = new UiRect(header.x, listY, header.w, listHeight);
        layout = new Layout(panel, header, searchBoxRect, refreshButtonRect, listBox, doneButtonRect, defaultButtonRect, cancelButtonRect);

        if (searchBox != null) {
            searchBox.setX(searchBoxRect.x + 4);
            searchBox.setY(searchBoxRect.y + 1);
            searchBox.setWidth(Math.max(8, searchBoxRect.w - 8));
        }
        if (refreshButton != null) {
            refreshButton.setX(refreshButtonRect.x);
            refreshButton.setY(refreshButtonRect.y);
            refreshButton.setWidth(refreshButtonRect.w);
        }
        if (chooseButton != null) {
            chooseButton.setX(doneButtonRect.x);
            chooseButton.setY(doneButtonRect.y);
            chooseButton.setWidth(doneButtonRect.w);
        }
        if (defaultButton != null) {
            defaultButton.setX(defaultButtonRect.x);
            defaultButton.setY(defaultButtonRect.y);
            defaultButton.setWidth(defaultButtonRect.w);
        }
        if (cancelButton != null) {
            cancelButton.setX(cancelButtonRect.x);
            cancelButton.setY(cancelButtonRect.y);
            cancelButton.setWidth(cancelButtonRect.w);
        }

        float maxScroll = maxScroll();
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll);
        animatedScroll = Mth.clamp(animatedScroll, 0.0f, maxScroll);
    }

    private void updateHoverState(int mouseX, int mouseY) {
        hoveredIndex = -1;
        if (layout.refreshButton.contains(mouseX, mouseY)
            || layout.doneButton.contains(mouseX, mouseY)
            || layout.defaultButton.contains(mouseX, mouseY)
            || layout.cancelButton.contains(mouseX, mouseY)) {
            return;
        }
        if (!layout.listBox.contains(mouseX, mouseY) || filteredModels.isEmpty()) {
            return;
        }

        float localY = (float) mouseY - (layout.listBox.y + LIST_PADDING) + animatedScroll;
        if (localY < 0.0f) {
            return;
        }
        int index = (int) (localY / (CARD_HEIGHT + CARD_GAP));
        if (index < 0 || index >= filteredModels.size()) {
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
        int count = filteredModels.size();
        float contentHeight = count <= 0
            ? 0.0f
            : LIST_PADDING * 2.0f + count * CARD_HEIGHT + Math.max(0, count - 1) * CARD_GAP;
        return Math.max(0.0f, contentHeight - layout.listBox.h);
    }

    private String buildStatusText() {
        String selection = selectedModel == null || selectedModel.isBlank()
            ? Component.translatable("gui.mmdskin.mod_settings.mob_replacement.vanilla").getString()
            : selectedModel;
        return Component.translatable("gui.mmdskin.model_selector.stats", filteredModels.size(), truncate(selection, 12)).getString();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength - 2) + ".." : value;
    }

    private record Layout(
        UiRect panel,
        UiRect header,
        UiRect searchBox,
        UiRect refreshButton,
        UiRect listBox,
        UiRect doneButton,
        UiRect defaultButton,
        UiRect cancelButton
    ) {
        static Layout empty() {
            UiRect empty = UiRect.empty();
            return new Layout(empty, empty, empty, empty, empty, empty, empty, empty);
        }
    }

    private record UiRect(int x, int y, int w, int h) {
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

    private static final class ChromeButton extends AbstractButton {
        private final Runnable onPressAction;

        private ChromeButton(UiRect rect, Component message, Runnable onPressAction) {
            super(rect.x, rect.y, rect.w, rect.h, message);
            this.onPressAction = onPressAction;
        }

        @Override
        public void onPress() {
            onPressAction.run();
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            TranslucentTrayChrome.drawButton(
                guiGraphics,
                Minecraft.getInstance().font,
                this.getX(),
                this.getY(),
                this.getWidth(),
                this.getHeight(),
                this.getMessage().getString(),
                this.isHoveredOrFocused(),
                this.active
            );
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }
}
