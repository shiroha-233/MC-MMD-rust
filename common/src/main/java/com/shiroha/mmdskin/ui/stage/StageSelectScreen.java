package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.config.StageConfig;
import com.shiroha.mmdskin.renderer.camera.MMDCameraController;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
import com.shiroha.mmdskin.ui.config.ModelSelectorConfig;
import com.shiroha.mmdskin.ui.network.StageNetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 舞台模式选择界面 — 左侧面板风格
 * 上半部分：舞台包列表（子文件夹）
 * 下半部分：选中包的 VMD 文件详情
 * 底部：影院模式开关 + 开始/取消按钮
 */
public class StageSelectScreen extends Screen {
    private static final Logger logger = LogManager.getLogger();
    
    // 左侧面板布局（与 ModelSelectorScreen 统一风格，但在左侧）
    private static final int PANEL_WIDTH = 160;
    private static final int PANEL_MARGIN = 4;
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 56;
    private static final int ITEM_HEIGHT = 14;
    private static final int ITEM_SPACING = 1;
    private static final int DETAIL_HEADER = 14;
    
    // 统一简约配色（与 ModelSelectorScreen 一致）
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
    
    // 舞台包列表
    private List<StagePack> stagePacks = new ArrayList<>();
    
    // 选择状态
    private int selectedPackIndex = -1;
    private boolean cinematicMode;
    private float cameraHeightOffset;
    private boolean stageStarted = false;
    
    // 滚动
    private int packScrollOffset = 0;
    private int packMaxScroll = 0;
    private int detailScrollOffset = 0;
    private int detailMaxScroll = 0;
    
    // 悬停
    private int hoveredPackIndex = -1;
    private boolean hoverStart = false;
    private boolean hoverCancel = false;
    private boolean hoverToggle = false;
    private boolean draggingHeightSlider = false;
    
    // 面板区域缓存
    private int panelX, panelY, panelH;
    private int packListTop, packListBottom;
    private int detailTop, detailBottom;
    // 包列表占面板上半部分（60%），详情占下半部分（40%）
    private int splitY;
    
