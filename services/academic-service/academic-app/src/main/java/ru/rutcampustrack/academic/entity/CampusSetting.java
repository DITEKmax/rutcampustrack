package ru.rutcampustrack.academic.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "campus_settings")
@Getter
@NoArgsConstructor
public class CampusSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = 128)
    private String name;

    @Setter
    @Column(nullable = false)
    private double lat;

    @Setter
    @Column(nullable = false)
    private double lng;

    @Setter
    @Column(name = "radius_m", nullable = false)
    private int radiusM;

    @Setter
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
