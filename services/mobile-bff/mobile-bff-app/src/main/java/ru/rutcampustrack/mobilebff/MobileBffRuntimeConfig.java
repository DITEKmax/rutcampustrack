package ru.rutcampustrack.mobilebff;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class MobileBffRuntimeConfig {
    @Bean
    Clock mobileBffClock() {
        return Clock.system(ZoneId.of("Europe/Moscow"));
    }
}
