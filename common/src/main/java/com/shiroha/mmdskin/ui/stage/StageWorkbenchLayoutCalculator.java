package com.shiroha.mmdskin.ui.stage;

/** 舞台工作台布局计算器。 */
final class StageWorkbenchLayoutCalculator {
    static final int WINDOW_MARGIN = 10;
    static final int LEFT_MIN_WIDTH = 176;
    static final int LEFT_MAX_WIDTH = 224;
    static final int RIGHT_MIN_WIDTH = 148;
    static final int RIGHT_MAX_WIDTH = 188;
    static final int RIGHT_MIN_HEIGHT = 126;
    static final int RIGHT_MAX_HEIGHT = 210;
    static final int HEADER_HEIGHT = 28;
    static final int LIST_PADDING = 4;
    static final int ROW_GAP = 3;
    static final int PACK_ROW_HEIGHT = 15;
    static final int MOTION_ROW_HEIGHT = 16;
    static final int SESSION_ROW_HEIGHT = 21;
    static final int BUTTON_HEIGHT = 16;
    static final int BUTTON_GAP = 5;

    StageWorkbenchLayout calculate(int screenWidth, int screenHeight, boolean sessionMember) {
        int leftWidth = clamp(Math.round(screenWidth * 0.18f), LEFT_MIN_WIDTH, LEFT_MAX_WIDTH);
        int leftHeight = Math.max(272, screenHeight - WINDOW_MARGIN * 2);
        int leftX = WINDOW_MARGIN;
        int leftY = WINDOW_MARGIN;

        int rightWidth = clamp(Math.round(screenWidth * 0.11f), RIGHT_MIN_WIDTH, RIGHT_MAX_WIDTH);
        int rightHeight = clamp(Math.round(screenHeight * 0.22f), RIGHT_MIN_HEIGHT, RIGHT_MAX_HEIGHT);
        int rightX = screenWidth - rightWidth - WINDOW_MARGIN;
        int rightY = WINDOW_MARGIN;

        StageWorkbenchLayout.UiRect leftPanel = new StageWorkbenchLayout.UiRect(leftX, leftY, leftWidth, leftHeight);
        StageWorkbenchLayout.UiRect rightPanel = new StageWorkbenchLayout.UiRect(rightX, rightY, rightWidth, rightHeight);

        int contentX = leftPanel.x() + 10;
        int contentWidth = leftPanel.w() - 20;
        StageWorkbenchLayout.UiRect leftHeader = new StageWorkbenchLayout.UiRect(contentX, leftPanel.y() + 10, contentWidth, HEADER_HEIGHT);
        StageWorkbenchLayout.UiRect refreshButton = new StageWorkbenchLayout.UiRect(contentX + contentWidth - 42, leftHeader.y() + 2, 42, BUTTON_HEIGHT);

        int packListY = leftHeader.y() + leftHeader.h() + 10;
        int listSectionGap = 8;
        int listToDetailsGap = 4;
        int buttonY = leftPanel.y() + leftPanel.h() - 8 - BUTTON_HEIGHT;
        int buttonWidth = (contentWidth - BUTTON_GAP) / 2;
        int toggleRowHeight = 14;

        StageWorkbenchLayout.UiRect primaryButton = new StageWorkbenchLayout.UiRect(contentX, buttonY, buttonWidth, BUTTON_HEIGHT);
        StageWorkbenchLayout.UiRect secondaryButton = new StageWorkbenchLayout.UiRect(
                contentX + buttonWidth + BUTTON_GAP,
                buttonY,
                contentWidth - buttonWidth - BUTTON_GAP,
                BUTTON_HEIGHT
        );

        int bottomCursor = primaryButton.y() - 8;
        StageWorkbenchLayout.UiRect cinematicToggle = new StageWorkbenchLayout.UiRect(contentX, bottomCursor - toggleRowHeight, contentWidth, toggleRowHeight);
        bottomCursor = cinematicToggle.y() - 4;

        StageWorkbenchLayout.UiRect customMotionToggle = StageWorkbenchLayout.UiRect.empty();
        StageWorkbenchLayout.UiRect useHostCameraToggle = StageWorkbenchLayout.UiRect.empty();
        StageWorkbenchLayout.UiRect cameraSlider = StageWorkbenchLayout.UiRect.empty();
        StageWorkbenchLayout.UiRect sessionButton = StageWorkbenchLayout.UiRect.empty();

        if (sessionMember) {
            useHostCameraToggle = new StageWorkbenchLayout.UiRect(contentX, bottomCursor - toggleRowHeight, contentWidth, toggleRowHeight);
            bottomCursor = useHostCameraToggle.y() - 4;
            customMotionToggle = new StageWorkbenchLayout.UiRect(contentX, bottomCursor - toggleRowHeight, contentWidth, toggleRowHeight);
            bottomCursor = customMotionToggle.y() - 4;
        } else {
            cameraSlider = new StageWorkbenchLayout.UiRect(contentX, bottomCursor - 10, contentWidth, 10);
            bottomCursor = cameraSlider.y() - 10;
        }

        int detailsHeight = 30;
        int detailsY = bottomCursor - detailsHeight;
        int listsAvailableHeight = Math.max(80, detailsY - listToDetailsGap - packListY - listSectionGap);
        int motionListHeight = Math.max(36, Math.min(Math.max(52, Math.round(listsAvailableHeight * 0.24f)), listsAvailableHeight - 44));
        int packListHeight = Math.max(44, listsAvailableHeight - motionListHeight);
        int motionListY = packListY + packListHeight + listSectionGap;

        StageWorkbenchLayout.UiRect packList = new StageWorkbenchLayout.UiRect(contentX, packListY, contentWidth, packListHeight);
        StageWorkbenchLayout.UiRect motionList = new StageWorkbenchLayout.UiRect(contentX, motionListY, contentWidth, motionListHeight);
        StageWorkbenchLayout.UiRect detailsArea = new StageWorkbenchLayout.UiRect(contentX, detailsY, contentWidth, detailsHeight);

        StageWorkbenchLayout.UiRect rightHeader = new StageWorkbenchLayout.UiRect(rightPanel.x() + 10, rightPanel.y() + 10, rightPanel.w() - 20, 24);
        int sessionListY;
        if (sessionMember) {
            sessionListY = rightHeader.y() + rightHeader.h() + 8;
        } else {
            int sessionButtonY = rightHeader.y() + rightHeader.h() + 8;
            sessionButton = new StageWorkbenchLayout.UiRect(rightPanel.x() + 10, sessionButtonY, rightPanel.w() - 20, BUTTON_HEIGHT);
            sessionListY = sessionButtonY + BUTTON_HEIGHT + 8;
        }
        StageWorkbenchLayout.UiRect sessionList = new StageWorkbenchLayout.UiRect(
                rightPanel.x() + 10,
                sessionListY,
                rightPanel.w() - 20,
                rightPanel.y() + rightPanel.h() - 10 - sessionListY
        );

        return new StageWorkbenchLayout(
                leftPanel,
                rightPanel,
                leftHeader,
                packList,
                refreshButton,
                motionList,
                detailsArea,
                customMotionToggle,
                useHostCameraToggle,
                cinematicToggle,
                cameraSlider,
                primaryButton,
                secondaryButton,
                rightHeader,
                sessionButton,
                sessionList
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
