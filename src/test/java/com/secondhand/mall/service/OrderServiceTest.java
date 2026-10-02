package com.secondhand.mall.service;

import com.secondhand.mall.entity.Order;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.OrderRepository;
import com.secondhand.mall.repository.ProductRepository;
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

        // 合法流转：unshipped -> shipped
        Order shipped = orderService.updateOrderStatus(order.getId(), "shipped", "seller1");
        assertEquals("shipped", shipped.getStatus());
    }

    @Test
    void testCancelOrder_SellerInsufficientBalance_ShouldThrow() {
        // 创建卖家，余额为0
        Product product = createTestProduct("seller1", 100.0, 1);
        User seller = createTestUser("seller1", 0.0);
        createTestUser("buyer1", 10000.0);

        // 创建订单（此时卖家余额会增加100）
        Order order = orderService.createOrder("buyer1", product.getId(), BigDecimal.valueOf(100.0),
                "Test Product", "electronics", "test.jpg",
                "Receiver", "13800000001", "Test Address", "balance");

        // 模拟卖家花掉所有余额
        seller.setBalance(BigDecimal.ZERO);
        userRepository.save(seller);

        // 尝试取消订单，卖家余额不足
        assertThrows(RuntimeException.class, () -> {
            orderService.cancelOrder(order.getId(), "buyer1");
        });
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

        // 创建订单
        Order order = orderService.createOrder("buyer1", product.getId(), BigDecimal.valueOf(100.0),
                "Test Product", "electronics", "test.jpg",
                "Receiver", "13800000001", "Test Address", "balance");

        // 完成订单
        orderService.updateOrderStatus(order.getId(), "shipped", "seller1");
        orderService.updateOrderStatus(order.getId(), "received", "buyer1");
        orderService.updateOrderStatus(order.getId(), "completed", "buyer1");

        // 删除已完成的订单
        assertDoesNotThrow(() -> {
            orderService.deleteOrder(order.getId(), "buyer1");
        });
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
}
