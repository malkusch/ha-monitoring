package de.malkusch.ha.shared.infrastructure.mqtt;

import java.io.IOException;

final class NullMqtt implements Mqtt {

    @Override
    public void close() throws Exception {
    }

    @Override
    public void subscribe(String topic, Consumer consumer) throws IOException {
    }
}
