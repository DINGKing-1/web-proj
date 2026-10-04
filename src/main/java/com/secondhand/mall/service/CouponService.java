package com.secondhand.mall.service;

import com.secondhand.mall.entity.Coupon;
import com.secondhand.mall.entity.UserCoupon;
import com.secondhand.mall.repository.CouponRepository;
import com.secondhand.mall.repository.UserCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 优惠券：平台发放 → 用户领取 → 支付时核销；订单取消/售后退款时把券退回去。
 *
 * 核销发生在 payOrder（而不是下单），因为订单可能挂着 30 分钟才付款，
 * 下单即锁券会让超时关单的券很难退干净。
 */
@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    public CouponService(CouponRepository couponRepository,
                         UserCouponRepository userCouponRepository) {
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
    }

    // ========== 领取 ==========

    /** 可领取列表：启用中、在有效期内、没领满、还有余量 */
    public List<Coupon> listClaimable(String username) {
        LocalDateTime now = LocalDateTime.now();
        List<Coupon> result = new ArrayList<>();
        for (Coupon c : couponRepository.findByEnabledTrueOrderByEndAtAsc()) {
            if (c.getStartAt() != null && c.getStartAt().isAfter(now)) continue;
            if (c.getEndAt() != null && c.getEndAt().isBefore(now)) continue;
            if (c.getTotalQuantity() != null && c.getTotalQuantity() > 0
                    && c.getIssuedCount() != null && c.getIssuedCount() >= c.getTotalQuantity()) {
                continue;
            }
            long claimed = userCouponRepository.countByCouponIdAndUsername(c.getId(), username);
            if (claimed >= (c.getPerUserLimit() == null ? 1 : c.getPerUserLimit())) continue;
            result.add(c);
        }
        return result;
    }

    @Transactional
    public UserCoupon claim(String username, Long couponId) {
        // 券行锁：防并发领取超发
        Coupon coupon = couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new RuntimeException("优惠券不存在"));
        LocalDateTime now = LocalDateTime.now();
        if (!Boolean.TRUE.equals(coupon.getEnabled())) {
            throw new RuntimeException("该优惠券已下架");
        }
        if (coupon.getStartAt() != null && coupon.getStartAt().isAfter(now)) {
            throw new RuntimeException("优惠券还未开始");
        }
        if (coupon.getEndAt() != null && coupon.getEndAt().isBefore(now)) {
            throw new RuntimeException("优惠券已过期");
        }
        if (coupon.getTotalQuantity() != null && coupon.getTotalQuantity() > 0
                && (coupon.getIssuedCount() == null || coupon.getIssuedCount() >= coupon.getTotalQuantity())) {
            throw new RuntimeException("手慢了，优惠券已被领完");
        }
        int limit = coupon.getPerUserLimit() == null ? 1 : coupon.getPerUserLimit();
        if (userCouponRepository.countByCouponIdAndUsername(couponId, username) >= limit) {
            throw new RuntimeException("已达每人限领数量");
        }
        coupon.setIssuedCount((coupon.getIssuedCount() == null ? 0 : coupon.getIssuedCount()) + 1);
        couponRepository.save(coupon);

        UserCoupon uc = new UserCoupon(couponId, username);
        return userCouponRepository.save(uc);
    }

    // ========== 我的券 ==========

    /** 我的优惠券（带券面信息），顺手把过期未用的置为 expired */
    @Transactional
    public List<Map<String, Object>> listMine(String username) {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UserCoupon uc : userCouponRepository.findByUsernameOrderByClaimedAtDesc(username)) {
            Coupon coupon = couponRepository.findById(uc.getCouponId()).orElse(null);
            if (coupon == null) continue;
            if ("unused".equals(uc.getStatus()) && coupon.getEndAt() != null
                    && coupon.getEndAt().isBefore(now)) {
                uc.setStatus("expired");
                userCouponRepository.save(uc);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userCouponId", uc.getId());
            row.put("couponId", coupon.getId());
            row.put("code", coupon.getCode());
            row.put("name", coupon.getName());
            row.put("discountAmount", coupon.getDiscountAmount());
            row.put("minAmount", coupon.getMinAmount());
            row.put("startAt", coupon.getStartAt());
            row.put("endAt", coupon.getEndAt());
            row.put("status", uc.getStatus());
            row.put("claimedAt", uc.getClaimedAt());
            row.put("usedOrderId", uc.getUsedOrderId());
            rows.add(row);
        }
        return rows;
    }

    // ========== 核销 / 退回 ==========

    /**
     * 支付时核销，返回抵扣金额（可能为 0：未传券时）。
     * 校验：归属、未用、有效期、门槛、金额不超过订单应付。
     */
    @Transactional
    public BigDecimal consume(String username, Long userCouponId, Long orderId, BigDecimal orderGross) {
        if (userCouponId == null) {
            return BigDecimal.ZERO;
        }
        // 行锁 + 二次状态复核：防两笔订单并发使用同一张券
        UserCoupon uc = userCouponRepository.findByIdAndUsernameForUpdate(userCouponId, username)
                .orElseThrow(() -> new RuntimeException("优惠券不存在或不属于你"));
        if (!"unused".equals(uc.getStatus())) {
            throw new RuntimeException("该优惠券已使用或已失效");
        }
        Coupon coupon = couponRepository.findById(uc.getCouponId())
                .orElseThrow(() -> new RuntimeException("优惠券不存在"));
        LocalDateTime now = LocalDateTime.now();
        if (!Boolean.TRUE.equals(coupon.getEnabled())) {
            throw new RuntimeException("该优惠券已下架");
        }
        if ((coupon.getStartAt() != null && coupon.getStartAt().isAfter(now))
                || (coupon.getEndAt() != null && coupon.getEndAt().isBefore(now))) {
            throw new RuntimeException("优惠券不在有效期内");
        }
        BigDecimal min = coupon.getMinAmount() != null ? coupon.getMinAmount() : BigDecimal.ZERO;
        if (orderGross.compareTo(min) < 0) {
            throw new RuntimeException("订单满 ¥" + min.toPlainString() + " 才能使用该优惠券");
        }
        BigDecimal discount = coupon.getDiscountAmount();
        if (discount == null || discount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("优惠券金额异常");
        }
        if (discount.compareTo(orderGross) > 0) {
            discount = orderGross;
        }

        uc.setStatus("used");
        uc.setUsedOrderId(orderId);
        uc.setUsedAt(now);
        userCouponRepository.save(uc);
        return discount;
    }

    /** 订单取消/全额退款时把券退回（可再次使用） */
    @Transactional
    public void releaseByOrder(Long orderId) {
        if (orderId == null) return;
        userCouponRepository.findByUsedOrderId(orderId).ifPresent(uc -> {
            uc.setStatus("unused");
            uc.setUsedOrderId(null);
            uc.setUsedAt(null);
            userCouponRepository.save(uc);
        });
    }
}
