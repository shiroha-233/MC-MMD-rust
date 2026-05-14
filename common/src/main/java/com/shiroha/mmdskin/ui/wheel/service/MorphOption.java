/* 文件职责：承载表情轮盘单个选项的显示与同步语义。 */
package com.shiroha.mmdskin.ui.wheel.service;

public record MorphOption(
        String displayName,
        String morphName,
        String filePath,
        String syncToken,
        boolean resetAction) {
}
