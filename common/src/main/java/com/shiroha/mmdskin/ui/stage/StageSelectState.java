/* 文件职责：维护原生舞台选择界面的可变选择状态。 */
package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StagePack;

import java.util.List;
import java.util.Objects;

/** 文件职责：维护原生舞台选择界面的可变选择状态。 */
final class StageSelectState {
    private List<StagePack> stagePacks = List.of();
    private int selectedPackIndex = -1;
    private String selectedHostMotionFileName;
    private boolean cinematicMode;
    private float cameraHeightOffset;

    StageSelectState(StageWorkbenchFacade.WorkbenchPreferences preferences, List<StagePack> initialStagePacks) {
        Objects.requireNonNull(preferences, "preferences");
        this.cinematicMode = preferences.cinematicMode();
        this.cameraHeightOffset = preferences.cameraHeightOffset();
        replaceStagePacks(initialStagePacks, preferences.lastStagePack());
    }

    void replaceStagePacks(List<StagePack> nextStagePacks, String preferredPackName) {
        this.stagePacks = List.copyOf(Objects.requireNonNull(nextStagePacks, "nextStagePacks"));
        this.selectedPackIndex = resolvePackIndex(preferredPackName);
        if (selectedPackIndex < 0 && !stagePacks.isEmpty()) {
            selectedPackIndex = 0;
        }
        normalizeSelectedHostMotion();
    }

    boolean selectPack(int packIndex) {
        if (packIndex < 0 || packIndex >= stagePacks.size() || packIndex == selectedPackIndex) {
            return false;
        }
        selectedPackIndex = packIndex;
        normalizeSelectedHostMotion();
        return true;
    }

    void toggleSelectedHostMotion(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return;
        }
        selectedHostMotionFileName = fileName.equals(selectedHostMotionFileName) ? null : fileName;
        normalizeSelectedHostMotion();
    }

    void clearSelectedHostMotion() {
        selectedHostMotionFileName = null;
    }

    void toggleCinematicMode() {
        cinematicMode = !cinematicMode;
    }

    void setCameraHeightOffset(float cameraHeightOffset) {
        this.cameraHeightOffset = cameraHeightOffset;
    }

    List<StagePack> stagePacks() {
        return stagePacks;
    }

    StagePack selectedPack() {
        if (selectedPackIndex < 0 || selectedPackIndex >= stagePacks.size()) {
            return null;
        }
        return stagePacks.get(selectedPackIndex);
    }

    int selectedPackIndex() {
        return selectedPackIndex;
    }

    String selectedHostMotionFileName() {
        return selectedHostMotionFileName;
    }

    boolean cinematicMode() {
        return cinematicMode;
    }

    float cameraHeightOffset() {
        return cameraHeightOffset;
    }

    private int resolvePackIndex(String preferredPackName) {
        if (preferredPackName == null || preferredPackName.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < stagePacks.size(); i++) {
            if (preferredPackName.equals(stagePacks.get(i).getName())) {
                return i;
            }
        }
        return -1;
    }

    private void normalizeSelectedHostMotion() {
        if (selectedHostMotionFileName == null) {
            return;
        }
        StagePack selectedPack = selectedPack();
        if (selectedPack == null) {
            selectedHostMotionFileName = null;
            return;
        }
        for (StagePack.VmdFileInfo info : selectedPack.getVmdFiles()) {
            if ((info.hasBones || info.hasMorphs) && info.name.equals(selectedHostMotionFileName)) {
                return;
            }
        }
        selectedHostMotionFileName = null;
    }
}
