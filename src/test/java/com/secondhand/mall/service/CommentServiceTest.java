package com.secondhand.mall.service;

import com.secondhand.mall.entity.Comment;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.CommentRepository;
import com.secondhand.mall.repository.ProductRepository;
import com.secondhand.mall.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CommentServiceTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Product createTestProduct() {
        Product product = new Product();
        product.setName("Test Product");
        product.setCategory("electronics");
        product.setDescription("Test");
        product.setOriginalPrice(BigDecimal.valueOf(200));
        product.setSecondHandPrice(BigDecimal.valueOf(100));
        product.setItemCondition("good");
        product.setSellerName("Seller");
        product.setSellerPhone("13800000000");
        product.setSellerUsername("seller");
        product.setImageUrl("test.jpg");
        product.setQuantity(1);
        product.setStatus("active");
        return productRepository.save(product);
    }

    private User createTestUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("password");
        user.setEmail(username + "@test.com");
        user.setPhone("13800000000");
        user.setNickname(username);
        user.setBalance(BigDecimal.valueOf(10000));
        return userRepository.save(user);
    }

    @Test
    void testAddComment_TooLong_ShouldThrow() {
        Product product = createTestProduct();
        createTestUser("user");

        String longContent = "a".repeat(501);

        assertThrows(RuntimeException.class, () -> {
            commentService.addComment(product.getId(), "user", longContent);
        });
    }

    @Test
    void testAddComment_WithinLimit_ShouldSucceed() {
        Product product = createTestProduct();
        createTestUser("user");

        String content = "a".repeat(500);

        assertDoesNotThrow(() -> {
            commentService.addComment(product.getId(), "user", content);
        });
    }
}
