package de.malkusch.ha.shared.infrastructure.mqtt;

import java.io.IOException;
import java.time.Duration;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class PahoMqtt implements Mqtt {

    private final IMqttClient mqtt;
    private final String host;
    private final MqttConnectOptions options;

    public PahoMqtt(String clientId, String host, int port, String user, String password, Duration timeout,
            Duration keepAlive) throws MqttSecurityException, MqttException {

        this.host = host;
        var uri = String.format("ssl://%s:%s", host, port);
        mqtt = new MqttClient(uri, clientId, new MemoryPersistence());
        mqtt.setCallback(new MqttEventHandler());

        options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        // options.setCleanSession(false);
        options.setCleanSession(true);
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

    private static final int QOS_LOWEST = 0;

    @Override
    public synchronized void subscribe(String topic, Consumer consumer) throws IOException {
        try {
            checkConnection();
            mqtt.subscribe(topic, QOS_LOWEST, (t, msg) -> consumer.consume(new String(msg.getPayload())));

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
            log.info("Connected {}", this);

        } catch (MqttException e) {
            throw new IOException("Couldn't connect to " + this, e);
        }
    }

    private class MqttEventHandler implements MqttCallbackExtended {

        @Override
        public void connectionLost(Throwable cause) {
            log.warn("Connection lost: {}", cause.getMessage());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            log.info("Mqtt connected [reconnect={}, uri={}]", reconnect, serverURI);
        }
    }

    @Override
    public String toString() {
        return String.format("MQTT(%s, %s)", host, mqtt.getClientId());
    }

    @Override
    public void close() throws MqttException {
        try (mqtt) {
            if (!mqtt.isConnected()) {
                return;
            }
            log.info("Disconnecting MQTT");
            mqtt.disconnect(options.getConnectionTimeout() * 1000);

        } catch (Exception e) {
            log.warn("Disconnecting MQTT Failed", e);
            mqtt.disconnectForcibly(options.getConnectionTimeout() * 1000);
        }
    }
}
