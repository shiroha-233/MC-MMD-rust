package com.shiroha.mmdskin.ui.stage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * 多人舞台邀请面板（右上角）
 * 列出15米内玩家，房主可点击邀请
 */
public class StageInvitePanel {

    private static final int PANEL_WIDTH = 130;
    private static final int HEADER_HEIGHT = 20;
    private static final int ITEM_HEIGHT = 16;
    private static final int MARGIN = 4;

    private static final int BG = 0xC0101418;
    private static final int BORDER = 0xFF2A3A4A;
    private static final int ACCENT = 0xFF60A0D0;
    private static final int TEXT = 0xFFDDDDDD;
    private static final int TEXT_DIM = 0xFF888888;
    private static final int HOVER = 0x30FFFFFF;
    private static final int STATE_PENDING = 0xFFD0A050;
    private static final int STATE_ACCEPTED = 0xFF40C080;
    private static final int STATE_DECLINED = 0xFFD05050;

    private final Font font;
    private int panelX, panelY, panelH;
    private int hoveredIndex = -1;
    private List<AbstractClientPlayer> nearbyPlayers;

    public StageInvitePanel(Font font) {
        this.font = font;
    }

    public void layout(int screenWidth) {
        this.panelX = screenWidth - PANEL_WIDTH - MARGIN;
        this.panelY = MARGIN;
        refreshPlayers();
        this.panelH = HEADER_HEIGHT + Math.max(1, nearbyPlayers.size()) * ITEM_HEIGHT + 4;
    }

    public void refreshPlayers() {
        this.nearbyPlayers = StageInviteManager.getInstance().getNearbyPlayers();
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, BG);
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, BORDER);

        g.drawCenteredString(font,
            Component.translatable("gui.mmdskin.stage.invite_title"),
            panelX + PANEL_WIDTH / 2, panelY + 6, ACCENT);

        hoveredIndex = -1;
        StageInviteManager mgr = StageInviteManager.getInstance();

        if (nearbyPlayers.isEmpty()) {
            g.drawCenteredString(font,
                Component.translatable("gui.mmdskin.stage.no_players"),
                panelX + PANEL_WIDTH / 2, panelY + HEADER_HEIGHT + 4, TEXT_DIM);
            return;
        }

        int listY = panelY + HEADER_HEIGHT;
        for (int i = 0; i < nearbyPlayers.size(); i++) {
            AbstractClientPlayer player = nearbyPlayers.get(i);
            int itemY = listY + i * ITEM_HEIGHT;
            int itemX = panelX + 4;
            int itemW = PANEL_WIDTH - 8;

            boolean hovered = mouseX >= itemX && mouseX <= itemX + itemW
                           && mouseY >= itemY && mouseY <= itemY + ITEM_HEIGHT;
            if (hovered) hoveredIndex = i;

            if (hovered) g.fill(itemX, itemY, itemX + itemW, itemY + ITEM_HEIGHT, HOVER);

            String name = truncate(player.getName().getString(), 12);
            g.drawString(font, name, itemX + 2, itemY + 4, TEXT, false);

            UUID uuid = player.getUUID();
            StageInviteManager.MemberState state = mgr.getMemberState(uuid);
            renderStateTag(g, itemX + itemW, itemY + 4, state);
        }
    }

    private void renderStateTag(GuiGraphics g, int rightX, int y,
                                 StageInviteManager.MemberState state) {
        String tag;
        int color;
        switch (state) {
            case PENDING:  tag = "...";  color = STATE_PENDING;  break;
            case ACCEPTED: tag = "\u2713"; color = STATE_ACCEPTED; break;
            case READY:    tag = "\u2605"; color = STATE_ACCEPTED; break;
            case DECLINED: tag = "\u2717"; color = STATE_DECLINED; break;
            default: tag = "\u25B6"; color = ACCENT; break;
        }
        int w = font.width(tag);
        g.drawString(font, tag, rightX - w - 2, y, color, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (hoveredIndex < 0 || hoveredIndex >= nearbyPlayers.size()) return false;

        AbstractClientPlayer player = nearbyPlayers.get(hoveredIndex);
        UUID uuid = player.getUUID();
        StageInviteManager mgr = StageInviteManager.getInstance();

        StageInviteManager.MemberState state = mgr.getMemberState(uuid);
        if (state == StageInviteManager.MemberState.NONE
                || state == StageInviteManager.MemberState.DECLINED) {
            mgr.sendInvite(uuid);
            return true;
        }
        return false;
    }

    public boolean isInside(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
            && mouseY >= panelY && mouseY <= panelY + panelH;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + ".." : s;
    }
}
