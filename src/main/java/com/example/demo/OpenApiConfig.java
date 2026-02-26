package com.example.demo;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Demo REST API")
                        .version("1.0.0")
                        .description("Sample REST API for Products and Orders — used for NL-to-API demo with Gemini")
                        .contact(new Contact()
                                .name("Demo Team")
                                .email("demo@example.com")));
    }
}
