package com.secondhand.mall.config;

import com.secondhand.mall.service.QuickReplyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class StartupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    private final QuickReplyService quickReplyService;

    public StartupRunner(QuickReplyService quickReplyService) {
        this.quickReplyService = quickReplyService;
    }

    @Override
    public void run(String... args) {
        try {
            quickReplyService.seedDefaultsIfEmpty();
        } catch (Exception e) {
            log.warn("初始化客服话术失败: {}", e.getMessage());
        }
        log.info("""

                ========================================
                  优品汇 - 二手交易平台启动成功!
                ========================================

                  前端地址: http://localhost:8080
                  管理后台: http://localhost:8080/admin

                  默认管理员: admin（密码见项目文档，切勿在生产环境使用默认口令）
                ========================================
                """);
    }
}
