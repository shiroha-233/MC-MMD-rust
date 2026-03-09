package com.shiroha.mmdskin.util;

import java.io.IOException;
import java.io.InputStream;

public class AssetsUtil {

    public static String getAssetsAsString(String assetsPath) {
        try (InputStream stream = AssetsUtil.class.getClassLoader()
                .getResourceAsStream("assets/mmdskin/" + assetsPath)) {
            if (stream == null) {
                return null;
            }
            return new String(stream.readAllBytes());
        } catch (IOException e) {
            return null;
        }
    }
}
