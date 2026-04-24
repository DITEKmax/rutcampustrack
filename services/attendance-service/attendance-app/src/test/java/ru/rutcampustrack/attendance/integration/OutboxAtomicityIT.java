package ru.rutcampustrack.attendance.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.excuse.ExcuseEventPublisher;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M13 G7 (M02 CRITICAL #1) — IT проверяет, что {@code @Transactional} в связке с
 * {@link org.springframework.data.mongodb.MongoTransactionManager} реально
 * rollback'ает **и** domain-save (excuse_ticket), **и** outbox-save, если
 * transaction коммитит exception'ом после {@code outbox.save}.
 *
 * <p>Сценарий M02 CRITICAL #1: "kill publisher между save и publish" —
 * симулируется через `RuntimeException`, выброшенный в {@code @Transactional}
 * методе после обоих save-ов. Без MongoTransactionManager (или на standalone
 * Mongo без replica set) ни один из save не rollback'нется — данные
 * остались бы на disk, нарушая invariant «excuse существует ⇔ event
 * в outbox».
 */
@Import(OutboxAtomicityIT.TestTxService.class)
class OutboxAtomicityIT extends AbstractAttendanceIntegrationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private TestTxService txService;

    @Autowired
    private ExcuseRepository excuseRepository;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new Query(), "excuse_tickets");
        mongoTemplate.remove(new Query(), "attendance_outbox");
    }

    @Test
    void saveExcuseAndOutbox_thenThrow_bothRolledBack() {
        ExcuseTicket ticket = ExcuseTicket.builder()
                .studentId(42L)
                .groupId(1L)
                .lessonIds(List.of(10L, 11L))
                .excuseType(ExcuseType.ILLNESS)
                .comment("test rollback")
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        assertThatThrownBy(() -> txService.saveAndFail(ticket))
                .isInstanceOf(IllegalStateException.class);

        // Ни excuse, ни outbox не должны остаться.
        long excuseCount = excuseRepository.count();
        long outboxCount = mongoTemplate.count(new Query(), "attendance_outbox");

        assertThat(excuseCount)
                .as("@Transactional rollback должен удалить незакоммиченный excuse")
                .isZero();
        assertThat(outboxCount)
                .as("@Transactional rollback должен удалить незакоммиченный outbox event")
                .isZero();
    }

    @Test
    void saveExcuseAndOutbox_withoutThrow_bothCommitted() {
        ExcuseTicket ticket = ExcuseTicket.builder()
                .studentId(42L)
                .groupId(1L)
                .lessonIds(List.of(20L, 21L))
                .excuseType(ExcuseType.ILLNESS)
                .comment("test commit")
                .status(ExcuseTicketStatus.SUBMITTED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ExcuseTicket saved = txService.saveAndCommit(ticket);

        assertThat(saved.getId()).isNotNull();
        assertThat(excuseRepository.count()).isEqualTo(1);
        assertThat(mongoTemplate.count(new Query(), "attendance_outbox")).isEqualTo(1);
    }

    /**
     * Test-only service: имитирует сценарий save domain + outbox → exception
     * после обоих save-ов. Если {@code @Transactional} работает — оба
     * rollback'аются; иначе — оба остаются (fail test).
     */
    @Service
    static class TestTxService {

        private final ExcuseRepository repository;
        private final ExcuseEventPublisher eventPublisher;

        TestTxService(ExcuseRepository repository, ExcuseEventPublisher eventPublisher) {
            this.repository = repository;
            this.eventPublisher = eventPublisher;
        }

        @Transactional
        public void saveAndFail(ExcuseTicket ticket) {
            ExcuseTicket saved = repository.save(ticket);
            eventPublisher.publishRequested(saved, List.of());
            throw new IllegalStateException("intentional — tests transaction rollback");
        }

        @Transactional
        public ExcuseTicket saveAndCommit(ExcuseTicket ticket) {
            ExcuseTicket saved = repository.save(ticket);
            eventPublisher.publishRequested(saved, List.of());
            return saved;
        }
    }
}
