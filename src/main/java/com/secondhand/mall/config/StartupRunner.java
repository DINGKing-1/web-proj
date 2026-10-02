package com.secondhand.mall.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class StartupRunner implements CommandLineRunner {

    @Override
    public void run(String... args) {
        System.out.println();
        System.out.println("========================================");
        System.out.println("  优品汇 - 二手交易平台启动成功!");
        System.out.println("========================================");
        System.out.println();
        System.out.println("  前端地址: http://localhost:8080");
        System.out.println("  管理后台: http://localhost:8080/admin");
        System.out.println();
        System.out.println("  默认管理员: admin / admin123");
        System.out.println("========================================");
        System.out.println();
    }
}
