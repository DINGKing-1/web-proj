package com.secondhand.mall.storage;

/**
 * 对象存储抽象：本地盘 / 共享盘 / OSS·MinIO 可替换实现。
 * 集群部署时把 root 指到共享卷，或实现对象存储版本保证多节点一致。
 */
public interface StorageService {

    /**
     * 保存文件并返回对外 URL 路径（如 /product-images/xxx.png）。
     */
    String store(StorageCategory category, byte[] content, String extension);

    /**
     * 删除已存储文件（按 URL 路径）。文件不存在时静默忽略。
     */
    void deleteByUrl(String url);
}
