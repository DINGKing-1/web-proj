package com.secondhand.mall.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端 SPA 路由转发：所有页面路由统一返回 index.html，
 * 由 Vue Router 在浏览器端接管。业务 API 保持不变。
 */
@Controller
public class SpaController {

    private static final String SPA_FORWARD = "forward:/index.html";

    @GetMapping({
            "/",
            "/login",
            "/register",
            "/search",
            "/upload",
            "/cart",
            "/checkout",
            "/orders",
            "/my-orders",
            "/user",
            "/favorites",
            "/messages",
            "/seller-orders",
            "/admin",
            "/admin/dashboard",
            "/admin/products",
            "/admin/users",
            "/category/{name}",
            "/product/{id}",
            "/order/{id}"
    })
    public String spa() {
        return SPA_FORWARD;
    }
}
