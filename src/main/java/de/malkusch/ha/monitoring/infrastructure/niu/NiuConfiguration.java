package de.malkusch.ha.monitoring.infrastructure.niu;

import java.io.IOException;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import de.malkusch.ha.shared.infrastructure.async.AsyncService;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
@RequiredArgsConstructor
class NiuConfiguration {

    private final NiuProperties properties;

    @Component
    @ConfigurationProperties("niu")
    @Data
    static class NiuProperties {
        private String account;
        private String password;
        private String countryCode;
        private Duration queryRate;
        private CircuitBreaker.Properties circuitBreaker;
    }

    @Bean
    Niu niu() throws IOException {
        var api = new de.malkusch.niu.Niu.Builder(properties.account, properties.password, properties.countryCode)
                .build();

        var circuitBreaker = new CircuitBreaker<Object>("NIU", properties.circuitBreaker, Throwable.class);
        log.info("Configured Niu(account={}, circuit-breaker({}))", properties.account, circuitBreaker);
        return new Niu(api, circuitBreaker);
    }

    @Bean
    NiuPoller niuPoller(AsyncService async) throws IOException {
        return new NiuPoller(niu(), properties.queryRate, async);
    }
}
