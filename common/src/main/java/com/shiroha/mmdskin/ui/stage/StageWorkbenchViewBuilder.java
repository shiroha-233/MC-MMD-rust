package com.shiroha.mmdskin.ui.stage;

import com.shiroha.mmdskin.config.StagePack;
import com.shiroha.mmdskin.stage.client.viewmodel.StageLobbyViewModel;
import com.shiroha.mmdskin.stage.domain.model.StageMemberState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 舞台工作台视图构建器，负责快照与渲染数据拼装。 */
final class StageWorkbenchViewBuilder {
    StageWorkbenchUiStateSnapshot buildSnapshot(StageWorkbenchFacade facade,
                                                List<StagePack> stagePacks,
                                                int selectedPackIndex,
                                                String selectedHostMotionFileName,
                                                float cameraHeightOffset,
                                                List<StageLobbyViewModel.MemberView> memberViews,
                                                List<StageLobbyViewModel.HostEntry> hostEntries) {
        StagePack selectedPack = getSelectedPack(stagePacks, selectedPackIndex);
        List<StagePack.VmdFileInfo> motionFiles = collectMotionFiles(selectedPack);
        List<StagePack.VmdFileInfo> cameraFiles = collectCameraFiles(selectedPack);
        List<StageWorkbenchUiStateSnapshot.PackRow> packRows = buildPackRows(stagePacks, selectedPackIndex);
        List<StageWorkbenchUiStateSnapshot.MotionRow> motionRows = buildMotionRows(facade, motionFiles, selectedHostMotionFileName);
        List<StageWorkbenchUiStateSnapshot.SessionRow> sessionRows = buildSessionRows(facade, memberViews, hostEntries);
        List<String> detailLines = buildDetailLines(facade, selectedPack, motionFiles, cameraFiles, cameraHeightOffset);

        String leftSubtitle = wb(facade.isSessionMember() ? "flow.guest" : "flow.host")
                + " · " + wb("packs.count", stagePacks.size());
        String roomStats = facade.isSessionMember()
                ? wb("guest.members.short", sessionRows.size())
                : wb("host.stats.short", sessionRows.size(), readyMemberCount(hostEntries));

        return new StageWorkbenchUiStateSnapshot(
                selectedPack,
                motionFiles,
                cameraFiles,
                List.copyOf(packRows),
                List.copyOf(motionRows),
                List.copyOf(sessionRows),
                List.copyOf(detailLines),
                leftSubtitle,
                roomStats,
                buildFooterStatus(facade, selectedPack)
        );
    }

    SkiaStageWorkbenchRenderer.WorkbenchView buildWorkbenchView(Component title,
                                                               StageWorkbenchFacade facade,
                                                               StageWorkbenchLayout layout,
                                                               StageWorkbenchUiStateSnapshot snapshot,
                                                               StageWorkbenchInteractionHandler.HoverState hoverState,
                                                               boolean cinematicMode,
                                                               float cameraHeightOffset,
                                                               float packAnimatedScroll,
                                                               float motionAnimatedScroll,
                                                               float sessionAnimatedScroll) {
        return new SkiaStageWorkbenchRenderer.WorkbenchView(
                title.getString(),
                snapshot.leftSubtitle(),
                wb("packs.section"),
                wb("playback.section.short"),
                wb("session.section"),
                snapshot.roomStats(),
                Component.translatable("gui.mmdskin.refresh").getString(),
                !facade.isSessionMember() ? wb("host.invite_all.short") : "",
                facade.isSessionMember()
                        ? (facade.isLocalReady()
                        ? Component.translatable("gui.mmdskin.stage.unready").getString()
                        : Component.translatable("gui.mmdskin.stage.ready").getString())
                        : Component.translatable("gui.mmdskin.stage.start").getString(),
                Component.translatable("gui.cancel").getString(),
                snapshot.footerStatus(),
                snapshot.detailLines(),
                layout.leftPanel(),
                layout.rightPanel(),
                layout.leftHeader(),
                layout.packList(),
                layout.refreshButton(),
                layout.motionList(),
                layout.detailsArea(),
                layout.customMotionToggle(),
                layout.useHostCameraToggle(),
                layout.cinematicToggle(),
                layout.cameraSlider(),
                layout.primaryButton(),
                layout.secondaryButton(),
                layout.rightHeader(),
                layout.sessionButton(),
                layout.sessionList(),
                hoverState.target() == StageWorkbenchInteractionHandler.HoverTarget.REFRESH,
                hoverState.target() == StageWorkbenchInteractionHandler.HoverTarget.CUSTOM_MOTION,
                hoverState.target() == StageWorkbenchInteractionHandler.HoverTarget.USE_HOST_CAMERA,
                hoverState.target() == StageWorkbenchInteractionHandler.HoverTarget.CINEMATIC,
                hoverState.target() == StageWorkbenchInteractionHandler.HoverTarget.CAMERA_SLIDER,
                hoverState.target() == StageWorkbenchInteractionHandler.HoverTarget.PRIMARY_ACTION,
                hoverState.target() == StageWorkbenchInteractionHandler.HoverTarget.SECONDARY_ACTION,
                hoverState.target() == StageWorkbenchInteractionHandler.HoverTarget.SESSION_ACTION,
                hoverState.packIndex(),
                hoverState.motionIndex(),
                hoverState.sessionIndex(),
                facade.isSessionMember(),
                !facade.isSessionMember(),
                facade.isSessionMember() && snapshot.selectedPack() != null,
                facade.isSessionMember(),
                !facade.isSessionMember(),
                cinematicMode,
                facade.isUseHostCamera(),
                facade.isLocalCustomMotionEnabled(),
                normalizeCameraHeight(cameraHeightOffset),
                packAnimatedScroll,
                motionAnimatedScroll,
                sessionAnimatedScroll,
                snapshot.packRows(),
                snapshot.motionRows(),
                snapshot.sessionRows(),
                StageWorkbenchLayoutCalculator.LIST_PADDING,
                StageWorkbenchLayoutCalculator.PACK_ROW_HEIGHT,
                StageWorkbenchLayoutCalculator.MOTION_ROW_HEIGHT,
                StageWorkbenchLayoutCalculator.SESSION_ROW_HEIGHT,
                StageWorkbenchLayoutCalculator.ROW_GAP
        );
    }

