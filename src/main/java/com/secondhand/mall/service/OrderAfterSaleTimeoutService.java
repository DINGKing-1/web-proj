package com.secondhand.mall.service;

import com.secondhand.mall.constant.OrderStatus;
import com.secondhand.mall.entity.Order;
import com.secondhand.mall.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 售后超时兜底：
 * 1. after_sale 卖家超 72h 未处理 → 自动同意退货（不直接退款，货到再放钱）
 * 2. return_shipping 买家超 7 天未寄回 → 自动关闭售后，回到申请前状态
 * 3. return_sent 卖家超 7 天未确认收货 → 自动退款（避免买家永久等待）
 */
@Service
public class OrderAfterSaleTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(OrderAfterSaleTimeoutService.class);
    private static final int SELLER_HANDLE_HOURS = 72;
    private static final int BUYER_SHIP_DAYS = 7;
    private static final int SELLER_CONFIRM_DAYS = 7;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final NotificationService notificationService;

    public OrderAfterSaleTimeoutService(OrderRepository orderRepository,
                                        OrderService orderService,
                                        NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.notificationService = notificationService;
    }

    /** 每 10 分钟扫一次 */
    @Scheduled(fixedDelay = 600_000L, initialDelay = 120_000L)
    public void sweep() {
        try {
            int n = autoApproveOverdue() + autoCloseUnshippedReturn() + autoRefundUnconfirmedReturn();
            if (n > 0) {
                log.info("[AfterSaleTimeout] 自动处理 {} 笔超时售后", n);
            }
        } catch (Exception e) {
            log.warn("[AfterSaleTimeout] 扫描失败: {}", e.getMessage());
        }
    }

    /** 卖家超时未处理售后申请 → 自动同意退货 */
    @Transactional
    public int autoApproveOverdue() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(SELLER_HANDLE_HOURS);
        List<Order> overdue = orderRepository.findOverdueAfterSale(deadline);
        int count = 0;
        for (Order o : overdue) {
            try {
                Order order = orderRepository.findByIdForUpdate(o.getId()).orElse(null);
                if (order == null || !OrderStatus.AFTER_SALE.equals(order.getStatus())) continue;
                LocalDateTime since = order.getAfterSaleAt() != null ? order.getAfterSaleAt() : order.getCreatedAt();
                if (since.isAfter(deadline)) continue;

                order.setStatus(OrderStatus.RETURN_SHIPPING);
                orderRepository.save(order);

                notificationService.push(order.getUsername(), "order", "售后申请已超时自动通过",
                    "「" + order.getProductName() + "」卖家超时未处理，平台已同意退货，请尽快寄回并填写退货运单号",
                    "order", String.valueOf(order.getId()));
                notificationService.push(order.getSellerUsername(), "order", "售后申请已自动同意",
                    "「" + order.getProductName() + "」买家售后申请超时未处理，平台已自动同意退货，请留意寄回包裹",
                    "order", String.valueOf(order.getId()));
                count++;
            } catch (Exception e) {
                log.warn("[AfterSaleTimeout] 订单 {} 处理失败: {}", o.getId(), e.getMessage());
            }
        }
        return count;
    }

    /** 买家获准退货后超 7 天未寄回 → 自动关闭售后，回到申请前状态 */
    @Transactional
    public int autoCloseUnshippedReturn() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(BUYER_SHIP_DAYS);
        List<Order> list = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.RETURN_SHIPPING, deadline);
        int count = 0;
        for (Order o : list) {
            try {
                Order order = orderRepository.findByIdForUpdate(o.getId()).orElse(null);
                if (order == null || !OrderStatus.RETURN_SHIPPING.equals(order.getStatus())) continue;
                // 已填运单则视为已寄出，不在这里关
                if (order.getReturnTrackingNumber() != null && !order.getReturnTrackingNumber().isBlank()) continue;
                LocalDateTime since = order.getAfterSaleAt() != null ? order.getAfterSaleAt() : order.getCreatedAt();
                if (since.isAfter(deadline)) continue;

                String back = order.getPrevStatus();
                if (back == null || back.isBlank() || OrderStatus.AFTER_SALE.equals(back)) {
                    back = OrderStatus.SHIPPED;
                }
                order.setStatus(back);
                order.setPrevStatus(null);
                order.setAfterSaleAt(null);
                orderRepository.save(order);

                notificationService.push(order.getUsername(), "order", "售后申请已自动关闭",
                    "「" + order.getProductName() + "」超时未寄回退货，售后申请已关闭，订单回到「" + back + "」",
                    "order", String.valueOf(order.getId()));
                notificationService.push(order.getSellerUsername(), "order", "售后申请已自动关闭",
                    "「" + order.getProductName() + "」买家超时未寄回，售后申请已关闭",
                    "order", String.valueOf(order.getId()));
                count++;
            } catch (Exception e) {
                log.warn("[AfterSaleTimeout] 订单 {} 关闭售后失败: {}", o.getId(), e.getMessage());
            }
        }
        return count;
    }

    /** 买家已寄回、卖家超 7 天未确认收货 → 自动退款 */
    @Transactional
    public int autoRefundUnconfirmedReturn() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(SELLER_CONFIRM_DAYS);
        List<Order> list = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.RETURN_SENT, deadline);
        int count = 0;
        for (Order o : list) {
            try {
                Order order = orderRepository.findByIdForUpdate(o.getId()).orElse(null);
                if (order == null || !OrderStatus.RETURN_SENT.equals(order.getStatus())) continue;
                LocalDateTime since = order.getReturnSentAt() != null ? order.getReturnSentAt() : order.getCreatedAt();
                if (since.isAfter(deadline)) continue;

                orderService.settleRefundAndMarkRefunded(order);

                notificationService.push(order.getUsername(), "order", "退货已自动退款",
                    "「" + order.getProductName() + "」卖家超时未确认收货，平台已自动退款",
                    "order", String.valueOf(order.getId()));
                notificationService.push(order.getSellerUsername(), "order", "退货已自动结算",
                    "「" + order.getProductName() + "」退货超时未确认，平台已自动退款给买家",
                    "order", String.valueOf(order.getId()));
                count++;
            } catch (Exception e) {
                log.warn("[AfterSaleTimeout] 订单 {} 自动退款失败: {}", o.getId(), e.getMessage());
            }
        }
        return count;
    }
}
