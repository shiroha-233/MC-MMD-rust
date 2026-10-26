package com.shiroha.mmdskin.distrib;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.shiroha.mmdskin.asset.catalog.ModelInfo;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.ui.network.PlayerModelSyncManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件职责：模型分发的客户端实现（接收方）。
 *
 * <p>落地策略：所有文件先写进模型目录<b>同级</b>的临时目录 {@code <名字>.__mmd_partial}，
 * 全部收齐并通过 MD5 校验后，再整体改名成正式目录 —— 避免半截文件被目录扫描扫到。
 *
 * <p><b>临时目录必须是同级的兄弟目录</b>：如果它在目标目录里面，
 * 安装时"先把目标改名备份"会把临时目录一起搬走，后面那步改名必然失败，
 * 还会把玩家原来的模型弄丢。
 *
 * <p>断点续传：本地记录已完成（已校验）的文件，重连时把"第一个没下完的文件路径"
 * 告诉服务器，服务器从那里继续。改用<b>路径</b>而不是序号，宿主的清单变了也不会错位。
 */
public final class ModelDistributionClient {

    private static final Logger logger = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PROGRESS_FILE = "distrib_progress.json";
    private static final int MAX_RETRY = 1;

    /** 平台层注入的请求发送实现。 */
    public interface Requester {
        void request(String modelName, String firstIncompletePath);
    }

    private static volatile Requester requester;
    private static final Map<String, ModelDistributionManifest> OFFERS = new ConcurrentHashMap<>();
    private static final Map<String, Map<Integer, ModelDistributionManifest.OfferPage>> OFFER_PAGES =
            new ConcurrentHashMap<>();
    private static final Map<String, ModelDistributionManifest> SUMMARIES = new ConcurrentHashMap<>();
    private static final Map<String, Progress> PROGRESS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> RETRY = new ConcurrentHashMap<>();

    /** 本地进度：清单 + 已完成文件（路径 -> MD5）。 */
    private static final class Progress {
        ModelDistributionManifest manifest;
        Map<String, String> completed = new LinkedHashMap<>();
    }

    private ModelDistributionClient() {
    }

    public static void setRequester(Requester value) {
        requester = value;
    }

    // ========================================================================
    // 清单
    // ========================================================================

    /** 服务器宣告的可分发模型清单。 */
    public static void onManifest(String json) {
        List<ModelDistributionManifest> list = ModelDistributionManifest.listFromJson(json);
        Map<String, ModelDistributionManifest> fresh = new ConcurrentHashMap<>();
        for (ModelDistributionManifest manifest : list) {
            // 清单来自服务器，模型名必须自己校验 —— 名字会拼进本地路径
            if (manifest == null || !ModelDistributionService.isValidModelName(manifest.modelName)) {
                logger.warn("收到非法模型名，已丢弃: {}", manifest == null ? "null" : manifest.modelName);
                continue;
            }
            fresh.put(manifest.modelName, manifest);
            OFFERS.put(manifest.modelName, manifest);
        }
        // 整份替换：房主撤销分发的模型要从列表里消失
        SUMMARIES.keySet().retainAll(fresh.keySet());
        SUMMARIES.putAll(fresh);
        OFFERS.keySet().retainAll(fresh.keySet());
        logger.info("收到模型分发清单: {} 个模型", fresh.size());
    }

    /**
     * 服务器当前提供、且**本地还没有**的可分发模型（摘要）。
     *
     * <p>本地已有的直接过滤掉：房主本人就在房间里，他电脑上的模型文件就是服务器要分发的那些，
     * 没必要再让他下载一遍；已经装过同款模型的玩家同理。
     */
    public static List<ModelDistributionManifest> availableModels() {
        List<ModelDistributionManifest> result = new ArrayList<>();
        for (ModelDistributionManifest manifest : SUMMARIES.values()) {
            if (!hasLocalModel(manifest.modelName)) {
                result.add(manifest);
            }
        }
        return result;
    }

