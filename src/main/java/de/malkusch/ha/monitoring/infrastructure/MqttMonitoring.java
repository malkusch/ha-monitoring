package de.malkusch.ha.monitoring.infrastructure;

import static java.util.Arrays.asList;
import static lombok.AccessLevel.PRIVATE;

import java.io.IOException;
import java.util.Collection;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.malkusch.ha.monitoring.infrastructure.persistence.GaugeFactory;
import de.malkusch.ha.shared.infrastructure.mqtt.Mqtt;
import io.prometheus.client.Gauge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor(access = PRIVATE)
public class MqttMonitoring<MESSAGE> {

    @RequiredArgsConstructor
    @Component
    @Slf4j
    public static class Factory implements AutoCloseable {

        private final ObjectMapper mapper;
        private final Mqtt mqtt;
        private final GaugeFactory gaugeFactory;

        public <MESSAGE> MqttMonitoring<MESSAGE> build(Class<MESSAGE> type, String topic,
                Collection<MessageGauge<MESSAGE>> fieldPollers) throws IOException {

            return build(topic, safeJson((it) -> mapper.readValue(it, type)), fieldPollers);
        }

        public MqttMonitoring<JsonNode> build(String name, String topic, String... paths) throws IOException {
            return build(name, topic, asList(paths));
        }

        public MqttMonitoring<JsonNode> build(String name, String topic, Collection<String> paths) throws IOException {
            var fieldPollers = paths.stream().map(path -> {
                var gauge = gaugeFactory.build(gaugeName(name, path));
                MessageGauge<JsonNode> messageGauge = new MessageGauge<>(gauge, it -> it.at(path).asDouble());
                return messageGauge;
            }).toList();
            return build(topic, safeJson((it) -> mapper.readTree(it)), fieldPollers);
        }

        private static String gaugeName(String topic, String path) {
            return topic + "_" + path.substring(1).replace(".", "");
        }

        private static final Function<String, String> FILTER_NAN = it -> it.replaceAll(": nan", ": null");

        <MESSAGE> MessageMapper<MESSAGE> safeJson(MessageMapper<MESSAGE> mapper) {
            return message -> {
                try {
                    return mapper.map(message);

                } catch (Exception e) {
                    var fixed = FILTER_NAN.apply(message);
                    var mapped = mapper.map(fixed);
                    log.warn("Message {} was repaired to {}: {}", message, fixed, e.getMessage());
                    return mapped;
                }
            };
        }

        private static interface MessageMapper<MESSAGE> {
            MESSAGE map(String message) throws Exception;
        }

        private <MESSAGE> MqttMonitoring<MESSAGE> build(String topic, MessageMapper<MESSAGE> messageMapper,
                Collection<MessageGauge<MESSAGE>> fieldPollers) throws IOException {

            var poller = new MqttMonitoring<>(fieldPollers);
            mqtt.subscribe(topic, it -> {
                var message = messageMapper.map(it);
                poller.update(message);
            });
            return poller;
        }

        @Override
        public void close() throws Exception {
            mqtt.close();
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
