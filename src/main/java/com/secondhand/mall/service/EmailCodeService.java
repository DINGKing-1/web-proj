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
 * 找回密码邮箱验证码。
 * 配置了 MAIL_USERNAME 时走真实 SMTP 发信；否则开发模式：验证码打进日志，便于本地联调。
 */
@Service
public class EmailCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);
    private static final long CODE_TTL_MS = 10 * 60 * 1000L;
    private static final long RESEND_INTERVAL_MS = 60 * 1000L;
    private static final int MAX_VERIFY_TRIES = 5;

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailFrom;

    /** key = username|email → CodeRecord */
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
                            @Value("${spring.mail.username:}") String mailFrom) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailFrom = mailFrom;
    }

    /** 生成并发送验证码 */
    public void sendResetCode(String username, String email) {
        String key = key(username, email);
        long now = System.currentTimeMillis();
        CodeRecord prev = store.get(key);
        if (prev != null && now - prev.lastSendAt < RESEND_INTERVAL_MS) {
            throw new RuntimeException("发送过于频繁，请 1 分钟后再试");
        }

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        store.put(key, new CodeRecord(code, now + CODE_TTL_MS, now));

        String subject = "【优品汇】找回密码验证码";
        String text = "您好，\n\n您正在重置优品汇账号密码。\n验证码：" + code
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
                log.info("[Mail] 重置验证码已发送至 {}", mask(email));
            } catch (Exception e) {
                // 发信失败不吞掉验证码：开发环境仍可在日志中看到
                log.warn("[Mail] 发送失败（{}），验证码已生成。原因: {}", mask(email), e.getMessage());
                log.info("[Mail][DEV] {} 重置验证码: {}", mask(email), code);
            }
        } else {
            log.info("[Mail][DEV] 未配置 SMTP，验证码（仅开发可见）→ {} : {}", mask(email), code);
        }
    }

    /** 校验验证码；通过后立即作废 */
    public void verifyResetCode(String username, String email, String code) {
        String key = key(username, email);
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

    private static String key(String username, String email) {
        return (username == null ? "" : username.trim().toLowerCase())
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
