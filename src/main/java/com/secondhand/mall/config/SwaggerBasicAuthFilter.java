package com.secondhand.mall.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 开发环境 Swagger Basic Auth。
 * 生产环境已通过 springdoc.api-docs.enabled=false 关闭，无需本过滤器。
 */
@Component
@Order(-300)
public class SwaggerBasicAuthFilter implements Filter {

    private final boolean enabled;
    private final String username;
    private final String password;

    public SwaggerBasicAuthFilter(
            @Value("${mall.security.swagger-basic-auth-enabled:true}") boolean enabled,
            @Value("${mall.security.swagger-basic-username:swagger}") String username,
            @Value("${mall.security.swagger-basic-password:swagger-dev}") String password) {
        this.enabled = enabled;
        this.username = username;
        this.password = password;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String uri = req.getRequestURI();
        boolean isSwagger = uri.startsWith("/v3/api-docs") || uri.startsWith("/swagger-ui")
                || uri.equals("/swagger-ui.html") || uri.startsWith("/swagger-ui/");
        if (!enabled || !isSwagger) {
            chain.doFilter(request, response);
            return;
        }
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Basic ")) {
            try {
                String decoded = new String(Base64.getDecoder().decode(auth.substring(6)), StandardCharsets.UTF_8);
                int sep = decoded.indexOf(':');
                if (sep > 0) {
                    String u = decoded.substring(0, sep);
                    String p = decoded.substring(sep + 1);
                    if (username.equals(u) && password.equals(p)) {
                        chain.doFilter(request, response);
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        res.setHeader("WWW-Authenticate", "Basic realm=\"Swagger\"");
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"success\":false,\"message\":\"Swagger 需要 Basic Auth\"}");
    }
}