package de.malkusch.ha.shared.infrastructure.mqtt;

import java.time.Duration;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
class MqttConfiguration {

    private final Properties properties;

    @Component
    @ConfigurationProperties("mqtt")
    @Data
    public static class Properties {

        String host;
        int port;
        String user;
        String password;
        Duration timeout;
        Duration keepAlive;
    }

    @Bean
    public Mqtt mqtt() throws MqttException {
        var uri = String.format("ssl://%s:%s", properties.host, properties.port);
        IMqttClient client = new MqttClient(uri, MqttClient.generateClientId(), new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        options.setConnectionTimeout((int) properties.timeout.toSeconds());
        options.setKeepAliveInterval((int) properties.keepAlive.toSeconds());
        options.setPassword(properties.password.toCharArray());
        options.setUserName(properties.user);

        client.connect(options);

        return new PahoMqtt(client);
    }
}
