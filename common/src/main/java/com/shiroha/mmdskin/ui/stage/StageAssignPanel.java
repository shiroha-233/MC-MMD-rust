/* 文件职责：渲染并处理舞台会话侧栏，包括房主邀请与成员自定义动作。 */
package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.stage.client.viewmodel.StageLobbyViewModel;
import com.shiroha.mmdskin.stage.domain.model.StageMemberState;
import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Objects;

/** 文件职责：渲染并处理舞台房间侧栏，包括 host 邀请与 guest 自选动作。 */
final class StageAssignPanel {
    private static final int PANEL_WIDTH = 192;
    private static final int PANEL_MARGIN = 8;
    private static final int PANEL_PADDING = 8;
    private static final int HEADER_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 15;
    private static final int LIST_ROW_HEIGHT = 16;
    private static final int ROW_GAP = 2;
    private static final int TOGGLE_HEIGHT = 13;
    private static final int INVITE_BUTTON_WIDTH = 66;

    private static final int COLOR_ACCENT = TranslucentTrayChrome.ACCENT;
    private static final int COLOR_TEXT = TranslucentTrayChrome.BODY_TEXT;
    private static final int COLOR_TEXT_DIM = TranslucentTrayChrome.SUBTITLE_TEXT;
    private static final int COLOR_TEXT_MUTED = TranslucentTrayChrome.MUTED_TEXT;
    private static final int COLOR_ROW = TranslucentTrayChrome.CARD_BACKGROUND;
    private static final int COLOR_ROW_HOVER = TranslucentTrayChrome.CARD_HOVER;
    private static final int COLOR_GOOD = 0xFF54C284;
    private static final int COLOR_WARN = 0xFFD4A353;
    private static final int COLOR_BAD = 0xFFD66B6B;
    private static final int COLOR_BUSY = 0xFFB06E7B;
    private static final int COLOR_TOGGLE_ON = 0xA04BB97C;
    private static final int COLOR_TOGGLE_OFF = 0x8053606D;
    private static final int COLOR_ACTION = 0x304A6683;
    private static final int COLOR_ACTION_HOVER = 0x485A7998;

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
        this.listTop = panelY + HEADER_HEIGHT + 8;
        this.inviteButtonX = panelX + PANEL_WIDTH - PANEL_PADDING - INVITE_BUTTON_WIDTH;
        this.inviteButtonY = panelY + 5;

