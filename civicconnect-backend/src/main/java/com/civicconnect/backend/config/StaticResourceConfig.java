package com.civicconnect.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Exposes the local uploads directory (blurred photos only — see
 * PhotoUploadService) at /uploads/**. This is a hackathon-appropriate
 * choice: real object storage (S3-compatible, etc.) would be the right
 * call for a production deployment, but local disk + static serving is
 * fine for a demo and avoids adding a cloud storage dependency.
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.uploads.dir:/app/uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadDir.endsWith("/") ? uploadDir : uploadDir + "/";
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations("file:" + location);
    }
}
