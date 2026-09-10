package com.chapchap.subscription.global.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI subscriptionOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("챱챱 Subscription Service API")
                .description("챱챱 구독 서비스 HTTP API 명세입니다.")
                .version("v1"))
            .components(new Components().addSecuritySchemes(
                BEARER_AUTH,
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
            ));
    }
}
