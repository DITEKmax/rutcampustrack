package ru.rutcampustrack.mobilebff;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.rutcampustrack.shared.web.time.EducationalTime;

@Configuration
public class MobileBffRuntimeConfig {
    @Bean
    Clock mobileBffClock() {
        return Clock.system(ZoneId.of("Europe/Moscow"));
    }

    @Bean
    public EducationalTime educationalTime(Clock realClock) {
        return new EducationalTime(realClock);
    }
}
