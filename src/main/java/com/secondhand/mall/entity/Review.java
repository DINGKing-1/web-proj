package com.secondhand.mall.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 100)
    private String buyerUsername;

    @Column(length = 100)
    private String sellerUsername;

    /** 1-5 星（三项均分，兼容旧数据） */
    @Column(nullable = false)
    private Integer rating;

    /** 商品质量 1-5 */
    @Column
    private Integer ratingGoods;

    /** 卖家服务 1-5 */
    @Column
    private Integer ratingService;

    /** 物流速度 1-5 */
    @Column
    private Integer ratingDelivery;

    @Column(length = 500)
    private String content;

    /** 评价图片，逗号分隔 URL */
    @Column(columnDefinition = "TEXT")
    private String images;

    /** 评价视频，逗号分隔 URL */
    @Column(columnDefinition = "TEXT")
    private String videos;

    /** 卖家回复 */
    @Column(length = 500)
    private String reply;

    @Column
    private LocalDateTime repliedAt;

    /** 买家追评（一条评价最多一次） */
    @Column(length = 500)
    private String followUpContent;

    @Column
    private LocalDateTime followUpAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (rating == null) rating = 5;
        if (ratingGoods == null) ratingGoods = rating;
        if (ratingService == null) ratingService = rating;
        if (ratingDelivery == null) ratingDelivery = rating;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getBuyerUsername() { return buyerUsername; }
    public void setBuyerUsername(String buyerUsername) { this.buyerUsername = buyerUsername; }

    public String getSellerUsername() { return sellerUsername; }
    public void setSellerUsername(String sellerUsername) { this.sellerUsername = sellerUsername; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public Integer getRatingGoods() { return ratingGoods; }
    public void setRatingGoods(Integer ratingGoods) { this.ratingGoods = ratingGoods; }

    public Integer getRatingService() { return ratingService; }
    public void setRatingService(Integer ratingService) { this.ratingService = ratingService; }

    public Integer getRatingDelivery() { return ratingDelivery; }
    public void setRatingDelivery(Integer ratingDelivery) { this.ratingDelivery = ratingDelivery; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }

    public String getVideos() { return videos; }
    public void setVideos(String videos) { this.videos = videos; }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public LocalDateTime getRepliedAt() { return repliedAt; }
    public void setRepliedAt(LocalDateTime repliedAt) { this.repliedAt = repliedAt; }

    public String getFollowUpContent() { return followUpContent; }
    public void setFollowUpContent(String followUpContent) { this.followUpContent = followUpContent; }

    public LocalDateTime getFollowUpAt() { return followUpAt; }
    public void setFollowUpAt(LocalDateTime followUpAt) { this.followUpAt = followUpAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
