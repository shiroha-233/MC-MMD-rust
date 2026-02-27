package com.shiroha.mmdskin.ui.stage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * 舞台邀请弹窗（轻量Screen，不暂停游戏）
 */
public class StageInvitePopup extends Screen {

    private static final int POPUP_W = 200;
    private static final int POPUP_H = 56;
    private static final int BTN_W = 70;
    private static final int BTN_H = 16;
    private static final int MARGIN = 6;

    private static final int BG = 0xE0101418;
    private static final int BORDER = 0xFF2A3A4A;
    private static final int ACCENT = 0xFF60A0D0;
    private static final int TEXT = 0xFFDDDDDD;
    private static final int BTN_ACCEPT = 0xFF40A060;
    private static final int BTN_ACCEPT_HOVER = 0xFF50C070;
    private static final int BTN_DECLINE = 0xFF804040;
    private static final int BTN_DECLINE_HOVER = 0xFFA05050;

    private final String inviterName;
    private int px, py;
    private int acceptX, declineX, btnY;
    private boolean hoverAccept, hoverDecline;

    public StageInvitePopup(String inviterName) {
        super(Component.translatable("gui.mmdskin.stage.invite_title"));
        this.inviterName = inviterName;
    }

    public static void show(UUID hostUUID) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        var host = mc.level.getPlayerByUUID(hostUUID);
        String name = host != null ? host.getName().getString() : hostUUID.toString().substring(0, 8);

        Runnable openPopup = () -> mc.setScreen(new StageInvitePopup(name));
        if (mc.isSameThread()) {
            openPopup.run();
        } else {
            mc.execute(openPopup);
        }
    }

    @Override
    protected void init() {
        px = (this.width - POPUP_W) / 2;
        py = 30;
        btnY = py + POPUP_H - BTN_H - MARGIN;
        acceptX = px + POPUP_W / 2 - BTN_W - 4;
        declineX = px + POPUP_W / 2 + 4;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {

        if (!StageInviteManager.getInstance().hasPendingInvite()) {
            this.onClose();
            return;
        }

        g.fill(px, py, px + POPUP_W, py + POPUP_H, BG);
        g.fill(px, py, px + POPUP_W, py + 1, BORDER);
        g.fill(px, py + POPUP_H - 1, px + POPUP_W, py + POPUP_H, BORDER);
        g.fill(px, py, px + 1, py + POPUP_H, BORDER);
        g.fill(px + POPUP_W - 1, py, px + POPUP_W, py + POPUP_H, BORDER);

        g.drawCenteredString(this.font, this.title, px + POPUP_W / 2, py + MARGIN, ACCENT);

        Component msg = Component.translatable("message.mmdskin.stage.invite_received", inviterName);
        g.drawCenteredString(this.font, msg, px + POPUP_W / 2, py + MARGIN + 13, TEXT);

        hoverAccept = mouseX >= acceptX && mouseX <= acceptX + BTN_W
                   && mouseY >= btnY && mouseY <= btnY + BTN_H;
        hoverDecline = mouseX >= declineX && mouseX <= declineX + BTN_W
                    && mouseY >= btnY && mouseY <= btnY + BTN_H;

        g.fill(acceptX, btnY, acceptX + BTN_W, btnY + BTN_H, hoverAccept ? BTN_ACCEPT_HOVER : BTN_ACCEPT);
        g.drawCenteredString(this.font,
            Component.translatable("message.mmdskin.stage.invite_accept"),
            acceptX + BTN_W / 2, btnY + 4, 0xFFFFFFFF);

        g.fill(declineX, btnY, declineX + BTN_W, btnY + BTN_H, hoverDecline ? BTN_DECLINE_HOVER : BTN_DECLINE);
        g.drawCenteredString(this.font,
            Component.translatable("message.mmdskin.stage.invite_decline"),
            declineX + BTN_W / 2, btnY + 4, 0xFFFFFFFF);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            StageInviteManager mgr = StageInviteManager.getInstance();
            if (hoverAccept) {
                mgr.acceptInvite();
                Minecraft.getInstance().setScreen(new StageSelectScreen());
                return true;
            }
            if (hoverDecline) {
                mgr.declineInvite();
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            StageInviteManager.getInstance().declineInvite();
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
