package com.secondhand.mall.config;

import com.secondhand.mall.storage.StorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    public WebConfig(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String productPath = storageProperties.resolveCategory(
                com.secondhand.mall.storage.StorageCategory.PRODUCT_IMAGE).toAbsolutePath() + java.io.File.separator;
        registry.addResourceHandler("/product-images/**")
                .addResourceLocations("file:" + productPath);

        String avatarPath = storageProperties.resolveCategory(
                com.secondhand.mall.storage.StorageCategory.USER_AVATAR).toAbsolutePath() + java.io.File.separator;
        registry.addResourceHandler("/user-avatar/**")
                .addResourceLocations("file:" + avatarPath);
    }
}
