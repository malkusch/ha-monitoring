package de.malkusch.ha.shared.infrastructure.mqtt;

import java.io.IOException;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class PahoMqtt implements Mqtt {

    private final IMqttClient mqtt;

    @Override
    public void subscribe(String topic, Consumer consumer) throws IOException {
        try {
            mqtt.subscribe(topic, (t, msg) -> consumer.consume(new String(msg.getPayload())));
        } catch (MqttException e) {
            throw new IOException("Couldn't subscribe to " + topic, e);
        }
    }

    @Override
    public void close() throws MqttException {
        mqtt.close();
    }
}
