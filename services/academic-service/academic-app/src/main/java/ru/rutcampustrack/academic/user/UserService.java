package ru.rutcampustrack.academic.user;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.dto.user.UpdateUserRequest;
import org.springframework.hateoas.EntityModel;
import ru.rutcampustrack.academic.contract.dto.user.UserCreatedResponse;
import ru.rutcampustrack.academic.contract.dto.user.UserResponse;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.StudentGroupHistory;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.event.GroupUpdatedEvent;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Business logic for User domain: CRUD, login generation, BCrypt password,
 * headman cascade revoke, student group transfer with history.
 */
@Service
public class UserService {

    private static final String CHARSET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int PASSWORD_LENGTH = 12;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    private final UserRepository userRepository;
    private final HeadmanAssistantRepository headmanAssistantRepository;
    private final StudentGroupHistoryRepository studentGroupHistoryRepository;
    private final RequestContext requestContext;
    private final UserAssembler userAssembler;
    private final CacheManager cacheManager;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(UserRepository userRepository,
                       HeadmanAssistantRepository headmanAssistantRepository,
                       StudentGroupHistoryRepository studentGroupHistoryRepository,
                       RequestContext requestContext,
                       UserAssembler userAssembler,
                       @Nullable CacheManager cacheManager,
                       ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.headmanAssistantRepository = headmanAssistantRepository;
        this.studentGroupHistoryRepository = studentGroupHistoryRepository;
        this.requestContext = requestContext;
        this.userAssembler = userAssembler;
        this.cacheManager = cacheManager;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public EntityModel<UserCreatedResponse> createUser(CreateUserRequest request) {
        // Generate login based on role
        String login = generateLogin(request.role());

        // Generate random plain-text password
        String plainPassword = generatePassword();

        // Hash password with BCrypt
        String passwordHash = passwordEncoder.encode(plainPassword);

        // Build user entity
        User user = new User();
        user.setLogin(login);
        user.setLastName(request.lastName());
        user.setFirstName(request.firstName());
        user.setMiddleName(request.middleName());
        user.setPasswordHash(passwordHash);
        user.setRole(request.role());
        user.setStatus(AccountStatus.ACTIVE);
        user.setGroupId(request.groupId());
        user.setEmployeeNumber(request.employeeNumber());
        user.setTelegramId(request.telegramId());
        user.setHeadman(false);
        user.setPasswordChanged(false);
        // BUG-006: храним plaintext до первой смены пароля чтобы админ мог
        // переиспользовать его (показать пользователю), пока тот его не сменил.
        user.setInitialPassword(plainPassword);
        OffsetDateTime now = OffsetDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        user = userRepository.save(user);
        return userAssembler.toCreatedModel(user, plainPassword);
    }

    public User findUserById(Long id) {
        return userRepository.findByIdIncludingArchived(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    public Page<User> listUsers(UserRole roleFilter, Pageable pageable) {
        if (roleFilter != null) {
            return userRepository.findByRole(roleFilter.name().toLowerCase(), pageable);
        }
        return userRepository.findAll(pageable);
    }

    public List<User> listTeachers() {
        return userRepository.findByRole("teacher", Pageable.ofSize(500)).getContent();
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public User updateUser(Long id, UpdateUserRequest request) {
        User user = findUserById(id);
        user.setLastName(request.lastName());
        user.setFirstName(request.firstName());
        user.setMiddleName(request.middleName());
        user.setRole(request.role());
        user.setGroupId(request.groupId());
        user.setEmployeeNumber(request.employeeNumber());
        user.setTelegramId(request.telegramId());
        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public User patchUser(Long id, PatchUserRequest request) {
        User user = findUserById(id);

        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.middleName() != null) {
            user.setMiddleName(request.middleName());
        }
        if (request.groupId() != null) {
            user.setGroupId(request.groupId());
        }
        if (request.employeeNumber() != null) {
            user.setEmployeeNumber(request.employeeNumber());
        }
        if (request.telegramId() != null) {
            user.setTelegramId(request.telegramId());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }

        // Headman revoke cascade (D-13)
        if (request.isHeadman() != null && !request.isHeadman() && user.isHeadman()) {
            headmanAssistantRepository.revokeAllByGroupId(user.getGroupId());
            user.setHeadman(false);
        }

        // Headman assign (USER-03)
        if (request.isHeadman() != null && request.isHeadman()) {
            if (user.getRole() != UserRole.STUDENT) {
                throw new BadRequestException("Только студент может быть назначен старостой");
            }
            user.setHeadman(true);
        }

        // After headman flag change — evict groups and group_members caches for this user's group (per D-10)
        if (request.isHeadman() != null && user.getGroupId() != null && cacheManager != null) {
            Cache groupsCache = cacheManager.getCache("groups");
            if (groupsCache != null) {
                groupsCache.evict(user.getGroupId());
            }
            Cache groupMembersCache = cacheManager.getCache("group_members");
            if (groupMembersCache != null) {
                groupMembersCache.evict(user.getGroupId());
            }
        }

        user.setUpdatedAt(OffsetDateTime.now());
        return userRepository.save(user);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public void archiveUser(Long id) {
        User user = findUserById(id);
        user.setStatus(AccountStatus.ARCHIVED);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    @CacheEvict(value = "group_members", key = "#request.newGroupId()")
    @Transactional
    public User transferStudent(Long id, TransferStudentRequest request) {
        User user = findUserById(id);
        if (user.getRole() != UserRole.STUDENT) {
            throw new BadRequestException("Перевод возможен только для студентов");
        }

        Long oldGroupId = user.getGroupId();

        // Close current group history entry
        studentGroupHistoryRepository.findByUserIdAndLeftAtIsNull(id)
                .ifPresent(history -> {
                    history.setLeftAt(LocalDate.now());
                    history.setReason(request.reason());
                    studentGroupHistoryRepository.save(history);
                });

        // Create new group history entry
        StudentGroupHistory newHistory = new StudentGroupHistory();
        newHistory.setUserId(id);
        newHistory.setGroupId(request.newGroupId());
        newHistory.setJoinedAt(LocalDate.now());
        newHistory.setCreatedAt(OffsetDateTime.now());
        studentGroupHistoryRepository.save(newHistory);

        // Update user's current group
        user.setGroupId(request.newGroupId());

        // If user was headman in old group — cascade revoke (same as D-13)
        if (user.isHeadman() && oldGroupId != null) {
            headmanAssistantRepository.revokeAllByGroupId(oldGroupId);
            user.setHeadman(false);
        }

        user.setUpdatedAt(OffsetDateTime.now());

        // Evict caches for old group (ID only known at runtime) and transferred user
        if (cacheManager != null) {
            Cache groupMembersCache = cacheManager.getCache("group_members");
            if (groupMembersCache != null && oldGroupId != null) {
                groupMembersCache.evict(oldGroupId);
            }
            Cache usersCache = cacheManager.getCache("users");
            if (usersCache != null) {
                usersCache.evict(id);
            }
        }

        User saved = userRepository.save(user);
        eventPublisher.publishEvent(new GroupUpdatedEvent(this, oldGroupId));
        eventPublisher.publishEvent(new GroupUpdatedEvent(this, request.newGroupId()));
        return saved;
    }

    public User getMe() {
        Long userId = requestContext.getUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    @Transactional
    public User updateMyAvatar(String avatarId) {
        Long userId = requestContext.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        // Empty/blank string == clear (render initials).
        user.setAvatarId(avatarId == null || avatarId.isBlank() ? null : avatarId);
        user.setUpdatedAt(OffsetDateTime.now());
        User saved = userRepository.save(user);
        if (cacheManager != null) {
            Cache usersCache = cacheManager.getCache("users");
            if (usersCache != null) {
                usersCache.evict(userId);
            }
        }
        return saved;
    }

    // --- Private helpers ---

    private String generateLogin(UserRole role) {
        if (role == UserRole.STUDENT) {
            long seq = userRepository.nextStudentLoginSeq();
            return String.format("student%05d", seq);
        } else if (role == UserRole.TEACHER) {
            long seq = userRepository.nextTeacherLoginSeq();
            return String.format("teacher%05d", seq);
        } else {
            // ADMIN — use teacher sequence with "admin" prefix
            long seq = userRepository.nextTeacherLoginSeq();
            return String.format("admin%05d", seq);
        }
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(CHARSET.charAt(secureRandom.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }
}
