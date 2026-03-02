package com.shiroha.mmdskin.renderer.animation;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.renderer.core.IMMDModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FbxDefaultAnimPack {
    private static final Logger logger = LogManager.getLogger();
    private static final String FBX_FILENAME = "UAL1.fbx";
    private static final String PREFIX = "Armature|";

    public enum MoveDir {
        FORWARD, BACKWARD, LEFT, RIGHT,
        FORWARD_LEFT, FORWARD_RIGHT, BACKWARD_LEFT, BACKWARD_RIGHT
    }

    public static class AnimationGroup {
        public final String groupId;
        public final String enter;
        public final String loop;
        public final String exit;
        private final Map<MoveDir, String> dirLoops;

        private AnimationGroup(String groupId, String enter, String loop, String exit) {
            this.groupId = groupId;
            this.enter = enter;
            this.loop = loop;
            this.exit = exit;
            this.dirLoops = new EnumMap<>(MoveDir.class);
        }

        private AnimationGroup dir(MoveDir d, String stack) {
            dirLoops.put(d, stack);
            return this;
        }

        /** 根据移动方向获取 loop 动画（无方向变体时回退到主 loop） */
        public String getLoopForDir(MoveDir dir) {
            if (dir == null) return loop;
            return dirLoops.getOrDefault(dir, loop);
        }

        public boolean hasEnter() { return enter != null; }
        public boolean hasExit()  { return exit != null; }
        public boolean hasDirectional() { return !dirLoops.isEmpty(); }
    }

    private static AnimationGroup group(String groupId, String enter, String loop, String exit) {
        return new AnimationGroup(groupId,
                enter != null ? PREFIX + enter : null,
                PREFIX + loop,
                exit != null ? PREFIX + exit : null);
    }

    private static AnimationGroup group(String groupId, String loop) {
        return group(groupId, null, loop, null);
    }

    private static final Map<String, AnimationGroup> SLOT_GROUPS;

    static {
        Map<String, AnimationGroup> m = new LinkedHashMap<>();

        m.put("walk", group("walk", "Walk_Loop"));
        m.put("sprint", group("sprint", "Sprint_Loop"));
        m.put("die", group("die", "Death01"));
        m.put("die2", group("die", "Death02"));
        m.put("ride", group("sit", "Sitting_Enter", "Sitting_Idle_Loop", "Sitting_Exit"));
        m.put("onHorse", group("sit", "Sitting_Enter", "Sitting_Idle_Loop", "Sitting_Exit"));
        m.put("swim", group("swim", "Swim_Fwd_Loop"));
        m.put("crawl", group("crawl", "Crawl_Enter", "Crawl_Fwd_Loop", "Crawl_Exit")
                .dir(MoveDir.FORWARD, PREFIX + "Crawl_Fwd_Loop")
                .dir(MoveDir.BACKWARD, PREFIX + "Crawl_Bwd_Loop")
                .dir(MoveDir.LEFT, PREFIX + "Crawl_Left_Loop")
                .dir(MoveDir.RIGHT, PREFIX + "Crawl_Right_Loop"));
        m.put("lieDown", group("crawl", "Crawl_Enter", "Crawl_Idle_Loop", "Crawl_Exit"));
        m.put("sneak", group("crouch", "Crouch_Enter", "Crouch_Idle_Loop", "Crouch_Exit"));
        m.put("drink", group("drink", "Drink"));
        m.put("hit1", group("hit", "Hit_Chest"));
        m.put("hit2", group("hit", "Hit_Head"));
        m.put("hit3", group("hit", "Hit_Shoulder_L"));
        m.put("hit4", group("hit", "Hit_Shoulder_R"));
        m.put("hit5", group("hit", "Hit_Stomach"));

        SLOT_GROUPS = Collections.unmodifiableMap(m);
    }

    private static volatile String fbxFilePath;
    private static volatile boolean preloaded;

    private static final Map<Long, Map<String, Long>> animCache = new ConcurrentHashMap<>();
    private static final Set<String> failedLoads = ConcurrentHashMap.newKeySet();

    private FbxDefaultAnimPack() {}

    public static void init() {
        File fbxFile = new File(PathConstants.getDefaultAnimDir(), FBX_FILENAME);
        if (!fbxFile.exists()) {
            logger.warn("FBX 默认动画包不存在: {}", fbxFile.getAbsolutePath());
            return;
        }
        fbxFilePath = fbxFile.getAbsolutePath();

        Thread t = new Thread(() -> {
            try {
                NativeFunc nf = NativeFunc.GetInst();
                preloaded = nf.PreloadFbxFile(fbxFilePath);
                if (preloaded) {
                    logger.info("FBX 默认动画包预加载完成: {}", FBX_FILENAME);
                }
            } catch (Exception e) {
                logger.error("FBX 预加载异常", e);
            }
        }, "FBX-Preload");
        t.setDaemon(true);
        t.start();
    }

    public static AnimationGroup getGroup(String slotName) {
        return SLOT_GROUPS.get(slotName);
    }

    public static String getGroupId(String slotName) {
        AnimationGroup g = SLOT_GROUPS.get(slotName);
        return g != null ? g.groupId : null;
    }

    public static boolean isAvailable() {
        return fbxFilePath != null;
    }

    public static boolean hasMapping(String slotName) {
        return SLOT_GROUPS.containsKey(slotName);
    }

    public static long loadAnimation(IMMDModel model, String slotName) {
        AnimationGroup g = SLOT_GROUPS.get(slotName);
        if (g == null) return 0;
        return loadStack(model, g.loop);
    }

    public static long loadStack(IMMDModel model, String stackName) {
        if (fbxFilePath == null || stackName == null) return 0;

        long modelHandle = model.getModelHandle();

        Map<String, Long> sub = animCache.computeIfAbsent(modelHandle, k -> new ConcurrentHashMap<>());
        Long cached = sub.get(stackName);
        if (cached != null) return cached;

        String failKey = stackName + "@" + modelHandle;
        if (failedLoads.contains(failKey)) return 0;

        String fullPath = fbxFilePath + "#" + stackName;
        NativeFunc nf = NativeFunc.GetInst();
        long handle = nf.LoadAnimation(modelHandle, fullPath);

        if (handle != 0) {
            sub.put(stackName, handle);
        } else {
            failedLoads.add(failKey);
        }
        return handle;
    }

    public static void onModelRemoved(IMMDModel model) {
        long h = model.getModelHandle();
        Map<String, Long> sub = animCache.remove(h);
        if (sub != null) {
            NativeFunc nf = NativeFunc.GetInst();
            for (Long handle : sub.values()) {
                if (handle != null && handle != 0) {
                    nf.DeleteAnimation(handle);
                }
            }
        }
        failedLoads.removeIf(key -> key.endsWith("@" + h));
    }

    public static String[] listAllStacks() {
        if (fbxFilePath == null) return new String[0];
        try {
            NativeFunc nf = NativeFunc.GetInst();
            String json = nf.ListFbxStacks(fbxFilePath);
            if (json == null) return new String[0];
            json = json.trim();
            if (json.startsWith("[") && json.endsWith("]")) {
                json = json.substring(1, json.length() - 1);
            }
            if (json.isEmpty()) return new String[0];
            String[] parts = json.split(",");
            String[] result = new String[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = parts[i].trim().replace("\"", "");
            }
            return result;
        } catch (Exception e) {
            logger.error("列出 FBX Stack 失败", e);
            return new String[0];
        }
    }

    public static String getFbxFilePath() {
        return fbxFilePath;
    }
}
