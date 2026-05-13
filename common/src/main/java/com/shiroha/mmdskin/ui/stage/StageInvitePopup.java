package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** 舞台邀请弹窗，负责确认加入舞台会话。 */
public class StageInvitePopup extends Screen {
    private final StageWorkbenchFacade facade = StageWorkbenchFacade.getInstance();

    private static final int POPUP_W = 236;
    private static final int POPUP_H = 72;
    private static final int BTN_W = 84;
    private static final int BTN_H = 16;
    private static final int MARGIN = 8;

    private static final int BTN_ACCEPT = 0x3440A060;
    private static final int BTN_ACCEPT_HOVER = 0x4850C070;
    private static final int BTN_DECLINE = 0x304F5863;
    private static final int BTN_DECLINE_HOVER = 0x4465707C;

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
        acceptX = px + MARGIN;
        declineX = px + POPUP_W - BTN_W - MARGIN;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {

        if (!facade.hasPendingInvite()) {
            this.onClose();
            return;
        }

        TranslucentTrayChrome.drawOverlay(g, this.width, this.height);
        TranslucentTrayChrome.drawPanel(g, px, py, POPUP_W, POPUP_H);
        TranslucentTrayChrome.drawSeparator(g, px + MARGIN, py + 18, POPUP_W - MARGIN * 2);

        g.drawCenteredString(this.font, this.title, px + POPUP_W / 2, py + MARGIN - 1, TranslucentTrayChrome.TITLE_TEXT);

        Component msg = Component.translatable("message.mmdskin.stage.invite_received", inviterName);
        g.drawCenteredString(this.font, msg, px + POPUP_W / 2, py + 26, TranslucentTrayChrome.BODY_TEXT);
        g.drawCenteredString(this.font,
                Component.translatable("gui.mmdskin.stage.invite_prompt"),
                px + POPUP_W / 2,
                py + 38,
                TranslucentTrayChrome.SUBTITLE_TEXT);

        hoverAccept = mouseX >= acceptX && mouseX <= acceptX + BTN_W
                   && mouseY >= btnY && mouseY <= btnY + BTN_H;
        hoverDecline = mouseX >= declineX && mouseX <= declineX + BTN_W
                    && mouseY >= btnY && mouseY <= btnY + BTN_H;

        g.fill(acceptX, btnY, acceptX + BTN_W, btnY + BTN_H, hoverAccept ? BTN_ACCEPT_HOVER : BTN_ACCEPT);
        g.drawCenteredString(this.font,
            Component.translatable("gui.mmdskin.stage.accept"),
            acceptX + BTN_W / 2, btnY + 4, TranslucentTrayChrome.TITLE_TEXT);

        g.fill(declineX, btnY, declineX + BTN_W, btnY + BTN_H, hoverDecline ? BTN_DECLINE_HOVER : BTN_DECLINE);
        g.drawCenteredString(this.font,
            Component.translatable("gui.mmdskin.stage.decline"),
            declineX + BTN_W / 2, btnY + 4, TranslucentTrayChrome.TITLE_TEXT);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hoverAccept) {
                facade.acceptInvite();
                StagePlaybackUiAdapter.INSTANCE.openStageSelection();
                return true;
            }
            if (hoverDecline) {
                facade.declineInvite();
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            facade.declineInvite();
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
