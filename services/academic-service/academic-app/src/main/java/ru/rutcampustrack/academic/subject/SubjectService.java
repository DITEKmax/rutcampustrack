package ru.rutcampustrack.academic.subject;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.subject.CreateSubjectRequest;
import ru.rutcampustrack.academic.contract.dto.subject.UpdateSubjectRequest;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;

import java.time.OffsetDateTime;

@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final RequestContext requestContext;

    public SubjectService(SubjectRepository subjectRepository, RequestContext requestContext) {
        this.subjectRepository = subjectRepository;
        this.requestContext = requestContext;
    }

    private void requireHeadman() {
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Только староста может управлять предметами");
        }
    }

    @Transactional
    public Subject createSubject(CreateSubjectRequest request) {
        requireHeadman();
        Subject subject = new Subject();
        subject.setName(request.name());
        subject.setType(request.type());
        // createdAt is set by @PrePersist on Subject entity
        return subjectRepository.save(subject);
    }

    @Transactional(readOnly = true)
    public Subject getSubject(Long id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subject", "id", id));
    }

    @Transactional(readOnly = true)
    public Page<Subject> listSubjects(Pageable pageable) {
        return subjectRepository.findAll(pageable);
    }

    @Transactional
    public Subject updateSubject(Long id, UpdateSubjectRequest request) {
        requireHeadman();
        Subject subject = getSubject(id);
        subject.setName(request.name());
        subject.setType(request.type());
        return subjectRepository.save(subject);
    }

    @Transactional
    public void deleteSubject(Long id) {
        requireHeadman();
        Subject subject = getSubject(id);
        subjectRepository.delete(subject);
    }
}
