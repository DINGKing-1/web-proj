package com.secondhand.mall.service;

import com.secondhand.mall.entity.Coupon;
import com.secondhand.mall.entity.UserCoupon;
import com.secondhand.mall.repository.CouponRepository;
import com.secondhand.mall.repository.UserCouponRepository;
import com.secondhand.mall.repository.UserRepository;
import com.secondhand.mall.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CouponServiceTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Coupon createCoupon(int total, BigDecimal discount, BigDecimal min, int perUser) {
        Coupon c = new Coupon();
        c.setCode("T" + System.nanoTime());
        c.setName("测试券");
        c.setDiscountAmount(discount);
        c.setMinAmount(min);
        c.setTotalQuantity(total);
        c.setIssuedCount(0);
        c.setPerUserLimit(perUser);
        c.setStartAt(LocalDateTime.now().minusDays(1));
        c.setEndAt(LocalDateTime.now().plusDays(7));
        c.setEnabled(true);
        return couponRepository.save(c);
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

    @Test
    void testClaim_RespectsTotalQuantity() {
        Coupon c = createCoupon(1, new BigDecimal("10"), BigDecimal.ZERO, 10);
        createUser("buyer1", 100);

        couponService.claim("buyer1", c.getId());
        assertThrows(RuntimeException.class, () -> couponService.claim("buyer2", c.getId()));
    }

    @Test
    void testClaim_RespectsPerUserLimit() {
        Coupon c = createCoupon(10, new BigDecimal("10"), BigDecimal.ZERO, 1);
        createUser("buyer1", 100);

        couponService.claim("buyer1", c.getId());
        assertThrows(RuntimeException.class, () -> couponService.claim("buyer1", c.getId()));
    }

    @Test
    void testConsume_ThenCannotReuse() {
        Coupon c = createCoupon(10, new BigDecimal("15"), new BigDecimal("50"), 5);
        createUser("buyer1", 100);
        UserCoupon uc = couponService.claim("buyer1", c.getId());

        BigDecimal discount = couponService.consume("buyer1", uc.getId(), 999L, new BigDecimal("100"));
        assertEquals(0, new BigDecimal("15").compareTo(discount));

        // 二次核销应失败
        assertThrows(RuntimeException.class,
                () -> couponService.consume("buyer1", uc.getId(), 1000L, new BigDecimal("100")));
    }

    @Test
    void testConsume_BelowMinAmount_ShouldThrow() {
        Coupon c = createCoupon(10, new BigDecimal("15"), new BigDecimal("200"), 5);
        createUser("buyer1", 100);
        UserCoupon uc = couponService.claim("buyer1", c.getId());

        assertThrows(RuntimeException.class,
                () -> couponService.consume("buyer1", uc.getId(), 1L, new BigDecimal("100")));
    }

    @Test
    void testConsume_DiscountCappedAtGross() {
        Coupon c = createCoupon(10, new BigDecimal("50"), BigDecimal.ZERO, 5);
        createUser("buyer1", 100);
        UserCoupon uc = couponService.claim("buyer1", c.getId());

        BigDecimal discount = couponService.consume("buyer1", uc.getId(), 2L, new BigDecimal("30"));
        assertEquals(0, new BigDecimal("30").compareTo(discount), "抵扣不得超过订单原价");
    }

    @Test
    void testReleaseByOrder_MakesCouponReusable() {
        Coupon c = createCoupon(10, new BigDecimal("10"), BigDecimal.ZERO, 5);
        createUser("buyer1", 100);
        UserCoupon uc = couponService.claim("buyer1", c.getId());

        couponService.consume("buyer1", uc.getId(), 42L, new BigDecimal("100"));
        couponService.releaseByOrder(42L);

        UserCoupon after = userCouponRepository.findById(uc.getId()).orElseThrow();
        assertEquals("unused", after.getStatus());
        assertNull(after.getUsedOrderId());
    }
}
