package com.secondhand.mall.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.mall.entity.AuditLog;
import com.secondhand.mall.repository.AuditLogRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作审计切面：拦截 @Audited 方法，无论成功失败都写一条 audit_logs。
 * 记录本身绝不能影响业务 —— 任何异常只打日志。
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final int DETAIL_LIMIT = 480;

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditAspect(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(audited)")
    public Object record(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        boolean success = true;
        try {
            return pjp.proceed();
        } catch (Throwable e) {
            success = false;
            throw e;
        } finally {
            try {
                String operator = currentUsername();
                Object[] args = pjp.getArgs();
                String targetId = extractTargetId(args);
                String detail = buildDetail(args);
                auditLogRepository.save(new AuditLog(
                        operator == null ? "unknown" : operator,
                        audited.action(),
                        audited.targetType().isEmpty() ? null : audited.targetType(),
                        targetId,
                        detail,
                        success));
            } catch (Exception e) {
                log.warn("审计日志写入失败: {}", e.getMessage());
            }
        }
    }

    private String currentUsername() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes s) {
                HttpSession session = s.getRequest().getSession(false);
                if (session != null) {
                    return (String) session.getAttribute("username");
                }
            }
        } catch (Exception ignored) {
            // 非 web 上下文（如测试）直接跳过
        }
        return null;
    }

    /** 第一个 Long 参数通常就是被操作对象的 id */
    private String extractTargetId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Long l) return String.valueOf(l);
        }
        return null;
    }

    private String buildDetail(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg == null) continue;
            if (arg instanceof HttpSession || arg instanceof HttpServletRequest
                    || arg instanceof HttpServletResponse) {
                continue;
            }
            if (sb.length() > 0) sb.append(" | ");
            sb.append(stringify(arg));
        }
        String s = sb.toString();
        return s.length() > DETAIL_LIMIT ? s.substring(0, DETAIL_LIMIT) : s;
    }

    /** 简单类型直接拼；DTO/实体用 Jackson 序列化，方便后台一眼看懂改了什么 */
    private String stringify(Object arg) {
        if (arg instanceof CharSequence || arg instanceof Number || arg instanceof Boolean
                || arg instanceof java.time.temporal.Temporal || arg.getClass().isEnum()) {
            return String.valueOf(arg);
        }
        try {
            return objectMapper.writeValueAsString(arg);
        } catch (Exception e) {
            return String.valueOf(arg);
        }
    }
}
