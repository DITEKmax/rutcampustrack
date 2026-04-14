package ru.rutcampustrack.academic.group;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.group.UpdateGroupRequest;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.event.GroupUpdatedEvent;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.time.OffsetDateTime;

/**
 * Business logic for Group domain: CRUD and member listing.
 */
@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final RequestContext requestContext;
    private final ApplicationEventPublisher eventPublisher;

    public GroupService(GroupRepository groupRepository,
                        UserRepository userRepository,
                        RequestContext requestContext,
                        ApplicationEventPublisher eventPublisher) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.requestContext = requestContext;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Group createGroup(CreateGroupRequest request) {
        // BUG-006-2 / 58-04: explicit pre-check per Plan 02 pattern — ConflictException несёт field=name,
        // frontend отрисует сообщение FIELD_MESSAGES.name. DB-level fallback — в GlobalExceptionHandler
        // через constraint groups_name_key.
        if (groupRepository.existsByName(request.name())) {
            throw new ConflictException(
                    "name",
                    request.name(),
                    "Группа с таким названием уже существует"
            );
        }
        Group group = new Group();
        group.setName(request.name());
        group.setActive(true);
        group.setCreatedAt(OffsetDateTime.now());
        return groupRepository.save(group);
    }

    public Group findGroupById(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", id));
    }

    public Page<Group> listGroups(Boolean active, Pageable pageable) {
        if (active != null) {
            return groupRepository.findByIsActive(active, pageable);
        }
        return groupRepository.findAll(pageable);
    }

    @Caching(evict = {
        @CacheEvict(value = "groups", key = "#id"),
        @CacheEvict(value = "group_members", key = "#id")
    })
    @Transactional
    public Group updateGroup(Long id, UpdateGroupRequest request) {
        Group group = findGroupById(id);
        // 58-04: при переименовании проверяем конфликт имени (кроме самой себя).
        if (!request.name().equals(group.getName())
                && groupRepository.existsByName(request.name())) {
            throw new ConflictException(
                    "name",
                    request.name(),
                    "Группа с таким названием уже существует"
            );
        }
        group.setName(request.name());
        group.setActive(request.active());
        Group saved = groupRepository.save(group);
        eventPublisher.publishEvent(new GroupUpdatedEvent(this, saved.getId()));
        return saved;
    }

    @Caching(evict = {
        @CacheEvict(value = "groups", key = "#id"),
        @CacheEvict(value = "group_members", key = "#id")
    })
    @Transactional
    public void deleteGroup(Long id) {
        Group group = findGroupById(id);
        groupRepository.delete(group);
        eventPublisher.publishEvent(new GroupUpdatedEvent(this, id));
    }

    public Page<User> getMyGroupMembers(Pageable pageable) {
        Long groupId = requestContext.getGroupId();
        return userRepository.findByGroupId(groupId, pageable);
    }
}
