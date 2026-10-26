// 负责把原生层的诊断入口暴露给上层调用（NativeBindings 本身是包私有的）。
package com.shiroha.mmdskin.bridge;

/**
 * 原生层诊断入口。
 *
 * <p>目前只提供第一人称诊断：Rust 侧把头部识别结果、被隐藏的材质、眼睛骨骼位置
 * 一次性摊平成字符串，这里原样透出，方便直接在游戏日志里排查
 * 「第一人称视角卡在模型内部」这类问题。
 */
public final class NativeDiagnostics {

    private NativeDiagnostics() {
    }

    /**
     * 第一人称诊断信息。
     *
     * @param modelHandle 原生模型句柄
     * @return 诊断字符串；句柄无效时返回 {@code "no-model"}
     */
    public static String firstPersonInfo(long modelHandle) {
        return NativeBindings.GetFirstPersonInfo(modelHandle);
    }
}
