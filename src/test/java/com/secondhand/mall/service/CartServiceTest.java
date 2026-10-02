package com.secondhand.mall.service;

import com.secondhand.mall.dto.CartItemDTO;
import com.secondhand.mall.entity.CartItem;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.CartItemRepository;
import com.secondhand.mall.repository.ProductRepository;
import com.secondhand.mall.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CartServiceTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        cartItemRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Product createTestProduct(String name, String status) {
        Product product = new Product();
        product.setName(name);
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
        product.setStatus(status);
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
    void testGetCart_ShouldMarkUnavailableProducts() {
        // 创建用户
        createTestUser("buyer");
        createTestUser("seller");

        // 创建活跃商品和已售商品
        Product activeProduct = createTestProduct("Active", "active");
        Product soldProduct = createTestProduct("Sold", "sold");

        // 直接创建购物车项（跳过addToCart的状态检查）
        CartItem item1 = new CartItem("buyer", activeProduct.getId());
        cartItemRepository.save(item1);

        CartItem item2 = new CartItem("buyer", soldProduct.getId());
        cartItemRepository.save(item2);

        List<CartItemDTO> cart = cartService.getCart("buyer");

        assertEquals(2, cart.size());

        CartItemDTO availableItem = cart.stream()
                .filter(CartItemDTO::isAvailable)
                .findFirst()
                .orElseThrow();
        assertEquals("Active", availableItem.getProductName());

        CartItemDTO unavailableItem = cart.stream()
                .filter(item -> !item.isAvailable())
                .findFirst()
                .orElseThrow();
        assertEquals("商品已下架", unavailableItem.getProductName());
    }
}
