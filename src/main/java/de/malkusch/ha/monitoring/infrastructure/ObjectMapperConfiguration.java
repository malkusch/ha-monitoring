package de.malkusch.ha.monitoring.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ObjectMapperConfiguration {

    @Bean
    ObjectMapper mapper() {
        return new ObjectMapper();
    }
}
