package com.secondhand.mall.config;

import com.secondhand.mall.service.UserService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@Order(1)
public class SessionValidationFilter implements Filter {

    private final UserService userService;

    public SessionValidationFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI();
        if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/product-images/") ||
            uri.startsWith("/user-avatar/") || uri.startsWith("/error") || uri.startsWith("/favicon")) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = httpRequest.getSession(false);

        // 未登录用户访问需要认证的 API 端点时，直接返回 401
        // 公开接口（仅 GET）：商品列表/详情/评论、店铺、搜索、举报列表等；
        // 写方法（POST/PUT/DELETE）一律要求登录，防「按前缀放行」把写接口裸奔
        if (session == null && uri.startsWith("/api/") && isAuthRequired(uri, httpRequest.getMethod())) {
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\"success\":false,\"message\":\"请先登录\"}");
            return;
        }

        if (session != null) {
            String username = (String) session.getAttribute("username");
            if (username != null) {
                String sessionId = session.getId();
                if (!userService.validateSession(username, sessionId)) {
                    session.invalidate();
                    if (uri.startsWith("/api/")) {
                        httpResponse.setContentType("application/json;charset=UTF-8");
                        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        httpResponse.getWriter().write(
                                "{\"success\":false,\"code\":\"SESSION_KICKED\",\"message\":\"您的账号已在其他地方登录\"}");
                        return;
                    }
                    // 公开页被踢：回首页继续逛，不强制进登录页；需登录的页面才跳登录
                    boolean needLogin = uri.startsWith("/cart") || uri.startsWith("/checkout")
                            || uri.startsWith("/orders") || uri.startsWith("/my-orders")
                            || uri.startsWith("/order/") || uri.startsWith("/user")
                            || uri.startsWith("/favorites") || uri.startsWith("/notifications")
                            || uri.startsWith("/support") || uri.startsWith("/messages")
                            || uri.startsWith("/upload") || uri.startsWith("/seller-orders")
                            || uri.startsWith("/admin/");
                    httpResponse.sendRedirect(needLogin ? "/login?kicked=1" : "/?kicked=1");
                    return;
                }
                // 更新用户最后活跃时间
                userService.updateLastActiveTime(username);
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * 该请求是否需要登录：
     * - 写方法（POST/PUT/DELETE/PATCH）一律需要登录（登录/注册/找回密码除外）
     * - GET 白名单按精确路径/前缀放行
     */
    private static boolean isAuthRequired(String uri, String method) {
        boolean write = !"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method);
        if (write) {
            // 公开的写接口仅认证类
            return !uri.equals("/api/login") && !uri.equals("/api/register")
                    && !uri.equals("/api/admin/login")
                    && !uri.equals("/api/user/reset-password") && !uri.equals("/api/user/reset-code");
        }
        return !uri.equals("/api/session")
                && !uri.startsWith("/api/products")
                && !uri.startsWith("/api/sellers")
                && !uri.startsWith("/api/search/")
                && !uri.equals("/api/support/faq")
                && !uri.startsWith("/api/reports")
                && !uri.equals("/api/announcements")
                && !uri.equals("/api/categories");
    }
}
