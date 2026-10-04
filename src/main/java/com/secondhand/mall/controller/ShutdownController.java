package com.secondhand.mall.controller;

import com.secondhand.mall.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 运维关闭：仅本机 + 管理员可调用（stop.ps1 / restart.ps1 使用）
 */
@RestController
public class ShutdownController {

    @Autowired
    private ApplicationContext applicationContext;

    private final AdminService adminService;

    public ShutdownController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping({"/internal/shutdown", "/api/internal/shutdown"})
    public Map<String, Object> internalShutdown(HttpSession session, HttpServletRequest request) {
        if (!isLocal(request)) {
            return Map.of("success", false, "message", "仅限本机调用");
        }
        String username = (String) session.getAttribute("username");
        if (username == null || !adminService.isAdmin(username)) {
            return Map.of("success", false, "message", "无权执行此操作");
        }
        exitAsync();
        return Map.of("success", true, "message", "应用即将关闭");
    }

    @PostMapping("/api/graceful-shutdown")
    public Map<String, Object> shutdown(HttpSession session, HttpServletRequest request) {
        if (!isLocal(request)) {
            return Map.of("success", false, "message", "仅限本机调用");
        }
        // 仅管理员可关闭
        String username = (String) session.getAttribute("username");
        if (username == null || !adminService.isAdmin(username)) {
            throw new RuntimeException("无权限执行此操作");
        }
        exitAsync();
        return Map.of("success", true, "message", "服务器正在优雅关闭...");
    }

    private void exitAsync() {
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
            SpringApplication.exit(applicationContext, () -> 0);
            System.exit(0);
        }).start();
    }

    private static boolean isLocal(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote != null
                && (remote.equals("127.0.0.1") || remote.equals("0:0:0:0:0:0:0:1")
                    || remote.equals("::1"));
    }
}
