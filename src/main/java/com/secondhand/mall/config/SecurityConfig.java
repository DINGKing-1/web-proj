package com.secondhand.mall.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        // SPA fetch 自定义头 X-XSRF-TOKEN / X-Requested-With（ApiCsrfHeaderFilter 双保险）
        requestHandler.setCsrfRequestAttributeName(null);
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(requestHandler)
                // 认证接口先拿 Token 再登录；这些路径另有 ApiCsrfHeaderFilter + SameSite 兜底
                .ignoringRequestMatchers("/api/login", "/api/register", "/api/admin/login",
                        "/api/user/reset-password", "/api/user/reset-code")
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                // 静态资源 & 公开页面
                .requestMatchers("/css/**", "/js/**", "/product-images/**", "/user-avatar/**").permitAll()
                .requestMatchers("/", "/login", "/register", "/search", "/category/**", "/product/**", "/shop/**").permitAll()
                // 公开 API（只读商品/搜索/店铺/公告等）
                .requestMatchers("/api/login", "/api/register").permitAll()
                .requestMatchers("/api/csrf").permitAll()
                .requestMatchers("/api/admin/login").permitAll()
                .requestMatchers("/api/user/reset-password", "/api/user/reset-code").permitAll()
                .requestMatchers("/api/session").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/sellers/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/search/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categories").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/announcements").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/support/faq").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/reports/**").permitAll()
                // 管理端登录页壳
                // Swagger：开发环境 Basic Auth，生产已通过 springdoc.enabled=false 关闭
                // Swagger 由 SwaggerBasicAuthFilter 做 Basic Auth（开发环境）
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/admin", "/admin/login").permitAll()
                // 其余一律要求已认证（SessionValidationFilter 桥接 SecurityContext）
                .requestMatchers("/api/admin/**", "/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            // 退出统一走 ApiController#logout（清 sessionId + invalidate），避免 LogoutFilter 抢占导致双路径
            .logout(logout -> logout.disable())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"success\":false,\"message\":\"请先登录\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"success\":false,\"message\":\"无管理员权限\"}");
                })
            );

        return http.build();
    }
}
