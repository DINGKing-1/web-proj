package com.secondhand.mall.service;

import com.secondhand.mall.constant.OrderStatus;
import com.secondhand.mall.constant.ProductStatus;
import com.secondhand.mall.entity.Order;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.repository.OrderRepository;
import com.secondhand.mall.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待付款订单超时自动关单（默认 30 分钟）。
 * 关单时回补库存、通知双方；未扣款故无需退款。
 */
@Service
public class OrderPaymentTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentTimeoutService.class);
    private static final int TIMEOUT_MINUTES = 30;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;

    public OrderPaymentTimeoutService(OrderRepository orderRepository,
                                      ProductRepository productRepository,
                                      NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.notificationService = notificationService;
    }

    /** 每 5 分钟扫一次超时未支付订单 */
    @Scheduled(fixedDelay = 300_000L, initialDelay = 90_000L)
    public void sweep() {
        try {
            int n = cancelExpiredPending();
            if (n > 0) {
                log.info("[PayTimeout] 自动关闭 {} 笔超时未支付订单", n);
            }
        } catch (Exception e) {
            log.warn("[PayTimeout] 扫描失败: {}", e.getMessage());
        }
    }

    @Transactional
    public int cancelExpiredPending() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        List<Order> expired = orderRepository.findByStatusAndCreatedAtBefore(
                OrderStatus.PENDING_PAYMENT, deadline);
        int count = 0;
        for (Order o : expired) {
            if (closeOne(o)) count++;
        }
        return count;
    }

    private boolean closeOne(Order o) {
        try {
            Order order = orderRepository.findByIdForUpdate(o.getId()).orElse(null);
            if (order == null || !OrderStatus.PENDING_PAYMENT.equals(order.getStatus())) {
                return false;
            }
            if (order.getCreatedAt() != null
                    && order.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES))) {
                return false;
            }

            int qty = order.getQuantity() != null && order.getQuantity() > 0 ? order.getQuantity() : 1;
            Product product = productRepository.findByIdForUpdate(order.getProductId()).orElse(null);
            if (product != null) {
                int restored = product.getQuantity() != null ? product.getQuantity() : 0;
                product.setQuantity(restored + qty);
                if (ProductStatus.SOLD.equals(product.getStatus())) {
                    product.setStatus(ProductStatus.ACTIVE);
                }
                productRepository.save(product);
            }

            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            notificationService.push(order.getUsername(), "order", "订单已自动关闭",
                    "「" + order.getProductName() + "」超时未支付，订单已关闭，库存已释放",
                    "order", String.valueOf(order.getId()));
            return true;
        } catch (Exception e) {
            log.warn("[PayTimeout] 订单 {} 关闭失败: {}", o.getId(), e.getMessage());
            return false;
        }
    }
}
