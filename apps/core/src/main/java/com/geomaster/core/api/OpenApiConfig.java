package com.geomaster.core.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Metadata for the web↔core contract committed at {@code packages/api-contract/openapi.yaml} (ADR-0016). */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI coreOpenApi() {
        return new OpenAPI()
                .info(new Info().title("GEOMASTER core API").version("0.1.0")
                        .description("Called only by apps/web with a short-lived EdDSA service token (ADR-0017)."))
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes("serviceToken",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("serviceToken"));
    }
}
