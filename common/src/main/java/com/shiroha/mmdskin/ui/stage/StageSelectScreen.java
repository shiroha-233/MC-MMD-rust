package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.config.StageConfig;
import com.shiroha.mmdskin.stage.client.StagePlaybackCoordinator;
import com.shiroha.mmdskin.stage.client.asset.LocalStagePackRepository;
import com.shiroha.mmdskin.stage.client.playback.StageHostPlaybackService;
import com.shiroha.mmdskin.stage.client.viewmodel.StageLobbyViewModel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 舞台模式选择界面 — 左侧面板 + 右侧动作分配面板
 */
public class StageSelectScreen extends Screen {
    private static final int PANEL_WIDTH = 160;
    private static final int PANEL_MARGIN = 4;
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 56;
    private static final int GUEST_FOOTER_HEIGHT = 76;
    private static final int ITEM_HEIGHT = 14;
    private static final int ITEM_SPACING = 1;
    private static final int DETAIL_HEADER = 14;
    
    private static final int COLOR_PANEL_BG = 0xC0101418;
    private static final int COLOR_PANEL_BORDER = 0xFF2A3A4A;
    private static final int COLOR_ITEM_HOVER = 0x30FFFFFF;
    private static final int COLOR_ITEM_SELECTED = 0x3060A0D0;
    private static final int COLOR_ACCENT = 0xFF60A0D0;
    private static final int COLOR_TEXT = 0xFFDDDDDD;
    private static final int COLOR_TEXT_DIM = 0xFF888888;
    private static final int COLOR_TEXT_SELECTED = 0xFF60A0D0;
    private static final int COLOR_SEPARATOR = 0x30FFFFFF;
    private static final int COLOR_TOGGLE_ON = 0xFF40C080;
    private static final int COLOR_TOGGLE_OFF = 0xFF505560;
    private static final int COLOR_TAG_CAMERA = 0xFF90D060;
    private static final int COLOR_TAG_BONE = 0xFFD0A050;
    private static final int COLOR_TAG_MORPH = 0xFFD070A0;
    private static final int COLOR_BTN_START = 0xFF40A060;
    private static final int COLOR_TAG_AUDIO = 0xFF60B0E0;
    
    private List<StagePack> stagePacks = new ArrayList<>();
    
    private int selectedPackIndex = -1;
    private String selectedHostMotionFileName;
    private boolean cinematicMode;
    private float cameraHeightOffset;
    private boolean stageStarted = false;

    public void markStartedByHost() {
        this.stageStarted = true;
    }

    public void prepareForExternalClose() {
        this.stageStarted = true;
    }
    
    private int packScrollOffset = 0;
    private int packMaxScroll = 0;
    private int detailScrollOffset = 0;
    private int detailMaxScroll = 0;
    
    private int hoveredPackIndex = -1;
    private String hoveredDetailMotionFileName;
    private boolean hoverStart = false;
    private boolean hoverCancel = false;
    private boolean hoverToggle = false;
    private boolean draggingHeightSlider = false;
    private boolean hoverReady = false;
    private boolean hoverGuestCameraToggle = false;

    private int panelX, panelY, panelH;
    private int packListTop, packListBottom;
    private int detailTop, detailBottom;
    private int splitY;
    
    private StageAssignPanel assignPanel;
    private final StageLobbyViewModel lobbyViewModel = StageLobbyViewModel.getInstance();
    private final LocalStagePackRepository stagePackRepository = LocalStagePackRepository.getInstance();
    private final StageHostPlaybackService hostPlaybackService = StageHostPlaybackService.getInstance();
    private final StagePlaybackCoordinator playbackCoordinator = StagePlaybackCoordinator.getInstance();
    
    public StageSelectScreen() {
        super(Component.translatable("gui.mmdskin.config.stage_mode"));
        StageConfig config = StageConfig.getInstance();
        this.cinematicMode = config.cinematicMode;
        this.cameraHeightOffset = config.cameraHeightOffset;
        stagePacks = stagePackRepository.loadStagePacks();
        
        restoreSelection(config);
    }
    