    /**
     * 服务器当前授权分发的<b>全部</b>模型名（含本地已经有的）。
     *
     * <p>「指定玩家模型」用它当候选列表：只有服务器授权分发的模型，
     * 其他客户端才拿得到（否则别人眼里会静默退回原版皮肤）。
     */
    public static List<String> distributableModelNames() {
        List<String> result = new ArrayList<>();
        for (ModelDistributionManifest manifest : SUMMARIES.values()) {
            if (manifest.modelName != null && !manifest.modelName.isBlank()) {
                result.add(manifest.modelName);
            }
        }
        return result;
    }

    /**
     * 本地是否已经装了这个模型。
     *
     * <p>看的是模型目录<b>顶层有没有模型文件</b>，不是"目录在不在" ——
     * 目录可能只是分发过程中留下的空壳。
     */
    public static boolean hasLocalModel(String modelName) {
        File dir = ModelDistributionService.modelDirOf(modelName);
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            if (!child.isFile()) {
                continue;
            }
            String lower = child.getName().toLowerCase();
            if (lower.endsWith(".pmx") || lower.endsWith(".pmd") || lower.endsWith(".vrm")) {
                return true;
            }
        }
        return false;
    }

    /** 服务器的限速（KB/s），0 表示不限。 */
    public static int serverRateLimitKbPerSec() {
        for (ModelDistributionManifest manifest : SUMMARIES.values()) {
            return manifest.rateLimitKbPerSec;
        }
        return 0;
    }

    /** 断线：清掉上一台服务器的清单，别把别的服的模型留在列表里。 */
    public static void onDisconnect() {
        SUMMARIES.clear();
        OFFERS.clear();
        OFFER_PAGES.clear();
        PROGRESS.clear();
        RETRY.clear();
        AUTO_REQUESTED.clear();
        autoDownloadAccepted = false;
    }

    // ========================================================================
    // 自动下载：只下"周围人"正在用的模型
    // ========================================================================

    /** 已经发起过下载的模型，避免反复请求。 */
    private static final java.util.Set<String> AUTO_REQUESTED = ConcurrentHashMap.newKeySet();

    /** 玩家是否已经确认过自动下载的责任条款。 */
    private static volatile boolean autoDownloadAccepted;

    /**
     * 责任条款提示页的打开方式（由 UI 层注入）。
     *
     * <p>26.3 移植：1.20.1 在这里直接 {@code new DistributionNoticeScreen(...)}，
     * 那会让分发核心依赖 UI 类。26.3 的 UI 要按抽取式 GUI 重写，这里改成回调，
     * 让"分发核心"与"提示页"两个阶段可以独立落地。
     */
    public interface NoticeOpener {
        void openNotice(Screen parent, List<ModelDistributionManifest> models,
                        int rateLimitKbPerSec, boolean autoDownload);
    }

    private static volatile NoticeOpener noticeOpener;

    /** 由 UI 层注入责任条款提示页的打开方式。 */
    public static void setNoticeOpener(NoticeOpener value) {
        noticeOpener = value;
    }

    private static long lastAutoScanMs;

    /** 服务器授权分发的模型里，这个在不在？ */
    public static boolean isDistributable(String modelName) {
        return modelName != null && SUMMARIES.containsKey(modelName);
    }

    /**
     * 自动下载扫描：只看**半径内**的玩家，只下他们正在用的、服务器授权过的、本地还没有的模型。
     *
     * <p>刻意不做"把服务器上所有模型都下下来"——那既没道理也会把带宽吃光。
     *
     * <p>26.3 移植：开关与半径都直接读配置层（{@code ConfigManager}），也就是
     * {@code config.json} 里的 {@code autoDownloadNearbyModels} / {@code autoDownloadRadius}
     * ——这两个配置项在 26.3 已经落地（见 spec 阶段 ⑤-5），玩家在设置界面改完立刻生效，
     * 不用重启游戏。分发核心依旧不认识任何 UI 类：责任条款提示页仍然走
     * {@link NoticeOpener} 注入。
     */
    public static void tickAutoDownload(Minecraft minecraft) {
        // 开关与半径都来自配置层（config.json）：关掉就完全不自动下载。
        if (!ConfigManager.isAutoDownloadNearbyModels()) {
            return;
        }
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        // 注意：本地「指定玩家模型」不会被自动下载覆盖 —— 上面的
        // getPlayerModel() 会先返回本地指定，所以这里自动下载拿到的
        // 也是"我指定的那个模型"，自动下载会跟着指定走。
        long now = System.currentTimeMillis();
        if (now - lastAutoScanMs < 1000L) {
            return;
        }
        lastAutoScanMs = now;

        // 半径 <= 0 时按 1 格处理（与 1.20.1 一致：手改配置文件也不会把扫描关掉）。
        double radius = Math.max(1.0D, ConfigManager.getAutoDownloadRadius());
        double radiusSq = radius * radius;
        for (net.minecraft.world.entity.player.Player player : minecraft.level.players()) {
            if (player == minecraft.player) {
                continue;
            }
            if (player.distanceToSqr(minecraft.player) > radiusSq) {
                continue;
            }
            String modelName = PlayerModelSyncManager.getPlayerModel(
                    player.getUUID(), player.getName().getString(), false);
            if (modelName == null || modelName.isEmpty() || !isDistributable(modelName)) {
                continue;
            }
            if (hasLocalModel(modelName) || AUTO_REQUESTED.contains(modelName)) {
                continue;
            }
            if (!autoDownloadAccepted) {
                // 第一次自动下载之前，先让玩家看一眼责任条款
                NoticeOpener opener = noticeOpener;
                if (opener != null) {
                    opener.openNotice(minecraft.gui.screen(), availableModels(),
                            serverRateLimitKbPerSec(), true);
                }
                return;
            }
            AUTO_REQUESTED.add(modelName);
            logger.info("自动下载附近玩家的模型: {}", modelName);
            requestModel(modelName);
        }
    }

    /** 玩家在提示页点了"我已了解"（自动下载模式）。 */
    public static void acceptAutoDownload() {
        autoDownloadAccepted = true;
    }

    public static boolean isAutoDownloadAccepted() {
        return autoDownloadAccepted;
    }

    // ========================================================================
    // 单模型下载
    // ========================================================================

    /** 玩家点了「下载」。 */
    public static void requestModel(String modelName) {
        if (!ModelDistributionService.isValidModelName(modelName)) {
            return;
        }
        Requester current = requester;
        if (current == null) {
            return;
        }
        loadProgress();
        String firstIncomplete = firstIncompletePath(modelName);
        current.request(modelName, firstIncomplete);
    }

    /**
     * 服务器下发单模型详细清单的**一页**（gzip 压缩）。
     *
     * <p>一份完整清单可能有上万个文件，直接塞一个包会撞代理的插件消息上限，
     * 所以服务端按页下发，这里攒齐了再开始下载。
     */
    public static void onOfferPage(byte[] gzipped) {
        byte[] raw = gunzip(gzipped);
        if (raw == null) {
            return;
        }
        ModelDistributionManifest.OfferPage page =
                ModelDistributionManifest.OfferPage.fromJson(new String(raw, StandardCharsets.UTF_8));
        if (page == null || !ModelDistributionService.isValidModelName(page.modelName)) {
            return;
        }
        Map<Integer, ModelDistributionManifest.OfferPage> pages =
                OFFER_PAGES.computeIfAbsent(page.modelName, k -> new java.util.concurrent.ConcurrentHashMap<>());
        pages.put(page.pageIndex, page);
        if (pages.size() < Math.max(1, page.pageCount)) {
            return;
        }
        // 攒齐了，拼成完整清单
        ModelDistributionManifest manifest = new ModelDistributionManifest();
        manifest.modelName = page.modelName;
        manifest.totalSize = page.totalSize;
        manifest.ossUrl = page.ossUrl;
        manifest.ossVerified = page.ossVerified;
        for (int i = 0; i < page.pageCount; i++) {
            ModelDistributionManifest.OfferPage p = pages.get(i);
            if (p == null) {
                logger.warn("分发清单缺页，放弃该模型: {} (缺 {})", page.modelName, i);
                OFFER_PAGES.remove(page.modelName);
                return;
            }
            manifest.files.addAll(p.entries);
        }
        OFFER_PAGES.remove(page.modelName);
        onOffer(manifest);
    }

    /** 解 gzip；失败就当没收到。 */
    private static byte[] gunzip(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(data);
             java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(in)) {
            return gz.readAllBytes();
        } catch (Exception e) {
            logger.warn("解压分发清单失败", e);
            return null;
        }
    }

    /** 服务器下发单模型详细清单（已拼完整）。 */
    private static void onOffer(ModelDistributionManifest manifest) {
        if (manifest == null || !ModelDistributionService.isValidModelName(manifest.modelName)) {
            return;
        }
        // 清单里的每个文件路径都来自服务器，客户端必须自己再校验一遍
        for (ModelDistributionManifest.FileEntry entry : manifest.files) {
            if (entry == null || !ModelDistributionService.isValidRelativePath(entry.path)) {
                logger.warn("清单里含非法文件路径，整个模型已丢弃: {}", manifest.modelName);
                return;
            }
        }
        OFFERS.put(manifest.modelName, manifest);
        if (manifest.ossVerified && !manifest.ossUrl.isEmpty()) {
            downloadFromOss(manifest);
            return;
        }
        preparePartialDir(manifest);
    }

    /** 收到一块数据。 */
    public static void onChunk(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return;
        }
        try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(payload)) {
            String modelName = ModelDistributionService.readUtf(in);
            int fileIndex = ModelDistributionService.readInt(in);
            int chunkIndex = ModelDistributionService.readInt(in);
            int chunkCount = ModelDistributionService.readInt(in);
            long fileSize = ModelDistributionService.readLong(in);
            long resumeOffset = ModelDistributionService.readLong(in);
            byte[] data = in.readAllBytes();

            ModelDistributionManifest manifest = OFFERS.get(modelName);
            if (manifest == null) {
                return;
            }
            ModelDistributionManifest.FileEntry entry = manifest.fileAt(fileIndex);
            if (entry == null || !ModelDistributionService.isValidRelativePath(entry.path)) {
                return;
            }
            if (resumeOffset < 0 || data.length == 0) {
                return;
            }
            File partial = ModelDistributionService.partialDirOf(modelName);
            if (partial == null) {
                return;
            }
            File target = new File(partial, entry.path);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                logger.warn("创建分发临时目录失败: {}", parent);
                return;
            }
            try (RandomAccessFile raf = new RandomAccessFile(target, "rw")) {
                raf.seek(resumeOffset);
                raf.write(data);
            }
            // 这个文件收齐了就记进进度（断点续传要用）
            if (target.length() == entry.size) {
                markFileCompleted(manifest, entry, target);
            }
        } catch (Exception e) {
            logger.warn("处理模型分块失败", e);
        }
    }

    /** 传输结束：逐文件校验 MD5，通过后整体替换到正式目录。 */
    public static void onComplete(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return;
        }
        ModelDistributionManifest manifest = OFFERS.get(modelName);
        if (manifest == null) {
            return;
        }
        if (manifest.ossVerified && !manifest.ossUrl.isEmpty()) {
            return;
        }
        // 校验（逐文件算 MD5）+ 改名在后台线程做：
        // 主线程算全量 MD5 会把渲染卡住好几秒。
        Thread worker = new Thread(() -> {
            if (!verifyAndInstall(manifest)) {
                // 装不上：可能是断点位置对不上了（宿主换了文件），
                // 清掉进度从零重来一次，别卡在死循环里。
                int retry = RETRY.merge(modelName, 1, Integer::sum);
                if (retry <= MAX_RETRY) {
                    logger.warn("模型安装失败，清进度后重下一次: {}", modelName);
                    PROGRESS.remove(modelName);
                    saveProgress();
                    requestModel(modelName);
                }
                return;
            }
            OFFERS.remove(modelName);
            PROGRESS.remove(modelName);
            RETRY.remove(modelName);
            saveProgress();
            ModelInfo.invalidateCache();
            logger.info("模型分发完成，已安装: {}", modelName);
        }, "MMD-ModelDistributor-Install");
        worker.setDaemon(true);
        worker.start();
    }

    // ========================================================================
    // 校验与安装
    // ========================================================================

    /**
     * 校验所有文件并把临时目录改名成正式目录。
     *
     * <p>顺序很重要：先把旧目录改名备份，再把临时目录改名成正式目录；
     * 中间任何一步失败都要把备份改回去，绝不能让玩家原来的模型消失。
     */
    public static boolean verifyAndInstall(ModelDistributionManifest manifest) {
        File partial = ModelDistributionService.partialDirOf(manifest.modelName);
        if (partial == null || !partial.isDirectory()) {
            logger.warn("分发临时目录不存在，放弃安装: {}", manifest.modelName);
            return false;
        }
        for (ModelDistributionManifest.FileEntry entry : manifest.files) {
            if (entry == null || !ModelDistributionService.isValidRelativePath(entry.path)) {
                logger.warn("清单含非法路径，放弃安装: {}", manifest.modelName);
                return false;
            }
            File file = new File(partial, entry.path);
            if (!file.isFile() || file.length() != entry.size) {
                logger.warn("分发文件不完整，放弃安装: {}/{}", manifest.modelName, entry.path);
                return false;
            }
            String md5 = ModelDistributionService.md5Of(file);
            if (md5 == null || !md5.equalsIgnoreCase(entry.md5)) {
                logger.warn("分发文件 MD5 校验失败，放弃安装: {}/{}", manifest.modelName, entry.path);
                return false;
            }
        }

        File target = ModelDistributionService.modelDirOf(manifest.modelName);
        if (target == null) {
            return false;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            logger.warn("创建模型目录失败: {}", parent);
            return false;
        }
        // 备份名以 "." 开头，永远不会和合法模型名撞上
        File backup = new File(parent, "." + manifest.modelName + ".__mmd_old");
        deleteRecursively(backup);
        boolean hadOld = target.exists();
        if (hadOld && !target.renameTo(backup)) {
            deleteRecursively(target);
            hadOld = false;
        }
        if (!partial.renameTo(target)) {
            logger.warn("替换模型目录失败: {}", manifest.modelName);
            // 把玩家原来的模型还回去
            if (hadOld && backup.exists()) {
                backup.renameTo(target);
            }
            return false;
        }
        deleteRecursively(backup);
        return true;
    }

    // ========================================================================
    // OSS 直链
    // ========================================================================

    /** 走 OSS 直链：服务器只提供清单与校验值，字节由客户端自取，服务器出口为 0。 */
    private static void downloadFromOss(ModelDistributionManifest manifest) {
        Thread thread = new Thread(() -> {
            try {
                File partial = preparePartialDir(manifest);
                if (partial == null) {
                    return;
                }
                for (ModelDistributionManifest.FileEntry entry : manifest.files) {
                    File target = new File(partial, entry.path);
                    File parent = target.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        return;
                    }
                    URL url = new URL(joinUrl(manifest.ossUrl, entry.path));
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                    connection.setInstanceFollowRedirects(true);
                    long written = 0L;
                    try (InputStream in = connection.getInputStream();
                         OutputStream out = new FileOutputStream(target)) {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = in.read(buffer)) > 0) {
                            written += read;
                            if (written > ModelDistributionService.MAX_FILE_SIZE) {
                                logger.warn("OSS 直链文件超过上限，已中止: {}/{}", manifest.modelName, entry.path);
                                return;
                            }
                            out.write(buffer, 0, read);
                        }
                    } finally {
                        connection.disconnect();
                    }
                    String md5 = ModelDistributionService.md5Of(target);
                    if (md5 == null || !md5.equalsIgnoreCase(entry.md5)) {
                        logger.warn("OSS 直链下载的文件 MD5 不匹配: {}/{}", manifest.modelName, entry.path);
                        fallbackToRelay(manifest.modelName);
                        return;
                    }
                }
                if (!verifyAndInstall(manifest)) {
                    fallbackToRelay(manifest.modelName);
                    return;
                }
                OFFERS.remove(manifest.modelName);
                PROGRESS.remove(manifest.modelName);
                saveProgress();
                ModelInfo.invalidateCache();
                logger.info("OSS 直链模型分发完成，已安装: {}", manifest.modelName);
            } catch (Exception e) {
                logger.warn("OSS 直链下载失败: {}", manifest.modelName, e);
                fallbackToRelay(manifest.modelName);
            }
        }, "MMD-ModelDistributor");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * OSS 直链走不通时的兜底：改回服务器转发。
     *
     * <p>服务器那边只要 ossVerified 就不会再补发块，所以必须由客户端主动再请求一次。
     */
    private static void fallbackToRelay(String modelName) {
        if (!ModelDistributionService.isValidModelName(modelName)) {
            return;
        }
        Requester current = requester;
        if (current == null) {
            return;
        }
        logger.warn("改用服务器转发补发模型: {}", modelName);
        current.request(modelName, "");
    }

    // ========================================================================
    // 进度
    // ========================================================================

    /** 第一个还没下完的文件路径（空串 = 从头开始）。 */
    private static String firstIncompletePath(String modelName) {
        Progress progress = PROGRESS.get(modelName);
        if (progress == null || progress.manifest == null) {
            return "";
        }
        for (ModelDistributionManifest.FileEntry entry : progress.manifest.files) {
            if (entry == null) {
                continue;
            }
            String done = progress.completed.get(entry.path);
            if (done == null || !done.equalsIgnoreCase(entry.md5)) {
                return entry.path;
            }
        }
        return "";
    }

    /** 某个文件收齐并校验通过后记进进度。 */
    private static void markFileCompleted(ModelDistributionManifest manifest, ModelDistributionManifest.FileEntry entry, File file) {
        String md5 = ModelDistributionService.md5Of(file);
        if (md5 == null || !md5.equalsIgnoreCase(entry.md5)) {
            return;
        }
        Progress progress = PROGRESS.get(manifest.modelName);
        if (progress == null) {
            progress = new Progress();
            progress.manifest = manifest;
            PROGRESS.put(manifest.modelName, progress);
        }
        progress.completed.put(entry.path, entry.md5);
        saveProgress();
    }

    private static File progressFile() {
        return new File(PathConstants.getConfigRootDir(), PROGRESS_FILE);
    }

    private static void loadProgress() {
        if (!PROGRESS.isEmpty()) {
            return;
        }
        File file = progressFile();
        if (!file.exists()) {
            return;
        }
        try (java.io.Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Map<String, Progress> loaded = GSON.fromJson(reader, new TypeToken<LinkedHashMap<String, Progress>>() {
            }.getType());
            if (loaded != null) {
                PROGRESS.putAll(loaded);
            }
        } catch (Exception e) {
            logger.debug("读取分发进度失败: {}", e.getMessage());
        }
    }

    private static void saveProgress() {
        try {
            File file = progressFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return;
            }
            try (java.io.Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(new LinkedHashMap<>(PROGRESS), writer);
            }
        } catch (Exception e) {
            logger.debug("保存分发进度失败: {}", e.getMessage());
        }
    }

    // ========================================================================
    // 目录工具
    // ========================================================================

    /** 客户端侧的模型目录（与服务器侧同构）。 */
    public static File modelDirOf(String modelName) {
        return ModelDistributionService.modelDirOf(modelName);
    }

    /** 客户端侧的临时目录（目标目录的同级兄弟）。 */
    public static File partialDir(String modelName) {
        return ModelDistributionService.partialDirOf(modelName);
    }

    private static File preparePartialDir(ModelDistributionManifest manifest) {
        File partial = partialDir(manifest.modelName);
        if (partial == null) {
            return null;
        }
        if (!partial.exists() && !partial.mkdirs()) {
            logger.warn("创建分发临时目录失败: {}", partial);
            return null;
        }
        Progress progress = new Progress();
        progress.manifest = manifest;
        for (ModelDistributionManifest.FileEntry entry : manifest.files) {
            if (entry == null || !ModelDistributionService.isValidRelativePath(entry.path)) {
                continue;
            }
            File file = new File(partial, entry.path);
            if (file.isFile() && file.length() == entry.size) {
                String md5 = ModelDistributionService.md5Of(file);
                if (md5 != null && md5.equalsIgnoreCase(entry.md5)) {
                    progress.completed.put(entry.path, entry.md5);
                } else {
                    // 半截或已损坏：删掉，等服务器重发
                    file.delete();
                }
            }
        }
        PROGRESS.put(manifest.modelName, progress);
        saveProgress();
        return partial;
    }

    public static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    private static String joinUrl(String base, String relative) {
        String trimmed = base.trim();
        if (!trimmed.endsWith("/")) {
            trimmed = trimmed + "/";
        }
        StringBuilder builder = new StringBuilder(trimmed);
        String[] parts = relative.split("/");
        for (String part : parts) {
            builder.append(java.net.URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"));
            builder.append('/');
        }
        builder.setLength(builder.length() - 1);
        return builder.toString();
    }
}
