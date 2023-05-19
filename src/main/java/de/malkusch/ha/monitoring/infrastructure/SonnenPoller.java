package de.malkusch.ha.monitoring.infrastructure;

import java.io.IOException;
import java.time.LocalTime;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class SonnenPoller implements Poller {

    private final Poller poller;
    private final DownTime downTime;

    public static record DownTime(LocalTime start, LocalTime end) {

        public DownTime {
            if (!start.isBefore(end)) {
                throw new IllegalArgumentException(String.format("Start %s must be before End %s", start, end));
            }
        }

        @Override
        public String toString() {
            return String.format("%s - %s", start, end);
        }

    }

    public SonnenPoller(Poller poller, DownTime downTime) {
        this.poller = poller;
        this.downTime = downTime;

        log.info("Configured {} with downtime {}", poller, downTime);
    }

    @Override
    public void update() throws IOException, InterruptedException {
        try {
            poller.update();

        } catch (Exception e) {
            if (isWithinDownTime()) {
                log.debug("{} is not available within daily down time {}", poller, downTime);

            } else {
                throw e;
            }
        }
    }

    private boolean isWithinDownTime() {
        var time = LocalTime.now();
        return (time.isAfter(downTime.start) && time.isBefore(downTime.end));
    }

    @Override
    public String toString() {
        return poller.toString();
    }
}
