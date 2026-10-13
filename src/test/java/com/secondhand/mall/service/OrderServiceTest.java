package com.secondhand.mall.service;

import com.secondhand.mall.entity.Coupon;
import com.secondhand.mall.entity.Order;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.entity.UserCoupon;
import com.secondhand.mall.repository.CouponRepository;
import com.secondhand.mall.repository.OrderRepository;
import com.secondhand.mall.repository.ProductRepository;
import com.secondhand.mall.repository.UserCouponRepository;
import com.secondhand.mall.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private OrderAfterSaleTimeoutService afterSaleTimeoutService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private Product createTestProduct(String sellerUsername, double price, int quantity) {
        Product product = new Product();
        product.setName("Test Product");
        product.setCategory("electronics");
        product.setDescription("Test");
        product.setOriginalPrice(BigDecimal.valueOf(price * 2));
        product.setSecondHandPrice(BigDecimal.valueOf(price));
        product.setItemCondition("good");
        product.setSellerName("Seller");
        product.setSellerPhone("13800000000");
        product.setSellerUsername(sellerUsername);
        product.setImageUrl("test.jpg");
        product.setQuantity(quantity);
        product.setStatus("active");
        return productRepository.save(product);
    }

    private User createTestUser(String username, double balance) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("password");
        user.setEmail(username + "@test.com");
        user.setPhone("13800000000");
        user.setNickname(username);
        user.setBalance(BigDecimal.valueOf(balance));
        return userRepository.save(user);
    }

    /** 下单并余额支付（新流程：pending_payment → unshipped） */
    private Order createAndPay(String buyer, Long productId, double price, String productName, int qty) {
        Order order = orderService.createOrder(buyer, productId, BigDecimal.valueOf(price),
                productName, "electronics", "test.jpg",
                "Receiver", "13800000001", "Test Address", "balance", qty);
        return orderService.payOrder(order.getId(), buyer);
    }

    @Test
    void testConcurrentOrderCreation_ShouldPreventOverselling() throws InterruptedException {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);
        createTestUser("buyer2", 10000.0);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int i = 1; i <= 2; i++) {
            final String buyerName = "buyer" + i;
            executor.submit(() -> {
                try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    transactionTemplate.executeWithoutResult(status -> {
                        orderService.createOrder(buyerName, product.getId(), BigDecimal.valueOf(100.0),
                                "Test Product", "electronics", "test.jpg",
                                "Receiver", "13800000001", "Test Address",
                                "balance");
                    });
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Threads should finish within timeout");

        assertEquals(1, successCount.get(), "Exactly one order should succeed");
        assertEquals(1, failCount.get(), "Exactly one order should fail");

        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertEquals("sold", updated.getStatus());
        assertEquals(0, updated.getQuantity());

        executor.shutdown();
    }

    @Test
    void testCreateOrdersBatch_AllOrNothing() {
        Product p1 = createTestProduct("seller1", 50.0, 5);
        Product p2 = createTestProduct("seller2", 30.0, 5);
        createTestUser("seller1", 0.0);
        createTestUser("seller2", 0.0);
        createTestUser("buyer1", 100.0);

        var items = List.of(
                new OrderService.OrderItemRequest(p1.getId(), new BigDecimal("50.00"), "Test Product", "electronics", "test.jpg", 1),
                new OrderService.OrderItemRequest(p2.getId(), new BigDecimal("30.00"), "Test Product", "electronics", "test.jpg", 1),
                // 第三条价格不一致，应导致整批失败
                new OrderService.OrderItemRequest(p1.getId(), new BigDecimal("99.00"), "Test Product", "electronics", "test.jpg", 1)
        );

        assertThrows(RuntimeException.class, () ->
                orderService.createOrders("buyer1", items, "R", "138", "Addr", "balance", List.of()));

        assertEquals(0, orderRepository.count(), "batch should roll back all orders");
        User buyer = userRepository.findByUsername("buyer1").orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(buyer.getBalance()), "balance should be unchanged");
    }

    @Test
    void testCreateOrdersBatch_Success() {
        Product p1 = createTestProduct("seller1", 50.0, 5);
        Product p2 = createTestProduct("seller2", 30.0, 5);
        createTestUser("seller1", 0.0);
        createTestUser("seller2", 0.0);
        createTestUser("buyer1", 100.0);

        var items = List.of(
                new OrderService.OrderItemRequest(p1.getId(), new BigDecimal("50.00"), "Test Product", "electronics", "test.jpg", 1),
                new OrderService.OrderItemRequest(p2.getId(), new BigDecimal("30.00"), "Test Product", "electronics", "test.jpg", 1)
        );

        var orders = orderService.createOrders("buyer1", items, "R", "138", "Addr", "balance", List.of());
        assertEquals(2, orders.size());
        assertEquals(2, orderRepository.count());
        // 下单占库存但不扣款
        User afterCreate = userRepository.findByUsername("buyer1").orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(afterCreate.getBalance()),
                "下单未支付前不应扣款");
        for (Order o : orders) {
            orderService.payOrder(o.getId(), "buyer1");
        }
        User buyer = userRepository.findByUsername("buyer1").orElseThrow();
        assertEquals(0, new BigDecimal("20.00").compareTo(buyer.getBalance()));
    }
    @Test
    void testInvalidStateTransition_ShouldThrowException() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);

        // 创建订单
        Order order = orderService.createOrder("buyer1", product.getId(), BigDecimal.valueOf(100.0),
                "Test Product", "electronics", "test.jpg",
                "Receiver", "13800000001", "Test Address", "balance");

        // 尝试从unshipped直接跳到completed（非法流转）
        assertThrows(RuntimeException.class, () -> {
            orderService.updateOrderStatus(order.getId(), "completed", "buyer1");
        });
    }

    @Test
    void testValidStateTransition_ShouldSucceed() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);

        // 创建订单
        Order order = orderService.createOrder("buyer1", product.getId(), BigDecimal.valueOf(100.0),
                "Test Product", "electronics", "test.jpg",
                "Receiver", "13800000001", "Test Address", "balance");

        // 合法流转：支付后 unshipped -> shipped
        orderService.payOrder(order.getId(), "buyer1");
        Order shipped = orderService.updateOrderStatus(order.getId(), "shipped", "seller1");
        assertEquals("shipped", shipped.getStatus());
    }

    @Test
    void testCancelOrder_Escrow_SellerNotCharged() {
        // 创建卖家，余额为0
        Product product = createTestProduct("seller1", 100.0, 1);
        User seller = createTestUser("seller1", 0.0);
        createTestUser("buyer1", 10000.0);

        // 创建订单并支付（资金托管：买家扣款，卖家尚未收款）
        Order order = createAndPay("buyer1", product.getId(), 100.0, "Test Product", 1);

        // 模拟卖家花掉所有余额
        seller.setBalance(BigDecimal.ZERO);
        userRepository.save(seller);

        // 取消订单：买家全额退款；卖家本就未收款，不应扣成负数
        assertDoesNotThrow(() -> {
            orderService.cancelOrder(order.getId(), "buyer1");
        });

        User buyerAfter = userRepository.findByUsername("buyer1").orElseThrow();
        assertEquals(0, new BigDecimal("10000.00").compareTo(buyerAfter.getBalance()), "买家应全额退款");
        User sellerAfter = userRepository.findByUsername("seller1").orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(sellerAfter.getBalance()), "托管下卖家未收款，取消不应扣款");
        Order cancelled = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("cancelled", cancelled.getStatus());
    }

    @Test
    void testCancelPendingPayment_DoesNotRefund() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 0.0);
        createTestUser("buyer1", 10000.0);

        // 只下单不支付：pending_payment 尚未扣款
        Order order = orderService.createOrder("buyer1", product.getId(), BigDecimal.valueOf(100.0),
                "Test Product", "electronics", "test.jpg",
                "Receiver", "13800000001", "Test Address", "balance", 1);

        // 通过状态更新路径取消（曾错误地无条件 settleRefund）
        orderService.updateOrderStatus(order.getId(), "cancelled", "buyer1");

        User buyerAfter = userRepository.findByUsername("buyer1").orElseThrow();
        assertEquals(0, new BigDecimal("10000.00").compareTo(buyerAfter.getBalance()),
                "未支付订单取消不得退款");
        Order cancelled = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("cancelled", cancelled.getStatus());
        // 库存已回补
        Product p = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(1, p.getQuantity());
    }

    @Test
    void testAdminForceCancelPendingPayment_DoesNotRefund() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 0.0);
        createTestUser("buyer1", 10000.0);

        Order order = orderService.createOrder("buyer1", product.getId(), BigDecimal.valueOf(100.0),
                "Test Product", "electronics", "test.jpg",
                "Receiver", "13800000001", "Test Address", "balance", 1);

        orderService.adminForceCancel(order.getId());

        User buyerAfter = userRepository.findByUsername("buyer1").orElseThrow();
        assertEquals(0, new BigDecimal("10000.00").compareTo(buyerAfter.getBalance()),
                "管理员强制取消未支付订单也不得退款");
        Order cancelled = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("cancelled", cancelled.getStatus());
    }

    @Test
    void testDeleteOrder_ActiveOrder_ShouldThrow() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);

        // 创建订单
        Order order = orderService.createOrder("buyer1", product.getId(), BigDecimal.valueOf(100.0),
                "Test Product", "electronics", "test.jpg",
                "Receiver", "13800000001", "Test Address", "balance");

        // 尝试删除活跃订单
        assertThrows(RuntimeException.class, () -> {
            orderService.deleteOrder(order.getId(), "buyer1");
        });
    }

    @Test
    void testDeleteOrder_CompletedOrder_ShouldSucceed() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);

        // 创建并支付订单
        Order order = createAndPay("buyer1", product.getId(), 100.0, "Test Product", 1);

        // 完成订单
        orderService.updateOrderStatus(order.getId(), "shipped", "seller1");
        orderService.updateOrderStatus(order.getId(), "received", "buyer1");
        orderService.updateOrderStatus(order.getId(), "completed", "buyer1");

        // 删除已完成的订单
        assertDoesNotThrow(() -> {
            orderService.deleteOrder(order.getId(), "buyer1");
        });
    }

    /** 回归：经 PUT 状态机把订单改成 completed，也必须给卖家打款 */
    @Test
    void testUpdateOrderStatus_Completed_ShouldPaySeller() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);

        Order order = createAndPay("buyer1", product.getId(), 100.0, "Test Product", 1);

        orderService.updateOrderStatus(order.getId(), "shipped", "seller1");
        orderService.updateOrderStatus(order.getId(), "received", "buyer1");
        Order done = orderService.updateOrderStatus(order.getId(), "completed", "buyer1");

        User seller = userRepository.findByUsername("seller1").orElseThrow();
        assertEquals(0, new BigDecimal("10100.00").compareTo(seller.getBalance()),
                "卖家完成订单后应收到货款");
        assertTrue(Boolean.TRUE.equals(done.getSellerPaid()));
    }

    /** 回归：非买家不能把他人订单改成 cancelled */
    @Test
    void testUpdateOrderStatus_Cancelled_RequiresBuyer() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);
        createTestUser("attacker", 10000.0);

        Order order = createAndPay("buyer1", product.getId(), 100.0, "Test Product", 1);
        orderService.updateOrderStatus(order.getId(), "shipped", "seller1");
        orderService.applyAfterSale(order.getId(), "buyer1", "不想要了");

        assertThrows(RuntimeException.class,
                () -> orderService.updateOrderStatus(order.getId(), "cancelled", "attacker"));
    }

    /** 回归：多数量下单与取消，金额和库存都必须按数量计算 */
    @Test
    void testCancelOrder_MultiQuantity_RefundAndRestockByQuantity() {
        Product product = createTestProduct("seller1", 100.0, 5);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);

        Order order = createAndPay("buyer1", product.getId(), 100.0, "Test Product", 3);

        User buyerAfterBuy = userRepository.findByUsername("buyer1").orElseThrow();
        assertEquals(0, new BigDecimal("9700.00").compareTo(buyerAfterBuy.getBalance()),
                "3 件应扣 300");
        Product afterBuy = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(2, afterBuy.getQuantity(), "库存应剩 2");

        orderService.cancelOrder(order.getId(), "buyer1");

        User buyer = userRepository.findByUsername("buyer1").orElseThrow();
        assertEquals(0, new BigDecimal("10000.00").compareTo(buyer.getBalance()),
                "取消应按 3 件退 300");
        Product restored = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(5, restored.getQuantity(), "库存应恢复 3 件");
    }

    @Test
    void testGetSellerOrders_ShouldReturnOrders() {
        // 创建卖家和商品
        Product product1 = createTestProduct("seller1", 100.0, 1);
        Product product2 = createTestProduct("seller1", 200.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);
        createTestUser("buyer2", 10000.0);

        // 创建订单
        orderService.createOrder("buyer1", product1.getId(), BigDecimal.valueOf(100.0),
                "Test Product 1", "electronics", "test.jpg",
                "Receiver", "13800000001", "Test Address", "balance");
        orderService.createOrder("buyer2", product2.getId(), BigDecimal.valueOf(200.0),
                "Test Product 2", "electronics", "test.jpg",
                "Receiver", "13800000002", "Test Address", "balance");

        List<Order> orders = orderService.getSellerOrders("seller1", null);

        assertEquals(2, orders.size());
    }

    /**
     * 回归：接受出价走协商价下单。原实现传 offerPrice 会撞上「价格不一致」校验，
     * 且订单金额被记成挂牌价，买家会被多扣。
     */
    @Test
    void testNegotiatedOrder_ShouldChargeOfferPriceNotListPrice() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);

        Order order = orderService.createNegotiatedOrder("buyer1", product.getId(),
                new BigDecimal("80.00"),
                "Test Product", "electronics", "test.jpg",
                "Receiver", "13800000001", "Test Address", "balance", 1);

        assertEquals(0, new BigDecimal("80.00").compareTo(order.getProductPrice()),
                "订单金额应为协商价 80");

        orderService.payOrder(order.getId(), "buyer1");
        User buyer = userRepository.findByUsername("buyer1").orElseThrow();
        assertEquals(0, new BigDecimal("9920.00").compareTo(buyer.getBalance()),
                "支付应扣协商价 80，而非挂牌价 100");
    }

    /** 议价不得高于当前售价，否则等于变相涨价 */
    @Test
    void testNegotiatedOrder_PriceAboveListPrice_ShouldReject() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);

        assertThrows(RuntimeException.class, () -> orderService.createNegotiatedOrder(
                "buyer1", product.getId(), new BigDecimal("150.00"),
                "Test Product", "electronics", "test.jpg",
                "Receiver", "13800000001", "Test Address", "balance", 1));
    }

    /** 普通下单传错价仍要被拦（防篡改不能因为重构丢掉） */
    @Test
    void testCreateOrder_WrongPrice_ShouldReject() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);

        assertThrows(RuntimeException.class, () -> orderService.createOrder(
                "buyer1", product.getId(), new BigDecimal("0.01"),
                "Test Product", "electronics", "test.jpg",
                "Receiver", "13800000001", "Test Address", "balance", 1));
    }

    /** 审核未通过（待审/驳回/下架）的商品一律不能下单 */
    @Test
    void testCreateOrder_ProductNotActive_ShouldReject() {
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);

        for (String status : new String[]{"pending", "rejected", "inactive"}) {
            Product product = createTestProduct("seller1", 100.0, 1);
            product.setStatus(status);
            Product finalProduct = productRepository.save(product);

            assertThrows(RuntimeException.class, () -> orderService.createOrder(
                    "buyer1", finalProduct.getId(), BigDecimal.valueOf(100.0),
                    "Test Product", "electronics", "test.jpg",
                    "Receiver", "13800000001", "Test Address", "balance", 1),
                    "状态为 " + status + " 的商品不应可下单");
        }
    }

    /**
     * 退货售后全链路：申请 → 卖家同意退货（此时不退款）→ 买家寄回填单号
     * → 卖家确认收货 → 退款 + 库存回补。
     * 回归点：老实现是「同意即退款」，钱货两空。
     */
    @Test
    void testAfterSaleReturnFlow_RefundOnlyAfterReturnReceived() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);

        Order order = createAndPay("buyer1", product.getId(), 100.0, "Test Product", 1);
        orderService.updateOrderStatus(order.getId(), "shipped", "seller1");

        orderService.applyAfterSale(order.getId(), "buyer1", "商品与描述不符");
        Order afterApply = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("after_sale", afterApply.getStatus());

        orderService.approveAfterSale(order.getId(), "seller1");
        Order afterApprove = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("return_shipping", afterApprove.getStatus(), "同意后应进入待买家寄回");
        User buyerBeforeReturn = userRepository.findByUsername("buyer1").orElseThrow();
        assertEquals(0, new BigDecimal("9900.00").compareTo(buyerBeforeReturn.getBalance()),
                "货还没回来，不能先退款");

        orderService.submitReturnTracking(order.getId(), "buyer1", "SF1234567890");
        Order afterShip = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("return_sent", afterShip.getStatus());
        assertEquals("SF1234567890", afterShip.getReturnTrackingNumber());

        orderService.confirmReturnReceived(order.getId(), "seller1");
        Order afterConfirm = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("refunded", afterConfirm.getStatus());
        User buyerAfterRefund = userRepository.findByUsername("buyer1").orElseThrow();
        assertEquals(0, new BigDecimal("10000.00").compareTo(buyerAfterRefund.getBalance()),
                "确认收货后应退回 100");
        Product restored = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(1, restored.getQuantity(), "退货后库存应回补");
    }

    /** 未寄回前买家可撤销售后，订单回到申请前状态 */
    @Test
    void testCancelAfterSale_BeforeShipping_RestoresPreviousStatus() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);

        Order order = createAndPay("buyer1", product.getId(), 100.0, "Test Product", 1);
        orderService.updateOrderStatus(order.getId(), "shipped", "seller1");
        orderService.applyAfterSale(order.getId(), "buyer1", "不想要了");

        orderService.cancelAfterSale(order.getId(), "buyer1");
        Order restored = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("shipped", restored.getStatus());
        assertNull(restored.getAfterSaleAt());
    }

    /**
     * 回归：售后中禁止走 PUT /api/orders/{id} 直改 cancelled 套取全款。
     * 买家必须走 cancelAfterSale（不退钱）或确认收货退货后才退款。
     */
    @Test
    void testAfterSale_CannotForceCancelForRefund() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);

        Order order = createAndPay("buyer1", product.getId(), 100.0, "Test Product", 1);
        orderService.updateOrderStatus(order.getId(), "shipped", "seller1");
        orderService.applyAfterSale(order.getId(), "buyer1", "想白嫖");
        orderService.approveAfterSale(order.getId(), "seller1");
        orderService.submitReturnTracking(order.getId(), "buyer1", "FAKE123");

        // 售后已寄回：直改 cancelled 必须被拒绝
        assertThrows(RuntimeException.class,
                () -> orderService.updateOrderStatus(order.getId(), "cancelled", "buyer1"),
                "售后中订单禁止直改 cancelled");

        // after_sale 阶段同样禁止（用另一件商品，避免复用已售罄库存）
        Product product2 = createTestProduct("seller1", 100.0, 1);
        Order order2 = createAndPay("buyer1", product2.getId(), 100.0, "Test Product2", 1);
        orderService.updateOrderStatus(order2.getId(), "shipped", "seller1");
        orderService.applyAfterSale(order2.getId(), "buyer1", "再试一次");
        assertThrows(RuntimeException.class,
                () -> orderService.updateOrderStatus(order2.getId(), "cancelled", "buyer1"));
        // 也不允许打回 shipped/received 抹掉售后上下文
        assertThrows(RuntimeException.class,
                () -> orderService.updateOrderStatus(order2.getId(), "shipped", "seller1"));
        assertThrows(RuntimeException.class,
                () -> orderService.updateOrderStatus(order2.getId(), "received", "buyer1"));

        // 钱仍在托管：两笔实付共 200，买家余额应仍是 9800（未被套现退回）
        User buyer = userRepository.findByUsername("buyer1").orElseThrow();
        assertEquals(0, new BigDecimal("9800.00").compareTo(buyer.getBalance()),
                "售后套现路径不得把钱退回买家");
    }

    /** 回归：return_shipping 禁止直改 received 再 completed 打款给卖家 */
    @Test
    void testReturnShipping_CannotSkipReturnToComplete() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);

        Order order = createAndPay("buyer1", product.getId(), 100.0, "Test Product", 1);
        orderService.updateOrderStatus(order.getId(), "shipped", "seller1");
        orderService.applyAfterSale(order.getId(), "buyer1", "退货");
        orderService.approveAfterSale(order.getId(), "seller1");

        assertThrows(RuntimeException.class,
                () -> orderService.updateOrderStatus(order.getId(), "received", "buyer1"),
                "待寄回阶段不能直接确认收货");
        assertThrows(RuntimeException.class,
                () -> orderService.updateOrderStatus(order.getId(), "completed", "buyer1"),
                "待寄回阶段不能直接完成打款");

        // 卖家此时未收款
        User seller = userRepository.findByUsername("seller1").orElseThrow();
        assertEquals(0, new BigDecimal("10000.00").compareTo(seller.getBalance()),
                "售后未完结不得给卖家打款");
    }

    /** 卖家超时未处理的售后由定时任务兜底同意退货 */
    @Test
    void testAfterSaleTimeout_AutoApprovesOverdue() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);

        Order order = createAndPay("buyer1", product.getId(), 100.0, "Test Product", 1);
        orderService.updateOrderStatus(order.getId(), "shipped", "seller1");
        Order applied = orderService.applyAfterSale(order.getId(), "buyer1", "尺寸不合适");
        // 把申请时间推到 4 天前，超过 72 小时门槛
        applied.setAfterSaleAt(java.time.LocalDateTime.now().minusDays(4));
        orderRepository.save(applied);

        int n = afterSaleTimeoutService.autoApproveOverdue();
        assertTrue(n >= 1, "超时售后应被自动处理");
        Order after = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("return_shipping", after.getStatus());
    }

    /**
     * 优惠券：支付按实付扣款、订单记录抵扣额；取消后钱全额退回且券回到可用。
     * 回归点：退款若按原价算，平台会多退钱。
     */
    @Test
    void testPayWithCoupon_DiscountAndReleaseOnCancel() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);

        Coupon coupon = new Coupon();
        coupon.setCode("SAVE20");
        coupon.setName("满50减20");
        coupon.setDiscountAmount(new BigDecimal("20.00"));
        coupon.setMinAmount(new BigDecimal("50.00"));
        coupon.setStartAt(java.time.LocalDateTime.now().minusDays(1));
        coupon.setEndAt(java.time.LocalDateTime.now().plusDays(30));
        coupon.setEnabled(true);
        couponRepository.save(coupon);

        UserCoupon userCoupon = couponService.claim("buyer1", coupon.getId());

        Order order = orderService.createOrder("buyer1", product.getId(), BigDecimal.valueOf(100.0),
                "Test Product", "electronics", "test.jpg",
                "Receiver", "13800000001", "Test Address", "balance", 1);

        orderService.payOrder(order.getId(), "buyer1", userCoupon.getId());

        Order paid = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("20.00").compareTo(paid.getDiscountAmount()), "应记录 20 元抵扣");
        User buyer = userRepository.findByUsername("buyer1").orElseThrow();
        assertEquals(0, new BigDecimal("9920.00").compareTo(buyer.getBalance()),
                "100 元商品用券后实付 80");

        orderService.cancelOrder(order.getId(), "buyer1");
        User afterCancel = userRepository.findByUsername("buyer1").orElseThrow();
        assertEquals(0, new BigDecimal("10000.00").compareTo(afterCancel.getBalance()),
                "取消应按实付 80 退款，不是按原价 100");
        UserCoupon released = userCouponRepository.findById(userCoupon.getId()).orElseThrow();
        assertEquals("unused", released.getStatus(), "取消后券应退回可用");
        assertNull(released.getUsedOrderId());
    }

    /** 抵扣不能超过订单金额 */
    @Test
    void testPayWithCoupon_DiscountCannotExceedOrderAmount() {
        Product product = createTestProduct("seller1", 100.0, 1);
        createTestUser("seller1", 10000.0);
        createTestUser("buyer1", 10000.0);

        Coupon coupon = new Coupon();
        coupon.setCode("BIG500");
        coupon.setName("满0减500");
        coupon.setDiscountAmount(new BigDecimal("500.00"));
        coupon.setMinAmount(new BigDecimal("0.00"));
        coupon.setStartAt(java.time.LocalDateTime.now().minusDays(1));
        coupon.setEndAt(java.time.LocalDateTime.now().plusDays(30));
        coupon.setEnabled(true);
        couponRepository.save(coupon);
        UserCoupon userCoupon = couponService.claim("buyer1", coupon.getId());

        Order order = orderService.createOrder("buyer1", product.getId(), BigDecimal.valueOf(100.0),
                "Test Product", "electronics", "test.jpg",
                "Receiver", "13800000001", "Test Address", "balance", 1);

        orderService.payOrder(order.getId(), "buyer1", userCoupon.getId());
        Order paid = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(paid.getDiscountAmount()),
                "抵扣最多只能等于订单金额，不能白送");
        User buyer = userRepository.findByUsername("buyer1").orElseThrow();
        assertEquals(0, new BigDecimal("10000.00").compareTo(buyer.getBalance()),
                "实付应为 0");
    }
}
