package de.malkusch.ha.shared.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;

import lombok.Data;

@Configuration
class MqttConfiguration {

    @Component
    @ConfigurationProperties("mqtt")
    @Data
    public static class Properties {

        String host;
        int port;
        String user;
        String password;

    }

    @Bean
    public Mqtt5BlockingClient mqtt(Properties properties) {
        var client = MqttClient.builder().useMqttVersion5().serverHost(properties.host).serverPort(properties.port)
                .sslWithDefaultConfig().buildBlocking();

        client.connectWith().simpleAuth().username(properties.user).password(UTF_8.encode(properties.password))
                .applySimpleAuth().send();

        return client;
    }

}
