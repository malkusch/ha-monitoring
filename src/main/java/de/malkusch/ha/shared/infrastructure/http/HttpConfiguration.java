package de.malkusch.ha.shared.infrastructure.http;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import lombok.Data;

@Configuration
class HttpConfiguration {

    @Component
    @ConfigurationProperties("http")
    @Data
    public static final class HttpProperties {
        private Duration timeout;
        private String userAgent;

        @Data
        public static final class Retry {
            private int retries;
            private Duration delay;
        }

        private Retry retry;
    }

    @Bean
    @Primary
    public HttpClient httpClient(HttpProperties properties) {
        var jdkClient = new JdkHttpClient(properties.timeout, properties.userAgent);
        var retrying = new RetryingHttpClient(jdkClient, properties.retry.delay, properties.retry.retries);
        return retrying;
    }
}
