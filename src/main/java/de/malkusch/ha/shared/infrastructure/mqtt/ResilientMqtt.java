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
    private final CircuitBreaker<Void> circuitBreaker;

    public ResilientMqtt(Mqtt mqtt, CircuitBreaker.Properties properties) {
        this.mqtt = mqtt;
        this.circuitBreaker = new CircuitBreaker<>(mqtt.toString(), properties, IOException.class);

        log.info("Configured MQTT with {}", circuitBreaker);
    }

    private static record Subscription(String topic, Consumer consumer) {

        @Override
        public String toString() {
            return topic;
        }
    }

    @Override
    public void subscribe(String topic, Consumer consumer) {
        var subscription = new Subscription(topic, consumer);
        if (!subscribe(subscription)) {
            subscriptions.add(subscription);
        }
    }

    private final Queue<Subscription> subscriptions = new ConcurrentLinkedQueue<>();

    private boolean subscribe(Subscription subscription) {
        try {
            circuitBreaker.run(() -> mqtt.subscribe(subscription.topic, subscription.consumer));
            log.info("Subscribed {} sucessfully", subscription);
            return true;

        } catch (CircuitBreakerOpenedException e) {
            log.warn("Subscribing {} failed: Circuit breaker opened", subscription, e.getCause());

        } catch (CircuitBreakerOpenException e) {
            log.info("Subscribing {} failed: Circuit breaker open", subscription);

        } catch (Exception e) {
            log.warn("Subscribing {} failed", subscription, e);
        }
        return false;
    }

    @Scheduled(fixedRateString = "${mqtt.resubscribe-rate}")
    void subscribeQueue() {
        if (subscriptions.isEmpty()) {
            return;
        }
        log.info("Resubscribing {} subscriptions", subscriptions.size());

        Subscription subscription;
        while ((subscription = subscriptions.poll()) != null) {
            if (!subscribe(subscription)) {
                subscriptions.add(subscription);
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
