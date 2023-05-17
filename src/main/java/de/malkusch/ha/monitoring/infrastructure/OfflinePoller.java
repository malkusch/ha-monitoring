package de.malkusch.ha.monitoring.infrastructure;

import java.io.IOException;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class OfflinePoller implements Poller {

    private final Poller poller;

    @Override
    public void update() throws InterruptedException {
        try {
            poller.update();

        } catch (IOException e) {
        }
    }
}
