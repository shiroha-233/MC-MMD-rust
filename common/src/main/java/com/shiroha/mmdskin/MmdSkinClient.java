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
    //public static String[] debugStr = new String[10];

    public static void initClient() {
        // 检查全局功能开关 - 如果禁用则不进行任何初始化
        if (!com.shiroha.mmdskin.config.ConfigManager.isModEnabled()) {
            logger.warn("全局功能开关已禁用，模组不会进行任何初始化");
            return;
        }

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
        File entityPlayerDir = PathConstants.getEntityPlayerDir();
        if (PathConstants.ensureDirectoryExists(entityPlayerDir)) {
            logger.info("创建 EntityPlayer 模型目录: " + entityPlayerDir.getAbsolutePath());
        }
        
        File customAnimDir = PathConstants.getCustomAnimDir();
        if (PathConstants.ensureDirectoryExists(customAnimDir)) {
            logger.info("创建 CustomAnim 目录: " + customAnimDir.getAbsolutePath());
        }
        
        File customMorphDir = PathConstants.getCustomMorphDir();
        if (PathConstants.ensureDirectoryExists(customMorphDir)) {
            logger.info("创建 CustomMorph 目录: " + customMorphDir.getAbsolutePath());
        }
        
        File defaultMorphDir = PathConstants.getDefaultMorphDir();
        if (PathConstants.ensureDirectoryExists(defaultMorphDir)) {
            logger.info("创建 DefaultMorph 目录: " + defaultMorphDir.getAbsolutePath());
        }
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
            logger.info("DefaultAnim 目录缺失，从模组内置资源提取...");
            PathConstants.ensureDirectoryExists(defaultAnimDir);
            
            int extracted = 0;
            for (String fileName : DEFAULT_ANIM_FILES) {
                try (InputStream is = MmdSkinClient.class.getResourceAsStream("/assets/mmdskin/default_anim/" + fileName)) {
                    if (is != null) {
                        File targetFile = new File(defaultAnimDir, fileName);
                        Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        extracted++;
                    }
                } catch (IOException e) {
                    logger.warn("提取动画文件失败: " + fileName, e);
                }
            }
            logger.info("已从模组内置资源提取 " + extracted + " 个默认动画文件");
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
            throw new IllegalStateException("File is outside extraction target directory.");
        }
    }

    public static final void unzip(String filename, String targetDir) throws java.io.IOException {
        FileInputStream fis = new FileInputStream(filename);
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis));
        ZipEntry entry;
        int entries = 0;
        long total = 0;
        try {
            while ((entry = zis.getNextEntry()) != null) {
                logger.info("Extracting: " + entry);
                int count;
                byte data[] = new byte[BUFFER];
                // Write the files to the disk, but ensure that the filename is valid,
                // and that the file is not insanely big
                String name = validateFilename(targetDir+entry.getName(), targetDir);
                File targetFile = new File(name);
                if (entry.isDirectory()) {
                    logger.info("Creating directory " + name);
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
                    throw new IllegalStateException("Too many files to unzip.");
                }
                if (total + BUFFER > TOOBIG) {
                    throw new IllegalStateException("File being unzipped is too big.");
                }
            }
        } finally {
            zis.close();
        }
    }

    private static void check3DSkinFolder(){
        File skin3DFolder = PathConstants.getSkinRootDir();
        if (!skin3DFolder.exists()){
            logger.info("3d-skin folder not found, try download from github!");
            skin3DFolder.mkdir();
            String gameDir = PathConstants.getGameDirectory();
            File zipFile = new File(gameDir, PathConstants.RESOURCE_ZIP_NAME);
            
            boolean downloadSuccess = false;
            try{
                FileUtils.copyURLToFile(new URL(PathConstants.RESOURCE_DOWNLOAD_URL), 
                    zipFile, 30000, 30000);
                downloadSuccess = true;
            }catch (IOException e){
                logger.error("Download 3d-skin.zip failed: {}", e.getMessage());
            }

            // 仅在下载成功后解压，避免解压损坏/不完整的文件
            if (downloadSuccess) {
                try{
                    unzip(zipFile.getAbsolutePath(), 
                          PathConstants.getSkinRootPath() + "/");
                }catch (IOException e){
                    logger.error("extract 3d-skin.zip failed: {}", e.getMessage());
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
            logger.warn("向量解析失败: {}", arg);
            return new Vector3f(0.0f);
        }
    }
    
}
