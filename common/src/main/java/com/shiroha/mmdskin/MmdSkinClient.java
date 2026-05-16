/* 文件职责：初始化客户端侧 MMD 运行时与舞台子系统。 */
package com.shiroha.mmdskin;

import com.shiroha.mmdskin.api.MmdSkinApi;
import com.shiroha.mmdskin.bridge.runtime.NativeModelBridgePorts;
import com.shiroha.mmdskin.renderer.runtime.animation.MMDAnimManager;
import com.shiroha.mmdskin.renderer.runtime.model.MMDModelManager;
import com.shiroha.mmdskin.renderer.runtime.texture.MMDTextureManager;
import com.shiroha.mmdskin.stage.client.bootstrap.StageClientBootstrap;
import com.shiroha.mmdskin.util.VectorParseUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

public class MmdSkinClient {
    public static final Logger logger = LogManager.getLogger();

    public static void initClient() {
        MmdSkinApi.configureRuntimeCollaborators(
                NativeModelBridgePorts.modelPort(),
                NativeModelBridgePorts.queryPort()
        );
        StageClientBootstrap.initialize();
        MmdClientResourceBootstrap.initialize();
        MMDModelManager.Init();
        MMDTextureManager.Init();
        MMDAnimManager.Init();
    }

    public static String calledFrom(int i){
        StackTraceElement[] steArray = Thread.currentThread().getStackTrace();
        if (steArray.length <= i) {
            return "";
        }
        return steArray[i].getClassName();
    }

    public static Vector3f str2Vec3f(String arg){
        return VectorParseUtil.parseVec3f(arg);
    }

}
