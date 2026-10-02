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
        if (session == null && uri.startsWith("/api/")
                && !uri.equals("/api/login") && !uri.equals("/api/register")
                && !uri.equals("/api/admin/login")
                && !uri.equals("/api/session")
                && !uri.startsWith("/api/products")) {
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
                        httpResponse.getWriter().write("{\"success\":false,\"message\":\"您的账号已在其他地方登录\"}");
                        return;
                    }
                    httpResponse.sendRedirect("/login?kicked=1");
                    return;
                }
                // 更新用户最后活跃时间
                userService.updateLastActiveTime(username);
            }
        }

        chain.doFilter(request, response);
    }
}
