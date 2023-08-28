package de.malkusch.ha.shared.infrastructure.mqtt;

import java.io.IOException;

public interface Mqtt extends AutoCloseable {

    @FunctionalInterface
    public interface Consumer {
        void consume(String message) throws Exception;
    }

    void subscribe(String topic, Consumer consumer) throws IOException;
}
