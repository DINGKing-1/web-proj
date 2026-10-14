package com.secondhand.mall.service;

import com.secondhand.mall.entity.*;
import com.secondhand.mall.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AdminServiceTest {

    @Autowired
    private AdminService adminService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private StockService stockService;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
        chatRoomRepository.deleteAll();
        commentRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        favoriteRepository.deleteAll();
        addressRepository.deleteAll();
        productRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createTestUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("password");
        user.setEmail(username + "@test.com");
        user.setPhone("13800000000");
        user.setNickname(username);
        user.setBalance(BigDecimal.valueOf(10000));
        user.setRole("user");
        return userRepository.save(user);
    }

    @Test
    void testDeleteUser_ShouldCascadeDeleteAllRelatedData() {
        // 创建用户
        User user = createTestUser("testuser");

        // 创建订单
        Order order = new Order();
        order.setOrderNumber("YPH20250101000001");
        order.setUsername("testuser");
        order.setProductId(1L);
        order.setProductName("Test Product");
        order.setProductPrice(BigDecimal.valueOf(100));
        order.setStatus("completed");
        order.setReceiverName("Test Receiver");
        order.setReceiverPhone("13800000000");
        order.setReceiverAddress("Test Address");
        order.setCategory("electronics");
        order.setImageUrl("test.jpg");
        order.setPaymentMethod("balance");
        orderRepository.save(order);

        // 创建购物车项
        CartItem cartItem = new CartItem();
        cartItem.setUsername("testuser");
        cartItem.setProductId(1L);
        cartItemRepository.save(cartItem);

        // 创建收藏
        Favorite favorite = new Favorite();
        favorite.setUsername("testuser");
        favorite.setProductId(1L);
        favoriteRepository.save(favorite);

        // 创建地址
        Address address = new Address();
        address.setUsername("testuser");
        address.setReceiverName("Test");
        address.setPhone("13800000000");
        address.setProvince("Test Province");
        address.setCity("Test City");
        address.setDistrict("Test District");
        address.setDetail("Test Detail");
        addressRepository.save(address);

        // 创建评论
        Comment comment = new Comment();
        comment.setUserName("testuser");
        comment.setProductId(1L);
        comment.setContent("Test comment");
        commentRepository.save(comment);

        // 创建聊天室
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setUser1Username("testuser");
        chatRoom.setUser2Username("otheruser");
        chatRoom.setProductId(1L);
        chatRoom.setLastMessage("");
        chatRoomRepository.save(chatRoom);

        // 创建聊天消息
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setRoomId(chatRoom.getId());
        chatMessage.setSenderUsername("testuser");
        chatMessage.setContent("Test message");
        chatMessageRepository.save(chatMessage);

        // 删除用户 = 软删除进回收站，业务数据保留以便追回
        adminService.deleteUser(user.getId());

        User deleted = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(deleted.getDeleted());
        // 软删除保留业务数据
        assertFalse(orderRepository.findByUsernameOrderByCreatedAtDesc("testuser").isEmpty());
        assertFalse(cartItemRepository.findByUsernameOrderByCreatedAtDesc("testuser").isEmpty());
        assertFalse(favoriteRepository.findByUsernameOrderByCreatedAtDesc("testuser").isEmpty());
        assertFalse(addressRepository.findByUsernameOrderByIsDefaultDescCreatedAtDesc("testuser").isEmpty());
        assertFalse(commentRepository.findByProductIdOrderByCreatedAtDesc(1L).isEmpty());
        assertFalse(chatRoomRepository.findByUsername("testuser").isEmpty());

        // 彻底删除：清空个人业务数据，但订单作为交易凭证保留并脱敏
        adminService.purgeUser(user.getId());
        assertTrue(userRepository.findById(user.getId()).isEmpty());
        List<Order> keptOrders = orderRepository.findAll();
        assertEquals(1, keptOrders.size(), "订单必须保留，不得物理删除");
        Order kept = keptOrders.get(0);
        assertEquals("已注销用户", kept.getUsername());
        assertEquals("已注销用户", kept.getReceiverName());
        assertEquals("", kept.getReceiverPhone());
        assertEquals("", kept.getReceiverAddress());
        assertEquals("completed", kept.getStatus(), "订单状态保持完整");
        assertEquals("Test Product", kept.getProductName(), "交易快照不得丢失");
        assertTrue(cartItemRepository.findByUsernameOrderByCreatedAtDesc("testuser").isEmpty());
        assertTrue(favoriteRepository.findByUsernameOrderByCreatedAtDesc("testuser").isEmpty());
        assertTrue(addressRepository.findByUsernameOrderByIsDefaultDescCreatedAtDesc("testuser").isEmpty());
        assertTrue(commentRepository.findByProductIdOrderByCreatedAtDesc(1L).isEmpty());
        assertTrue(chatRoomRepository.findByUsername("testuser").isEmpty());
    }

    /** B-03：彻底删买家后，卖家侧订单仍完整可见，仅买家隐私脱敏 */
    @Test
    void testPurgeUser_KeepsCounterpartyOrders_AndMasksPrivacy() {
        User buyer = createTestUser("buyer_gone");
        createTestUser("seller_keep");
        Order order = createOrder("buyer_gone", "completed", 1L, BigDecimal.valueOf(100));
        order.setSellerUsername("seller_keep");
        order.setProductName("Camera");
        order.setReceiverName("张三");
        order.setReceiverPhone("13800001111");
        order.setReceiverAddress("杭州市某小区");
        orderRepository.save(order);

        adminService.purgeUser(buyer.getId());

        List<Order> sellerView = orderRepository.findBySellerUsernameOrderByCreatedAtDesc("seller_keep");
        assertEquals(1, sellerView.size(), "卖家侧订单必须完整可见");
        Order o = sellerView.get(0);
        assertEquals("Camera", o.getProductName());
        assertEquals("completed", o.getStatus());
        assertEquals("seller_keep", o.getSellerUsername(), "未删卖家信息不得改动");
        assertEquals("已注销用户", o.getUsername());
        assertEquals("已注销用户", o.getReceiverName());
        assertEquals("", o.getReceiverPhone());
        assertEquals("", o.getReceiverAddress());
    }

    /** B-03：彻底删卖家后，买家侧订单仍完整可见，仅卖家名脱敏 */
    @Test
    void testPurgeSeller_KeepsBuyerOrders() {
        createTestUser("buyer_keep");
        User seller = createTestUser("seller_gone");
        Order order = createOrder("buyer_keep", "completed", 1L, BigDecimal.valueOf(100));
        order.setSellerUsername("seller_gone");
        order.setProductName("Book");
        order.setReceiverName("李四");
        order.setReceiverPhone("13800002222");
        order.setReceiverAddress("上海市某路");
        orderRepository.save(order);

        adminService.purgeUser(seller.getId());

        List<Order> buyerView = orderRepository.findByUsernameOrderByCreatedAtDesc("buyer_keep");
        assertEquals(1, buyerView.size(), "买家侧订单必须完整可见");
        Order o = buyerView.get(0);
        assertEquals("Book", o.getProductName());
        assertEquals("李四", o.getReceiverName(), "未删买家的收货信息不得清空");
        assertEquals("13800002222", o.getReceiverPhone());
        assertEquals("已注销用户", o.getSellerUsername(), "卖家名脱敏");
    }

    private Order createOrder(String username, String status, Long productId, BigDecimal price) {
        Order order = new Order();
        order.setOrderNumber("YPH" + System.nanoTime());
        order.setUsername(username);
        order.setProductId(productId);
        order.setProductName("Test Product");
        order.setProductPrice(price);
        order.setQuantity(1);
        order.setStatus(status);
        order.setReceiverName("Test Receiver");
        order.setReceiverPhone("13800000000");
        order.setReceiverAddress("Test Address");
        order.setCategory("electronics");
        order.setImageUrl("test.jpg");
        order.setPaymentMethod("balance");
        return orderRepository.save(order);
    }

    private Product createProduct(int quantity) {
        Product product = new Product();
        product.setName("Test Product");
        product.setCategory("electronics");
        product.setDescription("desc");
        product.setOriginalPrice(BigDecimal.valueOf(100));
        product.setSecondHandPrice(BigDecimal.valueOf(80));
        product.setSellerUsername("seller");
        product.setSellerName("seller");
        product.setItemCondition("9成新");
        product.setQuantity(quantity);
        product.setStatus("sold");
        return productRepository.save(product);
    }

    @Test
    void testSoftDelete_BlocksWhenBuyerHasShippedOrder() {
        User user = createTestUser("buyer_shipped");
        createOrder("buyer_shipped", "shipped", 1L, BigDecimal.valueOf(100));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> adminService.softDeleteUser(user, "self"));
        assertTrue(ex.getMessage().contains("已履约"));
        assertFalse(userRepository.findByUsername("buyer_shipped").orElseThrow().getDeleted());
    }

    @Test
    void testSoftDelete_BlocksWhenBuyerHasReceivedOrder() {
        User user = createTestUser("buyer_received");
        createOrder("buyer_received", "received", 1L, BigDecimal.valueOf(100));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> adminService.softDeleteUser(user, "admin"));
        assertTrue(ex.getMessage().contains("已履约"));
        assertFalse(userRepository.findByUsername("buyer_received").orElseThrow().getDeleted());
    }

    @Test
    void testSoftDelete_BlocksWhenBuyerHasAfterSaleOrder() {
        User user = createTestUser("buyer_after_sale");
        createOrder("buyer_after_sale", "after_sale", 1L, BigDecimal.valueOf(100));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> adminService.softDeleteUser(user, "self"));
        assertTrue(ex.getMessage().contains("已履约"));
    }

    @Test
    void testSoftDelete_BlocksWhenSellerHasShippedOrder() {
        User user = createTestUser("seller_shipped");
        Order order = createOrder("other_buyer", "shipped", 1L, BigDecimal.valueOf(100));
        order.setSellerUsername("seller_shipped");
        orderRepository.save(order);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> adminService.softDeleteUser(user, "admin"));
        assertTrue(ex.getMessage().contains("已履约"));
    }

    @Test
    void testSoftDelete_UnshippedOrder_RefundsBuyerAndRestoresStock() {
        User buyer = createTestUser("buyer_unshipped");
        Product product = createProduct(0);
        Order order = createOrder("buyer_unshipped", "unshipped", product.getId(), BigDecimal.valueOf(100));
        order.setSellerUsername("seller");
        order.setSellerPaid(true);
        User seller = createTestUser("seller");
        seller.setBalance(BigDecimal.valueOf(50));
        userRepository.save(seller);
        orderRepository.save(order);

        BigDecimal buyerBefore = buyer.getBalance();
        adminService.softDeleteUser(buyer, "self");

        User after = userRepository.findByUsername("buyer_unshipped").orElseThrow();
        assertTrue(after.getDeleted());
        assertEquals(0, after.getBalance().compareTo(buyerBefore.add(BigDecimal.valueOf(100))));
        Order settled = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals("cancelled", settled.getStatus());
        Product restored = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(1, restored.getQuantity());
        assertEquals("active", restored.getStatus());
    }

    @Test
    void testSoftDelete_PendingPayment_ClosesWithoutRefund() {
        User buyer = createTestUser("buyer_pending");
        Product product = createProduct(0);
        createOrder("buyer_pending", "pending_payment", product.getId(), BigDecimal.valueOf(100));

        BigDecimal buyerBefore = buyer.getBalance();
        adminService.softDeleteUser(buyer, "self");

        User after = userRepository.findByUsername("buyer_pending").orElseThrow();
        assertTrue(after.getDeleted());
        assertEquals(0, after.getBalance().compareTo(buyerBefore), "待付款不应退款");
        Product restored = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(1, restored.getQuantity());
    }

    @Test
    void testSoftDelete_DoesNotRefundShippedEvenIfSettleRuns() {
        // 即使绕过前置拦截，结算逻辑也不得对已发货订单退款
        User buyer = createTestUser("buyer_no_refund");
        createOrder("buyer_no_refund", "shipped", 1L, BigDecimal.valueOf(100));
        BigDecimal before = buyer.getBalance();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> adminService.assertNoFulfilledOrders("buyer_no_refund"));
        assertTrue(ex.getMessage().contains("已履约"));
        assertEquals(0, userRepository.findByUsername("buyer_no_refund").orElseThrow()
                .getBalance().compareTo(before));
        assertEquals("shipped", orderRepository.findByUsernameOrderByCreatedAtDesc("buyer_no_refund")
                .get(0).getStatus());
    }

    /** B-05：并发回补库存须串行化，最终数量准确、不超卖不少卖 */
    @Test
    void testConcurrentStockRestore_IsAccurate() throws Exception {
        Product product = createProduct(0);
        int threads = 8;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.atomic.AtomicInteger errors = new java.util.concurrent.atomic.AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    stockService.restore(product.getId(), 1);
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS), "并发回补应结束");
        pool.shutdownNow();
        assertEquals(0, errors.get(), "并发回补不应报错");
        Product after = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(threads, after.getQuantity(), "并发回补后库存应精确等于回补次数");
    }
}
