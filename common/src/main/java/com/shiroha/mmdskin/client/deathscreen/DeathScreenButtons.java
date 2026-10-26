// 负责死亡屏右下角两个按钮的布局与命中判定。
//
// 为什么放在这里：按钮要在两个地方用到 ——
//   1. DeathScreenMixin 负责画（extractRenderState 是 26.3 的抽取式绘制入口）；
//   2. MouseHandlerMixin 负责点（26.3 的输入层会在派发到 Screen 之前把点击消费掉，
//      所以点击判定必须放在原始输入层）。
// 两边共用同一份矩形计算，坐标才不会对不上。
package com.shiroha.mmdskin.client.deathscreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import com.shiroha.mmdskin.player.ragdoll.RagdollManager;

/**
 * 死亡屏劫持时右下角的操作按钮。
 *
 * <p>早先的交互是「屏幕中央一行提示 + 单击观战 / 双击复活」。双击在 26.3 的输入层
 * 不可靠（合成事件没有 click count，真人双击又和输入层抢事件），所以改成
 * **右下角两个普通按钮**：点哪个做哪个，不用记手势。
 */
public final class DeathScreenButtons {

    /** 按钮行为。 */
    public enum Action {
        /** 切换自由观战（鼠标环视 / 滚轮缩放）。 */
        FREE_LOOK,
        /** 立即复活。 */
        REVIVE
    }

    /** 按钮矩形（GUI 坐标）。 */
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        public int centerX() {
            return x + width / 2;
        }

        public int centerY() {
            return y + height / 2;
        }
    }

    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int SCREEN_MARGIN = 8;

    private DeathScreenButtons() {
    }

    /**
     * 死亡屏劫持是否生效（困难模式完全放行原版行为）。
     */
    public static boolean hijackActive(Minecraft minecraft) {
        return minecraft.level != null
                && minecraft.player != null
                && !minecraft.level.getLevelData().isHardcore()
                && RagdollManager.getInstance().isDeathScreenHijackActive();
    }

    /**
     * 两个按钮的矩形：左「自由观战」、右「复活」，贴在屏幕右下角。
     */
    public static Rect[] layout(int screenWidth, int screenHeight) {
        int y = screenHeight - SCREEN_MARGIN - BUTTON_HEIGHT;
        int right = screenWidth - SCREEN_MARGIN;
        Rect revive = new Rect(right - BUTTON_WIDTH, y, BUTTON_WIDTH, BUTTON_HEIGHT);
        Rect freeLook = new Rect(right - BUTTON_WIDTH * 2 - BUTTON_GAP, y, BUTTON_WIDTH, BUTTON_HEIGHT);
        return new Rect[]{freeLook, revive};
    }

    /**
     * 命中的按钮下标（0=自由观战，1=复活）；没命中返回 -1。
     */
    public static int indexAt(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        Rect[] rects = layout(screenWidth, screenHeight);
        for (int index = 0; index < rects.length; index++) {
            if (rects[index].contains(mouseX, mouseY)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 按钮下标对应的行为。
     */
    public static Action actionOf(int index) {
        return index == 0 ? Action.FREE_LOOK : Action.REVIVE;
    }

    /**
     * 当前屏幕是不是被劫持的死亡屏。
     */
    public static boolean isHijackedDeathScreen(Minecraft minecraft) {
        return minecraft.gui.screen() instanceof DeathScreen && hijackActive(minecraft);
    }
}