        if (sessionMember) {
            this.listBottom = panelY + Math.max(94, panelHeight / 2);
            this.guestToggleY = listBottom + 8;
            this.guestMotionTop = guestToggleY + TOGGLE_HEIGHT + 7;
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

        TranslucentTrayChrome.drawPanel(graphics, panelX, panelY, PANEL_WIDTH, panelHeight);
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

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, StageScreenUtils.wb("session.section"), panelX + PANEL_PADDING, panelY + 8, COLOR_ACCENT, false);
        if (facade.isSessionMember()) {
            String stats = StageScreenUtils.wb("guest.members.short", memberViews.size());
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
            graphics.drawCenteredString(font, StageScreenUtils.wb("host.invite_all.short"),
                    inviteButtonX + INVITE_BUTTON_WIDTH / 2, inviteButtonY + 4, TranslucentTrayChrome.TITLE_TEXT);
        }
        TranslucentTrayChrome.drawSeparator(graphics, panelX + PANEL_PADDING, listTop - 6, PANEL_WIDTH - PANEL_PADDING * 2);
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
            graphics.drawCenteredString(font, net.minecraft.network.chat.Component.translatable("gui.mmdskin.stage.waiting_host"),
                    panelX + PANEL_WIDTH / 2, listTop + 4, COLOR_TEXT_DIM);
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
            graphics.drawString(font, StageScreenUtils.shorten(prefix + " " + memberView.name(), 17), panelX + PANEL_PADDING, y + 4, COLOR_TEXT, false);
            String state = guestStateText(memberView.state(), memberView.useHostCamera());
            graphics.drawString(font, StageScreenUtils.shorten(state, 13),
                    panelX + PANEL_WIDTH - PANEL_PADDING - font.width(StageScreenUtils.shorten(state, 13)),
                    y + 4, colorForState(memberView.state()), false);
        }
    }

    private void renderHostMembers(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hostEntries.isEmpty()) {
            graphics.drawCenteredString(font, StageScreenUtils.wb("host.empty.short"), panelX + PANEL_WIDTH / 2, listTop + 4, COLOR_TEXT_DIM);
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
            graphics.drawString(font, StageScreenUtils.shorten(entry.name(), 17), panelX + PANEL_PADDING, y + 4,
                    entry.nearby() ? COLOR_TEXT : COLOR_TEXT_MUTED, false);
            String action = hostActionText(entry);
            graphics.drawString(font, StageScreenUtils.shorten(action, 11),
                    panelX + PANEL_WIDTH - PANEL_PADDING - font.width(StageScreenUtils.shorten(action, 11)),
                    y + 4, colorForHostEntry(entry), false);
        }
    }

    private void renderGuestMotionArea(GuiGraphics graphics, int mouseX, int mouseY) {
        TranslucentTrayChrome.drawSeparator(graphics, panelX + PANEL_PADDING, guestToggleY - 5, PANEL_WIDTH - PANEL_PADDING * 2);
        graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.mmdskin.stage.local_motion_override"),
                panelX + PANEL_PADDING, guestToggleY - 14, COLOR_TEXT, false);

        int toggleWidth = PANEL_WIDTH - PANEL_PADDING * 2;
        hoveredCustomMotionToggle = isRectHovered(mouseX, mouseY, panelX + PANEL_PADDING, guestToggleY, toggleWidth, TOGGLE_HEIGHT);
        drawToggle(graphics, panelX + PANEL_PADDING, guestToggleY, toggleWidth, TOGGLE_HEIGHT,
                facade.isLocalCustomMotionEnabled(), hoveredCustomMotionToggle);

        if (!facade.isLocalCustomMotionEnabled()) {
            graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.mmdskin.stage.local_motion_fallback"),
                    panelX + PANEL_PADDING, guestMotionTop, COLOR_TEXT_DIM, false);
            return;
        }
        if (motionFiles.isEmpty()) {
            graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.mmdskin.stage.local_motion_empty"),
                    panelX + PANEL_PADDING, guestMotionTop, COLOR_TEXT_DIM, false);
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
            drawCheckbox(graphics, panelX + PANEL_PADDING, y + 4, facade.isLocalCustomMotionSelected(info.name));
            graphics.drawString(font, StageScreenUtils.shorten(StageScreenUtils.stripExtension(info.name), 14), panelX + PANEL_PADDING + 13, y + 4, COLOR_TEXT, false);
            String tag = StageScreenUtils.motionTag(info);
            graphics.drawString(font, tag, panelX + PANEL_WIDTH - PANEL_PADDING - font.width(tag), y + 4, COLOR_TEXT_MUTED, false);
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
        int barX = panelX + PANEL_WIDTH - 4;
        TranslucentTrayChrome.drawScrollbar(graphics, barX, top, bottom, offset, maxScroll);
    }

    private void fillRow(GuiGraphics graphics, int y, int color) {
        graphics.fill(panelX + PANEL_PADDING - 2, y, panelX + PANEL_WIDTH - PANEL_PADDING + 2, y + LIST_ROW_HEIGHT, color);
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

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
                && mouseY >= panelY && mouseY <= panelY + panelHeight;
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

    private static String guestStateText(StageMemberState state, boolean useHostCamera) {
        if (state == null) {
            return StageScreenUtils.wb("state.unknown");
        }
        String text = switch (state) {
            case HOST -> StageScreenUtils.wb("state.host");
            case INVITED -> StageScreenUtils.wb("state.invited");
            case ACCEPTED -> StageScreenUtils.wb("state.accepted");
            case READY -> StageScreenUtils.wb("state.ready");
            case DECLINED -> StageScreenUtils.wb("state.declined");
            case BUSY -> StageScreenUtils.wb("state.busy");
        };
        return useHostCamera ? StageScreenUtils.wb("state.with_host_camera", text) : text;
    }

    private static String hostActionText(StageLobbyViewModel.HostEntry entry) {
        StageMemberState state = entry.state();
        if (state == null) {
            return entry.nearby() ? StageScreenUtils.wb("host.action.invite") : StageScreenUtils.wb("state.offline");
        }
        return switch (state) {
            case HOST -> StageScreenUtils.wb("state.host");
            case INVITED -> StageScreenUtils.wb("host.action.cancel_invite");
            case ACCEPTED -> entry.useHostCamera()
                    ? StageScreenUtils.wb("state.with_host_camera", StageScreenUtils.wb("state.accepted"))
                    : StageScreenUtils.wb("state.accepted");
            case READY -> entry.useHostCamera()
                    ? StageScreenUtils.wb("state.with_host_camera", StageScreenUtils.wb("state.ready"))
                    : StageScreenUtils.wb("state.ready");
            case DECLINED -> entry.nearby() ? StageScreenUtils.wb("host.action.invite") : StageScreenUtils.wb("state.declined");
            case BUSY -> entry.nearby() ? StageScreenUtils.wb("host.action.invite") : StageScreenUtils.wb("state.busy");
        };
    }

    private static int brighten(int color) {
        return TranslucentTrayChrome.brighten(color, 18);
    }
}