    public StageSelectScreen() {
        super(Component.translatable("gui.mmdskin.config.stage_mode"));
        StageConfig config = StageConfig.getInstance();
        this.cinematicMode = config.cinematicMode;
        this.cameraHeightOffset = config.cameraHeightOffset;
        
        // 确保 StageAnim 目录存在
        PathConstants.ensureStageAnimDir();
        
        // 扫描舞台包
        stagePacks = StagePack.scan(PathConstants.getStageAnimDir(), path -> {
            NativeFunc nf = NativeFunc.GetInst();
            long tempAnim = nf.LoadAnimation(0, path);
            if (tempAnim == 0) return null;
            boolean[] result = { nf.HasCameraData(tempAnim), nf.HasBoneData(tempAnim), nf.HasMorphData(tempAnim) };
            nf.DeleteAnimation(tempAnim);
            return result;
        });
        
        // 恢复上次选择
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
        
        // 立即进入舞台模式（切换第三人称 + 相机过渡到展示位置）
        MMDCameraController.getInstance().enterStageMode();
        
        // 面板位置：屏幕左侧
        panelX = PANEL_MARGIN;
        panelY = PANEL_MARGIN;
        panelH = this.height - PANEL_MARGIN * 2;
        
        // 包列表区域
        packListTop = panelY + HEADER_HEIGHT;
        splitY = panelY + (int)((panelH - HEADER_HEIGHT - FOOTER_HEIGHT) * 0.55f) + HEADER_HEIGHT;
        packListBottom = splitY - 2;
        
        // 详情区域
        detailTop = splitY + DETAIL_HEADER;
        detailBottom = panelY + panelH - FOOTER_HEIGHT;
        
        // 计算包列表滚动
        updatePackScroll();
        updateDetailScroll();
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
    
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 不渲染全屏背景，保持右侧透明
        
        // 左侧面板背景
        g.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, COLOR_PANEL_BG);
        // 面板右边框
        g.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + panelH, COLOR_PANEL_BORDER);
        
        // 头部
        renderHeader(g);
        
        // 包列表
        renderPackList(g, mouseX, mouseY);
        
        // 包列表滚动条
        renderScrollbar(g, packListTop, packListBottom, packScrollOffset, packMaxScroll);
        
        // 分隔线 + 详情标题
        renderDetailHeader(g);
        
        // 详情列表
        renderDetailList(g, mouseX, mouseY);
        
        // 详情滚动条
        renderScrollbar(g, detailTop, detailBottom, detailScrollOffset, detailMaxScroll);
        
        // 底部控件
        renderFooter(g, mouseX, mouseY);
        
        super.render(g, mouseX, mouseY, partialTick);
    }
    
    private void renderHeader(GuiGraphics g) {
        int cx = panelX + PANEL_WIDTH / 2;
        g.drawCenteredString(this.font, this.title, cx, panelY + 4, COLOR_ACCENT);
        
        String info = stagePacks.size() + " packs";
        g.drawCenteredString(this.font, info, cx, panelY + 16, COLOR_TEXT_DIM);
        
        // 分隔线
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
            
            // 背景
            if (isSelected) {
                g.fill(itemX, itemY, itemX + itemW, itemY + ITEM_HEIGHT, COLOR_ITEM_SELECTED);
                g.fill(itemX, itemY + 1, itemX + 2, itemY + ITEM_HEIGHT - 1, COLOR_ACCENT);
            } else if (isHovered) {
                g.fill(itemX, itemY, itemX + itemW, itemY + ITEM_HEIGHT, COLOR_ITEM_HOVER);
            }
            
            // 包名
            int nameStartX = itemX + 6;
            int nameColor = isSelected ? COLOR_TEXT_SELECTED : COLOR_TEXT;
            
            // 音频标记（包名前方）
            if (pack.hasAudio()) {
                g.drawString(this.font, "\u266B", nameStartX, itemY + 3, COLOR_TAG_AUDIO, false);
                nameStartX += 8;
            }
            
            String displayName = truncate(pack.getName(), pack.hasAudio() ? 15 : 16);
            g.drawString(this.font, displayName, nameStartX, itemY + 3, nameColor, false);
            
            // 选中标记
            if (isSelected) {
                g.drawString(this.font, "\u2713", itemX + itemW - 10, itemY + 3, COLOR_ACCENT, false);
            }
        }
        
        g.disableScissor();
    }
    
    private void renderDetailHeader(GuiGraphics g) {
        // 分隔线
        g.fill(panelX + 4, splitY - 1, panelX + PANEL_WIDTH - 4, splitY, COLOR_SEPARATOR);
        
        // 详情标题
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
        
        int row = 0;
        
        // VMD 文件
        List<StagePack.VmdFileInfo> files = selected.getVmdFiles();
        for (int i = 0; i < files.size(); i++) {
            StagePack.VmdFileInfo info = files.get(i);
            int itemY = detailTop + row * (ITEM_HEIGHT + ITEM_SPACING) - detailScrollOffset;
            row++;
            
            if (itemY + ITEM_HEIGHT < detailTop || itemY > detailBottom) continue;
            
            int itemX = panelX + 6;
            int itemW = PANEL_WIDTH - 12;
            
            // 文件名（去掉 .vmd 后缀）
            String fileName = info.name;
            if (fileName.toLowerCase().endsWith(".vmd")) {
                fileName = fileName.substring(0, fileName.length() - 4);
            }
            fileName = truncate(fileName, 14);
            g.drawString(this.font, fileName, itemX + 4, itemY + 3, COLOR_TEXT, false);
            
            // 类型标签（右对齐）
            int tagX = itemX + itemW;
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
        
        // 音频文件
        List<StagePack.AudioFileInfo> audios = selected.getAudioFiles();
        for (int i = 0; i < audios.size(); i++) {
            StagePack.AudioFileInfo audio = audios.get(i);
            int itemY = detailTop + row * (ITEM_HEIGHT + ITEM_SPACING) - detailScrollOffset;
            row++;
            
            if (itemY + ITEM_HEIGHT < detailTop || itemY > detailBottom) continue;
            
            int itemX = panelX + 6;
            int itemW = PANEL_WIDTH - 12;
            
            // 文件名（去掉扩展名）
            String fileName = audio.name;
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0) fileName = fileName.substring(0, dot);
            fileName = truncate(fileName, 14);
            g.drawString(this.font, fileName, itemX + 4, itemY + 3, COLOR_TEXT, false);
            
            // 音频格式标签（右对齐）
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
        
        // 轨道
        g.fill(barX, top, barX + 2, bottom, 0x20FFFFFF);
        
        // 滑块
        int thumbH = Math.max(10, barH * barH / (barH + maxScroll));
        int thumbY = top + (int)((barH - thumbH) * ((float) offset / maxScroll));
        g.fill(barX, thumbY, barX + 2, thumbY + thumbH, COLOR_ACCENT);
    }
    
    private void renderFooter(GuiGraphics g, int mouseX, int mouseY) {
        int footerY = panelY + panelH - FOOTER_HEIGHT;
        
        // 分隔线
        g.fill(panelX + 8, footerY, panelX + PANEL_WIDTH - 8, footerY + 1, COLOR_SEPARATOR);
        
        // 影院模式开关（第一行）
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
        
        // 镜头高度滑块（第二行）
        int sliderY = footerY + 18;
        int sliderX = panelX + 8;
        int sliderW = PANEL_WIDTH - 16;
        int sliderH = 10;
        
        // 滑块标签 + 数值
        String heightLabel = String.format("H: %+.2f", cameraHeightOffset);
        g.drawString(this.font, heightLabel, sliderX, sliderY, COLOR_TEXT_DIM, false);
        
        // 滑块轨道
        int trackX = sliderX + 36;
        int trackW = sliderW - 36;
        int trackY = sliderY + 3;
        int trackH = 4;
        g.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0xFF303030);
        
        // 滑块位置（范围 -2.0 ~ +2.0）
        float normalizedValue = (cameraHeightOffset + 2.0f) / 4.0f;
        normalizedValue = Math.max(0, Math.min(1, normalizedValue));
        int thumbX = trackX + (int)(normalizedValue * (trackW - 6));
        g.fill(thumbX, trackY - 1, thumbX + 6, trackY + trackH + 1, COLOR_ACCENT);
        
        // 按钮行（第三行）
        int btnY = footerY + 34;
        int btnW = (PANEL_WIDTH - 20) / 2;
        int btnH = 16;
        
        // 取消按钮
        int cancelX = panelX + PANEL_WIDTH - 6 - btnW;
        hoverCancel = mouseX >= cancelX && mouseX < cancelX + btnW 
                    && mouseY >= btnY && mouseY < btnY + btnH;
        int cancelColor = hoverCancel ? 0xFF888888 : 0xFF555555;
        g.fill(cancelX, btnY, cancelX + btnW, btnY + btnH, cancelColor);
        g.drawCenteredString(this.font, Component.translatable("gui.cancel"), 
                           cancelX + btnW / 2, btnY + 4, COLOR_TEXT);
        
        // 开始按钮
        int startX = panelX + 6;
        boolean canStart = canStartStage();
        hoverStart = canStart && mouseX >= startX && mouseX < startX + btnW 
                   && mouseY >= btnY && mouseY < btnY + btnH;
        int startColor = canStart ? (hoverStart ? 0xFF50C070 : COLOR_BTN_START) : 0xFF333333;
        g.fill(startX, btnY, startX + btnW, btnY + btnH, startColor);
        g.drawCenteredString(this.font, "\u25B6 " + Component.translatable("gui.mmdskin.stage.start").getString(), 
                           startX + btnW / 2, btnY + 4, 
                           canStart ? 0xFFFFFFFF : COLOR_TEXT_DIM);
    }
    
    /**
     * 判断是否可以开始：选中的包中至少有 1 个非相机 VMD
     */
    private boolean canStartStage() {
        StagePack selected = getSelectedPack();
        return selected != null && selected.hasMotionVmd();
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 镜头高度滑块
            int footerY = panelY + panelH - FOOTER_HEIGHT;
            int sliderY = footerY + 18;
            int trackX = panelX + 8 + 36;
            int trackW = PANEL_WIDTH - 16 - 36;
            if (mouseX >= trackX && mouseX < trackX + trackW && mouseY >= sliderY && mouseY < sliderY + 10) {
                draggingHeightSlider = true;
                updateHeightSliderFromMouse(mouseX, trackX, trackW);
                return true;
            }
            // 包列表点击
            if (hoveredPackIndex >= 0 && hoveredPackIndex < stagePacks.size()) {
                selectedPackIndex = hoveredPackIndex;
                detailScrollOffset = 0;
                updateDetailScroll();
                return true;
            }
            // 影院模式开关
            if (hoverToggle) {
                cinematicMode = !cinematicMode;
                return true;
            }
            // 开始按钮
            if (hoverStart) {
                startStage();
                return true;
            }
            // 取消按钮
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
        cameraHeightOffset = normalized * 4.0f - 2.0f; // 范围 -2.0 ~ +2.0
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < panelX || mouseX > panelX + PANEL_WIDTH) return false;
        
        int scrollAmount = (int) (-delta * (ITEM_HEIGHT + ITEM_SPACING) * 3);
        
        if (mouseY < splitY) {
            // 包列表滚动
            packScrollOffset = Math.max(0, Math.min(packMaxScroll, packScrollOffset + scrollAmount));
        } else {
            // 详情列表滚动
            detailScrollOffset = Math.max(0, Math.min(detailMaxScroll, detailScrollOffset + scrollAmount));
        }
        return true;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    /**
     * Phase C: 多文件播放逻辑
     * 1. 收集选中 StagePack 中的所有 VMD 文件
     * 2. 找出相机 VMD（第一个 hasCamera=true 的文件）
     * 3. 加载所有非相机 VMD 并合并为一个动画
     * 4. 将合并后的动画 TransitionLayerTo 到模型 layer 0
     * 5. 加载相机 VMD 到 MMDCameraController
     */
    private void startStage() {
        StagePack pack = getSelectedPack();
        if (pack == null || !pack.hasMotionVmd()) return;
        
        NativeFunc nf = NativeFunc.GetInst();
        Minecraft mc = Minecraft.getInstance();
        
        // 保存配置
        StageConfig config = StageConfig.getInstance();
        config.lastStagePack = pack.getName();
        config.cinematicMode = cinematicMode;
        config.cameraHeightOffset = cameraHeightOffset;
        config.save();
        
        // 分离相机和动作 VMD
        StagePack.VmdFileInfo cameraFile = null;
        List<StagePack.VmdFileInfo> motionFiles = new ArrayList<>();
        
        for (StagePack.VmdFileInfo info : pack.getVmdFiles()) {
            if (info.hasCamera && cameraFile == null) {
                cameraFile = info;
            }
            if (info.hasBones || info.hasMorphs) {
                motionFiles.add(info);
            }
        }
        
        if (motionFiles.isEmpty()) {
            logger.warn("[舞台模式] 没有可用的动作 VMD");
            return;
        }
        
        // 加载第一个动作 VMD 作为合并目标
        long mergedAnim = nf.LoadAnimation(0, motionFiles.get(0).path);
        if (mergedAnim == 0) {
            logger.error("[舞台模式] 动作 VMD 加载失败: {}", motionFiles.get(0).path);
            return;
        }
        
        // 合并其余动作 VMD
        List<Long> tempHandles = new ArrayList<>();
        for (int i = 1; i < motionFiles.size(); i++) {
            long tempAnim = nf.LoadAnimation(0, motionFiles.get(i).path);
            if (tempAnim != 0) {
                nf.MergeAnimation(mergedAnim, tempAnim);
                tempHandles.add(tempAnim);
            }
        }
        
        // 释放临时句柄
        for (long handle : tempHandles) {
            nf.DeleteAnimation(handle);
        }
        
        // 加载相机 VMD
        long cameraAnim = 0;
        if (cameraFile != null) {
            // 如果相机文件也是动作文件之一，且已被合并进 mergedAnim，则直接用 mergedAnim
            if (cameraFile.hasBones || cameraFile.hasMorphs) {
                // 相机数据可能在 mergedAnim 中（如果第一个文件就是相机文件）
                // 但合并不会合并相机数据，所以单独加载相机
                cameraAnim = nf.LoadAnimation(0, cameraFile.path);
            } else {
                cameraAnim = nf.LoadAnimation(0, cameraFile.path);
            }
        }
        
        // 获取当前玩家模型句柄
        long modelHandle = 0;
        String modelName = null;
        if (mc.player != null) {
            String playerName = mc.player.getName().getString();
            modelName = ModelSelectorConfig.getInstance().getSelectedModel();
            if (modelName != null && !modelName.isEmpty()) {
                MMDModelManager.Model modelData = MMDModelManager.GetModel(modelName, playerName);
                if (modelData != null) {
                    modelHandle = modelData.model.getModelHandle();
                    nf.TransitionLayerTo(modelHandle, 0, mergedAnim, 0.3f);
                }
            }
        }
        
        // 获取音频路径（取第一个音频文件）
        String audioPath = pack.getFirstAudioPath();
        
        // 启动相机控制器（传递 modelHandle + modelName + 音频路径 + 高度偏移）
        MMDCameraController.getInstance().startStage(mergedAnim, cameraAnim, cinematicMode, modelHandle, modelName, audioPath, cameraHeightOffset);
        
        // 广播舞台开始到其他客户端（联机同步）
        StringBuilder stageData = new StringBuilder(pack.getName());
        for (StagePack.VmdFileInfo info : motionFiles) {
            stageData.append("|").append(info.name);
        }
        StageNetworkHandler.sendStageStart(stageData.toString());
        
        // 标记已启动（onClose 不会退出舞台模式）
        this.stageStarted = true;
        this.onClose();
        
        logger.info("[舞台模式] 开始: 包={}, 动作文件={}, 相机={}, 影院={}", 
                   pack.getName(), motionFiles.size(), cameraFile != null, cinematicMode);
    }
    
    @Override
    public void onClose() {
        // 未启动播放时退出舞台模式（恢复视角）
        if (!stageStarted) {
            MMDCameraController.getInstance().exitStageMode();
        }
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
