package ru.rutcampustrack.academic.threshold;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.threshold.ResolvedThresholdResponse;
import ru.rutcampustrack.academic.contract.dto.threshold.SetThresholdRequest;
import ru.rutcampustrack.academic.entity.AttendanceThreshold;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.repository.AttendanceThresholdRepository;
import ru.rutcampustrack.academic.security.RequestContext;

@Service
public class ThresholdService {

    private final AttendanceThresholdRepository thresholdRepository;
    private final RequestContext requestContext;

    public ThresholdService(AttendanceThresholdRepository thresholdRepository,
                             RequestContext requestContext) {
        this.thresholdRepository = thresholdRepository;
        this.requestContext = requestContext;
    }

    private void requireHeadman() {
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Только староста мо��ет ��правлять порогами группы и п��ед��ета");
        }
    }

    /**
     * Set global threshold (ADMIN only — role check in controller via @RequireRole).
     * Most-general level (THRSH-01).
     */
    @Transactional
    public AttendanceThreshold setGlobalThreshold(SetThresholdRequest request) {
        AttendanceThreshold threshold = thresholdRepository.findByGroupIdIsNullAndSubjectIdIsNull()
                .orElseGet(() -> new AttendanceThreshold(null, null, 0, requestContext.getUserId()));
        threshold.setThresholdPct(request.minPercentage());
        return thresholdRepository.save(threshold);
    }

    /**
     * Set group-level threshold (HEADMAN only).
     * Mid-specificity level (THRSH-02).
     */
    @Transactional
    public AttendanceThreshold setGroupThreshold(SetThresholdRequest request) {
        requireHeadman();
        Long groupId = requestContext.getGroupId();
        AttendanceThreshold threshold = thresholdRepository.findByGroupIdAndSubjectIdIsNull(groupId)
                .orElseGet(() -> new AttendanceThreshold(groupId, null, 0, requestContext.getUserId()));
        threshold.setThresholdPct(request.minPercentage());
        return thresholdRepository.save(threshold);
    }

    /**
     * Set subject-level threshold for headman's group (HEADMAN only).
     * Most-specific level (THRSH-03).
     */
    @Transactional
    public AttendanceThreshold setSubjectThreshold(SetThresholdRequest request, Long subjectId) {
        requireHeadman();
        Long groupId = requestContext.getGroupId();
        AttendanceThreshold threshold = thresholdRepository.findByGroupIdAndSubjectId(groupId, subjectId)
                .orElseGet(() -> new AttendanceThreshold(groupId, subjectId, 0, requestContext.getUserId()));
        threshold.setThresholdPct(request.minPercentage());
        return thresholdRepository.save(threshold);
    }

    /**
     * Most-specific-wins resolution (THRSH-04):
     * subject > group > global > default(0)
     */
    @Transactional(readOnly = true)
    public ResolvedThresholdResponse resolveThreshold(Long groupId, Long subjectId) {
        // 1. Try subject-level
        if (subjectId != null && groupId != null) {
            var subjectThreshold = thresholdRepository.findByGroupIdAndSubjectId(groupId, subjectId);
            if (subjectThreshold.isPresent()) {
                AttendanceThreshold t = subjectThreshold.get();
                return new ResolvedThresholdResponse(t.getThresholdPct(), "subject", t.getId());
            }
        }

        // 2. Try group-level
        if (groupId != null) {
            var groupThreshold = thresholdRepository.findByGroupIdAndSubjectIdIsNull(groupId);
            if (groupThreshold.isPresent()) {
                AttendanceThreshold t = groupThreshold.get();
                return new ResolvedThresholdResponse(t.getThresholdPct(), "group", t.getId());
            }
        }

        // 3. Try global
        var globalThreshold = thresholdRepository.findByGroupIdIsNullAndSubjectIdIsNull();
        if (globalThreshold.isPresent()) {
            AttendanceThreshold t = globalThreshold.get();
            return new ResolvedThresholdResponse(t.getThresholdPct(), "global", t.getId());
        }

        // 4. Default — no threshold configured
        return new ResolvedThresholdResponse(0, "default", null);
    }

    @Transactional(readOnly = true)
    public Page<AttendanceThreshold> listThresholds(Pageable pageable) {
        return thresholdRepository.findAll(pageable);
    }
}
