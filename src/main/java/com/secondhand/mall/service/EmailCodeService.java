package com.secondhand.mall.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 邮箱验证码（找回密码 / 登录后修改密码）。
 * 配置了 MAIL_USERNAME 时走真实 SMTP 发信；验证码绝不写入日志，无 SMTP 时仅存存储并告警。
 * 不同用途的验证码相互隔离，找回密码的码不能拿来改密，反之亦然。
 */
@Service
public class EmailCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);
    private static final long CODE_TTL_MS = 10 * 60 * 1000L;
    private static final long RESEND_INTERVAL_MS = 60 * 1000L;
    private static final int MAX_VERIFY_TRIES = 5;

    /** 找回密码 */
    public static final String PURPOSE_RESET = "reset";
    /** 登录后修改密码 */
    public static final String PURPOSE_CHANGE = "change";
    /** 绑定/更换邮箱（码发往新邮箱） */
    public static final String PURPOSE_EMAIL = "email";

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailFrom;
    private final boolean logMailCodes;

    /** key = purpose|username|email → CodeRecord */
    private final Map<String, CodeRecord> store = new ConcurrentHashMap<>();

    private static final class CodeRecord {
        final String code;
        final long expireAt;
        final long lastSendAt;
        int tries;

        CodeRecord(String code, long expireAt, long lastSendAt) {
            this.code = code;
            this.expireAt = expireAt;
            this.lastSendAt = lastSendAt;
        }
    }

    public EmailCodeService(ObjectProvider<JavaMailSender> mailSenderProvider,
                            @Value("${spring.mail.username:}") String mailFrom,
                            @Value("${mall.security.log-mail-codes:false}") boolean logMailCodes) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailFrom = mailFrom;
        this.logMailCodes = logMailCodes;
    }

    /** 生成并发送验证码 */
    public void sendCode(String username, String email, String purpose) {
        String key = key(purpose, username, email);
        long now = System.currentTimeMillis();
        CodeRecord prev = store.get(key);
        if (prev != null && now - prev.lastSendAt < RESEND_INTERVAL_MS) {
            throw new RuntimeException("发送过于频繁，请 1 分钟后再试");
        }

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        store.put(key, new CodeRecord(code, now + CODE_TTL_MS, now));

        boolean isChange = PURPOSE_CHANGE.equals(purpose);
        boolean isEmail = PURPOSE_EMAIL.equals(purpose);
        String action = isChange ? "修改优品汇账号密码"
                : isEmail ? "绑定/更换优品汇账号邮箱"
                : "重置优品汇账号密码";
        String subject = isChange ? "【优品汇】修改密码验证码"
                : isEmail ? "【优品汇】邮箱绑定验证码"
                : "【优品汇】找回密码验证码";
        String text = "您好，\n\n您正在" + action + "。\n验证码：" + code
                + "\n\n10 分钟内有效，请勿转发给他人。\n若非本人操作，请忽略本邮件。";

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender != null && mailFrom != null && !mailFrom.isBlank()) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(mailFrom);
                message.setTo(email);
                message.setSubject(subject);
                message.setText(text);
                sender.send(message);
                log.info("[Mail] {}验证码已发送至 {}", isEmail ? "绑邮" : isChange ? "改密" : "重置", mask(email));
            } catch (Exception e) {
                // 禁止日志输出验证码等敏感信息，统一脱敏
                log.warn("[Mail] 发送失败（{}），验证码已生成并仅存存储不落日志。原因: {}", mask(email), e.getMessage());
            }
        } else {
            // 无 SMTP：验证码仅存存储，不打日志；触发配置告警
            log.warn("[Mail][CONFIG] 未配置 SMTP，验证码已生成但无法送达 {}。请配置 spring.mail.* / MAIL_* 环境变量后重试。", mask(email));
        }
    }

    /** 校验验证码；通过后立即作废 */
    public void verifyCode(String username, String email, String code, String purpose) {
        String key = key(purpose, username, email);
        CodeRecord rec = store.get(key);
        if (rec == null) {
            throw new RuntimeException("请先获取验证码");
        }
        if (System.currentTimeMillis() > rec.expireAt) {
            store.remove(key);
            throw new RuntimeException("验证码已过期，请重新获取");
        }
        if (rec.tries >= MAX_VERIFY_TRIES) {
            store.remove(key);
            throw new RuntimeException("尝试次数过多，请重新获取验证码");
        }
        rec.tries++;
        if (code == null || !code.trim().equals(rec.code)) {
            throw new RuntimeException("验证码错误");
        }
        store.remove(key);
    }

    /** 生成并发送找回密码验证码 */
    public void sendResetCode(String username, String email) {
        sendCode(username, email, PURPOSE_RESET);
    }

    /** 校验找回密码验证码；通过后立即作废 */
    public void verifyResetCode(String username, String email, String code) {
        verifyCode(username, email, code, PURPOSE_RESET);
    }

    /** 测试/联调用：查看当前有效验证码（不校验、不作废） */
    String peekCode(String username, String email, String purpose) {
        CodeRecord rec = store.get(key(purpose, username, email));
        return rec == null ? null : rec.code;
    }

    private static String key(String purpose, String username, String email) {
        return (purpose == null ? "" : purpose.trim().toLowerCase())
                + "|" + (username == null ? "" : username.trim().toLowerCase())
                + "|" + (email == null ? "" : email.trim().toLowerCase());
    }

    private static String mask(String email) {
        if (email == null || !email.contains("@")) return "***";
        int at = email.indexOf('@');
        String name = email.substring(0, at);
        String domain = email.substring(at);
        if (name.length() <= 2) return "**" + domain;
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }
}
