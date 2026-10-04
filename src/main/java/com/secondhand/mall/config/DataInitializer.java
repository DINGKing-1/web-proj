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
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 应用启动时初始化必要数据：
 * 1. 确保管理员账号存在（口令仅首次创建时写入，绝不覆盖已有密码）
 * 2. 可选修复非 BCrypt 历史口令（默认关闭，避免批量重置成弱口令）
 */
@Component
@Order(1)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final MallProperties mallProperties;

    public DataInitializer(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder,
                           MallProperties mallProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mallProperties = mallProperties;
    }

    @Override
    public void run(String... args) {
        initAdminUser();
        if (mallProperties.getSecurity().isLegacyPasswordRepair()) {
            repairLegacyPasswords();
        }
    }

    /**
     * 确保管理员账号存在、role=admin。
     * 安全约定：
     * - 已有 admin 账号时只纠正 role，绝不动密码（避免启动就把口令打回弱口令）
     * - 首次创建时用配置口令；未配置则生成随机口令并打印一次
     */
    private void initAdminUser() {
        userRepository.findByUsername("admin").ifPresentOrElse(
            existing -> {
                if (!"admin".equals(existing.getRole())) {
                    existing.setRole("admin");
                    userRepository.save(existing);
                    log.info("✓ 管理员角色已纠正为 admin");
                }
                if (!isBCrypt(existing.getPassword())) {
                    log.warn("⚠ 管理员密码不是 BCrypt 格式，请尽快登录并修改密码（不会自动重置）");
                }
                log.info("✓ 管理员账号已就绪（密码保持不变）");
            },
            () -> {
                String raw = mallProperties.getSecurity().getAdminInitPassword();
                boolean generated = raw == null || raw.isBlank();
                if (generated) {
                    raw = generateRandomPassword();
                }
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode(raw));
                admin.setNickname("管理员");
                admin.setRole("admin");
                admin.setBalance(new BigDecimal("999999.00"));
                admin.setEmail("admin@secondhand-mall.com");
                userRepository.save(admin);
                if (generated) {
                    log.warn("✓ 已创建管理员 admin，随机口令为: {} （请立即登录修改，此口令不会再次打印）", raw);
                } else {
                    log.info("✓ 默认管理员已创建（口令来自配置项 mall.security.admin-init-password / MALL_ADMIN_PASSWORD）");
                }
            }
        );
    }

    /**
     * 仅在显式开启时处理非 BCrypt 口令：只告警，不改成固定弱口令。
     * 需要重置时应由管理员走找回密码/后台重置流程。
     */
    private void repairLegacyPasswords() {
        int bad = 0;
        for (User user : userRepository.findAll()) {
            String pwd = user.getPassword();
            if (pwd != null && !isBCrypt(pwd)) {
                bad++;
                log.warn("⚠ 用户 [{}] 密码格式异常（非 BCrypt），请通知其通过找回密码重置", user.getUsername());
            }
        }
        if (bad > 0) {
            log.warn("✓ 共发现 {} 个密码格式异常的用户（未自动重置）", bad);
        }
    }

    private static String generateRandomPassword() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean isBCrypt(String encodedPassword) {
        return encodedPassword != null
                && (encodedPassword.startsWith("$2a$") || encodedPassword.startsWith("$2b$")
                    || encodedPassword.startsWith("$2y$"));
    }
}
