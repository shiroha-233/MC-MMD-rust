package com.shiroha.mmdskin.ui.stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多人舞台动作分配数据模型，管理房主为每个成员分配的VMD动作文件
 */
public class StageMotionAssignment {

    private static final StageMotionAssignment INSTANCE = new StageMotionAssignment();

    private final ConcurrentHashMap<UUID, List<String>> assignments = new ConcurrentHashMap<>();

    private StageMotionAssignment() {}

    public static StageMotionAssignment getInstance() {
        return INSTANCE;
    }

    public void assign(UUID playerUUID, List<String> vmdFileNames) {
        assignments.put(playerUUID, new ArrayList<>(vmdFileNames));
    }

    public void unassign(UUID playerUUID) {
        assignments.remove(playerUUID);
    }

    public List<String> getAssignment(UUID playerUUID) {
        List<String> list = assignments.get(playerUUID);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    public boolean hasAssignment(UUID playerUUID) {
        return assignments.containsKey(playerUUID);
    }

    public Set<UUID> getAssignedMembers() {
        return Collections.unmodifiableSet(assignments.keySet());
    }

    public String buildStageData(String packName, UUID playerUUID) {
        List<String> vmdFiles = assignments.get(playerUUID);
        if (vmdFiles == null || vmdFiles.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(packName);
        for (String vmd : vmdFiles) {
            sb.append('|').append(vmd);
        }
        return sb.toString();
    }

    public void reset() {
        assignments.clear();
    }

    public void assignSingle(UUID playerUUID, String vmdFileName) {
        assignments.computeIfAbsent(playerUUID, k -> new ArrayList<>()).add(vmdFileName);
    }

    public void removeSingleVmd(UUID playerUUID, String vmdFileName) {
        assignments.computeIfPresent(playerUUID, (k, list) -> {
            list.remove(vmdFileName);
            return list.isEmpty() ? null : list;
        });
    }
}
