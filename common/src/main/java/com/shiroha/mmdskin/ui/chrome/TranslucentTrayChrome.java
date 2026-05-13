/** 文件职责：统一托盘界面的半透明外观与基础绘制。 */
package com.shiroha.mmdskin.ui.chrome;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 文件职责：统一托盘界面的半透明外观与基础绘制。 */
public final class TranslucentTrayChrome {
    public static final int OVERLAY = 0x28000000;
    public static final int PANEL_OUTER = 0x2A000000;
    public static final int PANEL_INNER = 0x20000000;
    public static final int LIST_BACKGROUND = 0x22000000;
    public static final int CARD_BACKGROUND = 0x24000000;
    public static final int CARD_HOVER = 0x38FFFFFF;
    public static final int CARD_SELECTED = 0x52FFFFFF;
    public static final int BUTTON_BACKGROUND = 0x30000000;
    public static final int BUTTON_HOVER = 0x4AFFFFFF;
    public static final int BUTTON_DISABLED = 0x1A000000;
    public static final int SCROLL_TRACK = 0x20FFFFFF;
    public static final int SCROLL_THUMB = 0x88A8D8FF;
    public static final int SEPARATOR = 0x22FFFFFF;
    public static final int ACCENT = 0xFF60A0D0;
    public static final int TITLE_TEXT = 0xFFF1F5FB;
    public static final int SUBTITLE_TEXT = 0xC8D5DFEC;
    public static final int DETAIL_TEXT = 0xBCD0DCE9;
    public static final int BODY_TEXT = 0xFFE9F1FA;
    public static final int DIM_TEXT = 0xB3C4D6E6;
    public static final int MUTED_TEXT = 0x9BB2C5D7;
    public static final int ACCENT_STRIP = 0x44A8D8FF;
    public static final int ACCENT_STRIP_ACTIVE = 0x92A8D8FF;

    private TranslucentTrayChrome() {
    }

    public static void drawOverlay(GuiGraphics graphics, int screenWidth, int screenHeight) {
        graphics.fill(0, 0, screenWidth, screenHeight, OVERLAY);
    }

    public static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_OUTER);
        if (width > 2 && height > 2) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL_INNER);
        }
    }

    public static void fillListArea(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, LIST_BACKGROUND);
    }

    public static void drawSeparator(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, SEPARATOR);
    }

    public static void drawButton(GuiGraphics graphics, Font font,
                                  int x, int y, int width, int height,
                                  String text, boolean hovered, boolean enabled) {
        int background = enabled ? (hovered ? BUTTON_HOVER : BUTTON_BACKGROUND) : BUTTON_DISABLED;
        int textColor = enabled ? TITLE_TEXT : MUTED_TEXT;
        graphics.fill(x, y, x + width, y + height, background);
        graphics.drawCenteredString(font, text, x + width / 2, y + Math.max(0, (height - font.lineHeight) / 2), textColor);
    }

    public static void drawButton(GuiGraphics graphics, Font font,
                                  int x, int y, int width, int height,
                                  Component text, boolean hovered, boolean enabled) {
        drawButton(graphics, font, x, y, width, height, text.getString(), hovered, enabled);
    }

    public static void drawScrollbar(GuiGraphics graphics, int x, int top, int bottom, float offset, float maxScroll) {
        if (maxScroll <= 0.0f) {
            return;
        }
        int barHeight = bottom - top;
        graphics.fill(x, top, x + 2, bottom, SCROLL_TRACK);
        int thumbHeight = Math.max(12, Math.round(barHeight * ((float) barHeight / (barHeight + maxScroll))));
        int travel = Math.max(1, barHeight - thumbHeight);
        int thumbY = top + Math.round((offset / maxScroll) * travel);
        graphics.fill(x, thumbY, x + 2, thumbY + thumbHeight, SCROLL_THUMB);
    }

    public static int cardBackground(boolean selected, boolean hovered) {
        if (selected) {
            return CARD_SELECTED;
        }
        return hovered ? CARD_HOVER : CARD_BACKGROUND;
    }

    public static int brighten(int color, int delta) {
        int a = color >>> 24;
        int r = Math.min(255, ((color >>> 16) & 0xFF) + delta);
        int g = Math.min(255, ((color >>> 8) & 0xFF) + delta);
        int b = Math.min(255, (color & 0xFF) + delta);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
