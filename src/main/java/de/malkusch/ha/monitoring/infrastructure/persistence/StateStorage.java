package de.malkusch.ha.monitoring.infrastructure.persistence;

import java.util.function.Consumer;

public interface StateStorage {

    @FunctionalInterface
    public interface DeferredValue<T> {
        T value();
    }

    void persist(String key, Consumer<String> restore, DeferredValue<String> value);

    default void persistDouble(String key, Consumer<Double> restore, DeferredValue<Double> value) {
        persist(key, it -> restore.accept(Double.parseDouble(it)), () -> value.value().toString());
    }
}
