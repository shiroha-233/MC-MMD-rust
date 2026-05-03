package com.shiroha.mmdskin.stage.client.camera;

/** 文件职责：表达舞台播放时钟推进后的纯计算结果。 */
record StagePlaybackAdvance(float frame, float targetSyncFrame, float effectiveSpeed, boolean completed) {
}
