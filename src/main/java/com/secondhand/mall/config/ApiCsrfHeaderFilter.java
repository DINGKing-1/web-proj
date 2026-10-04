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
 * 轻量 CSRF 防护：写操作（POST/PUT/PATCH/DELETE）必须携带自定义头 X-Requested-With。
 * 浏览器跨站表单无法设置该头，配合 Session Cookie 的 SameSite=lax 可阻断常见 CSRF。
 * 公开探测类 GET 不受影响；前端 client.js 统一注入该头。
 */
@Component
@Order(2)
public class ApiCsrfHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Requested-With";
    public static final String HEADER_VALUE = "XMLHttpRequest";

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
        String header = request.getHeader(HEADER);
        if (header == null || !HEADER_VALUE.equalsIgnoreCase(header)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"非法请求来源\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
