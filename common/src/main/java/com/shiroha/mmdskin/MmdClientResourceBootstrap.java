package com.shiroha.mmdskin;

import com.shiroha.mmdskin.config.PathConstants;
import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class MmdClientResourceBootstrap {
    private static final int BUFFER = 512;
    private static final long TOO_BIG = 0x6400000;
    private static final int TOO_MANY = 1024;
    private static final String[] DEFAULT_ANIM_FILES = {
            "Drink.vmd", "crawl.vmd", "die.vmd", "elytraFly.vmd", "idle.vmd",
            "itemActive_minecraft.bow_Left_using.vmd", "itemActive_minecraft.iron_sword_Right_swinging.vmd",
            "itemActive_minecraft.shield_Left_using.vmd", "itemActive_minecraft.shield_Right_using.vmd",
            "lieDown.vmd", "onClimbable.vmd", "onClimbableDown.vmd", "onClimbableUp.vmd",
            "onHorse.vmd", "ride.vmd", "sleep.vmd", "sneak.vmd",
            "sprint.vmd", "swim.vmd", "swingLeft.vmd", "swingRight.vmd", "walk.vmd"
    };

    private MmdClientResourceBootstrap() {
    }

    static void initialize() {
        check3DSkinFolder();
        extractDefaultAnimIfNeeded();
        ensureEntityPlayerDirectory();
    }

    private static void ensureEntityPlayerDirectory() {
        PathConstants.ensureDirectoryExists(PathConstants.getEntityPlayerDir());
        PathConstants.ensureDirectoryExists(PathConstants.getCustomAnimDir());
        PathConstants.ensureDirectoryExists(PathConstants.getCustomMorphDir());
        PathConstants.ensureDirectoryExists(PathConstants.getDefaultMorphDir());
        PathConstants.ensureDirectoryExists(PathConstants.getSceneModelDir());
    }

    private static void extractDefaultAnimIfNeeded() {
        File defaultAnimDir = PathConstants.getDefaultAnimDir();
        PathConstants.ensureDirectoryExists(defaultAnimDir);

        for (String fileName : DEFAULT_ANIM_FILES) {
            File targetFile = new File(defaultAnimDir, fileName);
            if (targetFile.exists()) {
                continue;
            }

            try (InputStream is = MmdSkinClient.class.getResourceAsStream("/assets/mmdskin/default_anim/" + fileName)) {
                if (is != null) {
                    Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    MmdSkinClient.logger.info("提取默认动画: {}", fileName);
                }
            } catch (IOException e) {
                MmdSkinClient.logger.warn("提取动画文件失败: " + fileName, e);
            }
        }
    }

    private static void check3DSkinFolder() {
        File skin3DFolder = PathConstants.getSkinRootDir();
        if (skin3DFolder.exists()) {
            return;
        }

        skin3DFolder.mkdir();
        String gameDir = PathConstants.getGameDirectory();
        File zipFile = new File(gameDir, PathConstants.RESOURCE_ZIP_NAME);

        boolean downloadSuccess = false;
        try {
            FileUtils.copyURLToFile(new URL(PathConstants.RESOURCE_DOWNLOAD_URL), zipFile, 30000, 30000);
            downloadSuccess = true;
        } catch (IOException e) {
            MmdSkinClient.logger.error("下载 3d-skin.zip 失败: {}", e.getMessage());
        }

        if (downloadSuccess) {
            try {
                unzip(zipFile.getAbsolutePath(), PathConstants.getSkinRootPath() + "/");
            } catch (IOException e) {
                MmdSkinClient.logger.error("解压 3d-skin.zip 失败: {}", e.getMessage());
            }
        }

        try {
            zipFile.delete();
        } catch (Exception ignored) {
        }
    }

    private static void unzip(String filename, String targetDir) throws IOException {
        ZipEntry entry;
        int entries = 0;
        long total = 0;
        try (FileInputStream fis = new FileInputStream(filename);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis))) {
            while ((entry = zis.getNextEntry()) != null) {
                int count;
                byte[] data = new byte[BUFFER];
                String name = validateFilename(targetDir + entry.getName(), targetDir);
                File targetFile = new File(name);
                if (entry.isDirectory()) {
                    new File(name).mkdir();
                    continue;
                }
                if (!targetFile.getParentFile().exists()) {
                    targetFile.getParentFile().mkdirs();
                }
                try (FileOutputStream fos = new FileOutputStream(name);
                     BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER)) {
                    while (total + BUFFER <= TOO_BIG && (count = zis.read(data, 0, BUFFER)) != -1) {
                        dest.write(data, 0, count);
                        total += count;
                    }
                    dest.flush();
                }
                zis.closeEntry();
                entries++;
                if (entries > TOO_MANY) {
                    throw new IllegalStateException("解压文件数量过多");
                }
                if (total + BUFFER > TOO_BIG) {
                    throw new IllegalStateException("解压文件体积过大");
                }
            }
        }
    }

    private static String validateFilename(String filename, String intendedDir) throws IOException {
        File file = new File(filename);
        String canonicalPath = file.getCanonicalPath();
        String canonicalDir = new File(intendedDir).getCanonicalPath();
        if (canonicalPath.startsWith(canonicalDir)) {
            return canonicalPath;
        }
        throw new IllegalStateException("文件在目标解压目录之外");
    }
}
