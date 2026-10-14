package com.secondhand.mall.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal secondHandPrice;

    @Column(name = "item_condition", nullable = false, length = 50)
    private String itemCondition;

    @Column(nullable = false, length = 100)
    private String sellerName;

    /** 卖家手机号：仅写入，不随商品接口序列化返回（防公开接口批量抓取） */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    @Column(length = 20)
    private String sellerPhone;

    @Column(length = 100)
    private String sellerUsername;

    @Column(length = 255)
    private String sellerAvatar;

    /**
     * 多张图片，逗号分隔存储 (TEXT 列)
     */
    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    /**
     * 库存数量，默认 1
     */
    @Column(nullable = false)
    private Integer quantity = 1;

    /** 占用库存：已下单/已付款未终结订单占用的数量（总库存 quantity ≥ 占用） */
    @Column(nullable = false)
    private Integer reservedQuantity = 0;

    /** 乐观锁版本号：库存等并发更新冲突检测与重试兜底 */
    @Version
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long version;

    @Column(nullable = false, length = 20)
    private String status;

    /** 审核驳回理由（status=rejected 时有值），卖家在「我的商品」可见 */
    @Column(length = 255)
    private String reviewNote;

    /** 浏览量 */
    @Column(nullable = false)
    private Integer viewCount = 0;

    @Column(nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    /** 销量（未取消订单数），非持久化，查询时填充 */
    @Transient
    private Long salesCount;

    /** 买家评分均值，非持久化 */
    @Transient
    private Double avgRating;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "active";
        if (quantity == null) quantity = 1;
        if (reservedQuantity == null) reservedQuantity = 0;
        if (viewCount == null) viewCount = 0;
    }

    public Product() {}

    public Product(String name, String category, String description, BigDecimal originalPrice,
                   BigDecimal secondHandPrice, String itemCondition, String sellerName,
                   String sellerPhone, String imageUrl) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.originalPrice = originalPrice;
        this.secondHandPrice = secondHandPrice;
        this.itemCondition = itemCondition;
        this.sellerName = sellerName;
        this.sellerPhone = sellerPhone;
        this.imageUrl = imageUrl;
    }

    // ===== 多图片辅助方法 =====

    /**
     * 获取图片列表
     */
    public List<String> getImageUrls() {
        if (imageUrl == null || imageUrl.isEmpty()) return Collections.emptyList();
        return Arrays.asList(imageUrl.split(","));
    }

    /**
     * 设置图片列表（逗号拼接存储）
     */
    public void setImageUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            this.imageUrl = null;
        } else {
            this.imageUrl = String.join(",", urls);
        }
    }

    /**
     * 获取首张图片（兼容旧代码）
     */
    public String getFirstImage() {
        List<String> urls = getImageUrls();
        return urls.isEmpty() ? null : urls.get(0);
    }

    // ===== Getters / Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }

    public BigDecimal getSecondHandPrice() { return secondHandPrice; }
    public void setSecondHandPrice(BigDecimal secondHandPrice) { this.secondHandPrice = secondHandPrice; }

    public String getItemCondition() { return itemCondition; }
    public void setItemCondition(String itemCondition) { this.itemCondition = itemCondition; }

    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }

    public String getSellerPhone() { return sellerPhone; }
    public void setSellerPhone(String sellerPhone) { this.sellerPhone = sellerPhone; }

    public String getSellerUsername() { return sellerUsername; }
    public void setSellerUsername(String sellerUsername) { this.sellerUsername = sellerUsername; }

    public String getSellerAvatar() { return sellerAvatar; }
    public void setSellerAvatar(String sellerAvatar) { this.sellerAvatar = sellerAvatar; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(Integer reservedQuantity) { this.reservedQuantity = reservedQuantity; }

    /** 可售库存 = 总库存 - 占用 */
    public int getAvailableQuantity() {
        int total = quantity != null ? quantity : 0;
        int reserved = reservedQuantity != null ? reservedQuantity : 0;
        return Math.max(0, total - reserved);
    }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }

    public Long getSalesCount() { return salesCount; }
    public void setSalesCount(Long salesCount) { this.salesCount = salesCount; }

    public Double getAvgRating() { return avgRating; }
    public void setAvgRating(Double avgRating) { this.avgRating = avgRating; }
}
