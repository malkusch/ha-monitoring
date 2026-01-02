package de.malkusch.ha.monitoring.infrastructure;

import static de.malkusch.ha.shared.infrastructure.scheduler.Schedulers.singleThreadScheduler;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.malkusch.ha.shared.infrastructure.buderus.Heater;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenException;
import de.malkusch.ha.shared.infrastructure.circuitbreaker.CircuitBreaker.CircuitBreakerOpenedException;
import de.malkusch.ha.shared.infrastructure.scheduler.Schedulers;
import io.prometheus.client.Gauge;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BuderusPoller implements AutoCloseable {

    private final Heater heater;
    private final ScheduledExecutorService scheduler = singleThreadScheduler("BuderusHeater");

    BuderusPoller(Heater heater, @Value("${buderus.queryRate}") Duration rate) throws Exception {
        this.heater = heater;

        log.info("Polling KM200 with rate {}", rate);

        scheduleUpdate("/dhwCircuits/dhw1/actualTemp", rate);
        scheduleUpdate("/dhwCircuits/dhw1/currentSetpoint", rate);
        scheduleUpdate("/dhwCircuits/dhw1/waterFlow", rate);

        scheduleUpdate("/heatingCircuits/hc1/actualSupplyTemperature", rate);
        scheduleUpdate("/heatingCircuits/hc1/currentRoomSetpoint", rate);
        scheduleUpdate("/heatingCircuits/hc1/supplyTemperatureSetpoint", rate);
        scheduleUpdate("/heatingCircuits/hc1/pumpModulation", rate);

        scheduleUpdate("/heatSources/actualModulation", rate);
        scheduleUpdate("/heatSources/actualSupplyTemperature", rate);
        scheduleUpdate("/heatSources/supplyTemperatureSetpoint", rate);
        scheduleUpdate("/heatSources/applianceSupplyTemperature", rate);
        scheduleUpdate("/heatSources/CHpumpModulation", rate);
        scheduleUpdate("/heatSources/energyMonitoring/consumption", rate);
        scheduleUpdate("/heatSources/fanSpeed_setpoint", rate);
        scheduleUpdate("/heatSources/hs1/actualModulation", rate);
        scheduleUpdate("/heatSources/nominalCHPower", rate);
        scheduleUpdate("/heatSources/nominalDHWPower", rate);
        scheduleUpdate("/heatSources/returnTemperature", rate);
        scheduleUpdate("/heatSources/workingTime/totalSystem", rate);
        scheduleUpdate("/heatSources/numberOfStarts", rate);
        // scheduleUpdate("/heatSources/systemPressure", rate);

        scheduleUpdate("/system/appliance/actualSupplyTemperature", rate);
        scheduleUpdate("/system/sensors/temperatures/outdoor_t1", rate);
        scheduleUpdate("/system/sensors/temperatures/return", rate);
        scheduleUpdate("/system/sensors/temperatures/supply_t1", rate);
        scheduleUpdate("/system/sensors/temperatures/switch", rate);
    }

    private void scheduleUpdate(String path, Duration rate) throws Exception {
        var name = "heater" + path.replace('/', '_');
        var help = path;
        var gauge = Gauge.build().name(name).help(help).create();
        gauge.register();
        Callable<Void> update = () -> {
            var value = heater.query(path);
            gauge.set(value);
            log.debug("Update {} = {}", path, value);
            return null;
        };
        update.call();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                update.call();

            } catch (CircuitBreakerOpenedException e) {
                log.warn("Stop polling heater: Open circuit breaker");

            } catch (CircuitBreakerOpenException e) {
                log.debug("Stop polling heater: Circuit breaker is open");

            } catch (Exception e) {
                log.error("Failed to update heater's metric {}", path, e);
            }
        }, rate.toSeconds(), rate.toSeconds(), SECONDS);
    }

    @Override
    public void close() throws InterruptedException {
        Schedulers.close(scheduler);
    }
}
