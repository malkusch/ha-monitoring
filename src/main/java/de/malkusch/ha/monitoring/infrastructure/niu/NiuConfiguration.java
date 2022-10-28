package de.malkusch.ha.monitoring.infrastructure.niu;

import java.io.IOException;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import de.malkusch.niu.Niu;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Configuration
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
    }

    @Bean
    Niu niu() throws IOException {
        return new Niu.Builder(properties.account, properties.password, properties.countryCode).build();
    }

    @Bean
    NiuPoller niuPoller() throws IOException {
        return new NiuPoller(niu(), properties.queryRate);
    }
}
