package de.malkusch.ha.shared.infrastructure.mqtt;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

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

import de.malkusch.ha.shared.infrastructure.mqtt.ResilientMqtt.ReconnectableMqtt;
import de.malkusch.ha.shared.infrastructure.scheduler.Schedulers;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class PahoMqtt5 implements ReconnectableMqtt {

    private final MqttClient mqtt;
    private final String host;
    private final MqttConnectionOptions options;
    private final ScheduledExecutorService executorService;

    public PahoMqtt5(String clientId, String host, int port, String user, String password, Duration timeout,
            Duration keepAlive, Duration sessionExpiryInterval) throws MqttException {

        this.host = host;
        var uri = String.format("ssl://%s:%s", host, port);

        executorService = Executors.newScheduledThreadPool(10, r -> {
            var thread = new Thread(r, "mqtt");
            thread.setUncaughtExceptionHandler((t, e) -> {
                log.error("Shutting down due to an error in mqtt", e);
            });
            thread.setDaemon(true);
            return thread;
        });

        mqtt = new MqttClient(uri, clientId, new MemoryPersistence(), executorService);
        mqtt.setCallback(new MqttEventHandler());

        options = new MqttConnectionOptionsBuilder() //
                .automaticReconnect(true) //
                // .cleanStart(false) //
                .cleanStart(true) //
                .sessionExpiryInterval(sessionExpiryInterval.toSeconds()) //
                .connectionTimeout((int) timeout.toSeconds()) //
                .automaticReconnectDelay((int) timeout.toSeconds() * 2, (int) timeout.toSeconds() * 3) //
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
            log.info("Connecting {}", this);
            mqtt.connect(options);

        } catch (MqttException e) {
            throw new IOException("Couldn't connect to " + this, e);
        }
    }

    @Override
    public void reconnect() throws IOException {
        try {
            disconnect();
            log.info("Reconnecting {}", this);
            nextReconnect.set(true);
            mqtt.reconnect();

        } catch (MqttException e) {
            throw new IOException("Couldn't reconnect to " + this, e);
        }
    }

    private static final Runnable NOTHING = () -> {
    };

    private volatile Runnable onReconnect = NOTHING;

    @Override
    public void onReconnect(Runnable onReconnect) {
        this.onReconnect = onReconnect;
    }

    private void callOnRedirect() {
        onReconnect.run();
    }

    private AtomicBoolean nextReconnect = new AtomicBoolean(false);

    private class MqttEventHandler implements MqttCallback {

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            if (nextReconnect.compareAndSet(true, false)) {
                reconnect = true;
            }
            log.info("Connected [reconnect={}, uri={}]", reconnect, serverURI);
            if (reconnect) {
                callOnRedirect();
            }
        }

        @Override
        public void disconnected(MqttDisconnectResponse disconnectResponse) {
            log.warn("Disconnected: {}", disconnectResponse.getReasonString(), disconnectResponse.getException());
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
    public void close() throws Exception {
        try {
            disconnect();

        } finally {
            try {
                mqtt.close(true);

            } finally {
                Schedulers.close(executorService);
            }
        }
    }

    private void disconnect() throws MqttException {
        try {
            if (!mqtt.isConnected()) {
                return;
            }
            log.info("Disconnecting MQTT");
            mqtt.disconnect(options.getConnectionTimeout() * 1000);

        } catch (Exception e) {
            log.warn("Disconnecting MQTT Failed", e);
            mqtt.disconnectForcibly(0, options.getConnectionTimeout() * 1000, true);
        }
    }
}
