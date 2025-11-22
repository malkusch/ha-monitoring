package de.malkusch.ha.monitoring.infrastructure.mqtt;

import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenException;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenedException;
import de.malkusch.ha.shared.infrastructure.mqtt.MqttConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Function;

interface MessageMapper<MESSAGE> {

    MESSAGE map(String message) throws Exception;

    @Component
    @RequiredArgsConstructor
    @Slf4j
    static class Factory {

        private final ObjectMapper mapper;

        public <T> MessageMapper<T> jsonObject(Class<T> type) {
            return safeJson((message) -> mapper.readValue(message, type));
        }

        public MessageMapper<JsonNode> jsonTree() {
            return safeJson(mapper::readTree);
        }

        private final MqttConfiguration.Properties mqttProperties;

        private <T> CircuitBreaker<T> circuitBreaker() {
            return new CircuitBreaker<T>("json-mapper", mqttProperties.getCircuitBreaker(), Throwable.class);
        }

        private static final Function<String, String> FILTER_NAN = it -> it.replaceAll(": nan", ": null");

        <MESSAGE> MessageMapper<MESSAGE> safeJson(MessageMapper<MESSAGE> mapper) {
            var circuitBreaker = circuitBreaker();
            return message -> {
                try {
                    return circuitBreaker.get(() -> mapper.map(message));

                } catch (Throwable e) {
                    var fixed = FILTER_NAN.apply(message);
                    var mapped = mapper.map(fixed);
                    try {
                        throw e;

                    } catch (CircuitBreakerOpenedException e2) {
                        log.info("Message was repaired: Circuit breaker opened");

                    } catch (CircuitBreakerOpenException e2) {

                    } catch (Throwable e2) {
                        log.warn("Message {} was repaired to {}: {}", message, fixed, e.getMessage());
                    }

                    return mapped;
                }
            };
        }
    }
}