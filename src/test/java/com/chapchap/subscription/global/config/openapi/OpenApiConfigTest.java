package com.chapchap.subscription.global.config.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void Gateway_주소가_설정되면_OpenAPI_서버와_Bearer_인증_스키마를_노출한다() {
        OpenAPI openAPI = new OpenApiConfig("https://api.chapchap.example", "챱챱 API Gateway")
            .subscriptionOpenAPI();

        assertThat(openAPI.getServers()).singleElement()
            .satisfies(server -> {
                assertThat(server.getUrl()).isEqualTo("https://api.chapchap.example");
                assertThat(server.getDescription()).isEqualTo("챱챱 API Gateway");
            });
        assertThat(openAPI.getComponents().getSecuritySchemes())
            .containsKey(OpenApiConfig.BEARER_AUTH);
        assertThat(openAPI.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_AUTH))
            .satisfies(securityScheme -> {
                assertThat(securityScheme.getType()).isEqualTo(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP);
                assertThat(securityScheme.getScheme()).isEqualTo("bearer");
                assertThat(securityScheme.getBearerFormat()).isEqualTo("JWT");
            });
    }

    @Test
    void Gateway_주소가_없으면_서버를_고정하지_않는다() {
        OpenAPI openAPI = new OpenApiConfig("", "챱챱 API Gateway").subscriptionOpenAPI();

        assertThat(openAPI.getServers()).isNull();
    }
}
