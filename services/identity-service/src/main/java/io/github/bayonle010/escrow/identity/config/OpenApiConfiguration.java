package io.github.bayonle010.escrow.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI identityServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Escrow Platform Identity API")
                        .description("User identity, registration, authentication, and authorization APIs.")
                        .version("v1"));
    }
}
