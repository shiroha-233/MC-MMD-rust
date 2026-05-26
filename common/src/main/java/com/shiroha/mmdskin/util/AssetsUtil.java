package com.shiroha.mmdskin.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class AssetsUtil {
    private static final String ASSET_ROOT = "assets/mmdskin/";

    private AssetsUtil() {
    }

    public static String getAssetsAsString(String relativePath) {
        String resourcePath = ASSET_ROOT + relativePath;
        try (InputStream inputStream = AssetsUtil.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing asset resource: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read asset resource: " + resourcePath, e);
        }
    }
}
