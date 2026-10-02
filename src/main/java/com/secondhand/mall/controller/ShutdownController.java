package com.secondhand.mall.controller;

import com.secondhand.mall.service.AdminService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 开发专用：优雅关闭应用，释放端口
 */
@RestController
public class ShutdownController {

    @Autowired
    private ApplicationContext applicationContext;

    private final AdminService adminService;

    public ShutdownController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/api/graceful-shutdown")
    public Map<String, Object> shutdown(HttpSession session) {
        // 仅管理员可关闭
        String username = (String) session.getAttribute("username");
        if (username == null || !adminService.isAdmin(username)) {
            throw new RuntimeException("无权限执行此操作");
        }

        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
            SpringApplication.exit(applicationContext, () -> 0);
            System.exit(0);
        }).start();

        return Map.of("success", true, "message", "服务器正在优雅关闭...");
    }
}
