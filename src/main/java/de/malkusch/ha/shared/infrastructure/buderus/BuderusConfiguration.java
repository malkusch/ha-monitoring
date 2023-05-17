package de.malkusch.ha.shared.infrastructure.buderus;

import java.io.IOException;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker;
import de.malkusch.km200.KM200;
import de.malkusch.km200.KM200Exception;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
class BuderusConfiguration {

    @ConfigurationProperties("buderus")
    @Component
    @Data
    public static class BuderusProperties {
        private String salt;
        private String gatewayPassword;
        private String privatePassword;
        private String host;
        private Duration timeout;
        private CircuitBreaker.Properties circuitBreaker;
    }

    @Bean
    public Heater heater(BuderusProperties properties) throws KM200Exception, IOException, InterruptedException {
        var timeout = properties.timeout;
        var host = properties.host;
        var km200 = new KM200(host, timeout, properties.gatewayPassword, properties.privatePassword, properties.salt);

        var circuitBreaker = new CircuitBreaker<Double>(properties.circuitBreaker, KM200Exception.class,
                IOException.class);

        log.info("Configured KM200(host={}, timeout={}, circuit-breaker({}))", host, timeout, circuitBreaker);
        return new Heater(km200, circuitBreaker);
    }
}