    List<StagePack.VmdFileInfo> collectMotionFiles(StagePack pack) {
        List<StagePack.VmdFileInfo> result = new ArrayList<>();
        if (pack == null) {
            return result;
        }
        for (StagePack.VmdFileInfo info : pack.getVmdFiles()) {
            if (info.hasBones || info.hasMorphs) {
                result.add(info);
            }
        }
        return result;
    }

    private List<StagePack.VmdFileInfo> collectCameraFiles(StagePack pack) {
        List<StagePack.VmdFileInfo> result = new ArrayList<>();
        if (pack == null) {
            return result;
        }
        for (StagePack.VmdFileInfo info : pack.getVmdFiles()) {
            if (info.hasCamera) {
                result.add(info);
            }
        }
        return result;
    }

    private StagePack getSelectedPack(List<StagePack> stagePacks, int selectedPackIndex) {
        if (selectedPackIndex >= 0 && selectedPackIndex < stagePacks.size()) {
            return stagePacks.get(selectedPackIndex);
        }
        return null;
    }

    private List<StageWorkbenchUiStateSnapshot.PackRow> buildPackRows(List<StagePack> stagePacks, int selectedPackIndex) {
        List<StageWorkbenchUiStateSnapshot.PackRow> rows = new ArrayList<>(stagePacks.size());
        for (int i = 0; i < stagePacks.size(); i++) {
            StagePack pack = stagePacks.get(i);
            rows.add(new StageWorkbenchUiStateSnapshot.PackRow(shorten(pack.getName(), 15), i == selectedPackIndex, i));
        }
        return rows;
    }

    private List<StageWorkbenchUiStateSnapshot.MotionRow> buildMotionRows(StageWorkbenchFacade facade,
                                                                          List<StagePack.VmdFileInfo> motionFiles,
                                                                          String selectedHostMotionFileName) {
        List<StageWorkbenchUiStateSnapshot.MotionRow> rows = new ArrayList<>();
        if (motionFiles.isEmpty()) {
            return rows;
        }
        if (!facade.isSessionMember()) {
            rows.add(new StageWorkbenchUiStateSnapshot.MotionRow(
                    wb("playback.merge_all.short"),
                    "",
                    selectedHostMotionFileName == null,
                    0,
                    null,
                    true
            ));
        }
        for (StagePack.VmdFileInfo motionFile : motionFiles) {
            int index = rows.size();
            rows.add(new StageWorkbenchUiStateSnapshot.MotionRow(
                    buildCompactMotionLabel(motionFile),
                    "",
                    motionFile.name.equals(selectedHostMotionFileName)
                            || (facade.isSessionMember() && facade.isLocalCustomMotionSelected(motionFile.name)),
                    index,
                    motionFile.name,
                    false
            ));
        }
        return rows;
    }

    private List<StageWorkbenchUiStateSnapshot.SessionRow> buildSessionRows(StageWorkbenchFacade facade,
                                                                            List<StageLobbyViewModel.MemberView> memberViews,
                                                                            List<StageLobbyViewModel.HostEntry> hostEntries) {
        List<StageWorkbenchUiStateSnapshot.SessionRow> rows = new ArrayList<>();
        if (facade.isSessionMember()) {
            for (int i = 0; i < memberViews.size(); i++) {
                StageLobbyViewModel.MemberView member = memberViews.get(i);
                String prefix = member.host() ? wb("member_prefix.host") : member.local() ? wb("member_prefix.you") : wb("member_prefix.guest");
                rows.add(new StageWorkbenchUiStateSnapshot.SessionRow(
                        shorten(prefix + member.name(), 14),
                        formatGuestState(member.state(), member.useHostCamera()),
                        "",
                        member.local() || member.host(),
                        false,
                        i,
                        member.uuid()
                ));
            }
            return rows;
        }
        for (int i = 0; i < hostEntries.size(); i++) {
            StageLobbyViewModel.HostEntry entry = hostEntries.get(i);
            String action = hostActionLabel(entry);
            rows.add(new StageWorkbenchUiStateSnapshot.SessionRow(
                    shorten(entry.name(), 13),
                    formatHostState(entry.state(), entry.nearby(), entry.useHostCamera()),
                    action == null ? "" : action,
                    entry.state() == StageMemberState.READY,
                    action != null,
                    i,
                    entry.uuid()
            ));
        }
        return rows;
    }

