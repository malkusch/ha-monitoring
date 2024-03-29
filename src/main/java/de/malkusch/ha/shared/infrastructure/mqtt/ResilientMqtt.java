package de.malkusch.ha.shared.infrastructure.mqtt;

import static de.malkusch.ha.shared.infrastructure.DateUtil.formatTime;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.scheduling.annotation.Scheduled;

import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenException;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenedException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class ResilientMqtt implements Mqtt, AutoCloseable {

    private final ReconnectableMqtt mqtt;
    private volatile Instant lastMessage = Instant.now();

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
            lastMessage = Instant.now();
            log.debug("Received message for {}", topic);
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

    public ResilientMqtt(ReconnectableMqtt mqtt, CircuitBreaker.Properties properties, Duration keepAlive) {
        this.mqtt = mqtt;
        this.subscribeCircuitBreaker = new CircuitBreaker<>(mqtt.toString(), properties, IOException.class);
        this.keepAlive = keepAlive;

        log.info("Configured MQTT with {}", subscribeCircuitBreaker);

        mqtt.onReconnect(this::resubscribeAll);
    }

    public static interface ReconnectableMqtt extends Mqtt {

        public void onReconnect(Runnable onReconnect);

        public void reconnect() throws IOException;

    }

    private final Queue<ResilientConsumer> subscriptions = new ConcurrentLinkedQueue<>();

    void resubscribeAll() {
        log.info("Resubscribe all");
        pendingSubscriptions.clear();
        pendingSubscriptions.addAll(subscriptions);
        subscribePendingSubscriptions();
    }

    @Override
    public void subscribe(String topic, Consumer consumer) {
        var resilientConsumer = new ResilientConsumer(topic, consumer);
        subscriptions.add(resilientConsumer);

        if (!subscribe(resilientConsumer)) {
            pendingSubscriptions.add(resilientConsumer);
        }
    }

    private final Queue<ResilientConsumer> pendingSubscriptions = new ConcurrentLinkedQueue<>();
    private final CircuitBreaker<Void> subscribeCircuitBreaker;

    private boolean subscribe(ResilientConsumer consumer) {
        try {
            subscribeCircuitBreaker.run(() -> mqtt.subscribe(consumer.topic, consumer));
            log.info("Subscribed {} successfully", consumer);
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
    void subscribePendingSubscriptions() {
        if (pendingSubscriptions.isEmpty()) {
            return;
        }
        log.info("Resubscribing {} subscriptions", pendingSubscriptions.size());

        ResilientConsumer consumer;
        while ((consumer = pendingSubscriptions.poll()) != null) {
            if (!subscribe(consumer)) {
                pendingSubscriptions.add(consumer);
                return;
            }
        }
    }

    private final Duration keepAlive;

    @Scheduled(fixedRateString = "${mqtt.keep-alive}")
    void keepAlive() throws IOException {
        Instant threshold = lastMessage.plus(keepAlive);
        if (Instant.now().isBefore(threshold)) {
            return;
        }
        log.warn("MQTT seems inactive. Last message was at {}.", formatTime(lastMessage));
        log.info("Reconnecting {}", mqtt);
        mqtt.reconnect();
    }

    @Override
    public void close() throws Exception {
        try (mqtt) {
            pendingSubscriptions.clear();
        }
    }
}
