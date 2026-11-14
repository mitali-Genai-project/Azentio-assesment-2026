package com.azentio.clearlist.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Injected wherever "now" is needed so tests can control time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
