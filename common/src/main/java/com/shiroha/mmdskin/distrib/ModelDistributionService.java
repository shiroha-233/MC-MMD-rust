package com.shiroha.mmdskin.distrib;

import com.shiroha.mmdskin.config.PathConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件职责：模型分发的服务端实现（房主侧）。
 *
 * <p>职责边界：
 * <ul>
 *   <li>扫描服务器本地模型目录，生成带 MD5 的清单；</li>
 *   <li>按房主配置的速率把文件分块推给客户端（令牌桶限速，单客户端 + 全局双配额）；</li>
 *   <li>校验 OSS 直链的内容是否与本地副本一致（阿里云 OSS 的 ETag 即单次上传文件的 MD5）。</li>
 * </ul>
 *
 * <p>本类只在服务端使用；实际的网络发送由平台层注入 {@link Sender}，服务端 tick 里调用 {@link #tickServer()}。
 *
 * <p><b>内存约束</b>：分块是"用到才读"的，不预读整个模型 ——
 * 否则一个 500 MB 的模型会在服务端堆里占 500 MB，每个客户端一份。
 */
public final class ModelDistributionService {

    private static final Logger logger = LogManager.getLogger();

    /**
     * 单块大小 16 KB。
     *
     * <p>分块帧本身还有模型名等字段，实际载荷会比 CHUNK_SIZE 大几十字节；
     * 很多代理的插件消息上限是 32767 字节，用 16 KB 留足余量。
     */
    public static final int CHUNK_SIZE = 16 * 1024;

    /** 单个模型允许的最大总大小（字节），防止把超大的目录推给客户端。 */
    public static final long MAX_MODEL_TOTAL_SIZE = 512L * 1024 * 1024;

    /** 单个文件允许的最大大小（字节）。 */
    public static final long MAX_FILE_SIZE = 256L * 1024 * 1024;

    /** 客户端临时目录名（安装目标的同级兄弟目录，不是子目录）。 */
    public static final String PARTIAL_DIR_SUFFIX = ".__mmd_partial";

    /** 单个模型允许分发的最大文件数：再多就说明目录不正常，直接拒绝分发。 */
    public static final int MAX_FILES_PER_MODEL = 4096;

    /**
     * 一页清单的字节上限（压缩前）。
     *
     * <p>代理的插件消息上限常见是 32767 字节（BungeeCord 默认值），留一半余量。
     */
    public static final int MAX_OFFER_PAGE_BYTES = 12 * 1024;

    private static final Map<String, ModelDistributionManifest> MANIFEST_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> MANIFEST_STAMP = new ConcurrentHashMap<>();
    /** 每个玩家的传输任务队列（一次只能有一个在跑，后面的排队）。 */
    private static final Map<UUID, Deque<PlayerQueue>> QUEUES = new ConcurrentHashMap<>();
    private static final Map<UUID, TokenBucket> BUCKETS = new ConcurrentHashMap<>();

    private static volatile Sender sender;
    private static volatile TokenBucket globalBucket = new TokenBucket(Long.MAX_VALUE);
    private static final java.util.concurrent.atomic.AtomicBoolean WARMING = new java.util.concurrent.atomic.AtomicBoolean(false);

    private ModelDistributionService() {
    }

    /**
     * 平台层注入的网络发送实现。
     *
     * <p>handle 是平台侧的玩家对象（Fabric/Forge 的 ServerPlayer），
     * 但 common 模块不引用任何服务端专属的 MC 类 —— 只当不透明的 Object 传递。
     */
    public interface Sender {
        void sendManifest(Object handle, byte[] payload);

        void sendOffer(Object handle, byte[] payload);

        void sendChunk(Object handle, byte[] payload);

        void sendComplete(Object handle, String modelName);
    }

    /**
     * 一个进行中的传输任务。
     *
     * <p>只记"位置"（第几个文件、第几块、文件内偏移），数据在发送时才从磁盘读 ——
     * 这样无论模型多大，服务端内存里最多只有一个玩家一个 32 KB 块。
     */
    private static final class PlayerQueue {
        final Object handle;
        final String modelName;
        final ModelDistributionManifest manifest;
        int fileIndex;
        int chunkIndex;
        long fileOffset;

        PlayerQueue(Object handle, String modelName, ModelDistributionManifest manifest) {
            this.handle = handle;
            this.modelName = modelName;
            this.manifest = manifest;
        }

        boolean hasNext() {
            return fileIndex < manifest.files.size();
        }
    }

    public static void setSender(Sender value) {
        sender = value;
    }

    public static Sender sender() {
        return sender;
    }

    // ========================================================================
    // 配置
    // ========================================================================

    public static ModelDistributionConfig config() {
        return ModelDistributionConfig.get();
    }

    public static boolean isEnabled() {
        return config().isActive();
    }

    // ========================================================================
    // 扫描与清单
    // ========================================================================

    /**
     * 生成可分发模型的摘要清单（只含名字/大小/文件数，详情在 OFFER 里给）。
     *
     * <p><b>这里绝对不能算 MD5</b>：这个方法跑在服务端主线程（玩家进服时触发），
     * 一个 5 GB 的模型算全量 MD5 要几十秒到几分钟 —— 任何人连进来就能把服务器冻住。
     * MD5 挪到后台线程预热，只有玩家真的点了下载才需要。
     */
    public static byte[] buildManifestList() {
        // 顺手把模板写出来，房主知道去哪儿改
        config().ensureTemplate();
        List<ModelDistributionManifest> list = new ArrayList<>();
        for (String name : distributableModels()) {
            ModelDistributionManifest summary = buildSummary(name);
            if (summary == null) {
                continue;
            }
            list.add(summary);
        }
        scheduleWarmUp();
        return ModelDistributionManifest.listToJson(list).getBytes(StandardCharsets.UTF_8);
    }

    /** 摘要：只要文件列表和大小，不碰 MD5（快，主线程安全）。 */
    public static ModelDistributionManifest buildSummary(String modelName) {
        if (!isValidModelName(modelName) || !config().isDistributable(modelName)) {
            return null;
        }
        File modelDir = modelDirOf(modelName);
        if (modelDir == null || !modelDir.isDirectory()) {
            return null;
        }
        List<File> files = listModelFiles(modelDir);
        if (files.isEmpty()) {
            return null;
        }
        long total = 0L;
        int count = 0;
        for (File file : files) {
            String relative = relativePath(modelDir, file);
            if (relative == null || !isValidRelativePath(relative)) {
                continue;
            }
            total += file.length();
            count++;
        }
        if (count == 0) {
            return null;
        }
        ModelDistributionManifest summary = new ModelDistributionManifest();
        summary.modelName = modelName;
        summary.totalSize = total;
        summary.fileCount = count;
        summary.rateLimitKbPerSec = config().rateLimitKbPerSec;
        return summary;
    }

    /** 后台预热：把可分发模型的完整清单（MD5 + OSS 校验）算好缓存起来。 */
    public static void scheduleWarmUp() {
        if (WARMING.getAndSet(true)) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                for (String name : distributableModels()) {
                    try {
                        buildManifest(name);
                    } catch (Exception e) {
                        logger.warn("预热分发清单失败: {}", name, e);
                    }
                }
            } finally {
                WARMING.set(false);
            }
        }, "MMD-ModelDistributor-WarmUp");
        thread.setDaemon(true);
        thread.start();
    }

    /** 服务器上被标记为可分发的模型名列表。 */
    public static List<String> distributableModels() {
        List<String> result = new ArrayList<>();
        if (!isEnabled()) {
            return result;
        }
        File root = modelRootDir();
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) {
            return result;
        }
        for (File dir : dirs) {
            String name = dir.getName();
            if (name.startsWith(".") || name.endsWith(PARTIAL_DIR_SUFFIX)) {
                continue;
            }
            if (!config().isDistributable(name)) {
                continue;
            }
            if (buildSummary(name) != null) {
                result.add(name);
            }
        }
        result.sort(String::compareToIgnoreCase);
        return result;
    }

    /** 生成（或取缓存）某个模型的清单。 */
    public static ModelDistributionManifest buildManifest(String modelName) {
        if (!isValidModelName(modelName) || !config().isDistributable(modelName)) {
            return null;
        }
        File modelDir = modelDirOf(modelName);
        if (modelDir == null || !modelDir.isDirectory()) {
            return null;
        }

        List<File> files = listModelFiles(modelDir);
        if (files.isEmpty()) {
            return null;
        }
        // 缓存键用"文件内容指纹"而不是目录 mtime：
        // 改目录里的某个文件不会改目录 mtime，用目录 mtime 会让 md5/size 缓存过期，
        // 客户端拿旧 md5 去校验新内容 → 永远校验不过。
        long stamp = 0L;
        long totalSize = 0L;
        for (File file : files) {
            stamp = stamp * 31L + file.lastModified();
            stamp = stamp * 31L + file.length();
            totalSize += file.length();
        }
        Long cachedStamp = MANIFEST_STAMP.get(modelName);
        ModelDistributionManifest cached = MANIFEST_CACHE.get(modelName);
        if (cached != null && cachedStamp != null && cachedStamp == stamp) {
            return cached;
        }
        if (totalSize > MAX_MODEL_TOTAL_SIZE) {
            logger.warn("模型超过单模型体积上限（{} MB），不参与分发: {}", MAX_MODEL_TOTAL_SIZE / 1024 / 1024, modelName);
            return null;
        }

        if (files.size() > MAX_FILES_PER_MODEL) {
            logger.warn("模型文件数超过上限（{} 个），不参与分发: {}", MAX_FILES_PER_MODEL, modelName);
            return null;
        }
        List<ModelDistributionManifest.FileEntry> entries = new ArrayList<>();
        long total = 0L;
        for (File file : files) {
            String relative = relativePath(modelDir, file);
            if (relative == null || !isValidRelativePath(relative)) {
                continue;
            }
            if (file.length() > MAX_FILE_SIZE) {
                logger.warn("模型内单文件超过上限（{} MB），跳过: {}/{}", MAX_FILE_SIZE / 1024 / 1024, modelName, relative);
                continue;
            }
            ModelDistributionManifest.FileEntry entry = new ModelDistributionManifest.FileEntry();
            entry.path = relative;
            entry.size = file.length();
            entry.md5 = md5Of(file);
            if (entry.md5 == null) {
                continue;
            }
            entries.add(entry);
            total += entry.size;
        }
        if (entries.isEmpty()) {
            return null;
        }

        ModelDistributionManifest manifest = new ModelDistributionManifest();
        manifest.modelName = modelName;
        manifest.files = entries;
        manifest.totalSize = total;
        String ossUrl = config().ossUrlFor(modelName);
        if (!ossUrl.isEmpty() && verifyOss(modelName, ossUrl)) {
            manifest.ossUrl = ossUrl;
            manifest.ossVerified = true;
        }
        MANIFEST_CACHE.put(modelName, manifest);
        MANIFEST_STAMP.put(modelName, stamp);
        return manifest;
    }

    /** 读取某个文件的一块数据。 */
    public static byte[] readChunk(ModelDistributionManifest manifest, int fileIndex, long offset, int length) {
        if (manifest == null) {
            return null;
        }
        ModelDistributionManifest.FileEntry entry = manifest.fileAt(fileIndex);
        if (entry == null) {
            return null;
        }
        File file = new File(modelDirOf(manifest.modelName), entry.path);
        if (!file.isFile()) {
            return null;
        }
        if (offset < 0 || offset > file.length()) {
            return null;
        }
        int want = (int) Math.min(Math.min(length, CHUNK_SIZE), file.length() - offset);
        if (want <= 0) {
            return new byte[0];
        }
        byte[] buffer = new byte[want];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset);
            raf.readFully(buffer);
        } catch (IOException e) {
            logger.warn("读取模型分块失败: {}/{}", manifest.modelName, entry.path, e);
            return null;
        }
        return buffer;
    }

    // ========================================================================
    // 限速发送
    // ========================================================================

    /**
     * 客户端请求某个模型：把该模型剩余的部分排进发送队列（按需读盘，不预读）。
     *
     * @param firstIncompletePath 客户端第一个没下完的文件路径（空 = 从头开始）。
     *                            用路径而不是序号：宿主的清单变了（增删改文件）也不会错位。
     */
    public static void enqueueModel(Object handle, UUID player, String modelName, String firstIncompletePath) {
        if (!isEnabled() || player == null || handle == null) {
            return;
        }
        ModelDistributionManifest manifest = buildManifest(modelName);
        if (manifest == null) {
            return;
        }
        Sender current = sender;
        if (current == null) {
            return;
        }
        // 清单分页下发（每页都压缩），避免一个包塞不下上万个文件
        for (ModelDistributionManifest.OfferPage page : buildOfferPages(manifest)) {
            byte[] payload = gzip(page.toJson().getBytes(StandardCharsets.UTF_8));
            if (payload != null) {
                current.sendOffer(handle, payload);
            }
        }
        if (manifest.ossVerified) {
            // 走 OSS 直链：服务器只发清单与校验值，字节由客户端自取，服务器出口为 0。
            current.sendComplete(handle, modelName);
            return;
        }
        int start = 0;
        if (firstIncompletePath != null && !firstIncompletePath.isEmpty()) {
            for (int i = 0; i < manifest.files.size(); i++) {
                ModelDistributionManifest.FileEntry entry = manifest.files.get(i);
                if (entry != null && entry.path.equals(firstIncompletePath)) {
                    start = i;
                    break;
                }
            }
        }
        if (start >= manifest.files.size()) {
            // 客户端那边全都下好了，服务器没东西要发，直接告诉他"完了"
            current.sendComplete(handle, modelName);
            return;
        }
        PlayerQueue queue = new PlayerQueue(handle, modelName, manifest);
        queue.fileIndex = start;
        queue.fileOffset = 0;
        Deque<PlayerQueue> pending = QUEUES.computeIfAbsent(player, k -> new ArrayDeque<>());
        synchronized (pending) {
            pending.addLast(queue);
        }
    }

    /** 把完整清单拆成若干页，每页不超过 {@link #MAX_OFFER_PAGE_BYTES}。 */
    public static List<ModelDistributionManifest.OfferPage> buildOfferPages(ModelDistributionManifest manifest) {
        List<ModelDistributionManifest.OfferPage> pages = new ArrayList<>();
        if (manifest == null) {
            return pages;
        }
        ModelDistributionManifest.OfferPage current = newPage(manifest, 0);
        // 逐条累加"估算字节数"，满了就翻页 —— 避免每加一条都重新序列化整页（那是 O(n^2)）
        int currentBytes = PAGE_OVERHEAD_BYTES;
        for (ModelDistributionManifest.FileEntry entry : manifest.files) {
            int entryBytes = estimateEntryBytes(entry);
            if (!current.entries.isEmpty() && currentBytes + entryBytes > MAX_OFFER_PAGE_BYTES) {
                pages.add(current);
                current = newPage(manifest, pages.size());
                currentBytes = PAGE_OVERHEAD_BYTES;
            }
            current.entries.add(entry);
            currentBytes += entryBytes;
            if (current.entries.size() >= MAX_FILES_PER_PAGE) {
                pages.add(current);
                current = newPage(manifest, pages.size());
                currentBytes = PAGE_OVERHEAD_BYTES;
            }
        }
        if (!current.entries.isEmpty() || pages.isEmpty()) {
            pages.add(current);
        }
        // 页数定下来了，回头把 pageCount 填上
        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).pageIndex = i;
            pages.get(i).pageCount = pages.size();
        }
        return pages;
    }

    private static ModelDistributionManifest.OfferPage newPage(ModelDistributionManifest manifest, int index) {
        ModelDistributionManifest.OfferPage page = new ModelDistributionManifest.OfferPage();
        page.modelName = manifest.modelName;
        page.pageIndex = index;
        page.pageCount = 1;
        page.totalSize = manifest.totalSize;
        page.fileCount = manifest.files.size();
        page.ossUrl = manifest.ossUrl;
        page.ossVerified = manifest.ossVerified;
        return page;
    }

    /** 单页最多多少个文件（防止极端情况下页数过多）。 */
    private static final int MAX_FILES_PER_PAGE = 256;

    /** 页信封（modelName/pageIndex/...）的估算字节数。 */
    private static final int PAGE_OVERHEAD_BYTES = 256;

    /**
     * 单个文件条目的估算字节数（保守取大）。
     *
     * <p>路径里可能有中文/特殊字符，JSON 里占的字节比字符数多，所以按 6 字节/字符估。
     */
    private static int estimateEntryBytes(ModelDistributionManifest.FileEntry entry) {
        int pathLength = entry == null || entry.path == null ? 0 : entry.path.length();
        return 80 + pathLength * 6;
    }

    /** gzip 压缩载荷（清单页里路径重复率高，压缩比很好）。 */
    public static byte[] gzip(byte[] raw) {
        if (raw == null) {
            return null;
        }
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(raw.length / 2);
             java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(out)) {
            gz.write(raw);
            gz.finish();
            return out.toByteArray();
        } catch (IOException e) {
            logger.warn("压缩分发清单失败，退回原始数据", e);
            return raw;
        }
    }

    // ========================================================================
    // 配置热更新：房主改了配置文件就实时通知在线玩家
    // ========================================================================

    private static long lastConfigStamp = -1L;
    private static long lastConfigPollMs;

    /**
     * 服务端 tick 调用：检查房主有没有改分发配置。
     *
     * <p>返回 true 表示配置变了，调用方应该把新清单推给所有在线玩家 ——
     * 这样房主中途开启/关闭分发、调整限速，**在线玩家立刻就能看到**，不用重连。
     *
     * <p>用文件时间戳判断，而不是 WatchService：后者在部分系统上不可靠，
     * 而且会多一个线程要管。2 秒一次轮询的开销可以忽略。
     */
    public static boolean pollConfigChange() {
        long now = System.currentTimeMillis();
        if (now - lastConfigPollMs < 2000L) {
            return false;
        }
        lastConfigPollMs = now;
        long stamp = ModelDistributionConfig.fileStamp();
        if (stamp == lastConfigStamp) {
            return false;
        }
        lastConfigStamp = stamp;
        ModelDistributionConfig.reload();
        MANIFEST_CACHE.clear();
        MANIFEST_STAMP.clear();
        logger.info("检测到分发配置变化，已重新载入");
        return true;
    }

    /** 服务端 tick 调用：按令牌桶配额把队列排空。 */
    public static void tickServer() {
        Sender current = sender;
        if (current == null || QUEUES.isEmpty()) {
            return;
        }
        ModelDistributionConfig config = config();
        long now = System.currentTimeMillis();
        long perPlayerRate = config.rateLimitKbPerSec <= 0 ? Long.MAX_VALUE : config.rateLimitKbPerSec * 1024L;
        long globalRate = config.globalRateLimitKbPerSec <= 0 ? Long.MAX_VALUE : config.globalRateLimitKbPerSec * 1024L;

        TokenBucket global = globalBucket;
        global.refill(now, globalRate, globalRate);
        TokenBucket globalTokens = global;

        for (Map.Entry<UUID, Deque<PlayerQueue>> entry : new ArrayList<>(QUEUES.entrySet())) {
            UUID player = entry.getKey();
            Deque<PlayerQueue> pending = entry.getValue();
            PlayerQueue queue;
            synchronized (pending) {
                queue = pending.peek();
            }
            if (queue == null) {
                QUEUES.remove(player);
                BUCKETS.remove(player);
                continue;
            }
            if (!queue.hasNext()) {
                synchronized (pending) {
                    pending.poll();
                }
                QUEUES.remove(player);
                BUCKETS.remove(player);
                current.sendComplete(queue.handle, queue.modelName);
                continue;
            }
            TokenBucket bucket = BUCKETS.computeIfAbsent(player, k -> new TokenBucket(perPlayerRate));
            bucket.refill(now, perPlayerRate, perPlayerRate);

            while (queue.hasNext()) {
                ModelDistributionManifest.FileEntry fileEntry = queue.manifest.fileAt(queue.fileIndex);
                if (fileEntry == null) {
                    queue.fileIndex++;
                    continue;
                }
                long remaining = fileEntry.size - queue.fileOffset;
                if (remaining <= 0) {
                    queue.fileIndex++;
                    queue.fileOffset = 0;
                    continue;
                }
                int want = (int) Math.min(CHUNK_SIZE, remaining);
                // 额度不够就等下一个 tick（不硬发，避免实际速率超过配置值）
                if (bucket.available() < want || globalTokens.available() < want) {
                    break;
                }
                byte[] data = readChunk(queue.manifest, queue.fileIndex, queue.fileOffset, want);
                if (data == null || data.length == 0) {
                    queue.fileIndex++;
                    queue.fileOffset = 0;
                    continue;
                }
                bucket.spend(data.length);
                globalTokens.spend(data.length);
                byte[] payload = encodeChunk(new PendingChunk(queue.modelName, queue.fileIndex, queue.chunkIndex,
                        chunkCountOf(fileEntry.size), fileEntry.size, queue.fileOffset, data));
                if (payload != null) {
                    current.sendChunk(queue.handle, payload);
                }
                queue.fileOffset += data.length;
                queue.chunkIndex++;
                if (queue.fileOffset >= fileEntry.size) {
                    queue.fileIndex++;
                    queue.chunkIndex = 0;
                    queue.fileOffset = 0;
                }
            }

            if (!queue.hasNext()) {
                synchronized (pending) {
                    pending.poll();
                }
                boolean more;
                synchronized (pending) {
                    more = !pending.isEmpty();
                }
                if (!more) {
                    QUEUES.remove(player);
                    BUCKETS.remove(player);
                }
                current.sendComplete(queue.handle, queue.modelName);
            }
        }
    }

    private static int chunkCountOf(long fileSize) {
        if (fileSize <= 0) {
            return 1;
        }
        return (int) Math.max(1, (fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE);
    }

    /** 客户端断开：清掉它的队列，避免残留。 */
    public static void onPlayerLeave(UUID player) {
        if (player != null) {
            QUEUES.remove(player);
            BUCKETS.remove(player);
        }
    }

    // ========================================================================
    // OSS 直链校验
    // ========================================================================

    /**
     * 校验 OSS 直链上的内容是否与服务器本地副本一致。
     *
     * <p>阿里云 OSS 的 ETag 在单次上传（未分片）时就是文件的 MD5，
     * 所以直接拿 ETag 和本地 MD5 比对即可。分片上传的 ETag 不是 MD5，会判为不一致并退回 TCP 转发。
     */
    public static boolean verifyOss(String modelName, String ossUrl) {
        if (modelName == null || ossUrl == null || ossUrl.isEmpty()) {
            return false;
        }
        ModelDistributionManifest manifest = buildManifestWithoutOss(modelName);
        if (manifest == null || manifest.files.isEmpty()) {
            return false;
        }
        ModelDistributionManifest.FileEntry entry = manifest.files.get(0);
        try {
            URL url = new URL(joinUrl(ossUrl, entry.path));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            try {
                int code = connection.getResponseCode();
                if (code < 200 || code >= 400) {
                    logger.warn("OSS 直链探测失败（HTTP {}），该模型退回服务器转发: {}", code, modelName);
                    return false;
                }
                String etag = connection.getHeaderField("ETag");
                if (etag == null || etag.isEmpty()) {
                    logger.warn("OSS 直链没有 ETag，无法校验 MD5，该模型退回服务器转发: {}", modelName);
                    return false;
                }
                String normalized = etag.replace("\"", "").trim().toLowerCase(Locale.ROOT);
                if (!normalized.equals(entry.md5.toLowerCase(Locale.ROOT))) {
                    logger.warn("OSS 直链的 ETag 与服务器本地副本 MD5 不一致，该模型退回服务器转发: {} (etag={} local={})",
                            modelName, normalized, entry.md5);
                    return false;
                }
                return true;
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            logger.warn("校验 OSS 直链失败，该模型退回服务器转发: {} ({})", modelName, e.getMessage());
            return false;
        }
    }

    private static ModelDistributionManifest buildManifestWithoutOss(String modelName) {
        File modelDir = modelDirOf(modelName);
        if (modelDir == null || !modelDir.isDirectory()) {
            return null;
        }
        ModelDistributionManifest manifest = new ModelDistributionManifest();
        manifest.modelName = modelName;
        long total = 0L;
        for (File file : listModelFiles(modelDir)) {
            String relative = relativePath(modelDir, file);
            if (relative == null || !isValidRelativePath(relative)) {
                continue;
            }
            ModelDistributionManifest.FileEntry entry = new ModelDistributionManifest.FileEntry();
            entry.path = relative;
            entry.size = file.length();
            entry.md5 = md5Of(file);
            if (entry.md5 == null) {
                continue;
            }
            manifest.files.add(entry);
            total += entry.size;
        }
        manifest.totalSize = total;
        return manifest.files.isEmpty() ? null : manifest;
    }

    // ========================================================================
    // 内部工具
    // ========================================================================

    /** 排队中的一块数据（编码用）。 */
    public static final class PendingChunk {
        public final String modelName;
        public final int fileIndex;
        public final int chunkIndex;
        public final int chunkCount;
        public final long fileSize;
        public final long resumeOffset;
        public final byte[] data;

        PendingChunk(String modelName, int fileIndex, int chunkIndex, int chunkCount,
                     long fileSize, long resumeOffset, byte[] data) {
            this.modelName = modelName;
            this.fileIndex = fileIndex;
            this.chunkIndex = chunkIndex;
            this.chunkCount = chunkCount;
            this.fileSize = fileSize;
            this.resumeOffset = resumeOffset;
            this.data = data;
        }
    }

    /** 分块帧格式：utf modelName / int fileIndex / int chunkIndex / int chunkCount / long fileSize / long resumeOffset / byte[] data */
    public static byte[] encodeChunk(PendingChunk chunk) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(chunk.data.length + 64);
            writeUtf(out, chunk.modelName);
            writeInt(out, chunk.fileIndex);
            writeInt(out, chunk.chunkIndex);
            writeInt(out, chunk.chunkCount);
            writeLong(out, chunk.fileSize);
            writeLong(out, chunk.resumeOffset);
            out.write(chunk.data);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    public static void writeUtf(java.io.OutputStream out, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        writeInt(out, bytes.length);
        out.write(bytes);
    }

    public static void writeInt(java.io.OutputStream out, int value) throws IOException {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    public static void writeLong(java.io.OutputStream out, long value) throws IOException {
        writeInt(out, (int) (value >>> 32));
        writeInt(out, (int) value);
    }

    public static String readUtf(java.io.InputStream in) throws IOException {
        int len = readInt(in);
        if (len < 0 || len > 1 << 20) {
            throw new IOException("非法字符串长度: " + len);
        }
        byte[] bytes = new byte[len];
        int read = 0;
        while (read < len) {
            int n = in.read(bytes, read, len - read);
            if (n < 0) {
                throw new IOException("字符串数据不完整");
            }
            read += n;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static int readInt(java.io.InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        if ((b0 | b1 | b2 | b3) < 0) {
            throw new IOException("数据不完整");
        }
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    public static long readLong(java.io.InputStream in) throws IOException {
        return ((long) readInt(in) << 32) | (readInt(in) & 0xFFFFFFFFL);
    }

    public static String md5Of(File file) {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        } catch (Exception e) {
            logger.warn("计算 MD5 失败: {}", file.getName(), e);
            return null;
        }
    }

    public static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
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

    /** 模型根目录：&lt;gameDir&gt;/3d-skin/EntityPlayer */
    public static File modelRootDir() {
        return new File(PathConstants.getSkinRootDir(), PathConstants.ENTITY_PLAYER_DIR);
    }

    /** 取模型目录；越界（路径穿越）一律拒绝。 */
    public static File modelDirOf(String modelName) {
        if (!isValidModelName(modelName)) {
            return null;
        }
        File root = modelRootDir();
        File dir = new File(root, modelName);
        return containedIn(root, dir) ? dir : null;
    }

    /**
     * 客户端临时目录：安装目标的<b>同级兄弟目录</b>。
     *
     * <p>必须是同级而不是子目录：安装时要把临时目录 rename 成目标目录，
     * 如果临时目录在目标里面，先把目标改名备份就会把临时目录一起搬走，
     * 后面那步 rename 必然失败（而且会把玩家原来的模型弄丢）。
     */
    public static File partialDirOf(String modelName) {
        if (!isValidModelName(modelName)) {
            return null;
        }
        File root = modelRootDir();
        File dir = new File(root, modelName + PARTIAL_DIR_SUFFIX);
        return containedIn(root, dir) ? dir : null;
    }

    /** 目标是否确实位于 root 之内（防路径穿越 / 符号链接逃逸）。 */
    private static boolean containedIn(File root, File candidate) {
        try {
            String rootPath = root.getCanonicalPath();
            String candidatePath = candidate.getCanonicalPath();
            return candidatePath.startsWith(rootPath + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 模型名合法性：只允许安全的单层目录名。
     *
     * <p>名字可能来自服务器（客户端侧），必须严格校验。
     */
    public static boolean isValidModelName(String modelName) {
        if (modelName == null || modelName.isEmpty() || modelName.length() > 128) {
            return false;
        }
        if (modelName.startsWith(".") || modelName.contains("/") || modelName.contains("\\")) {
            return false;
        }
        for (int i = 0; i < modelName.length(); i++) {
            char c = modelName.charAt(i);
            if (c < 0x20 || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') {
                return false;
            }
        }
        return true;
    }

    /**
     * 相对路径合法性（清单里每个文件的路径都来自服务端，客户端必须自己再校验一遍）。
     *
     * <p>拒绝：绝对路径、盘符、反斜杠、空段、{@code .} 与 {@code ..} 段。
     */
    public static boolean isValidRelativePath(String path) {
        if (path == null || path.isEmpty() || path.length() > 512) {
            return false;
        }
        if (path.indexOf('\\') >= 0 || path.indexOf(':') >= 0) {
            return false;
        }
        if (path.startsWith("/")) {
            return false;
        }
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    /** 列出模型目录下参与分发的文件（跳过临时目录与隐藏文件）。 */
    private static List<File> listModelFiles(File modelDir) {
        List<File> result = new ArrayList<>();
        collectFiles(modelDir, modelDir, result);
        result.sort((a, b) -> a.getAbsolutePath().compareToIgnoreCase(b.getAbsolutePath()));
        return result;
    }

    private static void collectFiles(File root, File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            String name = child.getName();
            if (name.startsWith(".") || name.endsWith(PARTIAL_DIR_SUFFIX)) {
                continue;
            }
            if (child.isDirectory()) {
                collectFiles(root, child, out);
            } else if (child.isFile()) {
                out.add(child);
            }
        }
    }

    private static String relativePath(File root, File file) {
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (!filePath.startsWith(rootPath)) {
            return null;
        }
        String relative = filePath.substring(rootPath.length());
        while (relative.startsWith(File.separator)) {
            relative = relative.substring(1);
        }
        return relative.replace(File.separatorChar, '/');
    }

    /**
     * 令牌桶：按速率补充额度。
     *
     * <p>额度上限至少留一块（{@link #CHUNK_SIZE}）—— 否则限速低于一块大小时，
     * 额度永远攒不够一个整块，传输会卡死不动。
     */
    private static final class TokenBucket {
        private long tokens;
        private long lastRefillMs;
        private final long cap;

        TokenBucket(long cap) {
            this.cap = cap;
            this.tokens = 0L;
            this.lastRefillMs = System.currentTimeMillis();
        }

        synchronized void refill(long now, long rateBytesPerSec, long capBytes) {
            if (rateBytesPerSec == Long.MAX_VALUE) {
                tokens = Long.MAX_VALUE;
                lastRefillMs = now;
                return;
            }
            long limit = Math.max(capBytes, CHUNK_SIZE);
            long elapsed = now - lastRefillMs;
            if (elapsed > 0) {
                tokens = Math.min(limit, tokens + elapsed * rateBytesPerSec / 1000L);
                lastRefillMs = now;
            }
        }

        synchronized long available() {
            return tokens;
        }

        synchronized void spend(long bytes) {
            tokens = Math.max(0L, tokens - bytes);
        }
    }
}
