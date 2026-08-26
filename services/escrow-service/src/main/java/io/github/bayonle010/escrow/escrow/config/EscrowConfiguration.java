package io.github.bayonle010.escrow.escrow.config;

import java.time.Clock;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EscrowConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Set<String> supportedCurrencies() {
        return Set.of("EUR", "GBP", "NGN", "USD");
    }
}
