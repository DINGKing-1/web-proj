package com.secondhand.mall.service;

import com.secondhand.mall.storage.StorageCategory;
import com.secondhand.mall.storage.StorageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 文件上传服务：负责图片的 base64 解码、校验，落盘委托给 {@link StorageService}。
 * 切换 OSS/MinIO 时只需替换 StorageService 实现。
 */
@Service
public class FileUploadService {

    private static final long MAX_SINGLE_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final long MAX_VIDEO_SIZE = 30 * 1024 * 1024; // 30MB
    private static final int MAX_FILE_COUNT = 9;

    private final StorageService storageService;

    public FileUploadService(StorageService storageService) {
        this.storageService = storageService;
    }

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

        List<String> urls = new ArrayList<>();
        for (String base64Data : base64Images) {
            urls.add(saveBase64Image(base64Data));
        }
        return urls;
    }

    /**
     * 上传评价媒体（图片 + 视频），返回 URL 列表
     */
    public List<String> uploadReviewMedia(List<String> base64Items) {
        if (base64Items == null || base64Items.isEmpty()) {
            throw new RuntimeException("媒体数据为空");
        }
        if (base64Items.size() > MAX_FILE_COUNT) {
            throw new RuntimeException("最多上传 " + MAX_FILE_COUNT + " 个文件");
        }

        List<String> urls = new ArrayList<>();
        for (String base64Data : base64Items) {
            String[] parts = base64Data.split(",");
            String header = parts[0];
            String data = parts.length > 1 ? parts[1] : parts[0];
            byte[] bytes = Base64.getDecoder().decode(data);

            String ext;
            boolean isVideo = looksLikeVideo(bytes, header);
            if (isVideo) {
                if (bytes.length > MAX_VIDEO_SIZE) {
                    throw new RuntimeException("视频大小不能超过 30MB");
                }
                ext = detectVideoExt(bytes, header);
            } else {
                if (bytes.length > MAX_SINGLE_FILE_SIZE) {
                    throw new RuntimeException("单张图片大小不能超过 5MB");
                }
                ext = detectImageExt(bytes, header);
            }
            urls.add(storageService.store(StorageCategory.PRODUCT_IMAGE, bytes, ext));
        }
        return urls;
    }

    /** 是否视频：按魔数 / dataURL 头判断 */
    private boolean looksLikeVideo(byte[] bytes, String header) {
        String h = header == null ? "" : header.toLowerCase();
        if (h.contains("video")) return true;
        // ISO BMFF: ftyp
        if (bytes.length >= 12
                && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p') {
            return true;
        }
        // WebM / Matroska
        if (bytes.length >= 4
                && (bytes[0] & 0xFF) == 0x1A && (bytes[1] & 0xFF) == 0x45
                && (bytes[2] & 0xFF) == 0xDF && (bytes[3] & 0xFF) == 0xA3) {
            return true;
        }
        // AVI
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'A' && bytes[9] == 'V' && bytes[10] == 'I') {
            return true;
        }
        return false;
    }

    private String detectVideoExt(byte[] bytes, String header) {
        String h = header == null ? "" : header.toLowerCase();
        if (h.contains("webm") || (bytes.length >= 4
                && (bytes[0] & 0xFF) == 0x1A && (bytes[1] & 0xFF) == 0x45)) {
            return "webm";
        }
        if (h.contains("avi") || (bytes.length >= 12 && bytes[8] == 'A' && bytes[9] == 'V')) {
            return "avi";
        }
        // ftyp 品牌：qt → mov，其余按 mp4
        if (bytes.length >= 12 && bytes[8] == 'q' && bytes[9] == 't') {
            return "mov";
        }
        return "mp4";
    }

    /**
     * 解码并保存单张 base64 图片
     */
    private String saveBase64Image(String base64Data) {
        String[] parts = base64Data.split(",");
        String header = parts[0];
        String data = parts.length > 1 ? parts[1] : parts[0];

        byte[] imageBytes = Base64.getDecoder().decode(data);
        if (imageBytes.length > MAX_SINGLE_FILE_SIZE) {
            throw new RuntimeException("单张图片大小不能超过 5MB");
        }

        // 按文件魔数识别真实类型，避免伪造扩展名
        String ext = detectImageExt(imageBytes, header);
        return storageService.store(StorageCategory.PRODUCT_IMAGE, imageBytes, ext);
    }

    /** 根据文件魔数判断图片类型；无法识别则拒绝（不信任 dataURL header 声明） */
    private String detectImageExt(byte[] bytes, String header) {
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && (bytes[1] & 0xFF) == 0x50
                && (bytes[2] & 0xFF) == 0x4E && (bytes[3] & 0xFF) == 0x47) {
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
        throw new RuntimeException("仅支持 JPG/PNG/GIF/WebP 图片");
    }
}
