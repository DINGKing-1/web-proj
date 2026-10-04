package com.secondhand.mall.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * SMTP 冒烟：给发信邮箱自己发一封，确认授权码可用。
 * 仅本地验证用，CI/无外网时可忽略。
 */
@SpringBootTest
class MailSendSmokeTest {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Test
    @Disabled("SMTP 冒烟：需外网且会真发信。验证时用 mvn test -Dtest=MailSendSmokeTest -DfailIfNoTests=false 并去掉此注解")
    void sendSmokeMail() {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(mailFrom);
        message.setSubject("【优品汇】SMTP 配置成功");
        message.setText("如果你收到这封邮件，说明找回密码验证码可以真实下发。\n时间：" + java.time.LocalDateTime.now());
        assertDoesNotThrow(() -> mailSender.send(message));
    }
}
