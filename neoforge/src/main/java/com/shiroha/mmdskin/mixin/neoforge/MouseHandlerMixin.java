package com.shiroha.mmdskin.mixin.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import com.shiroha.mmdskin.client.deathscreen.DeathScreenButtons;
import com.shiroha.mmdskin.player.ragdoll.RagdollManager;
import com.shiroha.mmdskin.stage.client.camera.MMDCameraController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MouseHandler Mixin — 舞台模式下拦截鼠标点击和视角旋转
 *
 * <p>26.3 起 GLFW 换成 SDL，鼠标按键回调由 {@code onPress(long, int, int, int)}
 * 换成了 {@code onButton(long, MouseButtonInfo, int)}：
 * 按键编号从 GLFW 的 0=左 1=右 2=中，变成 26.3 的 1=左 2=中 3=右。
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    /**
     * 鼠标位置的 GUI 坐标换算 —— 直接借用游戏自己的实现。
     *
     * <p><b>为什么必须用游戏的：</b>26.3 里 {@code MouseHandler.getScaledXPos} 的算法是
     * {@code 原始像素 × getGuiScaledWidth() ÷ getScreenWidth()}。
     * 之前这里自己手写成了 {@code 原始像素 × Screen.width ÷ getGuiScaledWidth()}，
     * <b>乘除颠倒</b>：在 854×480、GUI scale=2 的实例上 {@code Screen.width} 恰好等于
     * {@code getGuiScaledWidth()}（都是 427），公式退化成"直接返回原始像素坐标"，
     * 正好是正确值的两倍 —— 于是两个按钮的点击<b>永远判定为未命中</b>
     * （看得见、点不动）。{@code Gui} 渲染屏幕时传的 mouseX/mouseY 用的也是
     * {@code getScaledXPos}，所以悬停高亮一直是对的，只有判定算错。
     *
     * <p>直接 shadow 游戏的方法，保证判定坐标与渲染坐标同源，不可能再分叉。
     */
    @Shadow
    public abstract double getScaledXPos(com.mojang.blaze3d.platform.Window window);

    @Shadow
    public abstract double getScaledYPos(com.mojang.blaze3d.platform.Window window);

    /**
     * 死亡屏劫持的点击处理：右下角「自由观战」/「复活」两个按钮。
     *
     * <p>必须放在<b>原始输入层</b>（{@code MouseHandler.onButton}）而不是
     * {@code DeathScreen.mouseClicked}：26.3 的输入层会在把事件派发到 Screen 之前
     * 把点击消费掉（Screen 里的按钮拿不到点击）。原始输入层每一下按压都必然到达。
     */

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onButton(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (action == 1 && button.button() == InputConstants.MOUSE_BUTTON_LEFT
                && DeathScreenButtons.isHijackedDeathScreen(Minecraft.getInstance())) {
            // 死亡屏劫持：右下角两个按钮（自由观战 / 复活），点哪个做哪个。
            // 26.3 的输入层会在把事件派发到 Screen 之前消费掉整个点击序列，
            // 所以判定必须留在原始输入层，Screen 里的按钮拿不到点击。
            Minecraft minecraft = Minecraft.getInstance();
            net.minecraft.client.gui.screens.Screen screen = minecraft.gui.screen();
            int index = DeathScreenButtons.indexAt(
                    getScaledXPos(minecraft.getWindow()), getScaledYPos(minecraft.getWindow()),
                    screen.width, screen.height);
            if (index >= 0) {
                if (DeathScreenButtons.actionOf(index) == DeathScreenButtons.Action.FREE_LOOK) {
                    RagdollManager.getInstance().toggleDeathCamObserve();
                } else {
                    minecraft.player.respawn();
                    minecraft.gui.setScreen(null);
                }
            }
            ci.cancel();
            return;
        }

        MMDCameraController controller = MMDCameraController.getInstance();
        if (!controller.shouldBlockInput()) return;

        // 旧版拦的是 GLFW button == 1（右键），对应 26.3 的 button() == 3。
        if (action == 1 && button.button() == 3 && controller.isPlaying()) {
            controller.toggleMouseGrab();
        }
        ci.cancel();
    }

    /**
     * Death cam observe mode: take over mouse look.
     *
     * <p>26.3 note: Screen does NOT declare mouseMoved / mouseScrolled (they are
     * default methods on GuiEventListener), so they cannot be injected into a Screen
     * subclass. MouseHandler.onMove is the real input entry point, and it also owns
     * the accumulated deltas that would otherwise turn the player.
     */
    @Inject(method = "onMove", at = @At("TAIL"))
    private void onDeathCamMove(long window, double xpos, double ypos, double dx, double dy, CallbackInfo ci) {
        RagdollManager ragdoll = RagdollManager.getInstance();
        if (!ragdoll.isDeathCamObserve()) {
            return;
        }
        ragdoll.deathCamMouseMove(dx, dy);
        // Do not let the mouse delta turn the player while free-observing.
        this.accumulatedDX = 0.0;
        this.accumulatedDY = 0.0;
    }

    /** Death cam observe mode: take over scroll zoom. */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onDeathCamScroll(long window, double scrollX, double scrollY, CallbackInfo ci) {
        RagdollManager ragdoll = RagdollManager.getInstance();
        if (!ragdoll.isDeathCamObserve()) {
            return;
        }
        ragdoll.deathCamScroll(scrollY);
        ci.cancel();
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onStageTurnPlayer(double partialTick, CallbackInfo ci) {
        if (MMDCameraController.getInstance().shouldBlockInput()) {
            this.accumulatedDX = 0.0;
            this.accumulatedDY = 0.0;
            ci.cancel();
        }
    }
}
