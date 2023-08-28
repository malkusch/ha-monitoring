package de.malkusch.ha.shared.infrastructure.mqtt;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.scheduling.annotation.Scheduled;

import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenException;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenedException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class ResilientMqtt implements Mqtt, AutoCloseable {

    private final Mqtt mqtt;

    private class ResilientConsumer implements Consumer {

        private final CircuitBreaker<Void> circuitBreaker;
        private final Consumer consumer;
        private final String topic;

        public ResilientConsumer(String topic, Consumer consumer) {
            circuitBreaker = new CircuitBreaker<Void>(topic, subscribeCircuitBreaker, Throwable.class);
            this.consumer = consumer;
            this.topic = topic;
        }

        @Override
        public void consume(String message) throws Exception {
            try {
                circuitBreaker.run(() -> consumer.consume(message));

            } catch (CircuitBreakerOpenedException e) {
                log.warn("Failed consuming message from {}: Circuit breaker opened", topic, e.getCause());

            } catch (CircuitBreakerOpenException e) {
                log.info("Failed consuming message from {}: Circuit breaker open", topic);

            } catch (Throwable e) {
                log.warn("Failed consuming message from {}: {}", topic, message, e);
            }
        }

        @Override
        public String toString() {
            return topic;
        }
    }

    public ResilientMqtt(Mqtt mqtt, CircuitBreaker.Properties properties) {
        this.mqtt = mqtt;
        this.subscribeCircuitBreaker = new CircuitBreaker<>(mqtt.toString(), properties, IOException.class);

        log.info("Configured MQTT with {}", subscribeCircuitBreaker);
    }

    @Override
    public void subscribe(String topic, Consumer consumer) {
        var resilientConsumer = new ResilientConsumer(topic, consumer);
        if (!subscribe(resilientConsumer)) {
            subscriptions.add(resilientConsumer);
        }
    }

    private final Queue<ResilientConsumer> subscriptions = new ConcurrentLinkedQueue<>();
    private final CircuitBreaker<Void> subscribeCircuitBreaker;

    private boolean subscribe(ResilientConsumer consumer) {
        try {
            subscribeCircuitBreaker.run(() -> mqtt.subscribe(consumer.topic, consumer));
            log.info("Subscribed {} sucessfully", consumer);
            return true;

        } catch (CircuitBreakerOpenedException e) {
            log.warn("Subscribing {} failed: Circuit breaker opened", consumer, e.getCause());

        } catch (CircuitBreakerOpenException e) {
            log.info("Subscribing {} failed: Circuit breaker open", consumer);

        } catch (Throwable e) {
            log.warn("Subscribing {} failed", consumer, e);
        }
        return false;
    }

    @Scheduled(fixedRateString = "${mqtt.resubscribe-rate}")
    void subscribeQueue() {
        if (subscriptions.isEmpty()) {
            return;
        }
        log.info("Resubscribing {} subscriptions", subscriptions.size());

        ResilientConsumer consumer;
        while ((consumer = subscriptions.poll()) != null) {
            if (!subscribe(consumer)) {
                subscriptions.add(consumer);
                return;
            }
        }
    }

    @Override
    public void close() throws Exception {
        try (mqtt) {
            subscriptions.clear();
        }
    }
}
