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
 * 售后超时兜底：
 * 1. after_sale 卖家超 72h 未处理 → 自动同意退货（不直接退款，货到再放钱）
 * 2. return_shipping 买家超 7 天未寄回（自同意退货起算）→ 自动关闭售后，回到申请前状态
 * 3. return_sent：不再超时自动退款。假运单/未确认收货不得触发退款+回补，
 *    退款与库存回补仅在卖家 confirmReturnReceived 确认收到退货后执行。
 * 每单独立事务：单笔失败只回滚自己，不拖垮整批、也不会半提交。
 */
@Service
public class OrderAfterSaleTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(OrderAfterSaleTimeoutService.class);
    private static final int SELLER_HANDLE_HOURS = 72;
    private static final int BUYER_SHIP_DAYS = 7;

    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final TransactionTemplate txTemplate;

    public OrderAfterSaleTimeoutService(OrderRepository orderRepository,
                                        NotificationService notificationService,
                                        PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.notificationService = notificationService;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /** 每 10 分钟扫一次 */
    @Scheduled(fixedDelay = 600_000L, initialDelay = 120_000L)
    public void sweep() {
        try {
            int n = autoApproveOverdue() + autoCloseUnshippedReturn();
            if (n > 0) {
                log.info("[AfterSaleTimeout] 自动处理 {} 笔超时售后", n);
            }
        } catch (Exception e) {
            log.warn("[AfterSaleTimeout] 扫描失败: {}", e.getMessage());
        }
    }

    /** 卖家超时未处理售后申请 → 自动同意退货 */
    public int autoApproveOverdue() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(SELLER_HANDLE_HOURS);
        List<Order> overdue = orderRepository.findOverdueAfterSale(deadline);
        int count = 0;
        for (Order o : overdue) {
            try {
                Boolean done = txTemplate.execute(status -> {
                    Order order = orderRepository.findByIdForUpdate(o.getId()).orElse(null);
                    if (order == null || !OrderStatus.AFTER_SALE.equals(order.getStatus())) return false;
                    LocalDateTime since = order.getAfterSaleAt() != null ? order.getAfterSaleAt() : order.getCreatedAt();
                    if (since.isAfter(deadline)) return false;

                    order.setStatus(OrderStatus.RETURN_SHIPPING);
                    // 自动同意同样要打寄回窗口起点，否则买家 7 天仍按申请时间起算
                    order.setReturnApprovedAt(LocalDateTime.now());
                    orderRepository.save(order);

                    notificationService.push(order.getUsername(), "order", "售后申请已超时自动通过",
                        "「" + order.getProductName() + "」卖家超时未处理，平台已同意退货，请尽快寄回并填写退货运单号",
                        "order", String.valueOf(order.getId()));
                    notificationService.push(order.getSellerUsername(), "order", "售后申请已自动同意",
                        "「" + order.getProductName() + "」买家售后申请超时未处理，平台已自动同意退货，请留意寄回包裹",
                        "order", String.valueOf(order.getId()));
                    return true;
                });
                if (Boolean.TRUE.equals(done)) count++;
            } catch (Exception e) {
                log.warn("[AfterSaleTimeout] 订单 {} 处理失败: {}", o.getId(), e.getMessage());
            }
        }
        return count;
    }

    /** 买家获准退货后超 7 天未寄回 → 自动关闭售后，回到申请前状态 */
    public int autoCloseUnshippedReturn() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(BUYER_SHIP_DAYS);
        List<Order> list = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.RETURN_SHIPPING, deadline);
        int count = 0;
        for (Order o : list) {
            try {
                Boolean done = txTemplate.execute(status -> {
                    Order order = orderRepository.findByIdForUpdate(o.getId()).orElse(null);
                    if (order == null || !OrderStatus.RETURN_SHIPPING.equals(order.getStatus())) return false;
                    // 已填运单则视为已寄出，不在这里关
                    if (order.getReturnTrackingNumber() != null && !order.getReturnTrackingNumber().isBlank()) return false;
                    // 寄回窗口自「同意退货」起算，而非申请时间——
                    // 否则卖家拖 72h 处理会把买家的 7 天寄回窗口吃掉 3 天
                    LocalDateTime since = order.getReturnApprovedAt() != null ? order.getReturnApprovedAt()
                            : order.getAfterSaleAt() != null ? order.getAfterSaleAt() : order.getCreatedAt();
                    if (since.isAfter(deadline)) return false;

                    String back = order.getPrevStatus();
                    if (back == null || back.isBlank() || OrderStatus.AFTER_SALE.equals(back)) {
                        back = OrderStatus.SHIPPED;
                    }
                    order.setStatus(back);
                    order.setPrevStatus(null);
                    order.setAfterSaleAt(null);
                    order.setReturnApprovedAt(null);
                    orderRepository.save(order);

                    notificationService.push(order.getUsername(), "order", "售后申请已自动关闭",
                        "「" + order.getProductName() + "」超时未寄回退货，售后申请已关闭，订单回到「" + cnStatus(back) + "」",
                        "order", String.valueOf(order.getId()));
                    notificationService.push(order.getSellerUsername(), "order", "售后申请已自动关闭",
                        "「" + order.getProductName() + "」买家超时未寄回，售后申请已关闭",
                        "order", String.valueOf(order.getId()));
                    return true;
                });
                if (Boolean.TRUE.equals(done)) count++;
            } catch (Exception e) {
                log.warn("[AfterSaleTimeout] 订单 {} 关闭售后失败: {}", o.getId(), e.getMessage());
            }
        }
        return count;
    }

    /** 状态中文名（通知文案用） */
    private static String cnStatus(String status) {
        return switch (status == null ? "" : status) {
            case "pending_payment" -> "待付款";
            case "unshipped" -> "未发货";
            case "shipped" -> "已发货";
            case "received" -> "待收货";
            case "completed" -> "已完成";
            case "cancelled" -> "已取消";
            case "after_sale" -> "售后中";
            case "return_shipping" -> "待寄回";
            case "return_sent" -> "已寄回";
            case "refunded" -> "已退款";
            default -> status == null ? "" : status;
        };
    }

    /**
     * 假运单/卖家未确认不得自动退款。
     * 历史上的「填运单 7 天自动退款 + 回补库存」已移除：货仍在买家侧时自动放款会造成资损。
     * 退款与库存回补的唯一出口是卖家 confirmReturnReceived；此处仅提醒卖家及时确认。
     */
    public int notifyUnconfirmedReturn() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(BUYER_SHIP_DAYS);
        List<Order> list = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.RETURN_SENT, deadline);
        int count = 0;
        for (Order o : list) {
            try {
                Boolean done = txTemplate.execute(status -> {
                    Order order = orderRepository.findByIdForUpdate(o.getId()).orElse(null);
                    if (order == null || !OrderStatus.RETURN_SENT.equals(order.getStatus())) return false;
                    LocalDateTime since = order.getReturnSentAt() != null ? order.getReturnSentAt() : order.getCreatedAt();
                    if (since.isAfter(deadline)) return false;
                    notificationService.push(order.getSellerUsername(), "order", "退货待确认提醒",
                        "「" + order.getProductName() + "」买家已寄回（运单 " + order.getReturnTrackingNumber()
                                + "），请确认收货后完成退款；平台不会自动退款",
                        "order", String.valueOf(order.getId()));
                    return true;
                });
                if (Boolean.TRUE.equals(done)) count++;
            } catch (Exception e) {
                log.warn("[AfterSaleTimeout] 订单 {} 退货提醒失败: {}", o.getId(), e.getMessage());
            }
        }
        return count;
    }
}