    private void restoreSelection(StageConfig config) {
        if (!config.lastStagePack.isEmpty()) {
            for (int i = 0; i < stagePacks.size(); i++) {
                if (stagePacks.get(i).getName().equals(config.lastStagePack)) {
                    selectedPackIndex = i;
                    break;
                }
            }
        }
    }
    
    @Override
    protected void init() {
        super.init();
        playbackCoordinator.onStageSelectionOpened();

        panelX = PANEL_MARGIN;
        panelY = PANEL_MARGIN;
        panelH = this.height - PANEL_MARGIN * 2;
        
        boolean isGuest = lobbyViewModel.isSessionMember();
        int footerH = isGuest ? GUEST_FOOTER_HEIGHT : FOOTER_HEIGHT;
        
        packListTop = panelY + HEADER_HEIGHT;
        splitY = panelY + (int)((panelH - HEADER_HEIGHT - footerH) * 0.55f) + HEADER_HEIGHT;
        packListBottom = splitY - 2;
        
        detailTop = splitY + DETAIL_HEADER;
        detailBottom = panelY + panelH - footerH;
        
        updatePackScroll();
        updateDetailScroll();
        
        assignPanel = new StageAssignPanel(this.font);
        assignPanel.layout(this.width, this.height);
        StagePack selected = getSelectedPack();
        normalizeSelectedHostMotion(selected);
        if (selected != null) {
            assignPanel.setStagePack(selected);
        }
    }
    
    private int tickCounter = 0;
    
    @Override
    public void tick() {
        super.tick();
        if (++tickCounter % 20 == 0 && assignPanel != null) {
            assignPanel.refreshPlayers();
        }
    }
    
    private void updatePackScroll() {
        int contentH = stagePacks.size() * (ITEM_HEIGHT + ITEM_SPACING);
        int visibleH = packListBottom - packListTop;
        packMaxScroll = Math.max(0, contentH - visibleH);
        packScrollOffset = Math.max(0, Math.min(packMaxScroll, packScrollOffset));
    }
    
    private void updateDetailScroll() {
        StagePack selected = getSelectedPack();
        int fileCount = 0;
        if (selected != null) {
            fileCount = selected.getVmdFiles().size() + selected.getAudioFiles().size();
        }
        int contentH = fileCount * (ITEM_HEIGHT + ITEM_SPACING);
        int visibleH = detailBottom - detailTop;
        detailMaxScroll = Math.max(0, contentH - visibleH);
        detailScrollOffset = Math.max(0, Math.min(detailMaxScroll, detailScrollOffset));
    }
    
    private StagePack getSelectedPack() {
        if (selectedPackIndex >= 0 && selectedPackIndex < stagePacks.size()) {
            return stagePacks.get(selectedPackIndex);
        }
        return null;
    }

