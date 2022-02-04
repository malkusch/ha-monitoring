package de.malkusch.ha.monitoring.infrastructure.persistence;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class StateStorageConfiguration {

    @Bean
    StateStorage fileStateStorage(@Value("${state-storage.file}") String path) throws IOException {
        return new FileStateStorage(path);
    }
}
