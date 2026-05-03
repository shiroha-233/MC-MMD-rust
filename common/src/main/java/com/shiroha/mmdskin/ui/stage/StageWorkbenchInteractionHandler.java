package com.shiroha.mmdskin.ui.stage;

import net.minecraft.util.Mth;

/** 舞台工作台交互命中与滚动计算。 */
final class StageWorkbenchInteractionHandler {
    HoverState resolveHover(StageWorkbenchLayout layout,
                            double mouseX,
                            double mouseY,
                            boolean sessionMember,
                            StageWorkbenchUiStateSnapshot snapshot,
                            float packScroll,
                            float motionScroll,
                            float sessionScroll) {
        if (layout.refreshButton().contains(mouseX, mouseY)) {
            return new HoverState(HoverTarget.REFRESH, -1, -1, -1);
        }
        if (sessionMember && layout.customMotionToggle().contains(mouseX, mouseY)) {
            return new HoverState(HoverTarget.CUSTOM_MOTION, -1, -1, -1);
        }
        if (sessionMember && layout.useHostCameraToggle().contains(mouseX, mouseY)) {
            return new HoverState(HoverTarget.USE_HOST_CAMERA, -1, -1, -1);
        }
        if (layout.cinematicToggle().contains(mouseX, mouseY)) {
            return new HoverState(HoverTarget.CINEMATIC, -1, -1, -1);
        }
        if (!sessionMember && layout.cameraSlider().contains(mouseX, mouseY)) {
            return new HoverState(HoverTarget.CAMERA_SLIDER, -1, -1, -1);
        }
        if (layout.primaryButton().contains(mouseX, mouseY)) {
            return new HoverState(HoverTarget.PRIMARY_ACTION, -1, -1, -1);
        }
        if (layout.secondaryButton().contains(mouseX, mouseY)) {
            return new HoverState(HoverTarget.SECONDARY_ACTION, -1, -1, -1);
        }
        if (!sessionMember && layout.sessionButton().contains(mouseX, mouseY)) {
            return new HoverState(HoverTarget.SESSION_ACTION, -1, -1, -1);
        }

        int hoveredPack = findHoveredIndex(layout.packList(), mouseY, packScroll, snapshot.packRows().size(), StageWorkbenchLayoutCalculator.PACK_ROW_HEIGHT);
        if (hoveredPack >= 0) {
            return new HoverState(HoverTarget.NONE, hoveredPack, -1, -1);
        }
        int hoveredMotion = findHoveredIndex(layout.motionList(), mouseY, motionScroll, snapshot.motionRows().size(), StageWorkbenchLayoutCalculator.MOTION_ROW_HEIGHT);
        if (hoveredMotion >= 0) {
            return new HoverState(HoverTarget.NONE, -1, hoveredMotion, -1);
        }
        int hoveredSession = findHoveredIndex(layout.sessionList(), mouseY, sessionScroll, snapshot.sessionRows().size(), StageWorkbenchLayoutCalculator.SESSION_ROW_HEIGHT);
        return new HoverState(HoverTarget.NONE, -1, -1, hoveredSession);
    }

    float updateScrollTarget(float currentTarget, double delta, int count, int rowHeight, int boxHeight) {
        float step = 14.0f;
        return Mth.clamp(currentTarget - (float) delta * step, 0.0f, maxScroll(count, rowHeight, boxHeight));
    }

    float clampScroll(float value, int count, int rowHeight, int boxHeight) {
        return Mth.clamp(value, 0.0f, maxScroll(count, rowHeight, boxHeight));
    }

    float animateScroll(float current, float target) {
        float next = Mth.lerp(0.24f, current, target);
        return Math.abs(next - target) < 0.25f ? target : next;
    }

    private int findHoveredIndex(StageWorkbenchLayout.UiRect rect, double mouseY, float scrollOffset, int count, int rowHeight) {
        if (count <= 0 || mouseY < rect.y() || mouseY > rect.y() + rect.h()) {
            return -1;
        }
        float localY = (float) mouseY - rect.y() - StageWorkbenchLayoutCalculator.LIST_PADDING + scrollOffset;
        if (localY < 0.0f) {
            return -1;
        }
        float stride = rowHeight + StageWorkbenchLayoutCalculator.ROW_GAP;
        int index = (int) (localY / stride);
        if (index < 0 || index >= count) {
            return -1;
        }
        float offsetInItem = localY - index * stride;
        return offsetInItem <= rowHeight ? index : -1;
    }

    private float maxScroll(int count, int rowHeight, int boxHeight) {
        float contentHeight = count <= 0
                ? 0.0f
                : StageWorkbenchLayoutCalculator.LIST_PADDING * 2.0f
                + count * rowHeight
                + Math.max(0, count - 1) * StageWorkbenchLayoutCalculator.ROW_GAP;
        return Math.max(0.0f, contentHeight - boxHeight);
    }

    enum HoverTarget {
        NONE,
        REFRESH,
        CUSTOM_MOTION,
        USE_HOST_CAMERA,
        CINEMATIC,
        CAMERA_SLIDER,
        PRIMARY_ACTION,
        SECONDARY_ACTION,
        SESSION_ACTION
    }

    enum ActiveSlider {
        NONE,
        CAMERA_HEIGHT
    }

    record HoverState(HoverTarget target, int packIndex, int motionIndex, int sessionIndex) {
        static HoverState empty() {
            return new HoverState(HoverTarget.NONE, -1, -1, -1);
        }
    }
}
