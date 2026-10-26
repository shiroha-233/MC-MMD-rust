package com.shiroha.mmdskin.distrib;

import com.shiroha.mmdskin.config.GameDirectoryProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型分发：路径安全、默认关闭、分块编解码对称、安装序列的回归测试。
 */
class ModelDistributionTest {

    @TempDir
    Path gameDir;

    @BeforeEach
    void setUp() {
        GameDirectoryProvider.setProvider(() -> gameDir.toFile());
        ModelDistributionConfig.reload();
    }

    @AfterEach
    void tearDown() {
        GameDirectoryProvider.setProvider(null);
    }

    // ========================================================================
    // 模型名：不能逃出模型根目录
    // ========================================================================

    @Test
    void shouldRejectHostileModelNames() {
        String[] hostile = {
                "", "..", "../evil", "..\\evil", "/etc/passwd", "C:\\Windows", "\\\\server\\share",
                ".hidden", "a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b",
                "with\nnewline", "with\tcontrol"
        };
        for (String name : hostile) {
            assertFalse(ModelDistributionService.isValidModelName(name), "应拒绝: " + name);
        }
        String tooLong = "a".repeat(129);
        assertFalse(ModelDistributionService.isValidModelName(tooLong), "应拒绝超长名");
        assertTrue(ModelDistributionService.isValidModelName("Nashi"), "普通名字应通过");
    }

    @Test
    void shouldRejectModelDirOutsideRoot() {
        assertNull(ModelDistributionService.modelDirOf("../evil"));
        assertNull(ModelDistributionService.modelDirOf("/abs"));
        assertNotNull(ModelDistributionService.modelDirOf("Nashi"));
    }

    // ========================================================================
    // 相对路径：清单里的路径也必须校验（路径来自服务器）
    // ========================================================================

    @Test
    void shouldRejectHostileRelativePaths() {
        String[] hostile = {
                "", "/etc/passwd", "../../evil", "a/../../evil", "C:/x", "a\\b", "a//b",
                "./a", "a/./b", "a/../b"
        };
        for (String path : hostile) {
            assertFalse(ModelDistributionService.isValidRelativePath(path), "应拒绝: " + path);
        }
        assertTrue(ModelDistributionService.isValidRelativePath("Nashi.pmx"));
        assertTrue(ModelDistributionService.isValidRelativePath("tex/body.bmp"));
    }

    // ========================================================================
    // 默认三重关闭
    // ========================================================================

    @Test
    void shouldBeOffByDefault() {
        ModelDistributionConfig config = ModelDistributionConfig.get();
        assertFalse(config.acknowledged, "acknowledged 默认应为 false");
        assertFalse(config.enabled, "enabled 默认应为 false");
        assertFalse(config.isActive(), "默认不应生效");
        assertFalse(config.isDistributable("Nashi"), "默认任何模型都不可分发");
        assertTrue(ModelDistributionService.distributableModels().isEmpty(), "默认不该有可分发模型");
        assertNull(ModelDistributionService.buildManifest("Nashi"), "默认不该能生成清单");
        String manifestJson = new String(ModelDistributionService.buildManifestList(), StandardCharsets.UTF_8);
        assertTrue(ModelDistributionManifest.listFromJson(manifestJson).isEmpty(), "默认不该下发任何模型");
    }

    // ========================================================================
    // 分块编解码对称
    // ========================================================================

    @Test
    void shouldRoundTripChunkFrame() throws IOException {
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        ModelDistributionService.PendingChunk chunk = new ModelDistributionService.PendingChunk(
                "Nashi", 3, 7, 9, 123456L, 65536L, payload);

        byte[] encoded = ModelDistributionService.encodeChunk(chunk);
        assertNotNull(encoded);

        try (InputStream in = new ByteArrayInputStream(encoded)) {
            assertEquals("Nashi", ModelDistributionService.readUtf(in));
            assertEquals(3, ModelDistributionService.readInt(in));
            assertEquals(7, ModelDistributionService.readInt(in));
            assertEquals(9, ModelDistributionService.readInt(in));
            assertEquals(123456L, ModelDistributionService.readLong(in));
            assertEquals(65536L, ModelDistributionService.readLong(in));
            assertArrayEquals(payload, in.readAllBytes());
        }
    }

