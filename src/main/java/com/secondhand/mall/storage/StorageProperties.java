package com.secondhand.mall.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 存储配置。root 为空时默认使用工作目录下的 product-images / user-avatar。
 * 集群部署：设置 mall.storage.root 为共享挂载路径。
 */
@ConfigurationProperties(prefix = "mall.storage")
public class StorageProperties {

    /** local | oss（当前仅实现 local） */
    private String type = "local";

    /** 存储根目录；空则用 user.dir */
    private String root = "";

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public Path resolveRoot() {
        if (root == null || root.isBlank()) {
            return Paths.get(System.getProperty("user.dir"));
        }
        return Paths.get(root);
    }

    public Path resolveCategory(StorageCategory category) {
        return resolveRoot().resolve(category.getDirectory());
    }
}
