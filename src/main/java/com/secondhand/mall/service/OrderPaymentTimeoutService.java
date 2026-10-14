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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待付款订单超时自动关单（默认 30 分钟）。
 * 关单时回补库存、通知双方；未扣款故无需退款。
 * 每单独立事务：单笔失败只回滚自己，不拖垮整批、也不会半提交。
 */
@Service
public class OrderPaymentTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentTimeoutService.class);
    private static final int TIMEOUT_MINUTES = 30;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;
    private final StockService stockService;
    private final TransactionTemplate txTemplate;

    public OrderPaymentTimeoutService(OrderRepository orderRepository,
                                      ProductRepository productRepository,
                                      NotificationService notificationService,
                                      StockService stockService,
                                      PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.notificationService = notificationService;
        this.stockService = stockService;
        this.txTemplate = new TransactionTemplate(transactionManager);
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

    public int cancelExpiredPending() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        List<Order> expired = orderRepository.findByStatusAndCreatedAtBefore(
                OrderStatus.PENDING_PAYMENT, deadline);
        int count = 0;
        for (Order o : expired) {
            try {
                // 每单独立事务：异常在此抛出并回滚本单，catch 在事务外，不吞半提交
                Boolean done = txTemplate.execute(status -> closeOne(o));
                if (Boolean.TRUE.equals(done)) count++;
            } catch (Exception e) {
                log.warn("[PayTimeout] 订单 {} 关闭失败: {}", o.getId(), e.getMessage());
            }
        }
        return count;
    }

    private boolean closeOne(Order o) {
        Order order = orderRepository.findByIdForUpdate(o.getId()).orElse(null);
        if (order == null || !OrderStatus.PENDING_PAYMENT.equals(order.getStatus())) {
            return false;
        }
        if (order.getCreatedAt() != null
                && order.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES))) {
            return false;
        }

        int qty = order.getQuantity() != null && order.getQuantity() > 0 ? order.getQuantity() : 1;
        // StockService：行锁 + 版本号，防与并发下单/注销结算丢失更新
        stockService.restore(order.getProductId(), qty);

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        notificationService.push(order.getUsername(), "order", "订单已自动关闭",
                "「" + order.getProductName() + "」超时未支付，订单已关闭，库存已释放",
                "order", String.valueOf(order.getId()));
        return true;
    }
}
