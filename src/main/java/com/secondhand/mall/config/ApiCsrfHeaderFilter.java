package com.secondhand.mall.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 轻量 CSRF 防护：写操作（POST/PUT/PATCH/DELETE）必须携带
 * X-Requested-With: XMLHttpRequest 或 CSRF Token 头（X-CSRF-Token / X-XSRF-TOKEN）。
 * 浏览器跨站表单无法伪造这些头，配合 Session Cookie 的 SameSite=lax 与 Spring CSRF Cookie 双保险。
 * 公开探测类 GET 不受影响；前端 client.js 统一注入。
 */
@Component
@Order(2)
public class ApiCsrfHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Requested-With";
    public static final String HEADER_VALUE = "XMLHttpRequest";
    public static final String CSRF_TOKEN_HEADER = "X-CSRF-Token";
    public static final String CSRF_TOKEN_HEADER_ALT = "X-XSRF-TOKEN";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }
        String uri = request.getRequestURI();
        // 仅保护业务 API；静态资源与页面表单不走这里
        if (!uri.startsWith("/api/") && !uri.startsWith("/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String xrw = request.getHeader(HEADER);
        String csrf = request.getHeader(CSRF_TOKEN_HEADER);
        if (csrf == null || csrf.isBlank()) {
            csrf = request.getHeader(CSRF_TOKEN_HEADER_ALT);
        }
        boolean okXrw = xrw != null && HEADER_VALUE.equalsIgnoreCase(xrw);
        boolean okToken = csrf != null && !csrf.isBlank();
        if (!okXrw && !okToken) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"非法请求来源\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
