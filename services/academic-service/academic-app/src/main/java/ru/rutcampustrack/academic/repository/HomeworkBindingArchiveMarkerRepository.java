package ru.rutcampustrack.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.rutcampustrack.academic.entity.HomeworkBindingArchiveMarker;

public interface HomeworkBindingArchiveMarkerRepository
        extends JpaRepository<HomeworkBindingArchiveMarker, Long> {
}
