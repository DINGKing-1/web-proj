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

        // 彻底删除才级联清空业务数据
        adminService.purgeUser(user.getId());
        assertTrue(userRepository.findById(user.getId()).isEmpty());
        assertTrue(orderRepository.findByUsernameOrderByCreatedAtDesc("testuser").isEmpty());
        assertTrue(cartItemRepository.findByUsernameOrderByCreatedAtDesc("testuser").isEmpty());
        assertTrue(favoriteRepository.findByUsernameOrderByCreatedAtDesc("testuser").isEmpty());
        assertTrue(addressRepository.findByUsernameOrderByIsDefaultDescCreatedAtDesc("testuser").isEmpty());
        assertTrue(commentRepository.findByProductIdOrderByCreatedAtDesc(1L).isEmpty());
        assertTrue(chatRoomRepository.findByUsername("testuser").isEmpty());
    }
}
