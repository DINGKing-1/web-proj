package com.secondhand.mall.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 文件上传服务：负责图片的 base64 解码、存储、URL 生成。
 * 从 Controller 中抽取，遵循单一职责原则。
 * 未来如需切换为 OSS/MinIO 存储，只需修改此 Service。
 */
@Service
public class FileUploadService {

    private static final long MAX_SINGLE_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int MAX_FILE_COUNT = 9;

    /**
     * 上传商品图片（支持多张 base64）
     */
    public List<String> uploadProductImages(List<String> base64Images) {
        if (base64Images == null || base64Images.isEmpty()) {
            throw new RuntimeException("图片数据为空");
        }
        if (base64Images.size() > MAX_FILE_COUNT) {
            throw new RuntimeException("最多上传 " + MAX_FILE_COUNT + " 张图片");
        }

        String uploadDir = System.getProperty("user.dir") + File.separator + "product-images";
        File dir = new File(uploadDir);
        if (!dir.exists()) dir.mkdirs();

        List<String> urls = new ArrayList<>();
        for (String base64Data : base64Images) {
            urls.add(saveBase64Image(base64Data, dir, "/product-images/"));
        }
        return urls;
    }

    /**
     * 解码并保存单张 base64 图片
     */
    private String saveBase64Image(String base64Data, File dir, String urlPrefix) {
        String[] parts = base64Data.split(",");
        String header = parts[0];
        String data = parts.length > 1 ? parts[1] : parts[0];

        byte[] imageBytes = Base64.getDecoder().decode(data);
        if (imageBytes.length > MAX_SINGLE_FILE_SIZE) {
            throw new RuntimeException("单张图片大小不能超过 5MB");
        }

        String ext = "jpg";
        if (header.contains("png")) ext = "png";
        else if (header.contains("gif")) ext = "gif";
        else if (header.contains("webp")) ext = "webp";

        String filename = UUID.randomUUID().toString().substring(0, 8) + "_" + System.currentTimeMillis() + "." + ext;
        File file = new File(dir, filename);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(imageBytes);
        } catch (Exception e) {
            throw new RuntimeException("图片保存失败: " + e.getMessage());
        }

        return urlPrefix + filename;
    }
}
