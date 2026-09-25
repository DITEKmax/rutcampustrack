package ru.rutcampustrack.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.rutcampustrack.gateway.filter.PwaVersionPolicyProperties;
import ru.rutcampustrack.gateway.security.InternalIssuerClientProperties;
import ru.rutcampustrack.gateway.security.ReportDownloadBackendProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({InternalIssuerClientProperties.class, PwaVersionPolicyProperties.class,
        ReportDownloadBackendProperties.class})
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
