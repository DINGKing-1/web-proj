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
    /** 创建优惠券：服务层重复校验（与库层唯一索引双保险） */
    @Transactional
    public Coupon createCoupon(Coupon coupon) {
        if (coupon == null) {
            throw new RuntimeException("优惠券不能为空");
        }
        String code = coupon.getCode() == null ? "" : coupon.getCode().trim();
        if (code.isEmpty()) {
            throw new RuntimeException("优惠券编码不能为空");
        }
        coupon.setCode(code);
        // 库层 uk_coupons_code 唯一 + 服务层 existsByCode 双保险
        if (couponRepository.existsByCode(code)) {
            throw new RuntimeException("优惠券编码已存在：" + code);
        }
        coupon.setId(null);
        if (coupon.getIssuedCount() == null) {
            coupon.setIssuedCount(0);
        }
        return couponRepository.save(coupon);
    }

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

    // ========== 可用券（与核销同一门槛口径） ==========

    /**
     * 按订单实付总额（不含运费）返回当前可用券。
     * 门槛/有效期/状态判断与 {@link #consume} 完全一致，前端只展示本接口结果，禁止自行算门槛。
     */
    @Transactional
    public List<Map<String, Object>> listUsable(String username, BigDecimal orderAmount) {
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("订单金额异常，无法查询可用优惠券");
        }
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : listMine(username)) {
            if (!"unused".equals(row.get("status"))) continue;
            Object minObj = row.get("minAmount");
            BigDecimal min = minObj instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
            Object discObj = row.get("discountAmount");
            BigDecimal discount = discObj instanceof BigDecimal bd ? bd : null;
            if (discount == null || discount.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (orderAmount.compareTo(min) < 0) continue;
            // 与 consume 同口径：抵扣不得超过本单实付
            if (discount.compareTo(orderAmount) > 0) {
                row.put("discountAmount", orderAmount);
            }
            rows.add(row);
        }
        return rows;
    }

    // ========== 核销 / 退回 ==========

    /**
     * 支付时核销，返回抵扣金额（可能为 0：未传券时）。
     * 校验：归属、未用、有效期、门槛（订单实付总额）、金额不超过订单应付。
     * 门槛不满足直接抛出，由支付事务回滚。
     */
    @Transactional
    public BigDecimal consume(String username, Long userCouponId, Long orderId, BigDecimal orderGross) {
        if (userCouponId == null) {
            return BigDecimal.ZERO;
        }
        if (orderGross == null || orderGross.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("订单金额异常，无法使用优惠券");
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
        // 唯一门槛：订单实付总额（不含运费）
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
