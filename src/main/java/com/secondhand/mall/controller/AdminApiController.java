package com.secondhand.mall.controller;

import com.secondhand.mall.aspect.Audited;
import com.secondhand.mall.exception.ForbiddenException;

import com.secondhand.mall.dto.LoginRequest;
import com.secondhand.mall.entity.Order;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.entity.Report;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController extends BaseApiController {

    private final AdminService adminService;
    private final UserService userService;
    private final ProductService productService;
    private final OrderService orderService;
    private final NotificationService notificationService;
    private final com.secondhand.mall.repository.AuditLogRepository auditLogRepository;

    public AdminApiController(AdminService adminService, UserService userService,
                              ProductService productService, OrderService orderService,
                              NotificationService notificationService,
                              com.secondhand.mall.repository.AuditLogRepository auditLogRepository) {
        this.adminService = adminService;
        this.userService = userService;
        this.productService = productService;
        this.orderService = orderService;
        this.notificationService = notificationService;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 校验当前用户是否为管理员
     */
    private void checkAdmin(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null || !adminService.isAdmin(username)) {
            throw new com.secondhand.mall.exception.ForbiddenException("无管理员权限");
        }
    }

    @PostMapping("/login")
    public Map<String, Object> adminLogin(@RequestBody Map<String, String> request,
                                          jakarta.servlet.http.HttpServletRequest httpRequest,
                                          HttpSession session) {
        try {
            LoginRequest loginReq = new LoginRequest();
            loginReq.setUsername(request.get("username"));
            loginReq.setPassword(request.get("password"));
            // 先验角色再走 login：login 会写会话指纹，普通用户拿自己口令打这个接口
            // 若先 login 就会把受害者已有会话踢下线（失败路径不回滚）
            User peek;
            try {
                peek = userService.findByUsername(loginReq.getUsername());
            } catch (RuntimeException ex) {
                // 与 login 同款统一文案，防用户枚举
                return fail("用户名或密码错误").toMap();
            }
            if (!"admin".equals(peek.getRole())) {
                return fail("无管理员权限").toMap();
            }
            User user = userService.login(loginReq, session.getId());

            // 防会话固定：登录成功后更换会?ID
            String newSessionId = httpRequest.changeSessionId();
            session.setAttribute("username", user.getUsername());
            session.setAttribute("nickname", user.getNickname());
            session.setAttribute("role", user.getRole());
            userService.setSessionId(user.getUsername(), newSessionId);

            return ok("登录成功").put("username", user.getUsername()).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats(HttpSession session) {
        checkAdmin(session);
        Map<String, Object> stats = adminService.getStats();
        return ok("获取成功").putAll(stats).toMap();
    }

    @GetMapping("/orders")
    public Map<String, Object> getAllOrders(HttpSession session) {
        checkAdmin(session);
        List<Order> orders = adminService.getAllOrders();
        return ok("获取成功").put("orders", orders).put("total", orders.size()).toMap();
    }

    @GetMapping("/reports")
    public Map<String, Object> getReports(HttpSession session) {
        checkAdmin(session);
        List<Report> reports = adminService.getAllReports();
        return ok("获取成功").put("reports", reports).put("total", reports.size()).toMap();
    }

    @PutMapping("/reports/{id}")
    public Map<String, Object> updateReport(@PathVariable Long id, @RequestBody Map<String, String> body, HttpSession session) {
        try {
            checkAdmin(session);
            String status = body.getOrDefault("status", "resolved");
            String action = body.getOrDefault("action", "none");
            String handler = (String) session.getAttribute("username");
            Report r = adminService.resolveReport(id, status, action, handler);
            return ok("已处").put("report", r).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @GetMapping("/products")
    public Map<String, Object> getAllProducts(HttpSession session) {
        checkAdmin(session);
        List<Product> products = productService.getAllProducts();
        return ok("获取成功").put("products", products).put("total", products.size()).toMap();
    }

    @DeleteMapping("/products/{id}")
    public Map<String, Object> deleteProduct(@PathVariable Long id, HttpSession session) {
        checkAdmin(session);
        productService.adminDeleteProduct(id);
        return ok("已移入回收站").toMap();
    }

    /** 回收站：已删除商?*/
    @GetMapping("/recycle/products")
    public Map<String, Object> getRecycleProducts(HttpSession session) {
        checkAdmin(session);
        List<Product> products = productService.getDeletedProducts();
        return ok("获取成功").put("products", products).put("total", products.size()).toMap();
    }

    /** 回收站：恢复商品 */
    @PostMapping("/recycle/products/{id}/restore")
    public Map<String, Object> restoreProduct(@PathVariable Long id, HttpSession session) {
        try {
            checkAdmin(session);
            productService.restoreProduct(id);
            return ok("商品已恢").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 回收站：彻底删除商品 */
    @DeleteMapping("/recycle/products/{id}")
    public Map<String, Object> purgeProduct(@PathVariable Long id, HttpSession session) {
        try {
            checkAdmin(session);
            productService.purgeProduct(id);
            return ok("商品已彻底删").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 回收站：已注销/删除用户 */
    @GetMapping("/recycle/users")
    public Map<String, Object> getRecycleUsers(HttpSession session) {
        checkAdmin(session);
        List<User> users = adminService.getDeletedUsers();
        return ok("获取成功").put("users", users).put("total", users.size()).toMap();
    }

    /** 回收站：恢复用户 */
    @PostMapping("/recycle/users/{id}/restore")
    public Map<String, Object> restoreUser(@PathVariable Long id, HttpSession session) {
        try {
            checkAdmin(session);
            User user = adminService.restoreUser(id);
            return ok("用户已恢").put("user", user).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 回收站：彻底删除用户 */
    @DeleteMapping("/recycle/users/{id}")
    public Map<String, Object> purgeUser(@PathVariable Long id, HttpSession session) {
        try {
            checkAdmin(session);
            adminService.purgeUser(id);
            return ok("用户已彻底删").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @GetMapping("/users")
    public Map<String, Object> getAllUsers(HttpSession session) {
        checkAdmin(session);
        List<User> users = adminService.getAllUsers();
        return ok("获取成功").put("users", users).put("total", users.size()).toMap();
    }

    @DeleteMapping("/users/{id}")
    public Map<String, Object> deleteUser(@PathVariable Long id, HttpSession session) {
        checkAdmin(session);
        adminService.deleteUser(id);
        return ok("已移入回收站").toMap();
    }

    /** 封禁 / 解封用户 */
    @PostMapping("/users/{id}/ban")
    public Map<String, Object> banUser(@PathVariable Long id,
                                       @RequestBody Map<String, Object> body,
                                       HttpSession session) {
        try {
            checkAdmin(session);
            boolean banned = Boolean.parseBoolean(String.valueOf(body.getOrDefault("banned", "true")));
            User user = adminService.setBanned(id, banned);
            return ok(banned ? "已封禁该用户" : "已解封该用户")
                    .put("user", user)
                    .toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 强制取消订单并退款（仅待付款/待发货；已履约须走售后） */
    @Audited(action = "强制取消订单", targetType = "order")
    @PostMapping("/orders/{id}/force-cancel")
    public Map<String, Object> forceCancelOrder(@PathVariable Long id,
                                                @RequestBody(required = false) Map<String, String> body,
                                                HttpSession session) {
        try {
            // 二次校验：会话 + 落库角色，仅管理员可操作
            String username = (String) session.getAttribute("username");
            if (username == null || !adminService.isAdmin(username)) {
                throw new com.secondhand.mall.exception.ForbiddenException("无管理员权限");
            }
            String reason = body == null ? null : body.get("reason");
            if (reason == null || reason.isBlank()) {
                return fail("请填写强制取消原因").toMap();
            }
            Order order = orderService.adminForceCancel(id, username, reason);
            return ok("已强制取消").put("order", order).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 管理员下架商品（前台不可见，不进回收站） */
    @PostMapping("/products/{id}/offline")
    public Map<String, Object> offlineProduct(@PathVariable Long id, HttpSession session) {
        try {
            checkAdmin(session);
            productService.adminOfflineProduct(id);
            return ok("商品已下").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 管理员重新上架商?*/
    @PostMapping("/products/{id}/online")
    public Map<String, Object> onlineProduct(@PathVariable Long id, HttpSession session) {
        try {
            checkAdmin(session);
            productService.adminOnlineProduct(id);
            return ok("商品已上").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 操作审计：谁在什么时候做了什么（AOP 落库?*/
    @GetMapping("/audit-logs")
    public Map<String, Object> auditLogs(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
                                         @org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int size,
                                         @org.springframework.web.bind.annotation.RequestParam(required = false) String operator,
                                         HttpSession session) {
        try {
            checkAdmin(session);
            int pageSize = Math.max(1, Math.min(size, 200));
            var pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page), pageSize);
            var result = (operator == null || operator.isBlank())
                    ? auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                    : auditLogRepository.findByOperatorContainingOrderByCreatedAtDesc(operator.trim(), pageable);
            return ok("获取成功")
                    .put("logs", result.getContent())
                    .put("total", result.getTotalElements())
                    .put("totalPages", result.getTotalPages())
                    .put("currentPage", result.getNumber())
                    .toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 商品审核：通过 → 上架，并通知发布人 */
    @PostMapping("/products/{id}/approve")
    public Map<String, Object> approveProduct(@PathVariable Long id, HttpSession session) {
        try {
            checkAdmin(session);
            Product product = productService.approveProduct(id);
            notificationService.push(product.getSellerUsername(), "product", "商品审核通过",
                    "「" + product.getName() + "」已通过审核并上架销售", "product", String.valueOf(id));
            return ok("已通过审核并上架").put("product", product).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    /** 商品审核：驳回并写明理由，卖家可修改后重新提交 */
    @PostMapping("/products/{id}/reject")
    public Map<String, Object> rejectProduct(@PathVariable Long id,
                                             @RequestBody Map<String, Object> body,
                                             HttpSession session) {
        try {
            checkAdmin(session);
            Object reason = body.get("reason");
            Product product = productService.rejectProduct(id, reason == null ? null : String.valueOf(reason));
            notificationService.push(product.getSellerUsername(), "product", "商品未通过审核",
                    "「" + product.getName() + "」未通过审核" + product.getReviewNote()
                            + "，修改后可重新提交", "product", String.valueOf(id));
            return ok("已驳回").put("product", product).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }
}

