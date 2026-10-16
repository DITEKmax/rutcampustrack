package ru.rutcampustrack.attendance.subjectdeletion;

import static org.assertj.core.api.Assertions.*;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.test.util.ReflectionTestUtils;

import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.studentrequest.entity.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

class ExcuseSubjectCoverageTest {
    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

    @Test
    void preservesOneDecisionOtherCoverageAndOriginalEvidenceOnMixedTicket() {
        var ticket = ticket();
        var originalSnapshots = ticket.getLessonSnapshots();
        var originalSelection = ticket.getDecisionSelection();
        assertThat(ExcuseSubjectCoverage.subtract(ticket, Set.of(11L), 101, "operation", 9, NOW))
                .isTrue();
        assertThat(ticket.getId()).isEqualTo("own-ticket");
        assertThat(ticket.getStatus()).isEqualTo(ExcuseTicketStatus.APPROVED);
        assertThat(ticket.getDecisionBy()).isEqualTo(8L);
        assertThat(ticket.getDecisionAt()).isEqualTo(NOW.minusSeconds(10));
        assertThat(ticket.getCreatedAt()).isEqualTo(NOW.minusSeconds(30));
        assertThat(ticket.getLessonIds()).containsExactly(12L);
        assertThat(ticket.getOriginalLessonIds()).containsExactly(12L);
        assertThat(ticket.getLessonSnapshots())
                .extracting(StudentLessonSnapshotDocument::getLessonId)
                .containsExactly(12L);
        assertThat(ticket.getDecisionSelection().getRequestKey())
                .isEqualTo(originalSelection.getRequestKey());
        assertThat(ticket.getDecisionSelection().getExpectedRevision())
                .isEqualTo(originalSelection.getExpectedRevision());
        assertThat(ticket.getDecisionSelection().getIncludedLessons()).isEmpty();
        assertThat(ticket.getDecisionSelection().getExcludedLessons())
                .extracting(StudentLessonSnapshotDocument::getLessonId)
                .containsExactly(12L);
        assertThat(ReflectionTestUtils.getField(ticket, "lessonSnapshots"))
                .isSameAs(originalSnapshots);
        assertThat(ReflectionTestUtils.getField(ticket, "decisionSelection"))
                .isSameAs(originalSelection);
        assertThat(ticket.getSubjectCoverageAdjustments()).hasSize(1);
        assertThat(ticket.getSubjectCoverageAdjustments().getFirst().removedLessonIds())
                .containsExactly(11L);
        assertThat(ticket.getSubjectCoverageAdjustments().getFirst().remainingLessonIds())
                .containsExactly(12L);
        assertThat(ExcuseSubjectCoverage.subtract(ticket, Set.of(11L), 101, "operation", 9, NOW))
                .isFalse();
        assertThat(ticket.getSubjectCoverageAdjustments()).hasSize(1);
    }

    @Test
    void canonicalPhysicalAliasesAreRemovedTogetherWithoutTouchingOtherSubject() {
        var ticket = ticket();
        ticket.setOriginalLessonIds(null);
        ticket.setLessonIds(List.of(21L, 12L));
        assertThat(
                        ExcuseSubjectCoverage.subtract(
                                ticket, Set.of(11L, 21L), 101, "operation", 9, NOW))
                .isTrue();
        assertThat(ticket.getLessonIds()).containsExactly(12L);
        assertThat(ticket.getSubjectCoverageAdjustments().getFirst().removedLessonIds())
                .containsExactly(11L, 21L);
    }

    @Test
    void unresolvedOrContradictorySnapshotsRefuseBeforeAnyTicketMutation() {
        var ticket = ticket();
        ticket.setLessonSnapshots(List.of(snapshot(99, 101), snapshot(12, 102)));
        assertThatThrownBy(
                        () ->
                                ExcuseSubjectCoverage.subtract(
                                        ticket, Set.of(11L), 101, "operation", 9, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ticket.getLessonIds()).containsExactly(11L, 12L);
        assertThat(ticket.getSubjectCoverageAdjustments()).isNull();
        assertThat(ticket.getEffectiveLessonSnapshots()).isNull();
    }

    @Test
    void soleSubjectIsExplicitlyDelegatedToWholeTicketCascade() {
        var ticket = ticket();
        ticket.setOriginalLessonIds(List.of(11L));
        ticket.setLessonIds(List.of(11L));
        assertThat(ExcuseSubjectCoverage.subtract(ticket, Set.of(11L), 101, "operation", 9, NOW))
                .isFalse();
        assertThat(ticket.getSubjectCoverageAdjustments()).isNull();
        assertThat(ticket.getLessonIds()).containsExactly(11L);
    }

    @Test
    void mongoMappingKeepsRawOriginalArraysWhileDomainReadsUseEffectiveCoverage() throws Exception {
        var ticket = ticket();
        ExcuseSubjectCoverage.subtract(ticket, Set.of(11L), 101, "operation", 9, NOW);
        var conversions =
                org.springframework.data.mongodb.core.convert.MongoCustomConversions.create(
                        adapter -> {});
        var context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.setInitialEntitySet(Set.of(ExcuseTicket.class));
        context.afterPropertiesSet();
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        Document stored = new Document();
        converter.write(ticket, stored);
        assertThat(stored.getList("lesson_snapshots", Document.class)).hasSize(2);
        assertThat(stored.getList("effective_lesson_snapshots", Document.class)).hasSize(1);
        assertThat(stored.get("decision_by")).isEqualTo(8L);
        var loaded = converter.read(ExcuseTicket.class, stored);
        assertThat(loaded.getLessonSnapshots())
                .extracting(StudentLessonSnapshotDocument::getLessonId)
                .containsExactly(12L);
        assertThat((List<?>) ReflectionTestUtils.getField(loaded, "lessonSnapshots")).hasSize(2);
        assertThat(loaded.getSubjectCoverageAdjustments()).hasSize(1);
    }

    private static ExcuseTicket ticket() {
        var first = snapshot(11, 101);
        var second = snapshot(12, 102);
        return ExcuseTicket.builder()
                .id("own-ticket")
                .studentId(7L)
                .groupId(5L)
                .semesterId(3L)
                .lessonIds(List.of(11L, 12L))
                .originalLessonIds(List.of(11L, 12L))
                .lessonSnapshots(List.of(first, second))
                .status(ExcuseTicketStatus.APPROVED)
                .decisionBy(8L)
                .decisionAt(NOW.minusSeconds(10))
                .createdAt(NOW.minusSeconds(30))
                .updatedAt(NOW.minusSeconds(10))
                .decisionSelection(
                        ExcuseDecisionSelectionDocument.builder()
                                .requestKey("original-decision")
                                .expectedRevision("original-revision")
                                .includedLessons(List.of(first))
                                .excludedLessons(List.of(second))
                                .build())
                .build();
    }

    private static StudentLessonSnapshotDocument snapshot(long lesson, long subject) {
        return StudentLessonSnapshotDocument.builder()
                .lessonId(lesson)
                .subjectId(subject)
                .groupId(5L)
                .semesterId(3L)
                .date(LocalDate.of(2026, 10, 14))
                .subjectName("Own synthetic subject")
                .subjectType("LECTURE")
                .build();
    }
}