    private List<String> buildDetailLines(StageWorkbenchFacade facade,
                                          StagePack selectedPack,
                                          List<StagePack.VmdFileInfo> motionFiles,
                                          List<StagePack.VmdFileInfo> cameraFiles,
                                          float cameraHeightOffset) {
        List<String> lines = new ArrayList<>();
        if (selectedPack == null) {
            lines.add(wb("select_pack_hint"));
            lines.add(wb("editor.no_pack"));
            return lines;
        }
        lines.add(wb("active_pack", shorten(selectedPack.getName(), 14)));
        lines.add(wb("summary.compact", motionFiles.size(), cameraFiles.size(), selectedPack.getAudioFiles().size()));
        if (!facade.isSessionMember()) {
            lines.add(wb("camera_height.short") + ": " + formatSigned(cameraHeightOffset));
        } else if (!selectedPack.getAudioFiles().isEmpty()) {
            lines.add(wb("lanes.audio_file.short", shorten(selectedPack.getAudioFiles().get(0).name, 14)));
        } else if (!cameraFiles.isEmpty()) {
            lines.add(wb("lanes.camera_file.short", shorten(cameraFiles.get(0).name, 14)));
        }
        return lines;
    }

    private String buildFooterStatus(StageWorkbenchFacade facade, StagePack selectedPack) {
        if (facade.isSessionMember()) {
            return facade.isLocalReady()
                    ? Component.translatable("gui.mmdskin.stage.ready_done").getString()
                    : Component.translatable("gui.mmdskin.stage.waiting_host").getString();
        }
        return facade.canStartStage(selectedPack)
                ? wb("ready_to_launch")
                : Component.translatable("gui.mmdskin.stage.waiting_ready").getString();
    }

    private int readyMemberCount(List<StageLobbyViewModel.HostEntry> hostEntries) {
        int readyCount = 0;
        for (StageLobbyViewModel.HostEntry entry : hostEntries) {
            if (entry.state() == StageMemberState.READY) {
                readyCount++;
            }
        }
        return readyCount;
    }

    private float normalizeCameraHeight(float value) {
        return Mth.clamp((value + 2.0f) / 4.0f, 0.0f, 1.0f);
    }

    private static String hostActionLabel(StageLobbyViewModel.HostEntry entry) {
        StageMemberState state = entry.state();
        if ((state == null || state == StageMemberState.DECLINED || state == StageMemberState.BUSY) && entry.nearby()) {
            return wb("host.action.invite");
        }
        if (state == StageMemberState.INVITED) {
            return wb("host.action.cancel_invite");
        }
        return null;
    }

    private static String buildCompactMotionLabel(StagePack.VmdFileInfo motionFile) {
        String base = stripExtension(motionFile.name);
        List<String> tags = new ArrayList<>();
        if (motionFile.hasBones) {
            tags.add("B");
        }
        if (motionFile.hasMorphs) {
            tags.add("M");
        }
        if (motionFile.hasCamera) {
            tags.add("C");
        }
        return shorten(base, 14) + (tags.isEmpty() ? "" : " [" + String.join("/", tags) + "]");
    }

    private static String formatHostState(StageMemberState state, boolean nearby, boolean useHostCamera) {
        if (state == null) {
            return nearby ? wb("state.nearby") : wb("state.offline");
        }
        return switch (state) {
            case HOST -> wb("state.host");
            case INVITED -> wb("state.invited");
            case ACCEPTED -> useHostCamera ? wb("state.with_host_camera", wb("state.accepted")) : wb("state.accepted");
            case READY -> useHostCamera ? wb("state.with_host_camera", wb("state.ready")) : wb("state.ready");
            case DECLINED -> wb("state.declined");
            case BUSY -> wb("state.busy");
        };
    }

    private static String formatGuestState(StageMemberState state, boolean useHostCamera) {
        if (state == null) {
            return wb("state.unknown");
        }
        return switch (state) {
            case HOST -> wb("state.host");
            case INVITED -> wb("state.invited");
            case ACCEPTED -> useHostCamera ? wb("state.with_host_camera", wb("state.accepted")) : wb("state.accepted");
            case READY -> useHostCamera ? wb("state.with_host_camera", wb("state.ready")) : wb("state.ready");
            case DECLINED -> wb("state.declined");
            case BUSY -> wb("state.busy");
        };
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String shorten(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        if (maxChars <= 3) {
            return text.substring(0, Math.max(0, maxChars));
        }
        return text.substring(0, maxChars - 2) + "..";
    }

    private static String formatSigned(float value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String wb(String suffix, Object... args) {
        return tr("gui.mmdskin.stage.workbench." + suffix, args);
    }
}
