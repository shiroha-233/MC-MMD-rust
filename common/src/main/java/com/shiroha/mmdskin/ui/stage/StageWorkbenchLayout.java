package com.shiroha.mmdskin.ui.stage;

/** 舞台工作台布局矩形集合。 */
record StageWorkbenchLayout(
        StageWorkbenchLayout.UiRect leftPanel,
        StageWorkbenchLayout.UiRect rightPanel,
        StageWorkbenchLayout.UiRect leftHeader,
        StageWorkbenchLayout.UiRect packList,
        StageWorkbenchLayout.UiRect refreshButton,
        StageWorkbenchLayout.UiRect motionList,
        StageWorkbenchLayout.UiRect detailsArea,
        StageWorkbenchLayout.UiRect customMotionToggle,
        StageWorkbenchLayout.UiRect useHostCameraToggle,
        StageWorkbenchLayout.UiRect cinematicToggle,
        StageWorkbenchLayout.UiRect cameraSlider,
        StageWorkbenchLayout.UiRect primaryButton,
        StageWorkbenchLayout.UiRect secondaryButton,
        StageWorkbenchLayout.UiRect rightHeader,
        StageWorkbenchLayout.UiRect sessionButton,
        StageWorkbenchLayout.UiRect sessionList
) {
    static StageWorkbenchLayout empty() {
        UiRect empty = UiRect.empty();
        return new StageWorkbenchLayout(empty, empty, empty, empty, empty, empty, empty, empty, empty, empty, empty, empty, empty, empty, empty, empty);
    }

    /** 界面矩形区域。 */
    record UiRect(int x, int y, int w, int h) {
        static UiRect empty() {
            return new UiRect(0, 0, 0, 0);
        }

        boolean contains(double px, double py) {
            return px >= x && py >= y && px <= x + w && py <= y + h;
        }

        int centerX() {
            return x + w / 2;
        }

        int centerY() {
            return y + h / 2;
        }
    }
}
