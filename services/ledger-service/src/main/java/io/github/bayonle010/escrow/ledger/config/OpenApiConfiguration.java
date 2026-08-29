package io.github.bayonle010.escrow.ledger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI ledgerServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Escrow Platform Ledger API")
                        .description("Double-entry journals, ledger accounts, and balance projection APIs.")
                        .version("v1"));
    }
}
