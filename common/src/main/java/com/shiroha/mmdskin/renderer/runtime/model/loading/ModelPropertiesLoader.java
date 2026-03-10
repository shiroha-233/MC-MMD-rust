package com.shiroha.mmdskin.renderer.runtime.model.loading;

import com.shiroha.mmdskin.asset.catalog.ModelInfo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ModelPropertiesLoader {

    private static final Logger logger = LogManager.getLogger();

    private ModelPropertiesLoader() {
    }

    public static void load(String modelName, Properties properties) {
        properties.clear();

        ModelInfo info = ModelInfo.findByFolderName(modelName);
        if (info == null) {
            logger.warn("模型属性加载失败，模型未找到: {}", modelName);
            return;
        }

        String pathToProperties = info.getFolderPath() + "/model.properties";
        try (InputStream stream = new FileInputStream(pathToProperties)) {
            properties.load(stream);
            logger.debug("模型属性加载成功: {}", modelName);
        } catch (IOException e) {
            logger.debug("模型属性文件未找到: {}", modelName);
        }
    }
}
