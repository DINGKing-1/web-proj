package com.secondhand.mall.service;

import com.secondhand.mall.constant.OrderStatus;
import com.secondhand.mall.entity.Order;
import com.secondhand.mall.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 自动确认收货 / 自动完成（两段式，禁止跳状态直接打款）：
 * 1. shipped 超过 7 天（自发货起）→ 自动确认收货 received（缓冲，不打款）
 * 2. received 超过 7 天（自收货起）→ 自动完成 completed 并打款给卖家
 * 每单独立事务：单笔失败只回滚自己。
 */
@Service
public class OrderAutoCompleteService {

    private static final Logger log = LoggerFactory.getLogger(OrderAutoCompleteService.class);
    /** 发货后买家确认收货窗口 / 收货后评价缓冲期 */
    private static final int AUTO_DAYS = 7;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final NotificationService notificationService;
    private final TransactionTemplate txTemplate;

    public OrderAutoCompleteService(OrderRepository orderRepository,
                                    OrderService orderService,
                                    NotificationService notificationService,
                                    PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.notificationService = notificationService;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /** 每小时扫一次 */
    @Scheduled(fixedDelay = 3600_000L, initialDelay = 60_000L)
    public void sweep() {
        try {
            int n = autoCompleteOverdue();
            if (n > 0) {
                log.info("[AutoComplete] 自动处理 {} 笔超时订单", n);
            }
        } catch (Exception e) {
            log.warn("[AutoComplete] 扫描失败: {}", e.getMessage());
        }
    }

    public int autoCompleteOverdue() {
        int count = 0;
        // 1) 已发货超时 → 仅自动确认收货，进入缓冲期，禁止直接打款
        LocalDateTime shipDeadline = LocalDateTime.now().minusDays(AUTO_DAYS);
        for (Order o : orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.SHIPPED, shipDeadline)) {
            if (confirmReceivedSafe(o, shipDeadline)) count++;
        }
        // 2) 已收货超时缓冲期满 → 自动完成并打款
        LocalDateTime receivedDeadline = LocalDateTime.now().minusDays(AUTO_DAYS);
        for (Order o : orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.RECEIVED, receivedDeadline)) {
            if (completeSafe(o, receivedDeadline)) count++;
        }
        return count;
    }

    /** shipped → received（不打款） */
    private boolean confirmReceivedSafe(Order o, LocalDateTime deadline) {
        try {
            Boolean done = txTemplate.execute(status -> {
                Order order = orderRepository.findByIdForUpdate(o.getId()).orElse(null);
                if (order == null || !OrderStatus.SHIPPED.equals(order.getStatus())) {
                    return false;
                }
                LocalDateTime since = order.getShippedAt() != null ? order.getShippedAt() : order.getCreatedAt();
                if (since.isAfter(deadline)) {
                    return false;
                }
                order.setStatus(OrderStatus.RECEIVED);
                order.setReceivedAt(LocalDateTime.now());
                orderRepository.save(order);
                notificationService.push(order.getUsername(), "order", "已自动确认收货",
                        "「" + order.getProductName() + "」超时未确认，系统已自动确认收货，评价缓冲期后自动完成",
                        "order", String.valueOf(order.getId()));
                notificationService.push(order.getSellerUsername(), "order", "买家已确认收货",
                        "「" + order.getProductName() + "」系统已自动确认收货，缓冲期结束后结算货款",
                        "order", String.valueOf(order.getId()));
                return true;
            });
            return Boolean.TRUE.equals(done);
        } catch (Exception e) {
            log.warn("[AutoComplete] 订单 {} 自动确认收货失败: {}", o.getId(), e.getMessage());
            return false;
        }
    }

    /** received → completed + 打款 */
    private boolean completeSafe(Order o, LocalDateTime deadline) {
        try {
            Boolean done = txTemplate.execute(status -> {
                Order order = orderRepository.findByIdForUpdate(o.getId()).orElse(null);
                if (order == null || !OrderStatus.RECEIVED.equals(order.getStatus())) {
                    return false;
                }
                LocalDateTime since = order.getReceivedAt() != null ? order.getReceivedAt() : order.getCreatedAt();
                if (since.isAfter(deadline)) {
                    return false;
                }
                // 打款仅在 completed 路径；不得从 shipped 直接进来
                orderService.releaseToSeller(order);
                order.setStatus(OrderStatus.COMPLETED);
                orderRepository.save(order);
                notificationService.push(order.getUsername(), "order", "订单已自动完成",
                        "「" + order.getProductName() + "」评价缓冲期结束，订单已完成",
                        "order", String.valueOf(order.getId()));
                notificationService.push(order.getSellerUsername(), "order", "订单已结算",
                        "「" + order.getProductName() + "」货款已结算到账",
                        "order", String.valueOf(order.getId()));
                return true;
            });
            return Boolean.TRUE.equals(done);
        } catch (Exception e) {
            log.warn("[AutoComplete] 订单 {} 自动完成失败: {}", o.getId(), e.getMessage());
            return false;
        }
    }
}
