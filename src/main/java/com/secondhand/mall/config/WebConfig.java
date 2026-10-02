package com.secondhand.mall.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = new File("product-images").getAbsolutePath() + File.separator;
        registry.addResourceHandler("/product-images/**")
                .addResourceLocations("file:" + uploadPath);

        String avatarPath = new File("user-avatar").getAbsolutePath() + File.separator;
        registry.addResourceHandler("/user-avatar/**")
                .addResourceLocations("file:" + avatarPath);
    }
}
