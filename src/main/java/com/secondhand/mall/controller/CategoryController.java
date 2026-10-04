package com.secondhand.mall.controller;

import com.secondhand.mall.exception.ForbiddenException;

import com.secondhand.mall.entity.Category;
import com.secondhand.mall.service.AdminService;
import com.secondhand.mall.service.CategoryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 分类：前台读启用项，后台增删改排?*/
@RestController
@RequestMapping("/api")
public class CategoryController extends BaseApiController {

    private final CategoryService categoryService;
    private final AdminService adminService;

    public CategoryController(CategoryService categoryService, AdminService adminService) {
        this.categoryService = categoryService;
        this.adminService = adminService;
    }

    /** 分类导航 / 筛?/ 发布表单用（公开?*/
    @GetMapping("/categories")
    public Map<String, Object> listEnabled() {
        List<Category> items = categoryService.listEnabled();
        List<String> names = items.stream().map(Category::getName).toList();
        return ok("获取成功").put("categories", items).put("names", names).put("total", names.size()).toMap();
    }

    private void checkAdmin(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (username == null || !adminService.isAdmin(username)) {
            throw new com.secondhand.mall.exception.ForbiddenException("无管理员权限");
        }
    }

    /** 后台：全部分?+ 各自商品数（含停用） */
    @GetMapping("/admin/categories")
    public Map<String, Object> listAll(HttpSession session) {
        checkAdmin(session);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Category c : categoryService.listAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("name", c.getName());
            row.put("sort", c.getSort());
            row.put("enabled", c.getEnabled());
            row.put("createdAt", c.getCreatedAt());
            row.put("productCount", categoryService.productCount(c.getName()));
            rows.add(row);
        }
        return ok("获取成功").put("categories", rows).put("total", rows.size()).toMap();
    }

    @PostMapping("/admin/categories")
    public Map<String, Object> create(@RequestBody Category body, HttpSession session) {
        try {
            checkAdmin(session);
            Category c = categoryService.create(body);
            return ok("创建成功").put("category", c).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @PutMapping("/admin/categories/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Category body, HttpSession session) {
        try {
            checkAdmin(session);
            Category c = categoryService.update(id, body);
            return ok("已更").put("category", c).toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }

    @DeleteMapping("/admin/categories/{id}")
    public Map<String, Object> delete(@PathVariable Long id, HttpSession session) {
        try {
            checkAdmin(session);
            categoryService.delete(id);
            return ok("已删").toMap();
        } catch (RuntimeException e) {
            return fail(e.getMessage()).toMap();
        }
    }
}

