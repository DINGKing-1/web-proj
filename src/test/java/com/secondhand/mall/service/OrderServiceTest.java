package com.secondhand.mall.service;

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
}
