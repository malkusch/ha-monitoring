package de.malkusch.ha.monitoring.infrastructure.persistence;

import org.springframework.stereotype.Service;

import io.prometheus.client.Gauge;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GaugeFactory {

    private final StateStorage stateStorage;

    public Gauge build(String name) {
        var gauge = Gauge.build().name(name).help(name).create();
        stateStorage.persistDouble(name, gauge::set, gauge::get);
        gauge.register();
        return gauge;
    }
}
