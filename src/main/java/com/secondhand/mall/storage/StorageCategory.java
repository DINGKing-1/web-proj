package com.secondhand.mall.storage;

/**
 * 存储类别：决定落盘子目录与对外 URL 前缀。
 */
public enum StorageCategory {
    PRODUCT_IMAGE("product-images", "/product-images/"),
    USER_AVATAR("user-avatar", "/user-avatar/");

    private final String directory;
    private final String urlPrefix;

    StorageCategory(String directory, String urlPrefix) {
        this.directory = directory;
        this.urlPrefix = urlPrefix;
    }

    public String getDirectory() {
        return directory;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }
}
