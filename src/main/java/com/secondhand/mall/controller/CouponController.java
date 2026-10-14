package com.secondhand.mall.controller;

import com.secondhand.mall.exception.ForbiddenException;

import com.secondhand.mall.aspect.Audited;
import com.secondhand.mall.entity.Coupon;
import com.secondhand.mall.repository.CouponRepository;
import com.secondhand.mall.service.AdminService;
import com.secondhand.mall.service.CouponService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 优惠券：用户领取/查询，管理员发放/停用 */
@RestController
@RequestMapping("/api")
public class CouponController extends BaseApiController {

    private final CouponService couponService;
    private final CouponRepository couponRepository;
    private final AdminService adminService;

    public CouponController(CouponService couponService, CouponRepository couponRepository,
                            AdminService adminService) {
        this.couponService = couponService;
        this.couponRepository = couponRepository;
        this.adminService = adminService;
    }

    private void checkAdmin(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null || !adminService.isAdmin(username)) {
            throw new com.secondhand.mall.exception.ForbiddenException("无管理员权限");
        }
    }

    private String me(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null) throw new RuntimeException("请先登录");
        return username;
    }

    /** 可领取的优惠?*/
    @GetMapping("/coupons/claimable")
    public Map<String, Object> claimable(HttpSession session) {
        try {
            List<Coupon> list = couponService.listClaimable(me(session));
            return ok("获取成功").put("coupons", list).put("total", list.size()).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 我的优惠券（含已?过期?*/
    @GetMapping("/coupons/mine")
    public Map<String, Object> mine(HttpSession session) {
        try {
            List<Map<String, Object>> list = couponService.listMine(me(session));
            return ok("获取成功").put("coupons", list).put("total", list.size()).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /**
     * 按订单实付总额查可用优惠券（与核销同口径）。
     * 前端只展示本接口结果，禁止自行计算门槛。
     */
    @GetMapping("/coupons/usable")
    public Map<String, Object> usable(@RequestParam("amount") java.math.BigDecimal amount,
                                      HttpSession session) {
        try {
            List<Map<String, Object>> list = couponService.listUsable(me(session), amount);
            return ok("获取成功").put("coupons", list).put("total", list.size()).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @PostMapping("/coupons/{id}/claim")
    public Map<String, Object> claim(@PathVariable Long id, HttpSession session) {
        try {
            var uc = couponService.claim(me(session), id);
            return ok("领取成功").put("userCouponId", uc.getId()).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    // ========== 管理?==========

    @GetMapping("/admin/coupons")
    public Map<String, Object> adminList(HttpSession session) {
        checkAdmin(session);
        List<Coupon> list = couponRepository.findAllByOrderByCreatedAtDesc();
        return ok("获取成功").put("coupons", list).put("total", list.size()).toMap();
    }

    @Audited(action = "新建优惠券", targetType = "coupon")
    @PostMapping("/admin/coupons")
    public Map<String, Object> create(@RequestBody Coupon body, HttpSession session) {
        try {
            checkAdmin(session);
            if (body.getCode() == null || body.getCode().isBlank()) {
                return fail("券码不能为空").toMap();
            }
            body.setCode(body.getCode().trim().toUpperCase());
            if (couponRepository.existsByCode(body.getCode())) {
                return fail("券码已存").toMap();
            }
            if (body.getDiscountAmount() == null || body.getDiscountAmount().doubleValue() <= 0) {
                return fail("抵扣金额必须大于 0").toMap();
            }
            if (body.getStartAt() == null || body.getEndAt() == null) {
                return fail("请设置有效期").toMap();
            }
            if (!body.getEndAt().isAfter(body.getStartAt())) {
                return fail("结束时间必须晚于开始时").toMap();
            }
            Coupon saved = couponService.createCoupon(body);
            return ok("已创建").put("coupon", saved).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @Audited(action = "修改优惠券", targetType = "coupon")
    @PutMapping("/admin/coupons/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Coupon patch, HttpSession session) {
        try {
            checkAdmin(session);
            Coupon c = couponRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("优惠券不存在"));
            if (patch.getName() != null && !patch.getName().isBlank()) c.setName(patch.getName());
            if (patch.getDiscountAmount() != null) c.setDiscountAmount(patch.getDiscountAmount());
            if (patch.getMinAmount() != null) c.setMinAmount(patch.getMinAmount());
            if (patch.getTotalQuantity() != null) c.setTotalQuantity(patch.getTotalQuantity());
            if (patch.getPerUserLimit() != null) c.setPerUserLimit(patch.getPerUserLimit());
            if (patch.getStartAt() != null) c.setStartAt(patch.getStartAt());
            if (patch.getEndAt() != null) c.setEndAt(patch.getEndAt());
            if (patch.getEnabled() != null) c.setEnabled(patch.getEnabled());
            return ok("已更").put("coupon", couponRepository.save(c)).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @Audited(action = "删除优惠券", targetType = "coupon")
    @DeleteMapping("/admin/coupons/{id}")
    public Map<String, Object> delete(@PathVariable Long id, HttpSession session) {
        try {
            checkAdmin(session);
            Coupon c = couponRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("优惠券不存在"));
            if (c.getIssuedCount() != null && c.getIssuedCount() > 0) {
                // 已经有人领了，直接删会让用户的券变孤儿，只能停用
                c.setEnabled(false);
                couponRepository.save(c);
                return ok("该券已发放，已改为停用（不再可领取）").toMap();
            }
            couponRepository.delete(c);
            return ok("已删").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }
}

