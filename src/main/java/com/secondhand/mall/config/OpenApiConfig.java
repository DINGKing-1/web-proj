package com.secondhand.mall.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 接口文档元信息：Swagger UI 见 /swagger-ui.html */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mallOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("优品汇二手交易平台 API")
                .description("""
                        高端二手交易平台后端接口。

                        认证方式：HttpSession（先 POST /api/login 拿 Cookie，后续请求带上）。
                        管理端接口（/api/admin/**）需管理员会话。
                        响应为扁平信封：`{ success, message, ...业务字段 }`。
                        """)
                .version("1.0.0")
                .license(new License().name("毕业设计").url("http://localhost:8080")));
    }
}
