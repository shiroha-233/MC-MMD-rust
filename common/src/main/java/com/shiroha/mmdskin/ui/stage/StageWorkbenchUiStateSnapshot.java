package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StagePack;

import java.util.List;
import java.util.UUID;

/** 舞台工作台静态视图快照，缓存列表与文案。 */
record StageWorkbenchUiStateSnapshot(
        StagePack selectedPack,
        List<StagePack.VmdFileInfo> motionFiles,
        List<StagePack.VmdFileInfo> cameraFiles,
        List<PackRow> packRows,
        List<MotionRow> motionRows,
        List<SessionRow> sessionRows,
        List<String> detailLines,
        String leftSubtitle,
        String roomStats,
        String footerStatus
) {
    static StageWorkbenchUiStateSnapshot empty() {
        return new StageWorkbenchUiStateSnapshot(null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "", "", "");
    }

    /** 舞台包列表行。 */
    record PackRow(String label, boolean selected, int index) {
    }

    /** 动作列表行。 */
    record MotionRow(String label, String subtitle, boolean selected, int index, String fileName, boolean mergeAll) {
    }

    /** 房间成员列表行。 */
    record SessionRow(String label, String subtitle, String actionText, boolean selected, boolean actionable, int index, UUID targetId) {
    }
}
