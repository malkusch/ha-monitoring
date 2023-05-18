package de.malkusch.ha.shared.infrastructure.mqtt;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.time.Duration;

import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptionsBuilder;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class PahoMqtt5 implements Mqtt {

    private final MqttClient mqtt;
    private final String host;
    private final MqttConnectionOptions options;

    public PahoMqtt5(String clientId, String host, int port, String user, String password, Duration timeout,
            Duration keepAlive, Duration sessionExpiryInterval) throws MqttException {

        this.host = host;
        var uri = String.format("ssl://%s:%s", host, port);
        mqtt = new MqttClient(uri, clientId, new MemoryPersistence());
        mqtt.setCallback(new MqttEventHandler());

        options = new MqttConnectionOptionsBuilder() //
                .automaticReconnect(true) //
                // .cleanStart(false) //
                .cleanStart(true) //
                .sessionExpiryInterval(sessionExpiryInterval.toSeconds()) //
                .connectionTimeout((int) timeout.toSeconds()) //
                .keepAliveInterval((int) keepAlive.toSeconds()) //
                .password(password.getBytes(UTF_8)) //
                .username(user) //
                .build();

        log.info("MQTT configured with sessionExpiry={}s, connectionTimeout={}s, keepAliveInterval={}s", //
                options.getSessionExpiryInterval(), //
                options.getConnectionTimeout(), //
                options.getKeepAliveInterval());
    }

    private static final int QOS_LOWEST = 0;

    @Override
    public synchronized void subscribe(String topic, Consumer consumer) throws IOException {
        try {
            checkConnection();
            MqttSubscription[] subscriptions = { new MqttSubscription(topic, QOS_LOWEST) };
            IMqttMessageListener[] listener = { (t, msg) -> consumer.consume(new String(msg.getPayload())) };
            mqtt.subscribe(subscriptions, listener);

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

    private class MqttEventHandler implements MqttCallback {

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            log.info("Connected [reconnect={}, uri={}]", reconnect, serverURI);
        }

        @Override
        public void disconnected(MqttDisconnectResponse disconnectResponse) {
            log.warn("Disconnected: {}", disconnectResponse.getReasonString());
        }

        @Override
        public void mqttErrorOccurred(MqttException exception) {
            log.warn("Mqtt error: {}", exception.getMessage());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
        }

        @Override
        public void deliveryComplete(IMqttToken token) {
        }

        @Override
        public void authPacketArrived(int reasonCode, MqttProperties properties) {
            log.info("Authentication: {}", reasonCode);
        }
    }

    @Override
    public String toString() {
        return String.format("MQTT(%s, %s)", host, mqtt.getClientId());
    }

    @Override
    public void close() throws MqttException {
        try {
            if (!mqtt.isConnected()) {
                return;
            }
            log.info("Disconnecting MQTT");
            mqtt.disconnect(options.getConnectionTimeout() * 1000);

        } catch (Exception e) {
            log.warn("Disconnecting MQTT Failed", e);
            mqtt.disconnectForcibly(0, options.getConnectionTimeout() * 1000, true);

        } finally {
            mqtt.close(true);
        }
    }
}
