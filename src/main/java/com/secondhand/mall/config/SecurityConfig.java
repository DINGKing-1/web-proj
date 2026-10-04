package com.secondhand.mall.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF：默认 Token 与 SPA fetch 不兼容，改用 ApiCsrfHeaderFilter（X-Requested-With）
            // + Session Cookie SameSite=lax 共同防护
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                // 静态资源 & 公开页面
                .requestMatchers("/css/**", "/js/**", "/product-images/**", "/user-avatar/**").permitAll()
                .requestMatchers("/", "/login", "/register", "/search", "/category/**", "/product/**", "/shop/**", "/notifications", "/order/**").permitAll()
                // 公开 API
                .requestMatchers("/api/login", "/api/register").permitAll()
                .requestMatchers("/api/products", "/api/products/**").permitAll()
                .requestMatchers("/api/admin/login").permitAll()
                .requestMatchers("/api/**").permitAll()
                // 管理后台页面
                .requestMatchers("/admin", "/admin/**").permitAll()
                // 鉴权由 SessionValidationFilter（未登录 401）+ 各 Controller 的
                // getLoginUsername / checkAdmin 完成；新增写接口务必自行校验
                .anyRequest().permitAll()
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
            );

        return http.build();
    }
}
