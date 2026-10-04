package com.secondhand.mall;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SecondhandMallApplication {

    private static final Logger log = LoggerFactory.getLogger(SecondhandMallApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(SecondhandMallApplication.class, args);

        // 注册 JVM 关闭钩子：确保 Ctrl+C / kill / IDE停止 时正确关闭容器，释放 8080 端口
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (context.isActive()) {
                context.close();
                log.info("[ShutdownHook] 应用已优雅关闭，8080 端口已释放");
            }
        }));
    }
}
