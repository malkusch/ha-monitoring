package de.malkusch.ha.shared.infrastructure.mqtt;

import java.io.IOException;
import java.time.Duration;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class PahoMqtt implements Mqtt {

    private final IMqttClient mqtt;
    private final String uri;
    private final MqttConnectOptions options;

    public PahoMqtt(String host, int port, String user, String password, Duration timeout, Duration keepAlive)
            throws MqttSecurityException, MqttException {

        uri = String.format("ssl://%s:%s", host, port);
        mqtt = new MqttClient(uri, MqttClient.generateClientId(), new MemoryPersistence());

        options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        options.setConnectionTimeout((int) timeout.toSeconds());
        options.setKeepAliveInterval((int) keepAlive.toSeconds());
        options.setPassword(password.toCharArray());
        options.setUserName(user);
    }

    public static class Properties {

        boolean enabled;
        String host;
        int port;
        String user;
        String password;
        Duration timeout;
        Duration keepAlive;
    }

    @Override
    public synchronized void subscribe(String topic, Consumer consumer) throws IOException {
        try {
            checkConnection();
            mqtt.subscribe(topic, (t, msg) -> consumer.consume(new String(msg.getPayload())));

        } catch (MqttException e) {
            throw new IOException("Couldn't subscribe to " + topic, e);
        }
    }

    private void checkConnection() throws IOException {
        try {
            if (mqtt.isConnected()) {
                return;
            }
            mqtt.connect(options);
            log.info("Connected to MQTT {}", uri);

        } catch (MqttException e) {
            throw new IOException("Couldn't connect to MQTT", e);
        }
    }

    @Override
    public String toString() {
        return uri;
    }

    @Override
    public void close() throws MqttException {
        try {
            log.info("Disconnecting MQTT");
            mqtt.disconnect(options.getConnectionTimeout() * 1000);

        } catch (Exception e) {
            log.warn("Disconnecting MQTT Failed", e);
            mqtt.disconnectForcibly(options.getConnectionTimeout() * 1000);

        } finally {
            mqtt.close();
        }
    }
}
