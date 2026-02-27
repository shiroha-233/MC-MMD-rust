package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StagePack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 多人舞台动作分配面板，嵌入 StageSelectScreen 右侧
 * 房主为每个已接受邀请的成员分配 VMD 动作文件
 */
public class StageAssignPanel {

    private static final int PANEL_WIDTH = 180;
    private static final int BG = 0xC0101418;
    private static final int BORDER = 0xFF2A3A4A;
    private static final int ACCENT = 0xFF60A0D0;
    private static final int TEXT = 0xFFDDDDDD;
    private static final int TEXT_DIM = 0xFF888888;
    private static final int HOVER = 0x30FFFFFF;
    private static final int SELECTED = 0x3060A0D0;
    private static final int ASSIGNED = 0xFF40C080;
    private static final int UNASSIGNED = 0xFFD0A050;
    private static final int CHECKBOX_ON = 0xFF40C080;
    private static final int CHECKBOX_OFF = 0xFF505560;
    private static final int HEADER_HEIGHT = 20;
    private static final int ITEM_HEIGHT = 16;
    private static final int MARGIN = 4;

    private static final int STATE_PENDING_COLOR = 0xFFD0A050;
    private static final int STATE_DECLINED_COLOR = 0xFFD05050;

    private final Font font;
    private int panelX, panelY, panelH;

    private List<AbstractClientPlayer> nearbyPlayers = new ArrayList<>();
    private int selectedMemberIndex = -1;
    private int hoveredMemberIndex = -1;

    private List<StagePack.VmdFileInfo> motionVmdFiles = new ArrayList<>();
    private int hoveredVmdIndex = -1;

    private int memberListTop, memberListBottom;
    private int assignTop, assignBottom;
    private int splitY;

    private int memberScrollOffset = 0, memberMaxScroll = 0;
    private int assignScrollOffset = 0, assignMaxScroll = 0;

    private boolean hoverInviteBtn = false;
    private int inviteBtnX, inviteBtnY;
    private static final int INVITE_BTN_W = 32;
    private static final int INVITE_BTN_H = 14;

    public StageAssignPanel(Font font) {
        this.font = font;
    }

    public void layout(int screenWidth, int screenHeight) {
        this.panelX = screenWidth - PANEL_WIDTH - MARGIN;
        this.panelY = MARGIN;
        this.panelH = screenHeight - MARGIN * 2;

        memberListTop = panelY + HEADER_HEIGHT;
        splitY = panelY + (int) ((panelH - HEADER_HEIGHT) * 0.45f) + HEADER_HEIGHT;
        memberListBottom = splitY - 2;

        assignTop = splitY + HEADER_HEIGHT;
        assignBottom = panelY + panelH - MARGIN;

        inviteBtnX = panelX + PANEL_WIDTH - INVITE_BTN_W - 6;
        inviteBtnY = panelY + 3;

        updateMemberScroll();
        updateAssignScroll();
    }

    public void setStagePack(StagePack pack) {
        motionVmdFiles = new ArrayList<>();
        if (pack != null) {
            for (StagePack.VmdFileInfo info : pack.getVmdFiles()) {
                if (info.hasBones || info.hasMorphs) {
                    motionVmdFiles.add(info);
                }
            }
        }
        assignScrollOffset = 0;
        updateAssignScroll();
    }

