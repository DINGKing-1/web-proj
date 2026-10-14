from pathlib import Path

# ---- SessionValidationFilter: empty session 401 + SecurityContext bridge ----
p = Path('src/main/java/com/secondhand/mall/config/SessionValidationFilter.java')
t = p.read_text(encoding='utf-8')

old_imports = '''import com.secondhand.mall.service.UserService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;'''
new_imports = '''import com.secondhand.mall.service.UserService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.List;'''
if old_imports not in t:
    raise SystemExit('imports not found')
t = t.replace(old_imports, new_imports, 1)

old_body = '''        HttpSession session = httpRequest.getSession(false);

        // 未登录用户访问需要认证的 API 端点时，直接返回 401
        // 公开接口（仅 GET）：商品列表/详情/评论、店铺、搜索、举报列表等；
        // 写方法（POST/PUT/DELETE）一律要求登录，防「按前缀放行」把写接口裸奔
        if (session == null && uri.startsWith("/api/") && isAuthRequired(uri, httpRequest.getMethod())) {
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\\"success\\":false,\\"message\\":\\"请先登录\\"}");
            return;
        }

        if (session != null) {
            String username = (String) session.getAttribute("username");
            if (username != null) {'''
new_body = '''        HttpSession session = httpRequest.getSession(false);
        String sessionUser = session == null ? null : (String) session.getAttribute("username");

        // 未登录/空会话访问需要认证的 API：一律 401，禁止「有 session 无用户」绕过
        if (uri.startsWith("/api/") && isAuthRequired(uri, httpRequest.getMethod())
                && (session == null || sessionUser == null || sessionUser.isBlank())) {
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\\"success\\":false,\\"message\\":\\"请先登录\\"}");
            return;
        }

        if (session != null) {
            String username = sessionUser;
            if (username != null && !username.isBlank()) {'''
if old_body not in t:
    raise SystemExit('body not found')
t = t.replace(old_body, new_body, 1)

old_active = '''                // 更新用户最后活跃时间
                userService.updateLastActiveTime(username);
            }
        }

        chain.doFilter(request, response);'''
new_active = '''                // 桥接到 Spring Security，供 authorizeHttpRequests 的 authenticated() 判定
                var auth = new UsernamePasswordAuthenticationToken(
                        username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
                // 更新用户最后活跃时间
                userService.updateLastActiveTime(username);
            }
        }

        chain.doFilter(request, response);'''
if old_active not in t:
    raise SystemExit('active block not found')
t = t.replace(old_active, new_active, 1)

p.write_text(t, encoding='utf-8')
print('SessionValidationFilter OK')
