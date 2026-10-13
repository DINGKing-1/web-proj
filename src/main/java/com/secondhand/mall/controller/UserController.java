package com.secondhand.mall.controller;

import com.secondhand.mall.entity.User;
import com.secondhand.mall.service.UserService;
import com.secondhand.mall.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final WalletService walletService;

    public UserController(UserService userService, WalletService walletService) {
        this.userService = userService;
        this.walletService = walletService;
    }

    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        try {
            HttpSession session = request.getSession();
            String username = (String) session.getAttribute("username");
            if (username == null) {
                return ResponseEntity.status(401).body(Map.of("message", "未登录"));
            }

            String avatarUrl = userService.uploadAvatar(username, file);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "avatar", avatarUrl
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /** 余额充值（模拟支付，受 mall.wallet.mock-recharge-* 配置约束） */
    @PostMapping("/recharge")
    public Map<String, Object> recharge(@RequestBody Map<String, Object> body, HttpSession session) {
        try {
            Object usernameAttr = session.getAttribute("username");
            if (usernameAttr == null) {
                return Map.of("success", false, "message", "请先登录");
            }
            BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
            User user = userService.recharge(usernameAttr.toString(), amount);
            return Map.of("success", true, "message", "充值成功", "balance", user.getBalance());
        } catch (RuntimeException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 发送找回密码验证码到注册邮箱（公开接口，限流在服务层） */
    @PostMapping("/reset-code")
    public Map<String, Object> sendResetCode(@RequestBody Map<String, String> body) {
        try {
            userService.sendResetCode(body.get("username"), body.get("email"));
            return Map.of("success", true, "message", "验证码已发送，请查收邮箱");
        } catch (RuntimeException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 忘记密码：邮箱验证码校验后重置 */
    @PostMapping("/reset-password")
    public Map<String, Object> resetPassword(@RequestBody Map<String, String> body) {
        try {
            userService.resetPassword(body.get("username"), body.get("email"),
                    body.get("code"), body.get("password"));
            return Map.of("success", true, "message", "密码重置成功，请使用新密码登录");
        } catch (RuntimeException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 发送修改密码验证码到绑定邮箱（需登录；未绑定邮箱不可改密） */
    @PostMapping("/change-password-code")
    public Map<String, Object> sendChangePasswordCode(HttpSession session) {
        try {
            Object usernameAttr = session.getAttribute("username");
            if (usernameAttr == null) {
                return Map.of("success", false, "message", "请先登录");
            }
            userService.sendChangePasswordCode(usernameAttr.toString());
            return Map.of("success", true, "message", "验证码已发送至绑定邮箱，请查收");
        } catch (RuntimeException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 登录后修改密码：需已绑定邮箱 + 当前密码 + 邮箱验证码 */
    @PostMapping("/change-password")
    public Map<String, Object> changePassword(@RequestBody Map<String, String> body, HttpSession session) {
        try {
            Object usernameAttr = session.getAttribute("username");
            if (usernameAttr == null) {
                return Map.of("success", false, "message", "请先登录");
            }
            userService.changePassword(
                    usernameAttr.toString(),
                    body.get("currentPassword"),
                    body.get("password"),
                    body.get("code"),
                    session.getId());
            return Map.of("success", true, "message", "密码修改成功，其他设备已下线");
        } catch (RuntimeException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 账单流水：全部资金变动落库（充值/支付/退款/结算/提现） */
    @GetMapping("/wallet")
    public Map<String, Object> wallet(HttpSession session) {
        Object usernameAttr = session.getAttribute("username");
        if (usernameAttr == null) {
            return Map.of("success", false, "message", "请先登录");
        }
        String username = usernameAttr.toString();
        // 统一走 wallet_transactions 账本，保证可对账、不与订单派生项重复
        List<Map<String, Object>> rows = walletService.listRecharges(username);
        return Map.of("success", true, "message", "获取成功", "bills", rows);
    }

    /** 注销账号 */
    @PostMapping("/delete-account")
    public Map<String, Object> deleteAccount(@RequestBody Map<String, String> body, HttpSession session) {
        try {
            Object usernameAttr = session.getAttribute("username");
            if (usernameAttr == null) {
                return Map.of("success", false, "message", "请先登录");
            }
            String username = usernameAttr.toString();
            String password = body.get("password");
            userService.deleteAccount(username, password);
            // 钱包流水保留：软删除可恢复，清掉流水会丢对账账本并重置模拟充值累计上限
            session.invalidate();
            return Map.of("success", true, "message", "账号已注销");
        } catch (RuntimeException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}
