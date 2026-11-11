package com.campus.remarket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PortalResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/owner/**")
                .addResourceLocations(
                        "classpath:/portal-sites/owner/", "classpath:/portal-sites/shared/");
        registry.addResourceHandler("/admin/**")
                .addResourceLocations(
                        "classpath:/portal-sites/admin/", "classpath:/portal-sites/shared/");
    }
}
