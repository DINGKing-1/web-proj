package com.secondhand.mall.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 暴露 CSRF Token（Cookie XSRF-TOKEN + 响应体），供 SPA 写请求携带 X-XSRF-TOKEN。
 */
@RestController
@RequestMapping("/api")
public class CsrfController {

    @GetMapping("/csrf")
    public Map<String, Object> csrf(CsrfToken token, HttpSession session) {
        return Map.of(
                "success", true,
                "token", token == null ? "" : token.getToken(),
                "headerName", token == null ? "X-XSRF-TOKEN" : token.getHeaderName()
        );
    }
}
