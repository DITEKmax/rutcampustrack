package ru.rutcampustrack.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.config.OtpProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, OtpProperties.class})
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
