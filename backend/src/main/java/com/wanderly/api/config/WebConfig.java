package com.wanderly.api.config;

import com.wanderly.api.service.FileStorageService;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final FileStorageService files;

    public WebConfig(FileStorageService files) {
        this.files = files;
    }

    /** Serves uploaded journal photos at /uploads/{file}. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**").addResourceLocations(files.root().toUri().toString());
    }

    /** Swagger UI with a "Authorize" button for the JWT. */
    @Bean
    OpenAPI wanderlyOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Wanderly API").version("1.0.0")
                        .description("REST backend for the Wanderly – Explore Beyond travel app"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
