package ru.rutcampustrack.academic.map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers the dedicated map-usage key without coupling it to auth secrets. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CampusMapUsageProperties.class)
public class CampusMapUsageConfig {
}