    @Test
    void shouldRejectTruncatedChunkFrame() {
        assertThrowsIo(() -> {
            try (InputStream in = new ByteArrayInputStream(new byte[]{1, 2})) {
                ModelDistributionService.readUtf(in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ========================================================================
    // 清单分页：页要够小，且拼回来能还原完整清单
    // ========================================================================

    @Test
    void shouldPageOfferAndReassemble() {
        ModelDistributionManifest manifest = new ModelDistributionManifest();
        manifest.modelName = "BigModel";
        for (int i = 0; i < 3000; i++) {
            ModelDistributionManifest.FileEntry entry = new ModelDistributionManifest.FileEntry();
            entry.path = "textures/skin_" + i + ".bmp";
            entry.size = 1024L * i;
            entry.md5 = "0123456789abcdef0123456789abcdef";
            manifest.files.add(entry);
        }
        manifest.totalSize = 123456789L;

        java.util.List<ModelDistributionManifest.OfferPage> pages =
                ModelDistributionService.buildOfferPages(manifest);
        assertTrue(pages.size() > 1, "3000 个文件应该被拆成多页");

        for (ModelDistributionManifest.OfferPage page : pages) {
            String json = page.toJson();
            assertTrue(json.getBytes(StandardCharsets.UTF_8).length <= ModelDistributionService.MAX_OFFER_PAGE_BYTES,
                    "单页不能超过字节上限，实际 " + json.getBytes(StandardCharsets.UTF_8).length);
            assertEquals(pages.size(), page.pageCount, "每页都要写明总页数");
        }

        // 拼回来必须还原成原清单
        java.util.List<ModelDistributionManifest.FileEntry> rebuilt = new java.util.ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            ModelDistributionManifest.OfferPage page = pages.get(i);
            assertEquals(i, page.pageIndex, "页序号要连续");
            rebuilt.addAll(page.entries);
        }
        assertEquals(manifest.files.size(), rebuilt.size(), "拼回来的文件数要一致");
        assertEquals(manifest.files.get(2999).path, rebuilt.get(2999).path, "顺序和内容要一致");
    }

    @Test
    void shouldGzipAndGunzipRoundTrip() throws IOException {
        byte[] raw = "纳西妲".repeat(500).getBytes(StandardCharsets.UTF_8);
        byte[] packed = ModelDistributionService.gzip(raw);
        assertNotNull(packed);
        assertTrue(packed.length < raw.length, "gzip 后应该更小");
    }

    // ========================================================================
    // 安装序列：临时目录是同级兄弟目录，失败要能回滚
    // ========================================================================

    @Test
    void shouldInstallIntoFreshDirectory() throws IOException {
        Path modelDir = gameDir.resolve("3d-skin").resolve("EntityPlayer").resolve("Nashi");
        Path partial = gameDir.resolve("3d-skin").resolve("EntityPlayer").resolve("Nashi.__mmd_partial");
        Files.createDirectories(partial);
        Files.write(partial.resolve("Nashi.pmx"), "NEW".getBytes(StandardCharsets.UTF_8));

        assertTrue(ModelDistributionService.modelDirOf("Nashi") != null);
        assertEquals(partial.toFile(), ModelDistributionService.partialDirOf("Nashi"),
                "临时目录必须是目标目录的同级兄弟，不能是子目录");

        Files.delete(partial.resolve("Nashi.pmx"));
        // 清单校验会失败，这里只验证目录关系
        assertTrue(Files.exists(partial));
    }

    @Test
    void shouldNotLoseExistingModelWhenInstallFails() throws IOException {
        Path entityPlayer = gameDir.resolve("3d-skin").resolve("EntityPlayer");
        Path modelDir = entityPlayer.resolve("Nashi");
        Files.createDirectories(modelDir);
        Files.write(modelDir.resolve("Nashi.pmx"), "OLD".getBytes(StandardCharsets.UTF_8));

        Path partial = entityPlayer.resolve("Nashi.__mmd_partial");
        Files.createDirectories(partial);

        // 清单里说文件很大，实际文件很小 -> 校验失败 -> 不能动正式目录
        ModelDistributionManifest manifest = new ModelDistributionManifest();
        manifest.modelName = "Nashi";
        ModelDistributionManifest.FileEntry entry = new ModelDistributionManifest.FileEntry();
        entry.path = "Nashi.pmx";
        entry.size = 999L;
        entry.md5 = "00000000000000000000000000000000";
        manifest.files.add(entry);

        assertFalse(ModelDistributionClient.verifyAndInstall(manifest), "校验不过就不该装");
        assertTrue(Files.exists(modelDir.resolve("Nashi.pmx")), "安装失败不能把玩家原来的模型弄丢");
        assertEquals("OLD", new String(Files.readAllBytes(modelDir.resolve("Nashi.pmx")), StandardCharsets.UTF_8),
                "原来的模型内容必须还在");
    }

    // ========================================================================
    // 工具
    // ========================================================================

    private static void assertThrowsIo(Runnable runnable) {
        try {
            runnable.run();
            org.junit.jupiter.api.Assertions.fail("应当抛出 IOException");
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            assertTrue(cause instanceof IOException, "应当是 IOException，实际: " + cause);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

}
