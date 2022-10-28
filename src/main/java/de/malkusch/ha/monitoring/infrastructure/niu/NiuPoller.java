package de.malkusch.ha.monitoring.infrastructure.niu;

import static de.malkusch.ha.shared.infrastructure.scheduler.Schedulers.singleThreadScheduler;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Function;

import de.malkusch.ha.shared.infrastructure.scheduler.Schedulers;
import de.malkusch.niu.Niu;
import de.malkusch.niu.Niu.BatteryInfo;
import de.malkusch.niu.Niu.Odometer;
import de.malkusch.niu.Niu.Vehicle;
import de.malkusch.niu.Niu.VehicleInfo;
import io.prometheus.client.Gauge;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NiuPoller implements AutoCloseable {

    private final Duration rate;
    private final ScheduledExecutorService scheduler = singleThreadScheduler("niu");

    NiuPoller(Niu niu, Duration rate) throws IOException {
        this.rate = rate;

        for (var vehicle : niu.vehicles()) {
            {
                var updates = new VehicleUpdates<>(niu::odometer,
                        new GaugeUpdate<>(gauge(vehicle, "odometer_days"), simpleUpdate(Odometer::days)),
                        new GaugeUpdate<>(gauge(vehicle, "odometer_mileage"), simpleUpdate(Odometer::mileage)));

                scheduleVehicleUpdates(vehicle.serialNumber(), updates);
            }

            {
                var updates = new VehicleUpdates<>(niu::batteryInfo,
                        new GaugeUpdate<>(gauge(vehicle, "battery_temperature"),
                                simpleUpdate(BatteryInfo::temperature)),
                        new GaugeUpdate<>(gauge(vehicle, "battery_grade"), simpleUpdate(BatteryInfo::grade)),
                        new GaugeUpdate<>(gauge(vehicle, "battery_charge"), simpleUpdate(BatteryInfo::charge)),
                        new GaugeUpdate<>(gauge(vehicle, "battery_status"), simpleUpdate(BatteryInfo::status)));

                scheduleVehicleUpdates(vehicle.serialNumber(), updates);
            }

            {
                var updates = new VehicleUpdates<>(niu::vehicle,
                        new GaugeUpdate<>(gauge(vehicle, "shaking"), simpleUpdate(VehicleInfo::shakingValue)),
                        new GaugeUpdate<>(gauge(vehicle, "gsm"), simpleUpdate(VehicleInfo::gsm)),
                        new GaugeUpdate<>(gauge(vehicle, "gps"), simpleUpdate(VehicleInfo::gps)),
                        new GaugeUpdate<>(gauge(vehicle, "ecuBatteryCharge"),
                                simpleUpdate(VehicleInfo::ecuBatteryCharge)),
                        new GaugeUpdate<>(gauge(vehicle, "speed"), simpleUpdate(VehicleInfo::nowSpeed)),
                        new GaugeUpdate<>(gauge(vehicle, "vehicleInfo_status"), simpleUpdate(VehicleInfo::status)),
                        new GaugeUpdate<>(gauge(vehicle, "leftTime"), simpleUpdate(VehicleInfo::leftTime)),
                        new GaugeUpdate<>(gauge(vehicle, "isConnected"), simpleUpdate(it -> it.isConnected() ? 1 : 0)),
                        new GaugeUpdate<>(gauge(vehicle, "estimatedMileage"),
                                simpleUpdate(VehicleInfo::estimatedMileage)),
                        new GaugeUpdate<>(gauge(vehicle, "ss_online_sta"), simpleUpdate(VehicleInfo::ss_online_sta)),
                        new GaugeUpdate<>(gauge(vehicle, "gpsTimestamp"), timestampUpdate(VehicleInfo::gpsTimestamp)),
                        new GaugeUpdate<>(gauge(vehicle, "gsmTimestamp"), timestampUpdate(VehicleInfo::gsmTimestamp)),
                        new GaugeUpdate<>(gauge(vehicle, "time"), timestampUpdate(VehicleInfo::time)),

                        new GaugeUpdate<>(gauge(vehicle, "position_lng"), simpleUpdate(it -> it.position().lng())),
                        new GaugeUpdate<>(gauge(vehicle, "position_lat"), simpleUpdate(it -> it.position().lat())),
                        new GaugeUpdate<>(gauge(vehicle, "position", "lng", "lat"), (info, gauge) -> gauge
                                .labels(Double.toString(info.position().lng()), Double.toString(info.position().lat()))
                                .set(1)));

                scheduleVehicleUpdates(vehicle.serialNumber(), updates);
            }
        }
    }

    private record VehicleUpdates<T> (VehicleQuery<T> query, GaugeUpdate<T>... updates) {
    }

    private record GaugeUpdate<T> (Gauge gauge, BiConsumer<T, Gauge> update) {
    }

    private static <T> BiConsumer<T, Gauge> simpleUpdate(Function<T, Number> update) {
        return (response, gauge) -> gauge.set(update.apply(response).doubleValue());
    }

    private static <T> BiConsumer<T, Gauge> timestampUpdate(Function<T, Instant> update) {
        return (response, gauge) -> gauge.set(update.apply(response).getEpochSecond());
    }

    @FunctionalInterface
    private interface VehicleQuery<T> {
        T query(String sn) throws IOException;
    }

    private <T> void scheduleVehicleUpdates(String serialNumber, VehicleUpdates<T> updates) {
        Runnable schedule = () -> {
            try {
                var result = updates.query().query(serialNumber);
                for (var update : updates.updates) {
                    update.update.accept(result, update.gauge);
                }
            } catch (Exception e) {
                log.error("Failed to update niu's metric", e);
            }
        };
        schedule.run();
        scheduler.scheduleAtFixedRate(schedule::run, rate.toSeconds(), rate.toSeconds(), SECONDS);
    }

    private static Gauge gauge(Vehicle vehicle, String name, String... labels) {
        var prefix = "niu_" + vehicle.name();
        var gaugeName = prefix + "_" + name;
        return Gauge.build().name(gaugeName).help(gaugeName).labelNames(labels).register();
    }

    @Override
    public void close() throws Exception {
        Schedulers.close(scheduler);
    }
}
