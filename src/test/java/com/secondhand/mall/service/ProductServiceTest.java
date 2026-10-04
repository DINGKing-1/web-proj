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

    /** 回归：编辑商品只改描述也要被敏感词拦下（原实现只校验请求带来的字段，可绕过） */
    @Test
    void testUpdateProduct_SensitiveWordInChangedField_ShouldReject() {
        Product product = createTestProduct("iPhone 13", "active");

        Product updates = new Product();
        updates.setDescription("包邮代开发票，假币交易");

        assertThrows(RuntimeException.class,
                () -> productService.updateProduct(product.getId(), updates, "seller"));
    }

    /** 未改动的原字段含敏感词，合并后同样要拦 */
    @Test
    void testUpdateProduct_SensitiveWordInExistingField_ShouldReject() {
        Product product = createTestProduct("iPhone 13", "active");
        product.setDescription("含赌博信息的旧描述");
        productRepository.save(product);

        Product updates = new Product();
        updates.setSecondHandPrice(BigDecimal.valueOf(80));

        assertThrows(RuntimeException.class,
                () -> productService.updateProduct(product.getId(), updates, "seller"));
    }

    @Test
    void testCreateProduct_SensitiveWord_ShouldReject() {
        Product product = new Product();
        product.setName("专业办证");
        product.setCategory("services");
        product.setDescription("正常描述");
        product.setOriginalPrice(BigDecimal.valueOf(200));
        product.setSecondHandPrice(BigDecimal.valueOf(100));
        product.setItemCondition("good");
        product.setSellerUsername("seller");
        product.setImageUrl("test.jpg");
        product.setQuantity(1);

        assertThrows(RuntimeException.class, () -> productService.createProduct(product));
    }

    private Product newProduct(String name) {
        Product product = new Product();
        product.setName(name);
        product.setCategory("electronics");
        product.setDescription("Test");
        product.setOriginalPrice(BigDecimal.valueOf(200));
        product.setSecondHandPrice(BigDecimal.valueOf(100));
        product.setItemCondition("good");
        product.setSellerUsername("seller");
        product.setSellerName("Seller");
        product.setSellerPhone("13800000000");
        product.setImageUrl("test.jpg");
        product.setQuantity(1);
        return product;
    }

    /** 发布审核：新商品先进待审，不在前台 */
    @Test
    void testCreateProduct_ShouldStartAsPendingReview() {
        Product saved = productService.createProduct(newProduct("iPhone 15"));
        assertEquals("pending", saved.getStatus(), "开启审核时新商品应为待审核");
    }

    @Test
    void testApprove_ShouldMakeProductActive() {
        Product saved = productService.createProduct(newProduct("iPhone 15"));
        Product approved = productService.approveProduct(saved.getId());
        assertEquals("active", approved.getStatus());
        assertNull(approved.getReviewNote());
    }

    @Test
    void testReject_ShouldRecordReasonAndNotifySellerOnEdit() {
        Product saved = productService.createProduct(newProduct("iPhone 15"));
        Product rejected = productService.rejectProduct(saved.getId(), "图片含联系方式水印");
        assertEquals("rejected", rejected.getStatus());
        assertEquals("图片含联系方式水印", rejected.getReviewNote());

        // 卖家修改后重新排队，驳回理由清空
        Product updates = new Product();
        updates.setDescription("已去掉水印的描述");
        Product resubmitted = productService.updateProduct(saved.getId(), updates, "seller");
        assertEquals("pending", resubmitted.getStatus(), "驳回后编辑应重新进入待审");
        assertNull(resubmitted.getReviewNote());
    }

    /** 未过审的商品不允许卖家自己改成在售（否则审核形同虚设） */
    @Test
    void testUpdateProduct_PendingCannotSelfPublish() {
        Product saved = productService.createProduct(newProduct("iPhone 15"));
        Product updates = new Product();
        updates.setStatus("active");

        assertThrows(RuntimeException.class,
                () -> productService.updateProduct(saved.getId(), updates, "seller"));
    }

    // ========== 真分页 ==========

    private Product createActive(String name, String category, double price) {
        Product p = new Product();
        p.setName(name);
        p.setCategory(category);
        p.setDescription(name);
        p.setOriginalPrice(BigDecimal.valueOf(price * 2));
        p.setSecondHandPrice(BigDecimal.valueOf(price));
        p.setItemCondition("good");
        p.setSellerName("Seller");
        p.setSellerUsername("seller");
        p.setImageUrl("t.jpg");
        p.setQuantity(1);
        p.setStatus("active");
        return productRepository.save(p);
    }

    @Test
    void testSearchProductsPaged_ReturnsSliceAndTotal() {
        for (int i = 1; i <= 25; i++) {
            createActive("Pad-" + i, "tablet", 100 + i);
        }
        Page<Product> page0 = productService.searchProductsPaged(
                null, null, null, null, null, null, "newest", 0, 12);
        assertEquals(25, page0.getTotalElements());
        assertEquals(3, page0.getTotalPages());
        assertEquals(12, page0.getContent().size());

        Page<Product> page2 = productService.searchProductsPaged(
                null, null, null, null, null, null, "newest", 2, 12);
        assertEquals(1, page2.getContent().size(), "最后一页只剩 1 条");
    }

    @Test
    void testSearchProductsPaged_SizeZeroClamped() {
        createActive("Pad-1", "tablet", 100);
        Page<Product> page = productService.searchProductsPaged(
                null, null, null, null, null, null, "newest", 0, 0);
        assertEquals(1, page.getTotalElements());
        assertFalse(page.getContent().isEmpty(), "size=0 夹取为 1，不应返回空页异常");
    }

    @Test
    void testSearchProductsPaged_NegativePageClamped() {
        createActive("Pad-1", "tablet", 100);
        Page<Product> page = productService.searchProductsPaged(
                null, null, null, null, null, null, "newest", -5, 12);
        assertEquals(0, page.getNumber());
        assertEquals(1, page.getContent().size());
    }

    @Test
    void testSearchProductsPaged_KeywordAndCategoryFilter() {
        createActive("MacBook Air", "laptop", 3000);
        createActive("MacBook Pro", "laptop", 6000);
        createActive("iPad", "tablet", 2000);

        Page<Product> byKw = productService.searchProductsPaged(
                "MacBook", null, null, null, null, null, "price-asc", 0, 10);
        assertEquals(2, byKw.getTotalElements());

        Page<Product> byCat = productService.searchProductsPaged(
                null, "tablet", null, null, null, null, "newest", 0, 10);
        assertEquals(1, byCat.getTotalElements());
        assertEquals("iPad", byCat.getContent().get(0).getName());
    }

    @Test
    void testSearchProductsPaged_PriceSort() {
        createActive("Cheap", "x", 10);
        createActive("Expensive", "x", 90);
        createActive("Medium", "x", 50);

        Page<Product> asc = productService.searchProductsPaged(
                null, "x", null, null, null, null, "price-asc", 0, 10);
        assertEquals("Cheap", asc.getContent().get(0).getName());
        assertEquals("Expensive", asc.getContent().get(2).getName());

        Page<Product> desc = productService.searchProductsPaged(
                null, "x", null, null, null, null, "price-desc", 0, 10);
        assertEquals("Expensive", desc.getContent().get(0).getName());
    }

    @Test
    void testSearchProductsPaged_SellerFilter() {
        Product p = createActive("Mine", "x", 10);
        p.setSellerUsername("alice");
        productRepository.save(p);
        createActive("Other", "x", 20);

        Page<Product> page = productService.searchProductsPaged(
                null, null, "alice", null, null, null, "newest", 0, 10);
        assertEquals(1, page.getTotalElements());
        assertEquals("Mine", page.getContent().get(0).getName());
    }
}
