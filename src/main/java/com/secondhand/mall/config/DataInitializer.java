package com.secondhand.mall.config;

import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 应用启动时初始化必要数据：
 * 1. 创建默认管理员账号 admin / admin123（BCrypt 加密）
 * 2. 修复数据库中非 BCrypt 编码的密码（可选）
 */
@Component
@Order(1)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        initAdminUser();
        fixNonBcryptPasswords();
    }

    /**
     * 确保管理员账号存在且密码正确
     */
    private void initAdminUser() {
        userRepository.findByUsername("admin").ifPresentOrElse(
            existing -> {
                // 管理员已存在，但确保密码是 BCrypt 编码
                if (!isBCrypt(existing.getPassword())) {
                    existing.setPassword(passwordEncoder.encode("admin123"));
                    existing.setRole("admin");
                    userRepository.save(existing);
                    log.info("✓ 管理员密码已更新为 BCrypt 编码");
                } else {
                    log.info("✓ 管理员账号已就绪");
                }
            },
            () -> {
                // 创建默认管理员
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setNickname("管理员");
                admin.setRole("admin");
                admin.setBalance(new BigDecimal("999999.00"));
                admin.setEmail("admin@secondhand-mall.com");
                userRepository.save(admin);
                log.info("✓ 默认管理员已创建: admin / admin123");
            }
        );
    }

    /**
     * 修复数据库中非 BCrypt 编码的密码（如手动 SQL 插入的明文密码）
     * 注意：无法恢复原始明文密码，将其重置为 "123456"
     */
    private void fixNonBcryptPasswords() {
        int fixed = 0;
        for (User user : userRepository.findAll()) {
            String pwd = user.getPassword();
            if (pwd != null && !isBCrypt(pwd)) {
                user.setPassword(passwordEncoder.encode("123456"));
                userRepository.save(user);
                fixed++;
                log.warn("⚠ 用户 [{}] 密码格式异常，已重置为 123456，请通知用户修改密码", user.getUsername());
            }
        }
        if (fixed > 0) {
            log.info("✓ 共修复 {} 个密码格式异常的用户", fixed);
        }
    }

    /**
     * 判断密码是否已是 BCrypt 格式（$2a$ 开头）
     */
    private boolean isBCrypt(String encodedPassword) {
        return encodedPassword != null && encodedPassword.startsWith("$2a$");
    }
}
