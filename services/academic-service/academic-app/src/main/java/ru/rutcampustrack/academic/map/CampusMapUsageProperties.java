package ru.rutcampustrack.academic.map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Dedicated, privacy-preserving key used only for campus-map usage events. */
@ConfigurationProperties(prefix = "campus-map.usage")
public record CampusMapUsageProperties(String hmacKey) {
}
