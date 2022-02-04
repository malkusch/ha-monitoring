package de.malkusch.ha.monitoring.infrastructure;

import static com.hivemq.client.mqtt.MqttGlobalPublishFilter.SUBSCRIBED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static lombok.AccessLevel.PRIVATE;

import java.util.Collection;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;

import de.malkusch.ha.monitoring.infrastructure.persistence.GaugeFactory;
import io.prometheus.client.Gauge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor(access = PRIVATE)
@Slf4j
public class MqttMonitoring<MESSAGE> {

    @RequiredArgsConstructor
    @Component
    public static class Factory {

        private final ObjectMapper mapper;
        private final Mqtt5BlockingClient mqtt;
        private final GaugeFactory gaugeFactory;

        public <MESSAGE> MqttMonitoring<MESSAGE> build(Class<MESSAGE> type, String topic,
                Collection<MessageGauge<MESSAGE>> fieldPollers) {

            return build(topic, (it) -> mapper.readValue(it, type), fieldPollers);
        }

        public MqttMonitoring<JsonNode> build(String topic, String... paths) {
            return build(topic, asList(paths));
        }

        public MqttMonitoring<JsonNode> build(String topic, Collection<String> paths) {
            var fieldPollers = paths.stream().map(path -> {
                var name = gaugeName(topic, path);
                var gauge = gaugeFactory.build(name);
                MessageGauge<JsonNode> messageGauge = new MessageGauge<>(gauge, it -> it.at(path).asDouble());
                return messageGauge;
            }).toList();
            return build(topic, (it) -> mapper.readTree(it), fieldPollers);
        }

        private static String gaugeName(String topic, String path) {
            return topic + "_" + path.substring(1).replace(".", "");
        }

        private static interface MessageMapper<MESSAGE> {
            MESSAGE map(String message) throws Exception;
        }

        private <MESSAGE> MqttMonitoring<MESSAGE> build(String topic, MessageMapper<MESSAGE> messageMapper,
                Collection<MessageGauge<MESSAGE>> fieldPollers) {
            var poller = new MqttMonitoring<>(fieldPollers);
            mqtt.subscribeWith().topicFilter(topic).send();

            mqtt.toAsync().publishes(SUBSCRIBED, publish -> {
                if (!publish.getTopic().filter().matches(MqttTopic.of(topic))) {
                    return;
                }
                var rawMessage = UTF_8.decode(publish.getPayload().get()).toString();
                try {
                    var message = messageMapper.map(rawMessage);
                    poller.update(message);
                } catch (Exception e) {
                    log.error("Updating MqttMonitoring failed for topic {} with message {}", topic, rawMessage, e);
                }
            });

            return poller;
        }
    }

    @RequiredArgsConstructor
    static final class MessageGauge<MESSAGE> {

        private final Gauge gauge;

        private final Function<MESSAGE, Double> fieldMapper;

        void update(MESSAGE message) {
            var value = fieldMapper.apply(message);
            gauge.set(value);
        }
    }

    private final Collection<MessageGauge<MESSAGE>> messageGauges;

    public void update(MESSAGE message) {
        messageGauges.forEach(it -> it.update(message));
    }
}
