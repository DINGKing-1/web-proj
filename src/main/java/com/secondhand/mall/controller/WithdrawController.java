package com.secondhand.mall.controller;

import com.secondhand.mall.exception.ForbiddenException;

import com.secondhand.mall.entity.WithdrawRequest;
import com.secondhand.mall.service.AdminService;
import com.secondhand.mall.service.WithdrawService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class WithdrawController extends BaseApiController {

    private final WithdrawService withdrawService;
    private final AdminService adminService;

    public WithdrawController(WithdrawService withdrawService, AdminService adminService) {
        this.withdrawService = withdrawService;
        this.adminService = adminService;
    }

    @PostMapping("/user/withdraw")
    public Map<String, Object> apply(@RequestBody Map<String, Object> body, HttpSession session) {
        try {
            String username = getLoginUsername(session);
            Object raw = body.get("amount");
            BigDecimal amount = raw == null ? null : new BigDecimal(String.valueOf(raw));
            String account = (String) body.get("account");
            WithdrawRequest req = withdrawService.apply(username, amount, account);
            return ok("提现申请已提交，等待审核").put("withdraw", req).toMap();
        } catch (NumberFormatException e) {
            return fail("金额格式不正").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @GetMapping("/user/withdraw")
    public Map<String, Object> myWithdraws(HttpSession session) {
        String username = getLoginUsername(session);
        List<WithdrawRequest> items = withdrawService.listByUser(username);
        return ok("获取成功").put("withdraws", items).put("total", items.size()).toMap();
    }

    private void checkAdmin(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null || !adminService.isAdmin(username)) {
            throw new com.secondhand.mall.exception.ForbiddenException("无管理员权限");
        }
    }

    @GetMapping("/admin/withdraws")
    public Map<String, Object> listAll(HttpSession session) {
        checkAdmin(session);
        List<WithdrawRequest> items = withdrawService.listAll();
        return ok("获取成功")
                .put("withdraws", items)
                .put("pending", withdrawService.pendingCount())
                .toMap();
    }

    @PostMapping("/admin/withdraws/{id}/approve")
    public Map<String, Object> approve(@PathVariable Long id, HttpSession session) {
        try {
            checkAdmin(session);
            String admin = (String) session.getAttribute("username");
            WithdrawRequest req = withdrawService.approve(id, admin);
            return ok("已通过，线下打款后到账").put("withdraw", req).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @PostMapping("/admin/withdraws/{id}/reject")
    public Map<String, Object> reject(@PathVariable Long id,
                                      @RequestBody(required = false) Map<String, String> body,
                                      HttpSession session) {
        try {
            checkAdmin(session);
            String admin = (String) session.getAttribute("username");
            String reason = body != null ? body.get("reason") : null;
            WithdrawRequest req = withdrawService.reject(id, admin, reason);
            return ok("已驳回，余额已退").put("withdraw", req).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }
}

