package com.secondhand.mall.service;

import com.secondhand.mall.entity.Product;
import com.secondhand.mall.repository.ProductRepository;
import com.secondhand.mall.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
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

    @Test
    void testSearchProducts_ShouldExcludeSoldProducts() {
        // 创建商品：一个活跃，一个已售
        createTestProduct("iPhone 13", "active");
        createTestProduct("iPhone 14", "sold");

        List<Product> results = productService.searchProducts("iPhone");

        assertEquals(1, results.size());
        assertEquals("iPhone 13", results.get(0).getName());
    }

    @Test
    void testGetFilteredProducts_SortByPriceAsc() {
        // 创建不同价格的商品
        Product cheap = createTestProduct("Cheap", "active");
        cheap.setSecondHandPrice(BigDecimal.valueOf(10));
        productRepository.save(cheap);

        Product expensive = createTestProduct("Expensive", "active");
        expensive.setSecondHandPrice(BigDecimal.valueOf(100));
        productRepository.save(expensive);

        Product medium = createTestProduct("Medium", "active");
        medium.setSecondHandPrice(BigDecimal.valueOf(50));
        productRepository.save(medium);

        List<Product> products = productService.getFilteredProducts(null, null, null, null, "price-asc");

        assertEquals(3, products.size());
        assertEquals("Cheap", products.get(0).getName());
        assertEquals("Medium", products.get(1).getName());
        assertEquals("Expensive", products.get(2).getName());
    }

    @Test
    void testGetProducts_Pagination() {
        // 创建 25 个商品
        for (int i = 0; i < 25; i++) {
            createTestProduct("Product " + i, "active");
        }

        // 第一页
        Page<Product> page1 = productService.getProducts(0, 10);
        assertEquals(10, page1.getContent().size());
        assertEquals(25, page1.getTotalElements());
        assertEquals(3, page1.getTotalPages());

        // 第二页
        Page<Product> page2 = productService.getProducts(1, 10);
        assertEquals(10, page2.getContent().size());
    }
}
