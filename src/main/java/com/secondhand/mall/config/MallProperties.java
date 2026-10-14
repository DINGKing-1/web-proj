package com.secondhand.mall.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 演示/生产可切换的资金与安全开关。
 * 生产侧（application-prod）应关闭模拟充值、初始余额清零、管理员口令外部注入。
 */
@Component
@ConfigurationProperties(prefix = "mall")
public class MallProperties {

    private final Wallet wallet = new Wallet();
    private final User user = new User();
    private final Security security = new Security();

    public Wallet getWallet() {
        return wallet;
    }

    public User getUser() {
        return user;
    }

    public Security getSecurity() {
        return security;
    }

    public static class Wallet {
        /** 模拟充值开关：默认关闭；仅演示/开发显式打开。生产必须 false */
        private boolean mockRechargeEnabled = false;
        private BigDecimal mockRechargeMaxSingle = new BigDecimal("50000");
        private BigDecimal mockRechargeMaxCumulative = new BigDecimal("200000");

        public boolean isMockRechargeEnabled() {
            return mockRechargeEnabled;
        }

        public void setMockRechargeEnabled(boolean mockRechargeEnabled) {
            this.mockRechargeEnabled = mockRechargeEnabled;
        }

        public BigDecimal getMockRechargeMaxSingle() {
            return mockRechargeMaxSingle;
        }

        public void setMockRechargeMaxSingle(BigDecimal mockRechargeMaxSingle) {
            this.mockRechargeMaxSingle = mockRechargeMaxSingle;
        }

        public BigDecimal getMockRechargeMaxCumulative() {
            return mockRechargeMaxCumulative;
        }

        public void setMockRechargeMaxCumulative(BigDecimal mockRechargeMaxCumulative) {
            this.mockRechargeMaxCumulative = mockRechargeMaxCumulative;
        }
    }

    public static class User {
        /** 新用户赠金：默认 0；活动期在配置里手动开启 */
        private BigDecimal initialBalance = BigDecimal.ZERO;

        public BigDecimal getInitialBalance() {
            return initialBalance;
        }

        public void setInitialBalance(BigDecimal initialBalance) {
            this.initialBalance = initialBalance;
        }
    }

    public static class Security {
        /** 仅创建 admin 时使用的初始口令；空则生成随机口令 */
        private String adminInitPassword = "";
        /** 是否尝试修复非 BCrypt 历史口令（默认关闭，避免重置成弱口令） */
        private boolean legacyPasswordRepair = false;
        /** 开发环境是否把找回密码验证码写入日志 */
        private boolean logMailCodes = false;

        public String getAdminInitPassword() {
            return adminInitPassword;
        }

        public void setAdminInitPassword(String adminInitPassword) {
            this.adminInitPassword = adminInitPassword;
        }

        public boolean isLegacyPasswordRepair() {
            return legacyPasswordRepair;
        }

        public void setLegacyPasswordRepair(boolean legacyPasswordRepair) {
            this.legacyPasswordRepair = legacyPasswordRepair;
        }

        public boolean isLogMailCodes() {
            return logMailCodes;
        }

        public void setLogMailCodes(boolean logMailCodes) {
            this.logMailCodes = logMailCodes;
        }
    }
}
