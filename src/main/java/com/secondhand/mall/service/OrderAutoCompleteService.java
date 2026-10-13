package com.secondhand.mall.service;

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
 * 自动确认收货 / 自动完成：
 * - shipped 超过 7 天 → 自动收货并完成（打款给卖家）
 * - received 超过 7 天 → 自动完成（打款给卖家）
 * 每单独立事务：单笔失败只回滚自己，不拖垮整批、也不会半提交。
 */
@Service
public class OrderAutoCompleteService {

    private static final Logger log = LoggerFactory.getLogger(OrderAutoCompleteService.class);
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
                log.info("[AutoComplete] 自动完结 {} 笔超时订单", n);
            }
        } catch (Exception e) {
            log.warn("[AutoComplete] 扫描失败: {}", e.getMessage());
        }
    }

    public int autoCompleteOverdue() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(AUTO_DAYS);
        List<Order> shipped = orderRepository.findByStatusAndCreatedAtBefore("shipped", deadline);
        List<Order> received = orderRepository.findByStatusAndCreatedAtBefore("received", deadline);
        int count = 0;
        for (Order o : shipped) {
            if (finishSafe(o, deadline, true, "买家超时未确认，系统自动确认收货")) count++;
        }
        for (Order o : received) {
            if (finishSafe(o, deadline, false, "买家超时未评价，系统自动完成")) count++;
        }
        return count;
    }

    private boolean finishSafe(Order o, LocalDateTime deadline, boolean fromShipped, String reason) {
        try {
            Boolean done = txTemplate.execute(status -> finish(o, deadline, fromShipped, reason));
            return Boolean.TRUE.equals(done);
        } catch (Exception e) {
            log.warn("[AutoComplete] 订单 {} 失败: {}", o.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * 逐单加锁并复核状态，防止与手动完成/取消并发时双重打款或脏写。
     * 超时按发货/收货时间判定，避免「下单很久才发货」的单被立刻自动完成。
     */
    private boolean finish(Order o, LocalDateTime deadline, boolean fromShipped, String reason) {
        Order order = orderRepository.findByIdForUpdate(o.getId()).orElse(null);
        if (order == null) return false;
        String expect = fromShipped ? "shipped" : "received";
        if (!expect.equals(order.getStatus())) {
            return false;
        }
        LocalDateTime since = fromShipped ? order.getShippedAt() : order.getReceivedAt();
        if (since == null) since = order.getCreatedAt();
        if (since.isAfter(deadline)) {
            return false;
        }
        orderService.releaseToSeller(order);
        order.setStatus("completed");
        orderRepository.save(order);
        notificationService.push(order.getUsername(), "order", "订单已自动完成",
            "「" + order.getProductName() + "」" + reason + "，货款已结算给卖家",
            "order", String.valueOf(order.getId()));
        notificationService.push(order.getSellerUsername(), "order", "订单已结算",
            "「" + order.getProductName() + "」" + reason + "，货款已到账",
            "order", String.valueOf(order.getId()));
        return true;
    }
}
