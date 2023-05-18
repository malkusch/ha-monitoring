package de.malkusch.ha.shared.infrastructure.mqtt;

import java.time.Duration;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@RequiredArgsConstructor
@Slf4j
class MqttConfiguration {

    private final Properties properties;

    @Component
    @ConfigurationProperties("mqtt")
    @Data
    public static class Properties {

        boolean enabled;
        String host;
        int port;
        String user;
        String password;
        Duration timeout;
        Duration keepAlive;
        CircuitBreaker.Properties circuitBreaker;
    }

    @Bean
    public Mqtt mqtt() throws MqttException {
        if (!properties.enabled) {
            log.warn("MQTT is disabled");
            return new NullMqtt();
        }
        var paho = new PahoMqtt(properties.host, properties.port, properties.user, properties.password,
                properties.timeout, properties.keepAlive);
        return new ResilientMqtt(paho, properties.circuitBreaker);
    }
}
