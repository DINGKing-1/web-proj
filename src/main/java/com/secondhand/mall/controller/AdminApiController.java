package com.secondhand.mall.controller;

import com.secondhand.mall.dto.LoginRequest;
import com.secondhand.mall.entity.Product;
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

    public AdminApiController(AdminService adminService, UserService userService,
                              ProductService productService, OrderService orderService) {
        this.adminService = adminService;
        this.userService = userService;
        this.productService = productService;
        this.orderService = orderService;
    }

    /**
     * 校验当前用户是否为管理员
     */
    private void checkAdmin(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null || !adminService.isAdmin(username)) {
            throw new RuntimeException("无管理员权限");
        }
    }

    @PostMapping("/login")
    public Map<String, Object> adminLogin(@RequestBody Map<String, String> request, HttpSession session) {
        try {
            LoginRequest loginReq = new LoginRequest();
            loginReq.setUsername(request.get("username"));
            loginReq.setPassword(request.get("password"));
            User user = userService.login(loginReq, session.getId());

            if (!"admin".equals(user.getRole())) {
                return fail("无管理员权限").toMap();
            }

            session.setAttribute("username", user.getUsername());
            session.setAttribute("nickname", user.getNickname());
            session.setAttribute("role", user.getRole());

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
        return ok("删除成功").toMap();
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
        return ok("删除成功").toMap();
    }
}
