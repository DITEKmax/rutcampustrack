package ru.rutcampustrack.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.academic.entity.CampusSetting;

public interface CampusSettingRepository extends JpaRepository<CampusSetting, Long> {
    // Single-row table; use findAll().get(0) or findById(1L)
}
