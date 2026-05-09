/* 文件职责：渲染并处理舞台会话侧栏，包括房主邀请与成员自定义动作。 */
package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.stage.client.viewmodel.StageLobbyViewModel;
import com.shiroha.mmdskin.stage.domain.model.StageMemberState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 文件职责：渲染并处理舞台房间侧栏，包括 host 邀请与 guest 自选动作。 */
final class StageAssignPanel {
    private static final int PANEL_WIDTH = 208;
    private static final int PANEL_MARGIN = 10;
    private static final int PANEL_PADDING = 10;
    private static final int HEADER_HEIGHT = 22;
    private static final int BUTTON_HEIGHT = 16;
    private static final int LIST_ROW_HEIGHT = 18;
    private static final int ROW_GAP = 3;
    private static final int TOGGLE_HEIGHT = 14;
    private static final int INVITE_BUTTON_WIDTH = 70;

    private static final int COLOR_BG = 0xC012171D;
    private static final int COLOR_BORDER = 0xFF314253;
    private static final int COLOR_ACCENT = 0xFF65A9D8;
    private static final int COLOR_TEXT = 0xFFEAF2FB;
    private static final int COLOR_TEXT_DIM = 0xFFA5B4C3;
    private static final int COLOR_TEXT_MUTED = 0xFF7E8D9D;
    private static final int COLOR_ROW = 0x16000000;
    private static final int COLOR_ROW_HOVER = 0x24FFFFFF;
    private static final int COLOR_GOOD = 0xFF54C284;
    private static final int COLOR_WARN = 0xFFD4A353;
    private static final int COLOR_BAD = 0xFFD66B6B;
    private static final int COLOR_BUSY = 0xFFB06E7B;
    private static final int COLOR_TOGGLE_ON = 0xFF4BB97C;
    private static final int COLOR_TOGGLE_OFF = 0xFF53606D;
    private static final int COLOR_ACTION = 0xFF4A6683;
    private static final int COLOR_ACTION_HOVER = 0xFF5A7998;
    private static final int COLOR_SCROLL_TRACK = 0x26000000;
    private static final int COLOR_SCROLL_THUMB = 0xFF65A9D8;
    private static final int COLOR_SEPARATOR = 0x18FFFFFF;

    private final Font font;
    private final StageWorkbenchFacade facade;

    private int panelX;
    private int panelY;
    private int panelHeight;
    private int listTop;
    private int listBottom;
    private int guestToggleY;
    private int guestMotionTop;
    private int guestMotionBottom;
    private int inviteButtonX;
    private int inviteButtonY;

    private float memberScroll;
    private float motionScroll;
    private int hoveredMemberIndex = -1;
    private int hoveredMotionIndex = -1;
    private boolean hoveredInviteButton;
    private boolean hoveredCustomMotionToggle;

    private List<StageLobbyViewModel.MemberView> memberViews = List.of();
    private List<StageLobbyViewModel.HostEntry> hostEntries = List.of();
    private List<StagePack.VmdFileInfo> motionFiles = List.of();

    StageAssignPanel(Font font, StageWorkbenchFacade facade) {
        this.font = Objects.requireNonNull(font, "font");
        this.facade = Objects.requireNonNull(facade, "facade");
    }

    void layout(int screenWidth, int screenHeight, boolean sessionMember) {
        this.panelX = screenWidth - PANEL_WIDTH - PANEL_MARGIN;
        this.panelY = PANEL_MARGIN;
        this.panelHeight = Math.max(220, screenHeight - PANEL_MARGIN * 2);
        this.listTop = panelY + HEADER_HEIGHT + 10;
        this.inviteButtonX = panelX + PANEL_WIDTH - PANEL_PADDING - INVITE_BUTTON_WIDTH;
        this.inviteButtonY = panelY + 4;

        if (sessionMember) {
            this.listBottom = panelY + Math.max(94, panelHeight / 2);
            this.guestToggleY = listBottom + 10;
            this.guestMotionTop = guestToggleY + TOGGLE_HEIGHT + 8;
            this.guestMotionBottom = panelY + panelHeight - PANEL_PADDING;
        } else {
            this.listBottom = panelY + panelHeight - PANEL_PADDING;
            this.guestToggleY = 0;
            this.guestMotionTop = 0;
            this.guestMotionBottom = 0;
        }
        clampScrolls();
    }

