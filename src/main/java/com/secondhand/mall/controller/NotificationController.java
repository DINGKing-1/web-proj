package com.secondhand.mall.controller;

import com.secondhand.mall.entity.Notification;
import com.secondhand.mall.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController extends BaseApiController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Map<String, Object> list(HttpSession session) {
        String username = getLoginUsername(session);
        List<Notification> items = notificationService.list(username);
        long unread = notificationService.unreadCount(username);
        return ok("获取成功").put("notifications", items).put("unread", unread).toMap();
    }

    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount(HttpSession session) {
        String username = getLoginUsername(session);
        return ok("获取成功").put("unread", notificationService.unreadCount(username)).toMap();
    }

    @PostMapping("/read-all")
    public Map<String, Object> readAll(HttpSession session) {
        String username = getLoginUsername(session);
        notificationService.markAllRead(username);
        return ok("已全部标为已读").toMap();
    }

    @PostMapping("/{id}/read")
    public Map<String, Object> readOne(@PathVariable Long id, HttpSession session) {
        String username = getLoginUsername(session);
        notificationService.markRead(username, id);
        return ok("已读").toMap();
    }
}
