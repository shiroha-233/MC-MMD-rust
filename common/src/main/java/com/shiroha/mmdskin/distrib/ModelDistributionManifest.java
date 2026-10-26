/* 文件职责：模型分发的清单模型（文件名 + 大小 + MD5），以及它的线上序列化格式。 */
package com.shiroha.mmdskin.distrib;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型分发的清单模型（文件名 + 大小 + MD5），以及它的线上序列化格式。
 */
public final class ModelDistributionManifest {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type LIST_TYPE = new TypeToken<List<ModelDistributionManifest>>() {
    }.getType();

    /** 模型名（同时是客户端落盘的目录名）。 */
    public String modelName = "";

    /** 参与分发的文件清单。 */
    public List<FileEntry> files = new ArrayList<>();

    /** 全部文件总大小（字节）。 */
    public long totalSize = 0L;

    /** 文件数（摘要清单里用，files 为空时靠它显示）。 */
    public int fileCount = 0;

    /** 服务器下发时的限速（KB/s），用于在一级提示页算账。 */
    public int rateLimitKbPerSec = 0;

    /** 非空表示走 OSS 直链；此时服务器只提供清单与校验值，字节由客户端自取。 */
    public String ossUrl = "";

    /** OSS 直链是否已通过 ETag(MD5) 校验。 */
    public boolean ossVerified = false;

    /**
     * 清单的一页。
     *
     * <p>一份完整清单可能有上万个文件，直接塞进一个包会撞上
     * 代理的插件消息上限（BungeeCord 默认 32767 字节），
     * 所以按页拆开下发，客户端攒齐了再开始下载。
     */
    public static final class OfferPage {
        public String modelName = "";
        public int pageIndex = 0;
        public int pageCount = 1;
        public long totalSize = 0L;
        public int fileCount = 0;
        public String ossUrl = "";
        public boolean ossVerified = false;
        public List<FileEntry> entries = new ArrayList<>();

        public String toJson() {
            return GSON.toJson(this);
        }

        public static OfferPage fromJson(String json) {
            if (json == null || json.isEmpty()) {
                return null;
            }
            try {
                OfferPage page = GSON.fromJson(json, OfferPage.class);
                if (page != null && page.entries == null) {
                    page.entries = new ArrayList<>();
                }
                return page;
            } catch (Exception e) {
                return null;
            }
        }
    }

    public static final class FileEntry {
        /** 相对模型目录的路径（'/' 分隔）。 */
        public String path = "";
        /** 文件大小（字节）。 */
        public long size = 0L;
        /** 文件 MD5（小写十六进制）。 */
        public String md5 = "";
    }

    public FileEntry fileAt(int index) {
        if (index < 0 || index >= files.size()) {
            return null;
        }
        return files.get(index);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static ModelDistributionManifest fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            ModelDistributionManifest manifest = GSON.fromJson(json, ModelDistributionManifest.class);
            if (manifest != null && manifest.files == null) {
                manifest.files = new ArrayList<>();
            }
            return manifest;
        } catch (Exception e) {
            return null;
        }
    }

    public static String listToJson(List<ModelDistributionManifest> manifests) {
        return GSON.toJson(manifests, LIST_TYPE);
    }

    public static List<ModelDistributionManifest> listFromJson(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<ModelDistributionManifest> list = GSON.fromJson(json, LIST_TYPE);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