    void sync(List<StagePack.VmdFileInfo> selectedPackMotionFiles) {
        this.memberViews = List.copyOf(facade.getSessionMembersView());
        this.hostEntries = List.copyOf(facade.getHostPanelEntries());
        this.motionFiles = List.copyOf(selectedPackMotionFiles);
        clampScrolls();
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredMemberIndex = -1;
        hoveredMotionIndex = -1;
        hoveredInviteButton = false;
        hoveredCustomMotionToggle = false;

        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, COLOR_BG);
        drawBorder(graphics, panelX, panelY, PANEL_WIDTH, panelHeight, COLOR_BORDER);
        renderHeader(graphics, mouseX, mouseY);
        renderMemberList(graphics, mouseX, mouseY);
        if (facade.isSessionMember()) {
            renderGuestMotionArea(graphics, mouseX, mouseY);
        }
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (!facade.isSessionMember() && hoveredInviteButton) {
            facade.inviteAllNearby();
            return true;
        }
        if (facade.isSessionMember() && hoveredCustomMotionToggle) {
            facade.toggleLocalCustomMotionEnabled();
            clampScrolls();
            return true;
        }
        if (!facade.isSessionMember() && hoveredMemberIndex >= 0 && hoveredMemberIndex < hostEntries.size()) {
            facade.handleHostAction(hostEntries.get(hoveredMemberIndex).uuid());
            return true;
        }
        if (facade.isSessionMember()
                && facade.isLocalCustomMotionEnabled()
                && hoveredMotionIndex >= 0
                && hoveredMotionIndex < motionFiles.size()) {
            facade.toggleLocalCustomMotion(motionFiles.get(hoveredMotionIndex).name);
            return true;
        }
        return false;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        float step = (float) ((LIST_ROW_HEIGHT + ROW_GAP) * 2.5);
        if (mouseY >= listTop && mouseY <= listBottom) {
            memberScroll = Mth.clamp(memberScroll - (float) delta * step, 0.0f, maxMemberScroll());
            return true;
        }
        if (facade.isSessionMember()
                && facade.isLocalCustomMotionEnabled()
                && mouseY >= guestMotionTop
                && mouseY <= guestMotionBottom) {
            motionScroll = Mth.clamp(motionScroll - (float) delta * step, 0.0f, maxMotionScroll());
            return true;
        }
        return false;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
                && mouseY >= panelY && mouseY <= panelY + panelHeight;
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, wb("session.section"), panelX + PANEL_PADDING, panelY + 8, COLOR_ACCENT, false);
        if (facade.isSessionMember()) {
            String stats = wb("guest.members.short", memberViews.size());
            graphics.drawString(font, stats, panelX + PANEL_WIDTH - PANEL_PADDING - font.width(stats), panelY + 8, COLOR_TEXT_MUTED, false);
        } else {
            hoveredInviteButton = isRectHovered(mouseX, mouseY, inviteButtonX, inviteButtonY, INVITE_BUTTON_WIDTH, BUTTON_HEIGHT);
            graphics.fill(
                    inviteButtonX,
                    inviteButtonY,
                    inviteButtonX + INVITE_BUTTON_WIDTH,
                    inviteButtonY + BUTTON_HEIGHT,
                    hoveredInviteButton ? COLOR_ACTION_HOVER : COLOR_ACTION
            );
            graphics.drawCenteredString(font, wb("host.invite_all.short"), inviteButtonX + INVITE_BUTTON_WIDTH / 2, inviteButtonY + 4, 0xFFFFFFFF);
        }
        graphics.fill(panelX + PANEL_PADDING, listTop - 6, panelX + PANEL_WIDTH - PANEL_PADDING, listTop - 5, COLOR_SEPARATOR);
    }

    private void renderMemberList(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.enableScissor(panelX, listTop, panelX + PANEL_WIDTH, listBottom);
        if (facade.isSessionMember()) {
            renderGuestMembers(graphics, mouseX, mouseY);
        } else {
            renderHostMembers(graphics, mouseX, mouseY);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, listTop, listBottom, memberScroll, maxMemberScroll());
    }

    private void renderGuestMembers(GuiGraphics graphics, int mouseX, int mouseY) {
        if (memberViews.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.mmdskin.stage.waiting_host"), panelX + PANEL_WIDTH / 2, listTop + 4, COLOR_TEXT_DIM);
            return;
        }
        for (int i = 0; i < memberViews.size(); i++) {
            StageLobbyViewModel.MemberView memberView = memberViews.get(i);
            int y = rowY(listTop, i, memberScroll);
            if (y + LIST_ROW_HEIGHT < listTop || y > listBottom) {
                continue;
            }
            boolean hovered = isRowHovered(mouseX, mouseY, y, listBottom);
            if (hovered) {
                hoveredMemberIndex = i;
                fillRow(graphics, y, COLOR_ROW_HOVER);
            } else {
                fillRow(graphics, y, COLOR_ROW);
            }
            String prefix = memberView.host() ? "HOST" : memberView.local() ? "YOU" : "GUEST";
            graphics.drawString(font, shorten(prefix + " " + memberView.name(), 18), panelX + PANEL_PADDING, y + 5, COLOR_TEXT, false);
            String state = guestStateText(memberView.state(), memberView.useHostCamera());
            graphics.drawString(font, shorten(state, 14), panelX + PANEL_WIDTH - PANEL_PADDING - font.width(shorten(state, 14)), y + 5, colorForState(memberView.state()), false);
        }
    }

    private void renderHostMembers(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hostEntries.isEmpty()) {
            graphics.drawCenteredString(font, wb("host.empty.short"), panelX + PANEL_WIDTH / 2, listTop + 4, COLOR_TEXT_DIM);
            return;
        }
        for (int i = 0; i < hostEntries.size(); i++) {
            StageLobbyViewModel.HostEntry entry = hostEntries.get(i);
            int y = rowY(listTop, i, memberScroll);
            if (y + LIST_ROW_HEIGHT < listTop || y > listBottom) {
                continue;
            }
            boolean hovered = isRowHovered(mouseX, mouseY, y, listBottom);
            if (hovered) {
                hoveredMemberIndex = i;
                fillRow(graphics, y, COLOR_ROW_HOVER);
            } else {
                fillRow(graphics, y, COLOR_ROW);
            }
            graphics.drawString(font, shorten(entry.name(), 18), panelX + PANEL_PADDING, y + 5, entry.nearby() ? COLOR_TEXT : COLOR_TEXT_MUTED, false);
            String action = hostActionText(entry);
            graphics.drawString(font, shorten(action, 12), panelX + PANEL_WIDTH - PANEL_PADDING - font.width(shorten(action, 12)), y + 5, colorForHostEntry(entry), false);
        }
    }

    private void renderGuestMotionArea(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(panelX + PANEL_PADDING, guestToggleY - 5, panelX + PANEL_WIDTH - PANEL_PADDING, guestToggleY - 4, COLOR_SEPARATOR);
        graphics.drawString(font, Component.translatable("gui.mmdskin.stage.local_motion_override"), panelX + PANEL_PADDING, guestToggleY - 14, COLOR_TEXT, false);

        int toggleWidth = PANEL_WIDTH - PANEL_PADDING * 2;
        hoveredCustomMotionToggle = isRectHovered(mouseX, mouseY, panelX + PANEL_PADDING, guestToggleY, toggleWidth, TOGGLE_HEIGHT);
        drawToggle(graphics, panelX + PANEL_PADDING, guestToggleY, toggleWidth, TOGGLE_HEIGHT, facade.isLocalCustomMotionEnabled(), hoveredCustomMotionToggle);

        if (!facade.isLocalCustomMotionEnabled()) {
            graphics.drawString(font, Component.translatable("gui.mmdskin.stage.local_motion_fallback"), panelX + PANEL_PADDING, guestMotionTop, COLOR_TEXT_DIM, false);
            return;
        }
        if (motionFiles.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.mmdskin.stage.local_motion_empty"), panelX + PANEL_PADDING, guestMotionTop, COLOR_TEXT_DIM, false);
            return;
        }

        graphics.enableScissor(panelX, guestMotionTop, panelX + PANEL_WIDTH, guestMotionBottom);
        for (int i = 0; i < motionFiles.size(); i++) {
            StagePack.VmdFileInfo info = motionFiles.get(i);
            int y = rowY(guestMotionTop, i, motionScroll);
            if (y + LIST_ROW_HEIGHT < guestMotionTop || y > guestMotionBottom) {
                continue;
            }
            boolean hovered = isRowHovered(mouseX, mouseY, y, guestMotionBottom);
            if (hovered) {
                hoveredMotionIndex = i;
                fillRow(graphics, y, COLOR_ROW_HOVER);
            } else {
                fillRow(graphics, y, COLOR_ROW);
            }
            drawCheckbox(graphics, panelX + PANEL_PADDING, y + 5, facade.isLocalCustomMotionSelected(info.name));
            graphics.drawString(font, shorten(stripExtension(info.name), 15), panelX + PANEL_PADDING + 14, y + 5, COLOR_TEXT, false);
            String tag = motionTag(info);
            graphics.drawString(font, tag, panelX + PANEL_WIDTH - PANEL_PADDING - font.width(tag), y + 5, COLOR_TEXT_MUTED, false);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, guestMotionTop, guestMotionBottom, motionScroll, maxMotionScroll());
    }

    private void drawToggle(GuiGraphics graphics, int x, int y, int width, int height, boolean enabled, boolean hovered) {
        int color = enabled ? COLOR_TOGGLE_ON : COLOR_TOGGLE_OFF;
        graphics.fill(x, y, x + width, y + height, hovered ? brighten(color) : color);
        int knob = height - 2;
        int knobX = enabled ? x + width - knob - 1 : x + 1;
        graphics.fill(knobX, y + 1, knobX + knob, y + knob + 1, 0xFFF5FAFF);
    }

    private void drawCheckbox(GuiGraphics graphics, int x, int y, boolean checked) {
        graphics.fill(x, y, x + 8, y + 8, checked ? COLOR_TOGGLE_ON : COLOR_TOGGLE_OFF);
        if (checked) {
            graphics.drawString(font, "v", x + 1, y - 1, 0xFFFFFFFF, false);
        }
    }

    private void drawScrollbar(GuiGraphics graphics, int top, int bottom, float offset, float maxScroll) {
        if (maxScroll <= 0.0f) {
            return;
        }
        int barX = panelX + PANEL_WIDTH - 4;
        int barHeight = bottom - top;
        graphics.fill(barX, top, barX + 2, bottom, COLOR_SCROLL_TRACK);
        int thumbHeight = Math.max(12, (int) (barHeight * (barHeight / (barHeight + maxScroll))));
        int thumbY = top + (int) ((barHeight - thumbHeight) * (offset / maxScroll));
        graphics.fill(barX, thumbY, barX + 2, thumbY + thumbHeight, COLOR_SCROLL_THUMB);
    }

    private void fillRow(GuiGraphics graphics, int y, int color) {
        graphics.fill(panelX + PANEL_PADDING - 2, y, panelX + PANEL_WIDTH - PANEL_PADDING + 2, y + LIST_ROW_HEIGHT, color);
    }

    private void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private float maxMemberScroll() {
        int count = facade.isSessionMember() ? memberViews.size() : hostEntries.size();
        return Math.max(0.0f, count * (LIST_ROW_HEIGHT + ROW_GAP) - (listBottom - listTop));
    }

    private float maxMotionScroll() {
        return Math.max(0.0f, motionFiles.size() * (LIST_ROW_HEIGHT + ROW_GAP) - (guestMotionBottom - guestMotionTop));
    }

    private void clampScrolls() {
        memberScroll = Mth.clamp(memberScroll, 0.0f, maxMemberScroll());
        motionScroll = Mth.clamp(motionScroll, 0.0f, maxMotionScroll());
    }

    private int rowY(int top, int index, float scroll) {
        return top + index * (LIST_ROW_HEIGHT + ROW_GAP) - Math.round(scroll);
    }

    private boolean isRowHovered(int mouseX, int mouseY, int y, int bottom) {
        return mouseX >= panelX + PANEL_PADDING - 2
                && mouseX <= panelX + PANEL_WIDTH - PANEL_PADDING + 2
                && mouseY >= Math.max(y, panelY)
                && mouseY <= Math.min(y + LIST_ROW_HEIGHT, bottom);
    }

    private boolean isRectHovered(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static String hostActionText(StageLobbyViewModel.HostEntry entry) {
        StageMemberState state = entry.state();
        if (state == null) {
            return entry.nearby() ? wb("host.action.invite") : wb("state.offline");
        }
        return switch (state) {
            case HOST -> wb("state.host");
            case INVITED -> wb("host.action.cancel_invite");
            case ACCEPTED -> entry.useHostCamera() ? wb("state.with_host_camera", wb("state.accepted")) : wb("state.accepted");
            case READY -> entry.useHostCamera() ? wb("state.with_host_camera", wb("state.ready")) : wb("state.ready");
            case DECLINED -> entry.nearby() ? wb("host.action.invite") : wb("state.declined");
            case BUSY -> entry.nearby() ? wb("host.action.invite") : wb("state.busy");
        };
    }

    private static String guestStateText(StageMemberState state, boolean useHostCamera) {
        if (state == null) {
            return wb("state.unknown");
        }
        String text = switch (state) {
            case HOST -> wb("state.host");
            case INVITED -> wb("state.invited");
            case ACCEPTED -> wb("state.accepted");
            case READY -> wb("state.ready");
            case DECLINED -> wb("state.declined");
            case BUSY -> wb("state.busy");
        };
        return useHostCamera ? wb("state.with_host_camera", text) : text;
    }

    private static int colorForHostEntry(StageLobbyViewModel.HostEntry entry) {
        if (entry.state() == null) {
            return entry.nearby() ? COLOR_ACCENT : COLOR_TEXT_MUTED;
        }
        return switch (entry.state()) {
            case HOST -> COLOR_ACCENT;
            case INVITED -> COLOR_WARN;
            case ACCEPTED, READY -> COLOR_GOOD;
            case DECLINED -> entry.nearby() ? COLOR_ACCENT : COLOR_BAD;
            case BUSY -> entry.nearby() ? COLOR_ACCENT : COLOR_BUSY;
        };
    }

    private static int colorForState(StageMemberState state) {
        if (state == null) {
            return COLOR_TEXT_MUTED;
        }
        return switch (state) {
            case HOST -> COLOR_ACCENT;
            case INVITED -> COLOR_WARN;
            case ACCEPTED, READY -> COLOR_GOOD;
            case DECLINED -> COLOR_BAD;
            case BUSY -> COLOR_BUSY;
        };
    }

    private static int brighten(int color) {
        int a = color >>> 24;
        int r = Math.min(255, ((color >>> 16) & 0xFF) + 18);
        int g = Math.min(255, ((color >>> 8) & 0xFF) + 18);
        int b = Math.min(255, (color & 0xFF) + 18);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static String motionTag(StagePack.VmdFileInfo info) {
        List<String> tags = new ArrayList<>(3);
        if (info.hasBones) {
            tags.add("B");
        }
        if (info.hasMorphs) {
            tags.add("M");
        }
        if (info.hasCamera) {
            tags.add("C");
        }
        return String.join("/", tags);
    }

    private static String stripExtension(String text) {
        int dot = text.lastIndexOf('.');
        return dot > 0 ? text.substring(0, dot) : text;
    }

    private static String shorten(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return maxChars <= 2 ? text.substring(0, Math.max(0, maxChars)) : text.substring(0, maxChars - 2) + "..";
    }

    private static String wb(String suffix, Object... args) {
        return Component.translatable("gui.mmdskin.stage.workbench." + suffix, args).getString();
    }
}
