package ru.rutcampustrack.academic.map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.FAILED_PRECONDITION;

@ExtendWith(MockitoExtension.class)
class CampusMapUsageServiceTest {
    private static final UUID SESSION_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID INTENT_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final byte[] OWNER_HMAC = new byte[32];
    private static final byte[] PAYLOAD_HASH = new byte[32];

    @Mock private CampusMapReadService mapReadService;
    @Mock private CampusMapUsageRepository repository;
    @Mock private CampusMapOwnerHmacProvider ownerHmacProvider;

    private CampusMapUsageService service;

    @BeforeEach
    void setUp() {
        service = new CampusMapUsageService(mapReadService, repository, ownerHmacProvider);
    }

    @Test
    void oneLogicalOpeningWritesIntentAndDedupeWithPseudonymousOwner() {
        givenPublishedFloor();
        OffsetDateTime acceptedAt = OffsetDateTime.parse("2999-01-01T12:00:00Z");
        when(repository.findOpenIntentForUpdate(OWNER_HMAC, INTENT_ID))
                .thenReturn(Optional.of(new CampusMapUsageRepository.OpenIntentRow(
                        OWNER_HMAC, INTENT_ID, 20L, 200L, payloadHash(),
                        LocalDate.parse("2999-01-01"), acceptedAt, acceptedAt.plusHours(48))));

        service.recordFloorOpen("20", "200", INTENT_ID, claims());

        verify(repository).insertOpenIntent(eq(OWNER_HMAC), eq(INTENT_ID), eq(20L), eq(200L),
                argThat(hash -> hash != null && hash.length == 32));
        verify(repository).insertDemandDedupe(OWNER_HMAC, 200L,
                LocalDate.parse("2999-01-01"), INTENT_ID, acceptedAt);
    }

    @Test
    void sameIntentBoundToAnotherFloorCannotIncrement() {
        givenPublishedFloor();
        when(repository.findOpenIntentForUpdate(OWNER_HMAC, INTENT_ID))
                .thenReturn(Optional.of(new CampusMapUsageRepository.OpenIntentRow(
                        OWNER_HMAC, INTENT_ID, 20L, 201L, PAYLOAD_HASH,
                        LocalDate.parse("2999-01-01"),
                        OffsetDateTime.parse("2999-01-01T12:00:00Z"),
                        OffsetDateTime.parse("2999-01-03T12:00:00Z"))));

        assertThatThrownBy(() -> service.recordFloorOpen("20", "200", INTENT_ID, claims()))
                .isInstanceOf(CampusMapReadException.class)
                .extracting(error -> ((CampusMapReadException) error).code())
                .isEqualTo(FAILED_PRECONDITION);

        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
                .insertDemandDedupe(any(), any(Long.class), any(), any(), any());
    }

    @Test
    void unpublishedFloorDoesNotCreateUsageRows() {
        when(mapReadService.readFloorPlan("20", "200", claims()))
                .thenReturn(new CampusMapReadModels.PlanResult(null));

        assertThatThrownBy(() -> service.recordFloorOpen("20", "200", INTENT_ID, claims()))
                .isInstanceOf(CampusMapReadException.class)
                .extracting(error -> ((CampusMapReadException) error).code())
                .isEqualTo(FAILED_PRECONDITION);

        verifyNoInteractions(repository, ownerHmacProvider);
    }

    @Test
    void adminQueriesDelegateToTheSameAggregateRepository() {
        when(repository.findAllTimeFloorCounts()).thenReturn(Map.of(200L, 3L));
        when(repository.findAllTimeTotal()).thenReturn(3L);

        assertThat(service.allTimeFloorCounts()).containsEntry(200L, 3L);
        assertThat(service.allTimeTotal()).isEqualTo(3L);
        verify(repository).findAllTimeFloorCounts();
        verify(repository).findAllTimeTotal();
    }

    private static InternalJwtClaims claims() {
        return new InternalJwtClaims(100L, SESSION_ID, 1L, 1L,
                "STUDENT", "ACTIVE", 10L, false, false);
    }

    private void givenPublishedFloor() {
        when(repository.lockFloorForOpen(200L)).thenReturn(true);
        when(mapReadService.readFloorPlan("20", "200", claims()))
                .thenReturn(new CampusMapReadModels.PlanResult(
                        new CampusMapReadModels.ManifestPlan(20L, 200L, 1L, "floor", null, null)));
        when(ownerHmacProvider.forUser(100L)).thenReturn(OWNER_HMAC);
    }

    private static byte[] payloadHash() {
        try {
            ByteBuffer payload = ByteBuffer.allocate(Long.BYTES * 2 + 16)
                    .putLong(20L)
                    .putLong(200L)
                    .putLong(INTENT_ID.getMostSignificantBits())
                    .putLong(INTENT_ID.getLeastSignificantBits());
            return MessageDigest.getInstance("SHA-256").digest(payload.array());
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
