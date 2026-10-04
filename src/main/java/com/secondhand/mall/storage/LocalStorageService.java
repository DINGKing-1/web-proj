package com.secondhand.mall.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 本地/共享盘存储。多节点部署时 root 指向 NFS 等共享目录即可保证图片一致。
 * 对象存储（OSS/MinIO）实现本接口后按 mall.storage.type 切换。
 */
@Service
@ConditionalOnProperty(name = "mall.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final StorageProperties properties;

    public LocalStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String store(StorageCategory category, byte[] content, String extension) {
        Path dir = properties.resolveCategory(category);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("创建存储目录失败: " + dir, e);
        }
        String filename = UUID.randomUUID().toString().replace("-", "")
                + "_" + System.currentTimeMillis() + "." + extension;
        Path target = dir.resolve(filename);
        try {
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("文件写入失败: " + target, e);
        }
        return category.getUrlPrefix() + filename;
    }

    @Override
    public void deleteByUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String filename = url.substring(url.lastIndexOf('/') + 1);
        if (filename.contains("..") || filename.contains("\\") || filename.isBlank()) {
            return;
        }
        StorageCategory category = resolveCategory(url);
        if (category == null) {
            return;
        }
        Path target = properties.resolveCategory(category).resolve(filename);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // 清理失败不影响主流程
        }
    }

    private StorageCategory resolveCategory(String url) {
        if (url.startsWith(StorageCategory.USER_AVATAR.getUrlPrefix())) {
            return StorageCategory.USER_AVATAR;
        }
        if (url.startsWith(StorageCategory.PRODUCT_IMAGE.getUrlPrefix())) {
            return StorageCategory.PRODUCT_IMAGE;
        }
        return null;
    }
}
