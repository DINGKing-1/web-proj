package com.secondhand.mall.config;

import com.secondhand.mall.service.UserService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.List;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@Order(-200)
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
        String sessionUser = session == null ? null : (String) session.getAttribute("username");

        // 未登录/空会话访问需要认证的 API：一律 401，禁止「有 session 无用户」绕过
        if (uri.startsWith("/api/") && isAuthRequired(uri, httpRequest.getMethod())
                && (session == null || sessionUser == null || sessionUser.isBlank())) {
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\"success\":false,\"message\":\"请先登录\"}");
            return;
        }

        if (session != null) {
            String username = sessionUser;
            if (username != null && !username.isBlank()) {
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
                // 桥接到 Spring Security，供 authorizeHttpRequests 判定（须在 Security 过滤链之前执行）
                String role = userService.roleOf(username);
                String authority = "admin".equals(role) ? "ROLE_ADMIN" : "ROLE_USER";
                var auth = new UsernamePasswordAuthenticationToken(
                        username, null, List.of(new SimpleGrantedAuthority(authority)));
                SecurityContextHolder.getContext().setAuthentication(auth);
                // 更新用户最后活跃时间
                userService.updateLastActiveTime(username);

                // 首次登录强制改密：除改密/退出/会话接口外一律拦截
                Boolean mustChange = session.getAttribute("mustChangePassword") != null
                        ? (Boolean) session.getAttribute("mustChangePassword")
                        : userService.mustChangePassword(username);
                if (Boolean.TRUE.equals(mustChange) && uri.startsWith("/api/")
                        && !isPasswordChangeAllowed(uri, httpRequest.getMethod())) {
                    httpResponse.setContentType("application/json;charset=UTF-8");
                    httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    httpResponse.getWriter().write(
                            "{\"success\":false,\"code\":\"MUST_CHANGE_PASSWORD\",\"message\":\"首次登录请先修改密码\"}");
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    /** 强制改密期间允许的接口 */
    private static boolean isPasswordChangeAllowed(String uri, String method) {
        return uri.equals("/api/session")
                || uri.equals("/api/csrf")
                || uri.equals("/api/logout")
                || uri.startsWith("/api/user/change-password")
                || uri.startsWith("/api/user/reset-")
                || uri.startsWith("/api/user/email-code")
                || ("GET".equals(method) && uri.startsWith("/api/user/me"));
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
                && !uri.equals("/api/csrf")
                && !uri.startsWith("/api/products")
                && !uri.startsWith("/api/sellers")
                && !uri.startsWith("/api/search/")
                && !uri.equals("/api/support/faq")
                && !uri.startsWith("/api/reports")
                && !uri.equals("/api/announcements")
                && !uri.equals("/api/categories");
    }
}
