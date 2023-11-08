package de.malkusch.ha.shared.infrastructure.async;

import static java.lang.Thread.currentThread;

import java.lang.Thread.Builder;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public final class AsyncService {

    private final Builder.OfVirtual builder = Thread //
            .ofVirtual() //
            .uncaughtExceptionHandler((t, e) -> log.error("Uncaught exception in thread {}", t, e));

    @FunctionalInterface
    public interface Task {
        void run() throws Exception;
    }

    public Runnable async(Runnable runnable) {
        return () -> executeAsync(runnable::run);
    }

    public void executeAsync(Task task) {
        builder.start(() -> {
            try {
                task.run();

            } catch (InterruptedException e) {
                currentThread().interrupt();

            } catch (Throwable e) {
                log.error("Error in async task", e);
            }
        });
    }
}
