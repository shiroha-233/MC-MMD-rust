/* 文件职责：提供生物模型替换选择器的共享托盘式界面。 */
package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MobReplacementPickerScreen extends Screen {
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
    private final Component targetTitle;
    private final Supplier<List<String>> modelSelectionSupplier;
    private final Consumer<String> selectionConsumer;

    private final List<String> allModels = new ArrayList<>();
    private final List<String> filteredModels = new ArrayList<>();

    private String currentValue;
    private String selectedModel;
    private EditBox searchBox;
    private float targetScroll;
    private float animatedScroll;
    private int hoveredIndex = -1;
    private ButtonTarget hoveredButton = ButtonTarget.NONE;
    private Layout layout = Layout.empty();

    private enum ButtonTarget {
        NONE,
        REFRESH,
        DONE,
        DEFAULT,
        CANCEL
    }

    public MobReplacementPickerScreen(
            Screen parent,
            Component targetTitle,
            String currentValue,
            Supplier<List<String>> modelSelectionSupplier,
            Consumer<String> selectionConsumer
    ) {
        super(Component.translatable("gui.mmdskin.mod_settings.category.mob_replacement"));
        this.parent = parent;
        this.targetTitle = targetTitle;
        this.currentValue = normalize(currentValue);
        this.selectedModel = UIConstants.DEFAULT_MODEL_NAME.equals(this.currentValue) ? null : this.currentValue;
        this.modelSelectionSupplier = modelSelectionSupplier;
        this.selectionConsumer = selectionConsumer;
    }

    @Override
    protected void init() {
        super.init();
        updateLayout();
        rebuildSearchBox();
        refreshModels();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateLayout();
        updateHoverState(mouseX, mouseY);
        updateScrollAnimation();

        drawFrame(guiGraphics);
        drawModelList(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (layout.refreshButton.contains(mouseX, mouseY)) {
            refreshModels();
            return true;
        }
        if (layout.doneButton.contains(mouseX, mouseY) && selectedModel != null) {
            applySelection();
            return true;
        }
        if (layout.defaultButton.contains(mouseX, mouseY)) {
            applyDefault();
            return true;
        }
        if (layout.cancelButton.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (layout.listBox.contains(mouseX, mouseY) && hoveredIndex >= 0 && hoveredIndex < filteredModels.size()) {
            selectedModel = filteredModels.get(hoveredIndex);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            if (selectedModel != null) {
                applySelection();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

    private void rebuildSearchBox() {
        String query = searchBox == null ? "" : searchBox.getValue();
        searchBox = new EditBox(this.font, layout.searchBox.x, layout.searchBox.y, layout.searchBox.w, layout.searchBox.h,
                Component.translatable("gui.mmdskin.model_selector.search"));
        searchBox.setMaxLength(64);
        searchBox.setBordered(false);
        searchBox.setTextColor(TranslucentTrayChrome.BODY_TEXT);
        searchBox.setTextColorUneditable(TranslucentTrayChrome.MUTED_TEXT);
        searchBox.setSuggestion(Component.translatable("gui.mmdskin.model_selector.search").getString());
        searchBox.setResponder(value -> {
            refreshFilter();
            targetScroll = 0.0f;
            animatedScroll = 0.0f;
        });
        searchBox.setValue(query);
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);
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
        UiRect refreshButton = new UiRect(searchBoxRect.x + searchBoxRect.w + BUTTON_GAP, searchY, REFRESH_BUTTON_WIDTH, SEARCH_HEIGHT);

        int buttonY = panel.y + panel.h - BUTTON_HEIGHT - 6;
        int buttonWidth = (header.w - BUTTON_GAP * 2) / 3;
        UiRect doneButton = new UiRect(header.x, buttonY, buttonWidth, BUTTON_HEIGHT);
        UiRect defaultButton = new UiRect(header.x + buttonWidth + BUTTON_GAP, buttonY, buttonWidth, BUTTON_HEIGHT);
        UiRect cancelButton = new UiRect(header.x + (buttonWidth + BUTTON_GAP) * 2, buttonY, buttonWidth, BUTTON_HEIGHT);

        int listY = searchY + SEARCH_HEIGHT + 6;
        int listHeight = Math.max(72, buttonY - listY - 6);
        UiRect listBox = new UiRect(header.x, listY, header.w, listHeight);
        layout = new Layout(panel, header, searchBoxRect, refreshButton, listBox, doneButton, defaultButton, cancelButton);

        if (searchBox != null) {
            searchBox.setX(searchBoxRect.x + 4);
            searchBox.setY(searchBoxRect.y + 1);
            searchBox.setWidth(Math.max(8, searchBoxRect.w - 8));
            searchBox.setHeight(searchBoxRect.h - 2);
        }

        float maxScroll = maxScroll();
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll);
        animatedScroll = Mth.clamp(animatedScroll, 0.0f, maxScroll);
    }

    private void refreshModels() {
        String query = searchBox == null ? "" : searchBox.getValue();
        allModels.clear();
        for (String modelName : modelSelectionSupplier.get()) {
            if (!UIConstants.DEFAULT_MODEL_NAME.equals(modelName) && !modelName.isBlank() && !allModels.contains(modelName)) {
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
        if (searchBox != null && !query.equals(searchBox.getValue())) {
            searchBox.setValue(query);
        }
        refreshFilter();
        targetScroll = 0.0f;
        animatedScroll = 0.0f;
    }

    private void refreshFilter() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        filteredModels.clear();
        for (String modelName : allModels) {
            if (query.isEmpty() || modelName.toLowerCase(Locale.ROOT).contains(query)) {
                filteredModels.add(modelName);
            }
        }
        if (selectedModel != null && !filteredModels.contains(selectedModel)) {
            selectedModel = filteredModels.isEmpty() ? null : filteredModels.get(0);
        }
    }

    private void drawFrame(GuiGraphics guiGraphics) {
        TranslucentTrayChrome.drawOverlay(guiGraphics, this.width, this.height);
        TranslucentTrayChrome.drawPanel(guiGraphics, layout.panel.x, layout.panel.y, layout.panel.w, layout.panel.h);

        guiGraphics.drawString(this.font, this.title.getString(), layout.header.x, layout.header.y + 1, TranslucentTrayChrome.TITLE_TEXT, false);
        guiGraphics.drawString(this.font, shorten(targetTitle.getString(), 24), layout.header.x, layout.header.y + 11, TranslucentTrayChrome.SUBTITLE_TEXT, false);
        guiGraphics.drawString(this.font, buildStatusText(), layout.header.x, layout.header.y + 21, TranslucentTrayChrome.DETAIL_TEXT, false);

        drawSearchBoxBackplate(guiGraphics);
        drawButton(guiGraphics, layout.refreshButton, Component.translatable("gui.mmdskin.refresh").getString(),
                hoveredButton == ButtonTarget.REFRESH, true);
        drawButton(guiGraphics, layout.doneButton, Component.translatable("gui.done").getString(),
                hoveredButton == ButtonTarget.DONE, selectedModel != null);
        drawButton(guiGraphics, layout.defaultButton, Component.translatable("gui.mmdskin.mod_settings.mob_replacement.vanilla").getString(),
                hoveredButton == ButtonTarget.DEFAULT, true);
        drawButton(guiGraphics, layout.cancelButton, Component.translatable("gui.cancel").getString(),
                hoveredButton == ButtonTarget.CANCEL, true);
    }

    private void drawSearchBoxBackplate(GuiGraphics guiGraphics) {
        boolean focused = searchBox != null && searchBox.isFocused();
        int fill = focused ? 0x30FFFFFF : TranslucentTrayChrome.LIST_BACKGROUND;
        guiGraphics.fill(layout.searchBox.x, layout.searchBox.y,
                layout.searchBox.x + layout.searchBox.w, layout.searchBox.y + layout.searchBox.h, fill);
    }

    private void drawModelList(GuiGraphics guiGraphics) {
        UiRect list = layout.listBox;
        TranslucentTrayChrome.fillListArea(guiGraphics, list.x, list.y, list.w, list.h);
        if (filteredModels.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "-", list.centerX(), list.centerY() - 4, TranslucentTrayChrome.DIM_TEXT);
            return;
        }

        guiGraphics.enableScissor(list.x, list.y, list.x + list.w, list.y + list.h);
        int y = Math.round(list.y + LIST_PADDING - animatedScroll);
        for (int i = 0; i < filteredModels.size(); i++) {
            if (y + CARD_HEIGHT < list.y) {
                y += CARD_HEIGHT + CARD_GAP;
                continue;
            }
            if (y > list.y + list.h) {
                break;
            }
            String modelName = filteredModels.get(i);
            boolean selected = modelName.equals(selectedModel);
            boolean hovered = i == hoveredIndex;
            guiGraphics.fill(list.x + 4, y, list.x + list.w - 4, y + CARD_HEIGHT,
                    TranslucentTrayChrome.cardBackground(selected, hovered));
            guiGraphics.drawString(this.font, shorten(modelName, 24), list.x + 7, y + 4, TranslucentTrayChrome.BODY_TEXT, false);
            y += CARD_HEIGHT + CARD_GAP;
        }
        guiGraphics.disableScissor();
        TranslucentTrayChrome.drawScrollbar(guiGraphics, list.x + list.w - 3, list.y, list.y + list.h, animatedScroll, maxScroll());
    }

    private void drawButton(GuiGraphics guiGraphics, UiRect rect, String text, boolean hovered, boolean enabled) {
        TranslucentTrayChrome.drawButton(guiGraphics, this.font, rect.x, rect.y, rect.w, rect.h, text, hovered, enabled);
    }

    private void updateHoverState(int mouseX, int mouseY) {
        hoveredButton = ButtonTarget.NONE;
        hoveredIndex = -1;

        if (layout.refreshButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.REFRESH;
            return;
        }
        if (layout.doneButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.DONE;
            return;
        }
        if (layout.defaultButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.DEFAULT;
            return;
        }
        if (layout.cancelButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.CANCEL;
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

    private void applySelection() {
        if (selectedModel == null || selectedModel.isBlank()) {
            return;
        }
        currentValue = selectedModel;
        selectionConsumer.accept(selectedModel);
        Minecraft.getInstance().setScreen(parent);
    }

    private void applyDefault() {
        currentValue = UIConstants.DEFAULT_MODEL_NAME;
        selectedModel = null;
        selectionConsumer.accept(UIConstants.DEFAULT_MODEL_NAME);
        Minecraft.getInstance().setScreen(parent);
    }

    private String buildStatusText() {
        String selection = buildSelectionText();
        return Component.translatable(
                "gui.mmdskin.model_selector.stats",
                filteredModels.size(),
                shorten(selection, 12)
        ).getString();
    }

    private String buildSelectionText() {
        if (selectedModel == null || selectedModel.isBlank()) {
            return Component.translatable("gui.mmdskin.mod_settings.mob_replacement.vanilla").getString();
        }
        return selectedModel;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return UIConstants.DEFAULT_MODEL_NAME;
        }
        return value;
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
}
