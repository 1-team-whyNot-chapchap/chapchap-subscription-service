package com.chapchap.subscription.global.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    private final String gatewayUri;
    private final String gatewayDescription;

    public OpenApiConfig(
        @Value("${GATEWAY_URI:}") String gatewayUri,
        @Value("${APP_DESCRIPTION:API Gateway}") String gatewayDescription
    ) {
        this.gatewayUri = gatewayUri;
        this.gatewayDescription = gatewayDescription;
    }

    @Bean
    public OpenAPI subscriptionOpenAPI() {
        OpenAPI openAPI = new OpenAPI()
            .info(new Info()
                .title("챱챱 Subscription Service API")
                .description("챱챱 구독 서비스 HTTP API 명세입니다. 실제 인증 요청은 API Gateway를 통해 Bearer JWT로 호출합니다.")
                .version("v1"))
            .components(new Components().addSecuritySchemes(
                BEARER_AUTH,
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
            ));

        if (StringUtils.hasText(gatewayUri)) {
            openAPI.setServers(List.of(new Server()
                .url(gatewayUri)
                .description(gatewayDescription)));
        }

        return openAPI;
    }
}
