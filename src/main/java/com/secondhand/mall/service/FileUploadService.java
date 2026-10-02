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

        // 按文件魔数识别真实类型，避免伪造扩展名
        String ext = detectImageExt(imageBytes, header);

        String filename = UUID.randomUUID().toString().substring(0, 8) + "_" + System.currentTimeMillis() + "." + ext;
        File file = new File(dir, filename);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(imageBytes);
        } catch (Exception e) {
            throw new RuntimeException("图片保存失败: " + e.getMessage());
        }

        return urlPrefix + filename;
    }

    /** 根据文件魔数判断图片类型；无法识别时回退 dataURL header，仍不认识则拒绝 */
    private String detectImageExt(byte[] bytes, String header) {
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "png";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (bytes.length >= 6
                && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        // 无魔数匹配时仅信任 dataURL header 的声明
        if (header != null) {
            if (header.contains("png")) return "png";
            if (header.contains("gif")) return "gif";
            if (header.contains("webp")) return "webp";
            if (header.contains("jpeg") || header.contains("jpg")) return "jpg";
        }
        throw new RuntimeException("仅支持 JPG/PNG/GIF/WebP 图片");
    }
}
