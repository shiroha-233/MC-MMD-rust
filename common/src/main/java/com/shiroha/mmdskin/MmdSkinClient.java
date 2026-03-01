package com.shiroha.mmdskin;

import com.shiroha.mmdskin.config.PathConstants;
import com.shiroha.mmdskin.renderer.model.MMDModelManager;
import com.shiroha.mmdskin.renderer.resource.MMDTextureManager;
import com.shiroha.mmdskin.renderer.animation.MMDAnimManager;
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
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

public class MmdSkinClient {
    public static final Logger logger = LogManager.getLogger();
    public static int usingMMDShader = 0;
    static final int BUFFER = 512;
    static final long TOOBIG = 0x6400000; // Max size of unzipped data, 100MB
    static final int TOOMANY = 1024;      // Max number of files


    public static void initClient() {
        check3DSkinFolder();
        extractDefaultAnimIfNeeded();
        MMDModelManager.Init();
        MMDTextureManager.Init();
        MMDAnimManager.Init();
        
        // 确保 EntityPlayer 目录存在
        ensureEntityPlayerDirectory();
    }
    
    /**
     * 确保所有必需的目录结构存在
     */
    private static void ensureEntityPlayerDirectory() {
        PathConstants.ensureDirectoryExists(PathConstants.getEntityPlayerDir());
        PathConstants.ensureDirectoryExists(PathConstants.getCustomAnimDir());
        PathConstants.ensureDirectoryExists(PathConstants.getCustomMorphDir());
        PathConstants.ensureDirectoryExists(PathConstants.getDefaultMorphDir());
        PathConstants.ensureDirectoryExists(PathConstants.getSceneModelDir());
    }
    
    /** 内置默认动画文件列表 */
    private static final String[] DEFAULT_ANIM_FILES = {
        "crawl.vmd", "die.vmd", "elytraFly.vmd", "idle.vmd",
        "itemActive_minecraft.bow_Left_using.vmd", "itemActive_minecraft.iron_sword_Right_swinging.vmd",
        "itemActive_minecraft.shield_Left_using.vmd", "itemActive_minecraft.shield_Right_using.vmd",
        "lieDown.vmd", "onClimbable.vmd", "onClimbableDown.vmd", "onClimbableUp.vmd",
        "onHorse.vmd", "ride.vmd", "sleep.vmd", "sneak.vmd",
        "sprint.vmd", "swim.vmd", "swingLeft.vmd", "swingRight.vmd", "walk.vmd"
    };
    
    /**
     * 检查并提取内置默认动画到 3d-skin/DefaultAnim
     */
    private static void extractDefaultAnimIfNeeded() {
        File defaultAnimDir = PathConstants.getDefaultAnimDir();
        
        // 如果目录不存在或为空，则提取内置动画
        String[] files = defaultAnimDir.list();
        if (!defaultAnimDir.exists() || files == null || files.length == 0) {
            PathConstants.ensureDirectoryExists(defaultAnimDir);
            
            for (String fileName : DEFAULT_ANIM_FILES) {
                try (InputStream is = MmdSkinClient.class.getResourceAsStream("/assets/mmdskin/default_anim/" + fileName)) {
                    if (is != null) {
                        File targetFile = new File(defaultAnimDir, fileName);
                        Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    logger.warn("提取动画文件失败: " + fileName, e);
                }
            }
        }
    }

    private static String validateFilename(String filename, String intendedDir) throws java.io.IOException {
        File f = new File(filename);
        String canonicalPath = f.getCanonicalPath();

        File iD = new File(intendedDir);
        String canonicalID = iD.getCanonicalPath();

        if (canonicalPath.startsWith(canonicalID)) {
            return canonicalPath;
        } else {
            throw new IllegalStateException("文件在目标解压目录之外");
        }
    }

    public static final void unzip(String filename, String targetDir) throws java.io.IOException {
        ZipEntry entry;
        int entries = 0;
        long total = 0;
        try (FileInputStream fis = new FileInputStream(filename);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis))) {
            while ((entry = zis.getNextEntry()) != null) {
                int count;
                byte data[] = new byte[BUFFER];
                String name = validateFilename(targetDir+entry.getName(), targetDir);
                File targetFile = new File(name);
                if (entry.isDirectory()) {
                    new File(name).mkdir();
                    continue;
                }
                if (!targetFile.getParentFile().exists()){
                    targetFile.getParentFile().mkdirs();
                }
                try (FileOutputStream fos = new FileOutputStream(name);
                     BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER)) {
                    while (total + BUFFER <= TOOBIG && (count = zis.read(data, 0, BUFFER)) != -1) {
                        dest.write(data, 0, count);
                        total += count;
                    }
                    dest.flush();
                }
                zis.closeEntry();
                entries++;
                if (entries > TOOMANY) {
                    throw new IllegalStateException("解压文件数量过多");
                }
                if (total + BUFFER > TOOBIG) {
                    throw new IllegalStateException("解压文件体积过大");
                }
            }
        }
    }

    private static void check3DSkinFolder(){
        File skin3DFolder = PathConstants.getSkinRootDir();
        if (!skin3DFolder.exists()){
            skin3DFolder.mkdir();
            String gameDir = PathConstants.getGameDirectory();
            File zipFile = new File(gameDir, PathConstants.RESOURCE_ZIP_NAME);
            
            boolean downloadSuccess = false;
            try{
                FileUtils.copyURLToFile(new URL(PathConstants.RESOURCE_DOWNLOAD_URL), 
                    zipFile, 30000, 30000);
                downloadSuccess = true;
            }catch (IOException e){
                logger.error("下载 3d-skin.zip 失败: {}", e.getMessage());
            }

            // 仅在下载成功后解压，避免解压损坏/不完整的文件
            if (downloadSuccess) {
                try{
                    unzip(zipFile.getAbsolutePath(), 
                          PathConstants.getSkinRootPath() + "/");
                }catch (IOException e){
                    logger.error("解压 3d-skin.zip 失败: {}", e.getMessage());
                }
            }

            // 清理下载的 zip 文件
            try {
                zipFile.delete();
            } catch (Exception ignored) {}
        }
    }

    public static String calledFrom(int i){
        StackTraceElement[] steArray = Thread.currentThread().getStackTrace();
        if (steArray.length <= i) {
            return "";
        }
        return steArray[i].getClassName();
    }

    public static Vector3f str2Vec3f(String arg){
        String[] splittedStr = arg.split(",");
        if (splittedStr.length != 3){
            return new Vector3f(0.0f);
        }
        try {
            float x = Float.parseFloat(splittedStr[0]);
            float y = Float.parseFloat(splittedStr[1]);
            float z = Float.parseFloat(splittedStr[2]);
            return new Vector3f(x, y, z);
        } catch (NumberFormatException e) {
            return new Vector3f(0.0f);
        }
    }
    
}
