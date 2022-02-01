package de.malkusch.ha.monitoring.infrastructure;

import java.io.IOException;

interface Poller {

    void update() throws IOException, InterruptedException;

}