    public void refreshPlayers() {
        this.nearbyPlayers = StageInviteManager.getInstance().getNearbyPlayers();
        updateMemberScroll();
        if (selectedMemberIndex >= nearbyPlayers.size()) {
            selectedMemberIndex = -1;
        }
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, BG);
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, BORDER);

        renderHeader(g, mouseX, mouseY);
        renderMemberList(g, mouseX, mouseY);
        renderSeparator(g);
        renderAssignArea(g, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics g, int mouseX, int mouseY) {
        g.drawCenteredString(font,
                Component.translatable("gui.mmdskin.stage.assign_title"),
                panelX + PANEL_WIDTH / 2, panelY + 6, ACCENT);

        hoverInviteBtn = mouseX >= inviteBtnX && mouseX <= inviteBtnX + INVITE_BTN_W
                && mouseY >= inviteBtnY && mouseY <= inviteBtnY + INVITE_BTN_H;
        int btnColor = hoverInviteBtn ? HOVER : 0x20FFFFFF;
        g.fill(inviteBtnX, inviteBtnY, inviteBtnX + INVITE_BTN_W, inviteBtnY + INVITE_BTN_H, btnColor);
        g.drawCenteredString(font, "+", inviteBtnX + INVITE_BTN_W / 2, inviteBtnY + 3, ACCENT);
    }

    private void renderMemberList(GuiGraphics g, int mouseX, int mouseY) {
        g.enableScissor(panelX, memberListTop, panelX + PANEL_WIDTH, memberListBottom);

        hoveredMemberIndex = -1;
        StageInviteManager mgr = StageInviteManager.getInstance();
        StageMotionAssignment assignment = StageMotionAssignment.getInstance();

        if (nearbyPlayers.isEmpty()) {
            g.drawCenteredString(font,
                    Component.translatable("gui.mmdskin.stage.no_nearby"),
                    panelX + PANEL_WIDTH / 2, memberListTop + 4, TEXT_DIM);
            g.disableScissor();
            return;
        }

        for (int i = 0; i < nearbyPlayers.size(); i++) {
            AbstractClientPlayer player = nearbyPlayers.get(i);
            int itemY = memberListTop + i * ITEM_HEIGHT - memberScrollOffset;

            if (itemY + ITEM_HEIGHT < memberListTop || itemY > memberListBottom) continue;

            int itemX = panelX + 4;
            int itemW = PANEL_WIDTH - 8;
            boolean hovered = mouseX >= itemX && mouseX <= itemX + itemW
                    && mouseY >= Math.max(itemY, memberListTop)
                    && mouseY <= Math.min(itemY + ITEM_HEIGHT, memberListBottom);
            if (hovered) hoveredMemberIndex = i;

            if (i == selectedMemberIndex) {
                g.fill(itemX, itemY, itemX + itemW, itemY + ITEM_HEIGHT, SELECTED);
            } else if (hovered) {
                g.fill(itemX, itemY, itemX + itemW, itemY + ITEM_HEIGHT, HOVER);
            }

            String name = truncate(player.getName().getString(), 10);
            g.drawString(font, name, itemX + 2, itemY + 4, TEXT, false);

            UUID uuid = player.getUUID();
            StageInviteManager.MemberState state = mgr.getMemberState(uuid);
            int tagX = itemX + itemW;

            if (assignment.hasAssignment(uuid)) {
                tagX -= font.width("♪") + 2;
                g.drawString(font, "♪", tagX, itemY + 4, ASSIGNED, false);
            }

            renderMemberState(g, tagX, itemY + 4, state);
        }

        g.disableScissor();
        renderScrollbar(g, memberListTop, memberListBottom, memberScrollOffset, memberMaxScroll);
    }

    private void renderMemberState(GuiGraphics g, int rightX, int y,
                                    StageInviteManager.MemberState state) {
        String tag;
        int color;
        switch (state) {
            case PENDING:  tag = "..."; color = STATE_PENDING_COLOR; break;
            case ACCEPTED: tag = "✓";   color = ASSIGNED;           break;
            case DECLINED: tag = "✗";   color = STATE_DECLINED_COLOR; break;
            default:       tag = "▶";   color = ACCENT;             break;
        }
        int w = font.width(tag);
        g.drawString(font, tag, rightX - w - 2, y, color, false);
    }

    private void renderSeparator(GuiGraphics g) {
        g.fill(panelX + 8, splitY - 1, panelX + PANEL_WIDTH - 8, splitY, 0x30FFFFFF);
    }

    private void renderAssignArea(GuiGraphics g, int mouseX, int mouseY) {
        hoveredVmdIndex = -1;

        AbstractClientPlayer selectedPlayer = getSelectedAcceptedPlayer();
        if (selectedPlayer == null) {
            g.drawCenteredString(font,
                    Component.translatable("gui.mmdskin.stage.select_member"),
                    panelX + PANEL_WIDTH / 2, splitY + 6, TEXT_DIM);
            return;
        }

        String label = Component.translatable("gui.mmdskin.stage.assign_for",
                selectedPlayer.getName().getString()).getString();
        g.drawString(font, truncate(label, 24), panelX + 6, splitY + 4, TEXT, false);

        g.enableScissor(panelX, assignTop, panelX + PANEL_WIDTH, assignBottom);

        UUID uuid = selectedPlayer.getUUID();
        List<String> assigned = StageMotionAssignment.getInstance().getAssignment(uuid);

        for (int i = 0; i < motionVmdFiles.size(); i++) {
            StagePack.VmdFileInfo info = motionVmdFiles.get(i);
            int itemY = assignTop + i * ITEM_HEIGHT - assignScrollOffset;

            if (itemY + ITEM_HEIGHT < assignTop || itemY > assignBottom) continue;

            int itemX = panelX + 4;
            int itemW = PANEL_WIDTH - 8;
            boolean hovered = mouseX >= itemX && mouseX <= itemX + itemW
                    && mouseY >= Math.max(itemY, assignTop)
                    && mouseY <= Math.min(itemY + ITEM_HEIGHT, assignBottom);
            if (hovered) hoveredVmdIndex = i;

            if (hovered) {
                g.fill(itemX, itemY, itemX + itemW, itemY + ITEM_HEIGHT, HOVER);
            }

            boolean checked = assigned.contains(info.name);
            int cbX = itemX + 2;
            int cbY = itemY + 3;
            int cbSize = 8;
            g.fill(cbX, cbY, cbX + cbSize, cbY + cbSize, checked ? CHECKBOX_ON : CHECKBOX_OFF);
            if (checked) {
                g.drawString(font, "✓", cbX + 1, cbY, 0xFFFFFFFF, false);
            }

            String fileName = info.name;
            if (fileName.toLowerCase().endsWith(".vmd")) {
                fileName = fileName.substring(0, fileName.length() - 4);
            }
            g.drawString(font, truncate(fileName, 14), cbX + cbSize + 4, itemY + 4, TEXT, false);

            String typeTag = info.getTypeTag();
            int tagW = font.width(typeTag);
            g.drawString(font, typeTag, itemX + itemW - tagW - 2, itemY + 4, TEXT_DIM, false);
        }

        g.disableScissor();
        renderScrollbar(g, assignTop, assignBottom, assignScrollOffset, assignMaxScroll);
    }

    private void renderScrollbar(GuiGraphics g, int top, int bottom, int offset, int maxScroll) {
        if (maxScroll <= 0) return;
        int barX = panelX + PANEL_WIDTH - 4;
        int barH = bottom - top;
        g.fill(barX, top, barX + 2, bottom, 0x20FFFFFF);
        int thumbH = Math.max(10, barH * barH / (barH + maxScroll));
        int thumbY = top + (int) ((barH - thumbH) * ((float) offset / maxScroll));
        g.fill(barX, thumbY, barX + 2, thumbY + thumbH, ACCENT);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        if (hoverInviteBtn) {
            inviteAllNone();
            return true;
        }

        if (hoveredMemberIndex >= 0 && hoveredMemberIndex < nearbyPlayers.size()) {
            AbstractClientPlayer player = nearbyPlayers.get(hoveredMemberIndex);
            UUID uuid = player.getUUID();
            StageInviteManager mgr = StageInviteManager.getInstance();
            StageInviteManager.MemberState state = mgr.getMemberState(uuid);

            if (state == StageInviteManager.MemberState.NONE) {
                mgr.sendInvite(uuid);
                return true;
            }
            if (state == StageInviteManager.MemberState.ACCEPTED) {
                selectedMemberIndex = hoveredMemberIndex;
                assignScrollOffset = 0;
                updateAssignScroll();
                return true;
            }
            return false;
        }

        if (hoveredVmdIndex >= 0 && hoveredVmdIndex < motionVmdFiles.size()) {
            AbstractClientPlayer selectedPlayer = getSelectedAcceptedPlayer();
            if (selectedPlayer == null) return false;

            UUID uuid = selectedPlayer.getUUID();
            StagePack.VmdFileInfo info = motionVmdFiles.get(hoveredVmdIndex);
            StageMotionAssignment assignment = StageMotionAssignment.getInstance();

            if (assignment.getAssignment(uuid).contains(info.name)) {
                assignment.removeSingleVmd(uuid, info.name);
            } else {
                assignment.assignSingle(uuid, info.name);
            }
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isInside(mouseX, mouseY)) return false;

        int scrollAmount = (int) (-delta * ITEM_HEIGHT * 3);

        if (mouseY < splitY) {
            memberScrollOffset = Math.max(0, Math.min(memberMaxScroll, memberScrollOffset + scrollAmount));
        } else {
            assignScrollOffset = Math.max(0, Math.min(assignMaxScroll, assignScrollOffset + scrollAmount));
        }
        return true;
    }

    public boolean isInside(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
                && mouseY >= panelY && mouseY <= panelY + panelH;
    }

    private AbstractClientPlayer getSelectedAcceptedPlayer() {
        if (selectedMemberIndex < 0 || selectedMemberIndex >= nearbyPlayers.size()) return null;
        AbstractClientPlayer player = nearbyPlayers.get(selectedMemberIndex);
        StageInviteManager.MemberState state =
                StageInviteManager.getInstance().getMemberState(player.getUUID());
        return state == StageInviteManager.MemberState.ACCEPTED ? player : null;
    }

    private void inviteAllNone() {
        StageInviteManager mgr = StageInviteManager.getInstance();
        for (AbstractClientPlayer player : nearbyPlayers) {
            if (mgr.getMemberState(player.getUUID()) == StageInviteManager.MemberState.NONE) {
                mgr.sendInvite(player.getUUID());
            }
        }
    }

    private void updateMemberScroll() {
        int contentH = nearbyPlayers.size() * ITEM_HEIGHT;
        int visibleH = memberListBottom - memberListTop;
        memberMaxScroll = Math.max(0, contentH - visibleH);
        memberScrollOffset = Math.max(0, Math.min(memberMaxScroll, memberScrollOffset));
    }

    private void updateAssignScroll() {
        int contentH = motionVmdFiles.size() * ITEM_HEIGHT;
        int visibleH = assignBottom - assignTop;
        assignMaxScroll = Math.max(0, contentH - visibleH);
        assignScrollOffset = Math.max(0, Math.min(assignMaxScroll, assignScrollOffset));
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + ".." : s;
    }
}
