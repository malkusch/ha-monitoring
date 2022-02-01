package de.malkusch.ha.shared.infrastructure.scheduler;

import static java.lang.System.exit;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.ScheduledExecutorService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Schedulers {

    public static void close(ScheduledExecutorService scheduler) throws InterruptedException  {
        scheduler.shutdown();
        if (scheduler.awaitTermination(10, SECONDS)) {
            return;
        }
        log.warn("Failed shutting down scheduler. Forcing shutdown now!");
        scheduler.shutdownNow();
        if (!scheduler.awaitTermination(10, SECONDS)) {
            log.error("Forced shutdown failed");
        }
    }
    
    public static ScheduledExecutorService singleThreadScheduler(String name) {
        return newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, name);
            thread.setUncaughtExceptionHandler((t, e) -> {
                log.error("Shutting down due to an error in " + name, e);
                exit(-1);
            });
            thread.setDaemon(true);
            return thread;
        });
    }
}
