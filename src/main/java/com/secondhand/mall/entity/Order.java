package com.secondhand.mall.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 30)
    private String orderNumber;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    private Long productId;

    @Column(length = 100)
    private String sellerUsername;

    @Column(nullable = false, length = 255)
    private String productName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal productPrice;

    /** 用券抵扣金额（支付时写入）；实付 = 单价×数量 - 该值 */
    @Column(precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column
    private Integer quantity;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(nullable = false, length = 100)
    private String receiverName;

    @Column(nullable = false, length = 20)
    private String receiverPhone;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String receiverAddress;

    @Column(nullable = false, length = 50)
    private String paymentMethod;

    @Column(nullable = false, length = 20)
    private String status = "unshipped";

    @Column(length = 20)
    private String prevStatus;

    @Column(length = 200)
    private String afterSaleReason;

    /** 卖家是否已收款（资金托管：完成后再打款） */
    @Column(nullable = false)
    private Boolean sellerPaid = false;

    @Column(length = 100)
    private String trackingNumber;

    /** 买家寄回的退货运单号（after_sale 流程） */
    @Column(length = 100)
    private String returnTrackingNumber;

    /** 售后申请时间：卖家超时未处理的兜底判定 */
    @Column
    private LocalDateTime afterSaleAt;

    /** 买家寄回时间 */
    @Column
    private LocalDateTime returnSentAt;

    /** 买家评分 1-5，未评价为 null */
    @Column
    private Integer rating;

    @Column(length = 500)
    private String reviewContent;

    @Column
    private LocalDateTime reviewedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 发货时间：自动完成超时判定用（拖很久才发货的单不应立刻被打款） */
    @Column
    private LocalDateTime shippedAt;

    @Column
    private LocalDateTime receivedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "unshipped";
        if (quantity == null) quantity = 1;
        if (sellerPaid == null) sellerPaid = false;
    }

    public Order() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getSellerUsername() { return sellerUsername; }
    public void setSellerUsername(String sellerUsername) { this.sellerUsername = sellerUsername; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public BigDecimal getProductPrice() { return productPrice; }
    public void setProductPrice(BigDecimal productPrice) { this.productPrice = productPrice; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }

    public String getReceiverAddress() { return receiverAddress; }
    public void setReceiverAddress(String receiverAddress) { this.receiverAddress = receiverAddress; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPrevStatus() { return prevStatus; }
    public void setPrevStatus(String prevStatus) { this.prevStatus = prevStatus; }

    public String getAfterSaleReason() { return afterSaleReason; }
    public void setAfterSaleReason(String afterSaleReason) { this.afterSaleReason = afterSaleReason; }

    public Boolean getSellerPaid() { return sellerPaid; }
    public void setSellerPaid(Boolean sellerPaid) { this.sellerPaid = sellerPaid; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public String getReturnTrackingNumber() { return returnTrackingNumber; }
    public void setReturnTrackingNumber(String returnTrackingNumber) { this.returnTrackingNumber = returnTrackingNumber; }

    public LocalDateTime getAfterSaleAt() { return afterSaleAt; }
    public void setAfterSaleAt(LocalDateTime afterSaleAt) { this.afterSaleAt = afterSaleAt; }

    public LocalDateTime getReturnSentAt() { return returnSentAt; }
    public void setReturnSentAt(LocalDateTime returnSentAt) { this.returnSentAt = returnSentAt; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public String getReviewContent() { return reviewContent; }
    public void setReviewContent(String reviewContent) { this.reviewContent = reviewContent; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getShippedAt() { return shippedAt; }
    public void setShippedAt(LocalDateTime shippedAt) { this.shippedAt = shippedAt; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
}
