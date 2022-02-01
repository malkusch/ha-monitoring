package de.malkusch.ha.shared.infrastructure.buderus;

import java.io.IOException;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import de.malkusch.km200.KM200;
import de.malkusch.km200.KM200Exception;
import lombok.Data;

@Configuration
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
    }

    @Bean
    public KM200 km200(BuderusProperties properties) throws KM200Exception, IOException, InterruptedException {
        return new KM200(properties.host, properties.timeout, properties.gatewayPassword,
                properties.privatePassword, properties.salt);
    }
}
