package com.secondhand.mall.service;

import com.secondhand.mall.entity.Order;
import com.secondhand.mall.entity.Review;
import com.secondhand.mall.repository.OrderRepository;
import com.secondhand.mall.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public ReviewService(ReviewRepository reviewRepository, OrderRepository orderRepository,
                         OrderService orderService) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @Transactional
    public Review reviewOrder(Long orderId, String buyerUsername, Integer ratingGoods,
                              Integer ratingService, Integer ratingDelivery,
                              String content, String images, String videos) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        if (!order.getUsername().equals(buyerUsername)) {
            throw new RuntimeException("只有买家可以评价");
        }
        // 确认收货后（received / completed）才能评价
        if (!"received".equals(order.getStatus()) && !"completed".equals(order.getStatus())) {
            throw new RuntimeException("确认收货后才能评价");
        }
        if (order.getRating() != null || reviewRepository.existsByOrderId(orderId)) {
            throw new RuntimeException("该订单已评价");
        }
        checkStars("商品质量", ratingGoods);
        checkStars("卖家服务", ratingService);
        checkStars("物流速度", ratingDelivery);
        if (content != null && content.length() > 500) {
            throw new RuntimeException("评价内容不能超过 500 字");
        }

        int avg = (int) Math.round((ratingGoods + ratingService + ratingDelivery) / 3.0);

        Review review = new Review();
        review.setOrderId(orderId);
        review.setProductId(order.getProductId());
        review.setBuyerUsername(buyerUsername);
        review.setSellerUsername(order.getSellerUsername());
        review.setRating(avg);
        review.setRatingGoods(ratingGoods);
        review.setRatingService(ratingService);
        review.setRatingDelivery(ratingDelivery);
        review.setContent(content);
        review.setImages(images);
        review.setVideos(videos);
        try {
            review = reviewRepository.save(review);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // orderId 唯一索引兜底：并发重复评价
            throw new RuntimeException("该订单已评价");
        }

        order.setRating(avg);
        order.setReviewContent(content);
        order.setReviewedAt(LocalDateTime.now());
        if ("received".equals(order.getStatus())) {
            orderService.releaseToSeller(order);
            order.setStatus("completed");
        }
        orderRepository.save(order);

        return review;
    }

    private void checkStars(String label, Integer score) {
        if (score == null || score < 1 || score > 5) {
            throw new RuntimeException(label + "评分需在 1-5 星之间");
        }
    }

    /** 卖家回复自己商品的评价 */
    @Transactional
    public Review reply(Long reviewId, String sellerUsername, String content) {
        if (content == null || content.isBlank()) {
            throw new RuntimeException("回复内容不能为空");
        }
        String text = content.trim();
        if (text.length() > 500) {
            throw new RuntimeException("回复不能超过 500 字");
        }
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("评价不存在"));
        if (!review.getSellerUsername().equals(sellerUsername)) {
            throw new RuntimeException("只有该商品的卖家可以回复");
        }
        review.setReply(text);
        review.setRepliedAt(LocalDateTime.now());
        return reviewRepository.save(review);
    }

    /** 买家追评：一条评价最多追评一次 */
    @Transactional
    public Review followUp(Long reviewId, String buyerUsername, String content) {
        if (content == null || content.isBlank()) {
            throw new RuntimeException("追评内容不能为空");
        }
        String text = content.trim();
        if (text.length() > 500) {
            throw new RuntimeException("追评不能超过 500 字");
        }
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("评价不存在"));
        if (!review.getBuyerUsername().equals(buyerUsername)) {
            throw new RuntimeException("只有评价本人可以追评");
        }
        if (review.getFollowUpContent() != null && !review.getFollowUpContent().isBlank()) {
            throw new RuntimeException("该评价已追评过");
        }
        review.setFollowUpContent(text);
        review.setFollowUpAt(LocalDateTime.now());
        return reviewRepository.save(review);
    }

    public List<Review> byProduct(Long productId) {
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }

    public List<Review> bySeller(String sellerUsername) {
        return reviewRepository.findBySellerUsernameOrderByCreatedAtDesc(sellerUsername);
    }

    public Map<String, Object> sellerRating(String sellerUsername) {
        Double avg = reviewRepository.avgRatingBySeller(sellerUsername);
        long count = reviewRepository.countBySellerUsername(sellerUsername);
        Map<String, Object> map = new HashMap<>();
        map.put("avgRating", avg == null ? 0 : Math.round(avg * 10) / 10.0);
        map.put("reviewCount", count);
        return map;
    }

    public Double productAvgRating(Long productId) {
        Double avg = reviewRepository.avgRatingByProduct(productId);
        return avg == null ? null : Math.round(avg * 10) / 10.0;
    }
}
