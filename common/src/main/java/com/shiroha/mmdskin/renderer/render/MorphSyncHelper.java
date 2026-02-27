package com.shiroha.mmdskin.renderer.render;

import com.shiroha.mmdskin.NativeFunc;
import com.shiroha.mmdskin.config.PathConstants;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * 表情远程同步 (SRP - 单一职责原则)
 * 
 * 负责远程玩家表情的查找与应用。
 * 从 MmdSkinRendererPlayerHelper 中拆分而来。
 */
public final class MorphSyncHelper {
    
    private static final Logger logger = LogManager.getLogger();
    
    /** 重置所有表情的特殊标记 */
    private static final String RESET_TOKEN = "__reset__";
    
    private MorphSyncHelper() {
    }
    
    /**
     * 远程玩家表情同步
     * 
     * @param player    远程玩家
     * @param morphName 表情名称（"__reset__" 表示重置所有表情）
     */
    public static void applyRemoteMorph(Player player, String morphName) {
        if (player == null || morphName == null || morphName.isEmpty()) return;
        
        PlayerModelResolver.Result resolved = PlayerModelResolver.resolve(player);
        if (resolved == null) return;
        
        long modelHandle = resolved.model().model.getModelHandle();
        NativeFunc nf = NativeFunc.GetInst();
        
        if (RESET_TOKEN.equals(morphName)) {
            nf.ResetAllMorphs(modelHandle);
        } else {
            applyMorphFromFile(nf, modelHandle, morphName, resolved.playerName(),
                    resolved.model().model.getModelName());
        }
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 按优先级在已知目录中查找 VPD 文件并应用
     * 查找顺序：CustomMorph → DefaultMorph → EntityPlayer/模型名
     */
    private static void applyMorphFromFile(NativeFunc nf, long modelHandle, 
                                            String morphName, String playerName, String modelName) {
        String vpdPath = findVpdFile(morphName, modelName);
        if (vpdPath != null) {
            int result = nf.ApplyVpdMorph(modelHandle, vpdPath);
            if (result < 0) {
                logger.warn("[表情同步] 远程玩家 {} 表情应用失败: {} ({})", playerName, morphName, result);
            }
        } else {
            logger.warn("[表情同步] 本地未找到表情文件: {}", morphName);
        }
    }
    
    /**
     * 按优先级在已知目录中查找 VPD 文件（避免全盘扫描）
     * 查找顺序：CustomMorph → DefaultMorph → EntityPlayer/模型名
     */
    private static String findVpdFile(String morphName, String modelName) {
        // 1. 自定义表情目录
        String path = PathConstants.getCustomMorphPath(morphName);
        if (new File(path).exists()) return path;
        
        // 2. 默认表情目录
        path = PathConstants.getDefaultMorphPath(morphName);
        if (new File(path).exists()) return path;
        
        // 3. 模型专属目录
        if (modelName != null && !modelName.isEmpty()) {
            path = PathConstants.getModelMorphPath(modelName, morphName);
            if (new File(path).exists()) return path;
        }
        
        return null;
    }
}
