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

    /** B-04：可用券与核销同门槛——订单实付总额不达标不得出现在可用列表 */
    @Test
    void testListUsable_SameThresholdAsConsume() {
        Coupon highMin = createCoupon(10, new BigDecimal("15"), new BigDecimal("200"), 5);
        Coupon lowMin = createCoupon(10, new BigDecimal("10"), new BigDecimal("50"), 5);
        createUser("buyer1", 100);
        UserCoupon ucHigh = couponService.claim("buyer1", highMin.getId());
        couponService.claim("buyer1", lowMin.getId());

        // 订单 80：只有满 50 可用
        var usable = couponService.listUsable("buyer1", new BigDecimal("80"));
        assertEquals(1, usable.size());
        assertEquals(lowMin.getCode(), usable.get(0).get("code"));

        // 同金额下核销高门槛券必须失败，与列表口径一致
        assertThrows(RuntimeException.class,
                () -> couponService.consume("buyer1", ucHigh.getId(), 1L, new BigDecimal("80")),
                "总金额不满门槛不得核销");
    }

    /** B-04：订单金额异常时核销直接失败 */
    @Test
    void testConsume_NullOrZeroAmount_ShouldThrow() {
        Coupon c = createCoupon(10, new BigDecimal("10"), BigDecimal.ZERO, 5);
        createUser("buyer1", 100);
        UserCoupon uc = couponService.claim("buyer1", c.getId());

        assertThrows(RuntimeException.class,
                () -> couponService.consume("buyer1", uc.getId(), 1L, null));
        assertThrows(RuntimeException.class,
                () -> couponService.consume("buyer1", uc.getId(), 1L, BigDecimal.ZERO));
    }

    /** B-04：listUsable 与 consume 对同一金额判定一致 */
    @Test
    void testListUsable_EmptyWhenBelowAllMins() {
        Coupon c = createCoupon(10, new BigDecimal("15"), new BigDecimal("100"), 5);
        createUser("buyer1", 100);
        couponService.claim("buyer1", c.getId());

        assertTrue(couponService.listUsable("buyer1", new BigDecimal("99")).isEmpty());
        assertEquals(1, couponService.listUsable("buyer1", new BigDecimal("100")).size());
    }

    @Test
    void createCoupon_DuplicateCode_ShouldReject() {
        Coupon first = new Coupon();
        first.setCode("DUP-CODE-001");
        first.setName("重复编码券");
        first.setDiscountAmount(new BigDecimal("10.00"));
        first.setMinAmount(new BigDecimal("0"));
        first.setTotalQuantity(10);
        first.setPerUserLimit(1);
        first.setStartAt(java.time.LocalDateTime.now().minusDays(1));
        first.setEndAt(java.time.LocalDateTime.now().plusDays(1));
        first.setEnabled(true);
        couponService.createCoupon(first);

        Coupon second = new Coupon();
        second.setCode("DUP-CODE-001");
        second.setName("重复编码券2");
        second.setDiscountAmount(new BigDecimal("5.00"));
        second.setMinAmount(new BigDecimal("0"));
        second.setTotalQuantity(10);
        second.setPerUserLimit(1);
        second.setStartAt(java.time.LocalDateTime.now().minusDays(1));
        second.setEndAt(java.time.LocalDateTime.now().plusDays(1));
        second.setEnabled(true);
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> couponService.createCoupon(second));
    }}
