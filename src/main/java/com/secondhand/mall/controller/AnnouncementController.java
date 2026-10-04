package com.secondhand.mall.controller;

import com.secondhand.mall.exception.ForbiddenException;

import com.secondhand.mall.entity.Announcement;
import com.secondhand.mall.service.AdminService;
import com.secondhand.mall.service.AnnouncementService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AnnouncementController extends BaseApiController {

    private final AnnouncementService announcementService;
    private final AdminService adminService;

    public AnnouncementController(AnnouncementService announcementService, AdminService adminService) {
        this.announcementService = announcementService;
        this.adminService = adminService;
    }

    /** 首页公告/轮播（公开?*/
    @GetMapping("/announcements")
    public Map<String, Object> listEnabled() {
        List<Announcement> items = announcementService.listEnabled();
        return ok("获取成功").put("announcements", items).put("total", items.size()).toMap();
    }

    private void checkAdmin(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null || !adminService.isAdmin(username)) {
            throw new com.secondhand.mall.exception.ForbiddenException("无管理员权限");
        }
    }

    @GetMapping("/admin/announcements")
    public Map<String, Object> listAll(HttpSession session) {
        checkAdmin(session);
        List<Announcement> items = announcementService.listAll();
        return ok("获取成功").put("announcements", items).toMap();
    }

    @PostMapping("/admin/announcements")
    public Map<String, Object> create(@RequestBody Announcement body, HttpSession session) {
        try {
            checkAdmin(session);
            Announcement a = announcementService.create(body);
            return ok("创建成功").put("announcement", a).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @PutMapping("/admin/announcements/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Announcement body, HttpSession session) {
        try {
            checkAdmin(session);
            Announcement a = announcementService.update(id, body);
            return ok("已更").put("announcement", a).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @DeleteMapping("/admin/announcements/{id}")
    public Map<String, Object> delete(@PathVariable Long id, HttpSession session) {
        try {
            checkAdmin(session);
            announcementService.delete(id);
            return ok("已删").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }
}