    private void normalizeSelectedHostMotion(StagePack pack) {
        if (selectedHostMotionFileName == null || pack == null) {
            return;
        }
        boolean exists = pack.getVmdFiles().stream()
                .filter(info -> info.hasBones || info.hasMorphs)
                .anyMatch(info -> info.name.equals(selectedHostMotionFileName));
        if (!exists) {
            selectedHostMotionFileName = null;
        }
    }
    
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, COLOR_PANEL_BG);
        g.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + panelH, COLOR_PANEL_BORDER);
        
        renderHeader(g);
        renderPackList(g, mouseX, mouseY);
        renderScrollbar(g, packListTop, packListBottom, packScrollOffset, packMaxScroll);
        renderDetailHeader(g);
        renderDetailList(g, mouseX, mouseY);
        renderScrollbar(g, detailTop, detailBottom, detailScrollOffset, detailMaxScroll);
        renderFooter(g, mouseX, mouseY);
        
        if (assignPanel != null) {
            assignPanel.render(g, mouseX, mouseY);
        }
        
        super.render(g, mouseX, mouseY, partialTick);
    }
    
    private void renderHeader(GuiGraphics g) {
        int cx = panelX + PANEL_WIDTH / 2;
        g.drawCenteredString(this.font, this.title, cx, panelY + 4, COLOR_ACCENT);
        
        String info = stagePacks.size() + " packs";
        g.drawCenteredString(this.font, info, cx, panelY + 16, COLOR_TEXT_DIM);
        
        g.fill(panelX + 8, packListTop - 2, panelX + PANEL_WIDTH - 8, packListTop - 1, COLOR_SEPARATOR);
    }
    
    private void renderPackList(GuiGraphics g, int mouseX, int mouseY) {
        g.enableScissor(panelX, packListTop, panelX + PANEL_WIDTH, packListBottom);
        
        hoveredPackIndex = -1;
        
        for (int i = 0; i < stagePacks.size(); i++) {
            StagePack pack = stagePacks.get(i);
            int itemY = packListTop + i * (ITEM_HEIGHT + ITEM_SPACING) - packScrollOffset;
            
            if (itemY + ITEM_HEIGHT < packListTop || itemY > packListBottom) continue;
            
            int itemX = panelX + 6;
            int itemW = PANEL_WIDTH - 12;
            boolean isSelected = (i == selectedPackIndex);
            boolean isHovered = mouseX >= itemX && mouseX <= itemX + itemW
                             && mouseY >= Math.max(itemY, packListTop) 
                             && mouseY <= Math.min(itemY + ITEM_HEIGHT, packListBottom);
            
            if (isHovered) hoveredPackIndex = i;
            
            if (isSelected) {
                g.fill(itemX, itemY, itemX + itemW, itemY + ITEM_HEIGHT, COLOR_ITEM_SELECTED);
                g.fill(itemX, itemY + 1, itemX + 2, itemY + ITEM_HEIGHT - 1, COLOR_ACCENT);
            } else if (isHovered) {
                g.fill(itemX, itemY, itemX + itemW, itemY + ITEM_HEIGHT, COLOR_ITEM_HOVER);
            }
            
            int nameStartX = itemX + 6;
            int nameColor = isSelected ? COLOR_TEXT_SELECTED : COLOR_TEXT;
            
            if (pack.hasAudio()) {
                g.drawString(this.font, "\u266B", nameStartX, itemY + 3, COLOR_TAG_AUDIO, false);
                nameStartX += 8;
            }
            
            String displayName = truncate(pack.getName(), pack.hasAudio() ? 15 : 16);
            g.drawString(this.font, displayName, nameStartX, itemY + 3, nameColor, false);
            
            if (isSelected) {
                g.drawString(this.font, "\u2713", itemX + itemW - 10, itemY + 3, COLOR_ACCENT, false);
            }
        }
        
        g.disableScissor();
    }
    
    private void renderDetailHeader(GuiGraphics g) {
        g.fill(panelX + 4, splitY - 1, panelX + PANEL_WIDTH - 4, splitY, COLOR_SEPARATOR);
        
        StagePack selected = getSelectedPack();
        String detailTitle;
        if (selected != null) {
            int total = selected.getVmdFiles().size() + selected.getAudioFiles().size();
            detailTitle = total + " files";
        } else {
            detailTitle = "---";
        }
        g.drawString(this.font, detailTitle, panelX + 8, splitY + 3, COLOR_TEXT_DIM, false);
    }
    
    private void renderDetailList(GuiGraphics g, int mouseX, int mouseY) {
        StagePack selected = getSelectedPack();
        if (selected == null) return;

        g.enableScissor(panelX, detailTop, panelX + PANEL_WIDTH, detailBottom);
        hoveredDetailMotionFileName = null;

        int row = 0;

        List<StagePack.VmdFileInfo> files = selected.getVmdFiles();
        for (int i = 0; i < files.size(); i++) {
            StagePack.VmdFileInfo info = files.get(i);
            int itemY = detailTop + row * (ITEM_HEIGHT + ITEM_SPACING) - detailScrollOffset;
            row++;
            
            if (itemY + ITEM_HEIGHT < detailTop || itemY > detailBottom) continue;
            
            int itemX = panelX + 6;
            int itemW = PANEL_WIDTH - 12;
            boolean selectableMotion = !lobbyViewModel.isSessionMember() && (info.hasBones || info.hasMorphs);
            boolean isSelectedMotion = selectableMotion && info.name.equals(selectedHostMotionFileName);
            boolean isHoveredMotion = selectableMotion
                    && mouseX >= itemX && mouseX <= itemX + itemW
                    && mouseY >= Math.max(itemY, detailTop)
                    && mouseY <= Math.min(itemY + ITEM_HEIGHT, detailBottom);

            if (isHoveredMotion) {
                hoveredDetailMotionFileName = info.name;
            }

            if (isSelectedMotion) {
                g.fill(itemX, itemY, itemX + itemW, itemY + ITEM_HEIGHT, COLOR_ITEM_SELECTED);
                g.fill(itemX, itemY + 1, itemX + 2, itemY + ITEM_HEIGHT - 1, COLOR_ACCENT);
            } else if (isHoveredMotion) {
                g.fill(itemX, itemY, itemX + itemW, itemY + ITEM_HEIGHT, COLOR_ITEM_HOVER);
            }

            String fileName = info.name;
            if (fileName.toLowerCase().endsWith(".vmd")) {
                fileName = fileName.substring(0, fileName.length() - 4);
            }
            fileName = truncate(fileName, 14);
            g.drawString(this.font, fileName, itemX + 4, itemY + 3, COLOR_TEXT, false);

            int tagX = itemX + itemW;
            if (isSelectedMotion) {
                tagX -= 10;
                g.drawString(this.font, "\u2713", tagX, itemY + 3, COLOR_ACCENT, false);
            }
            if (info.hasCamera) {
                tagX -= 10;
                g.drawString(this.font, "\uD83D\uDCF7", tagX, itemY + 3, COLOR_TAG_CAMERA, false);
            }
            if (info.hasBones) {
                tagX -= 10;
                g.drawString(this.font, "\uD83E\uDDB4", tagX, itemY + 3, COLOR_TAG_BONE, false);
            }
            if (info.hasMorphs) {
                tagX -= 10;
                g.drawString(this.font, "\uD83D\uDE0A", tagX, itemY + 3, COLOR_TAG_MORPH, false);
            }
        }
        
        List<StagePack.AudioFileInfo> audios = selected.getAudioFiles();
        for (int i = 0; i < audios.size(); i++) {
            StagePack.AudioFileInfo audio = audios.get(i);
            int itemY = detailTop + row * (ITEM_HEIGHT + ITEM_SPACING) - detailScrollOffset;
            row++;
            
            if (itemY + ITEM_HEIGHT < detailTop || itemY > detailBottom) continue;
            
            int itemX = panelX + 6;
            int itemW = PANEL_WIDTH - 12;
            
            String fileName = audio.name;
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0) fileName = fileName.substring(0, dot);
            fileName = truncate(fileName, 14);
            g.drawString(this.font, fileName, itemX + 4, itemY + 3, COLOR_TEXT, false);
            
            String formatTag = "\u266B" + audio.format;
            int tagW = this.font.width(formatTag);
            g.drawString(this.font, formatTag, itemX + itemW - tagW, itemY + 3, COLOR_TAG_AUDIO, false);
        }
        
        g.disableScissor();
    }
    
    private void renderScrollbar(GuiGraphics g, int top, int bottom, int offset, int maxScroll) {
        if (maxScroll <= 0) return;
        
        int barX = panelX + PANEL_WIDTH - 5;
        int barH = bottom - top;
        
        g.fill(barX, top, barX + 2, bottom, 0x20FFFFFF);
        
        int thumbH = Math.max(10, barH * barH / (barH + maxScroll));
        int thumbY = top + (int)((barH - thumbH) * ((float) offset / maxScroll));
        g.fill(barX, thumbY, barX + 2, thumbY + thumbH, COLOR_ACCENT);
    }
    
    private void renderFooter(GuiGraphics g, int mouseX, int mouseY) {
        boolean isGuest = lobbyViewModel.isSessionMember();
        int footerH = isGuest ? GUEST_FOOTER_HEIGHT : FOOTER_HEIGHT;
        int footerY = panelY + panelH - footerH;
        
        g.fill(panelX + 8, footerY, panelX + PANEL_WIDTH - 8, footerY + 1, COLOR_SEPARATOR);
        
        if (isGuest) {
            renderGuestFooter(g, mouseX, mouseY, footerY);
        } else {
            renderHostFooter(g, mouseX, mouseY, footerY);
        }
    }
    
    
    private void renderHostFooter(GuiGraphics g, int mouseX, int mouseY, int footerY) {
        int toggleX = panelX + 8;
        int toggleY = footerY + 4;
        int toggleW = 20;
        int toggleH = 10;
        
        hoverToggle = mouseX >= toggleX && mouseX < toggleX + PANEL_WIDTH - 16 
                    && mouseY >= toggleY && mouseY < toggleY + toggleH;
        
        int toggleColor = cinematicMode ? COLOR_TOGGLE_ON : COLOR_TOGGLE_OFF;
        g.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, toggleColor);
        int dotX = cinematicMode ? toggleX + toggleW - toggleH : toggleX;
        g.fill(dotX + 1, toggleY + 1, dotX + toggleH - 1, toggleY + toggleH - 1, 0xFFFFFFFF);
        g.drawString(this.font, Component.translatable("gui.mmdskin.stage.cinematic"), 
                     toggleX + toggleW + 4, toggleY + 1, COLOR_TEXT, false);
        
        int sliderY = footerY + 18;
        int sliderX = panelX + 8;
        int sliderW = PANEL_WIDTH - 16;
        
        String heightLabel = String.format("H: %+.2f", cameraHeightOffset);
        g.drawString(this.font, heightLabel, sliderX, sliderY, COLOR_TEXT_DIM, false);
        
        int trackX = sliderX + 36;
        int trackW = sliderW - 36;
        int trackY = sliderY + 3;
        int trackH = 4;
        g.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0xFF303030);
        
        float normalizedValue = (cameraHeightOffset + 2.0f) / 4.0f;
        normalizedValue = Math.max(0, Math.min(1, normalizedValue));
        int thumbX = trackX + (int)(normalizedValue * (trackW - 6));
        g.fill(thumbX, trackY - 1, thumbX + 6, trackY + trackH + 1, COLOR_ACCENT);
        
        int btnY = footerY + 34;
        int btnW = (PANEL_WIDTH - 20) / 2;
        int btnH = 16;
        
        int cancelX = panelX + PANEL_WIDTH - 6 - btnW;
        hoverCancel = mouseX >= cancelX && mouseX < cancelX + btnW 
                    && mouseY >= btnY && mouseY < btnY + btnH;
        int cancelColor = hoverCancel ? 0xFF888888 : 0xFF555555;
        g.fill(cancelX, btnY, cancelX + btnW, btnY + btnH, cancelColor);
        g.drawCenteredString(this.font, Component.translatable("gui.cancel"), 
                           cancelX + btnW / 2, btnY + 4, COLOR_TEXT);
        
        int startX = panelX + 6;
        boolean canStart = canStartStage();
        hoverStart = canStart && mouseX >= startX && mouseX < startX + btnW
                   && mouseY >= btnY && mouseY < btnY + btnH;
        int startColor = canStart ? (hoverStart ? 0xFF50C070 : COLOR_BTN_START) : 0xFF333333;
        g.fill(startX, btnY, startX + btnW, btnY + btnH, startColor);
        
        boolean hasAccepted = !lobbyViewModel.getAcceptedMembers().isEmpty();
        boolean allReady = lobbyViewModel.allMembersReady();
        
        if (hasAccepted && !allReady) {
            g.drawCenteredString(this.font, 
                Component.translatable("gui.mmdskin.stage.waiting_ready"),
                startX + btnW / 2, btnY + 4,
                COLOR_TEXT_DIM);
        } else {
            g.drawCenteredString(this.font, 
                "\u25B6 " + Component.translatable("gui.mmdskin.stage.start").getString(),
                startX + btnW / 2, btnY + 4,
                canStart ? 0xFFFFFFFF : COLOR_TEXT_DIM);
        }
    }
    

    private void renderGuestFooter(GuiGraphics g, int mouseX, int mouseY, int footerY) {
        boolean useHostCamera = lobbyViewModel.isUseHostCamera();
        boolean guestReady = lobbyViewModel.isLocalReady();
        
        int toggleX = panelX + 8;
        int toggleY = footerY + 4;
        int toggleW = 20;
        int toggleH = 10;
        
        hoverGuestCameraToggle = mouseX >= toggleX && mouseX < toggleX + PANEL_WIDTH - 16
                               && mouseY >= toggleY && mouseY < toggleY + toggleH;
        
        int toggleColor = useHostCamera ? COLOR_TOGGLE_ON : COLOR_TOGGLE_OFF;
        g.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, toggleColor);
        int dotX = useHostCamera ? toggleX + toggleW - toggleH : toggleX;
        g.fill(dotX + 1, toggleY + 1, dotX + toggleH - 1, toggleY + toggleH - 1, 0xFFFFFFFF);
        g.drawString(this.font, Component.translatable("gui.mmdskin.stage.use_host_camera"),
                     toggleX + toggleW + 4, toggleY + 1, COLOR_TEXT, false);
        
        int toggle2Y = footerY + 18;
        hoverToggle = mouseX >= toggleX && mouseX < toggleX + PANEL_WIDTH - 16
                    && mouseY >= toggle2Y && mouseY < toggle2Y + toggleH;
        
        int toggle2Color = cinematicMode ? COLOR_TOGGLE_ON : COLOR_TOGGLE_OFF;
        g.fill(toggleX, toggle2Y, toggleX + toggleW, toggle2Y + toggleH, toggle2Color);
        int dot2X = cinematicMode ? toggleX + toggleW - toggleH : toggleX;
        g.fill(dot2X + 1, toggle2Y + 1, dot2X + toggleH - 1, toggle2Y + toggleH - 1, 0xFFFFFFFF);
        g.drawString(this.font, Component.translatable("gui.mmdskin.stage.cinematic"),
                     toggleX + toggleW + 4, toggle2Y + 1, COLOR_TEXT, false);
        
        int btnY = footerY + 34;
        int btnW = (PANEL_WIDTH - 20) / 2;
        int btnH = 16;
        
        int cancelX = panelX + PANEL_WIDTH - 6 - btnW;
        hoverCancel = mouseX >= cancelX && mouseX < cancelX + btnW
                    && mouseY >= btnY && mouseY < btnY + btnH;
        int cancelColor = hoverCancel ? 0xFF888888 : 0xFF555555;
        g.fill(cancelX, btnY, cancelX + btnW, btnY + btnH, cancelColor);
        g.drawCenteredString(this.font, Component.translatable("gui.cancel"),
                           cancelX + btnW / 2, btnY + 4, COLOR_TEXT);
        
        int readyX = panelX + 6;
        hoverReady = mouseX >= readyX && mouseX < readyX + btnW
                   && mouseY >= btnY && mouseY < btnY + btnH;
        int readyColor = guestReady
                ? (hoverReady ? 0xFF4F90AC : 0xFF3B7188)
                : (hoverReady ? 0xFF50C070 : COLOR_BTN_START);
        g.fill(readyX, btnY, readyX + btnW, btnY + btnH, readyColor);
        g.drawCenteredString(this.font,
            Component.translatable(guestReady ? "gui.mmdskin.stage.unready" : "gui.mmdskin.stage.ready"),
            readyX + btnW / 2, btnY + 4, 0xFFFFFFFF);
        
        g.drawCenteredString(this.font,
            Component.translatable(guestReady ? "gui.mmdskin.stage.ready_done" : "gui.mmdskin.stage.waiting_host"),
            panelX + PANEL_WIDTH / 2, btnY + 22, guestReady ? COLOR_TOGGLE_ON : COLOR_TEXT_DIM);
    }
    
    private boolean canStartStage() {
        StagePack selected = getSelectedPack();
        if (selected == null || !selected.hasMotionVmd()) return false;
        if (!lobbyViewModel.getAcceptedMembers().isEmpty() && !lobbyViewModel.allMembersReady()) return false;
        return true;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (assignPanel != null && assignPanel.isInside(mouseX, mouseY)) {
                if (assignPanel.mouseClicked(mouseX, mouseY, button)) return true;
            }
            
            boolean isGuest = lobbyViewModel.isSessionMember();
            
            if (!isGuest) {
                int footerY = panelY + panelH - FOOTER_HEIGHT;
                int sliderY = footerY + 18;
                int trackX = panelX + 8 + 36;
                int trackW = PANEL_WIDTH - 16 - 36;
                if (mouseX >= trackX && mouseX < trackX + trackW && mouseY >= sliderY && mouseY < sliderY + 10) {
                    draggingHeightSlider = true;
                    updateHeightSliderFromMouse(mouseX, trackX, trackW);
                    return true;
                }
            }
            
            if (isGuest && hoverGuestCameraToggle) {
                lobbyViewModel.setUseHostCamera(!lobbyViewModel.isUseHostCamera());
                return true;
            }

            if (isGuest && hoverReady) {
                lobbyViewModel.setLocalReady(!lobbyViewModel.isLocalReady());
                return true;
            }
            
            if (hoveredPackIndex >= 0 && hoveredPackIndex < stagePacks.size()) {
                selectedPackIndex = hoveredPackIndex;
                detailScrollOffset = 0;
                normalizeSelectedHostMotion(getSelectedPack());
                updateDetailScroll();
                if (assignPanel != null) {
                    assignPanel.setStagePack(getSelectedPack());
                }
                return true;
            }
            if (!isGuest && hoveredDetailMotionFileName != null) {
                selectedHostMotionFileName = hoveredDetailMotionFileName.equals(selectedHostMotionFileName)
                        ? null
                        : hoveredDetailMotionFileName;
                return true;
            }
            if (hoverToggle) {
                cinematicMode = !cinematicMode;
                return true;
            }
            if (hoverStart) {
                startStage();
                return true;
            }
            if (hoverCancel) {
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingHeightSlider && button == 0) {
            int trackX = panelX + 8 + 36;
            int trackW = PANEL_WIDTH - 16 - 36;
            updateHeightSliderFromMouse(mouseX, trackX, trackW);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingHeightSlider) {
            draggingHeightSlider = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    private void updateHeightSliderFromMouse(double mouseX, int trackX, int trackW) {
        float normalized = (float)(mouseX - trackX) / trackW;
        normalized = Math.max(0, Math.min(1, normalized));
        cameraHeightOffset = normalized * 4.0f - 2.0f;
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (assignPanel != null && assignPanel.isInside(mouseX, mouseY)) {
            return assignPanel.mouseScrolled(mouseX, mouseY, delta);
        }
        if (mouseX < panelX || mouseX > panelX + PANEL_WIDTH) return false;
        
        int scrollAmount = (int) (-delta * (ITEM_HEIGHT + ITEM_SPACING) * 3);
        
        if (mouseY < splitY) {
            packScrollOffset = Math.max(0, Math.min(packMaxScroll, packScrollOffset + scrollAmount));
        } else {
            detailScrollOffset = Math.max(0, Math.min(detailMaxScroll, detailScrollOffset + scrollAmount));
        }
        return true;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    private void startStage() {
        StagePack pack = getSelectedPack();
        if (pack == null || !pack.hasMotionVmd()) {
            return;
        }
        boolean started = hostPlaybackService.startPack(pack, cinematicMode, cameraHeightOffset, selectedHostMotionFileName);
        if (!started) {
            return;
        }

        this.stageStarted = true;
        this.onClose();
    }
    
    @Override
    public void onClose() {
        playbackCoordinator.onStageSelectionClosed(stageStarted);
        super.onClose();
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 2) + ".." : s;
    }
    
}
