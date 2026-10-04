package com.secondhand.mall.service;

import com.secondhand.mall.entity.Address;
import com.secondhand.mall.entity.Offer;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.AddressRepository;
import com.secondhand.mall.repository.OfferRepository;
import com.secondhand.mall.repository.OrderRepository;
import com.secondhand.mall.repository.ProductRepository;
import com.secondhand.mall.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OfferServiceTest {

    @Autowired
    private OfferService offerService;

    @Autowired
    private OfferRepository offerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        offerRepository.deleteAll();
        addressRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createUser(String username, double balance) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("pwd");
        u.setEmail(username + "@t.com");
        u.setPhone("13800000000");
        u.setNickname(username);
        u.setBalance(BigDecimal.valueOf(balance));
        return userRepository.save(u);
    }

    private Product createProduct(String seller, double price) {
        Product p = new Product();
        p.setName("Test Product");
        p.setCategory("electronics");
        p.setDescription("d");
        p.setOriginalPrice(BigDecimal.valueOf(price * 2));
        p.setSecondHandPrice(BigDecimal.valueOf(price));
        p.setItemCondition("good");
        p.setSellerName(seller);
        p.setSellerUsername(seller);
        p.setImageUrl("t.jpg");
        p.setQuantity(1);
        p.setStatus("active");
        return productRepository.save(p);
    }

    private Address createAddress(String username) {
        Address a = new Address();
        a.setUsername(username);
        a.setReceiverName("收货人");
        a.setPhone("13800000001");
        a.setProvince("省");
        a.setCity("市");
        a.setDistrict("区");
        a.setDetail("路 1 号");
        a.setIsDefault(true);
        return addressRepository.save(a);
    }

    private Offer createOffer(Product product, String buyer, String seller, double price) {
        Offer o = new Offer();
        o.setProductId(product.getId());
        o.setProductName(product.getName());
        o.setImageUrl(product.getImageUrl());
        o.setBuyerUsername(buyer);
        o.setSellerUsername(seller);
        o.setOfferPrice(BigDecimal.valueOf(price));
        o.setMessage("便宜点");
        o.setStatus("pending");
        return offerRepository.save(o);
    }

    @Test
    void testAcceptOffer_CreatesPendingPaymentOrder() {
        createUser("seller1", 0);
        createUser("buyer1", 10000);
        Product product = createProduct("seller1", 100);
        createAddress("buyer1");
        Offer offer = createOffer(product, "buyer1", "seller1", 80);

        Offer accepted = offerService.acceptOffer(offer.getId(), "seller1");
        assertEquals("accepted", accepted.getStatus());
        assertNotNull(accepted.getOrderId());

        var order = orderRepository.findById(accepted.getOrderId()).orElseThrow();
        assertEquals("pending_payment", order.getStatus(), "议价成交生成待付款订单");
        assertEquals(0, new BigDecimal("80").compareTo(order.getProductPrice()), "成交价=出价");
    }

    @Test
    void testAcceptOffer_OnlySeller() {
        createUser("seller1", 0);
        createUser("buyer1", 10000);
        createUser("attacker", 0);
        Product product = createProduct("seller1", 100);
        createAddress("buyer1");
        Offer offer = createOffer(product, "buyer1", "seller1", 80);

        assertThrows(RuntimeException.class, () -> offerService.acceptOffer(offer.getId(), "attacker"));
    }

    @Test
    void testAcceptOffer_Twice_ShouldThrow() {
        createUser("seller1", 0);
        createUser("buyer1", 10000);
        Product product = createProduct("seller1", 100);
        createAddress("buyer1");
        Offer offer = createOffer(product, "buyer1", "seller1", 80);

        offerService.acceptOffer(offer.getId(), "seller1");
        assertThrows(RuntimeException.class, () -> offerService.acceptOffer(offer.getId(), "seller1"));
    }

    @Test
    void testRejectOffer() {
        createUser("seller1", 0);
        createUser("buyer1", 10000);
        Product product = createProduct("seller1", 100);
        Offer offer = createOffer(product, "buyer1", "seller1", 50);

        Offer rejected = offerService.rejectOffer(offer.getId(), "seller1");
        assertEquals("rejected", rejected.getStatus());
    }

    @Test
    void testAcceptOffer_NegotiatedPriceAboveList_ShouldThrow() {
        createUser("seller1", 0);
        createUser("buyer1", 10000);
        Product product = createProduct("seller1", 100);
        createAddress("buyer1");
        // 出价高于售价 —— 创建 Offer 时可能不校验，接受时由下单路径拦下
        Offer offer = createOffer(product, "buyer1", "seller1", 150);

        assertThrows(RuntimeException.class, () -> offerService.acceptOffer(offer.getId(), "seller1"));
    }
}
