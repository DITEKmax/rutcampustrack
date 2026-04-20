package ru.rutcampustrack.academic.grpc;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.CampusSetting;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.repository.CampusSettingRepository;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.UserRepository;

import java.util.List;
import java.util.Optional;

/**
 * Separate Spring bean for cached gRPC read operations.
 * Must be a separate bean (not inner methods) to avoid AOP self-invocation
 * where @Cacheable proxy is bypassed (per D-01).
 */
@Service
public class AcademicReadService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final SemesterRepository semesterRepository;
    private final CampusSettingRepository campusSettingRepository;

    public AcademicReadService(GroupRepository groupRepository,
                               UserRepository userRepository,
                               SemesterRepository semesterRepository,
                               CampusSettingRepository campusSettingRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.semesterRepository = semesterRepository;
        this.campusSettingRepository = campusSettingRepository;
    }

    @Cacheable(value = "groups", key = "#groupId")
    public Group fetchGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));
    }

    @Cacheable(value = "group_members", key = "#groupId")
    public List<User> fetchGroupMembers(Long groupId) {
        return userRepository.findByGroupId(groupId);
    }

    @Cacheable(value = "active_semester", key = "'current'")
    public Semester fetchActiveSemester() {
        return semesterRepository.findByIsActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "isActive", true));
    }

    @Cacheable(value = "campus_geofence", key = "'global'")
    public CampusSetting fetchCampusGeofence() {
        return campusSettingRepository.findById(1L)
                .orElseThrow(() -> new ResourceNotFoundException("CampusSetting", "id", 1L));
    }

    @Cacheable(value = "users", key = "#userId")
    public User fetchUserById(Long userId) {
        return userRepository.findByIdIncludingArchived(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    /**
     * Lookup user by Telegram ID — not cached, fresh data needed for bot /start command.
     * Returns Optional.empty() if no user has the given telegram_id.
     */
    public Optional<User> fetchUserByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    /**
     * M05 Группа 3 / D6: RBAC-проверка «пользователь — староста указанной группы».
     * Hot-path для synchronous gRPC {@code AcademicGrpcService.isHeadman}
     * (вызывается schedule-service / attendance-service на каждую операцию
     * старосты). Кеш namespace {@code rbac} с TTL 1 минута — балансирует
     * между латентностью и скоростью инвалидации при смене флага
     * {@code is_headman} администратором. Программатическое eviction
     * в {@link ru.rutcampustrack.academic.user.UserService#patchUser}
     * мгновенно инвалидирует запись при изменении флага.
     */
    @Cacheable(value = "rbac", key = "#userId + ':' + #groupId")
    public boolean isHeadmanOf(Long userId, Long groupId) {
        return userRepository.findById(userId)
                .map(user -> user.isHeadman()
                        && user.getGroupId() != null
                        && user.getGroupId().equals(groupId))
                .orElse(false);
    }
}
