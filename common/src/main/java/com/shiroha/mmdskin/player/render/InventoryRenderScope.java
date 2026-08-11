package com.shiroha.mmdskin.player.render;

/** 文件职责：标记当前线程是否正在执行背包人物预览 Draw。 */
public final class InventoryRenderScope {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private InventoryRenderScope() {
    }

    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void exit() {
        int depth = DEPTH.get();
        if (depth <= 1) {
            DEPTH.remove();
            return;
        }
        DEPTH.set(depth - 1);
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }
}
