package ru.rutcampustrack.academic.map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.FAILED_PRECONDITION;
import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.INVALID_ARGUMENT;
import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.NOT_FOUND;
import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.PERMISSION_DENIED;
import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.RESOURCE_EXHAUSTED;
import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.UNAVAILABLE;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Asset;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.AssetMetadata;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Building;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Catalog;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Floor;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.FormatSlot;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Manifest;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.ManifestBuilding;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.ManifestFloor;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.ManifestPlan;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.ManifestResult;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Plan;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.PlanResult;

class CampusMapReadServiceTest {
    private static final UUID SESSION_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final byte[] PNG_CONTENT = "campus-map-png".getBytes(StandardCharsets.UTF_8);

    private final CampusMapReadRepository repository = mock(CampusMapReadRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CampusMapReadService service = new CampusMapReadService(repository, users);
    private final User existingUser = mock(User.class);

    @BeforeEach
    void authenticatedUserExists() {
        when(users.findByIdIncludingArchived(100L)).thenReturn(Optional.of(existingUser));
        when(repository.findCatalogById(anyLong())).thenAnswer(invocation ->
                Optional.of(new Catalog((Long) invocation.getArgument(0), 1L, 1, 1, false)));
    }

    @Test
    void manifestPreservesBuildingAndFloorOrderAndIndependentFormatStates() {
        Catalog catalog = new Catalog(1L, 9L, 2, 3, true);
        Building first = new Building(20L, "B", 0, true);
        Building second = new Building(10L, "A", 1, true);
        Floor firstFloor = new Floor(200L, 20L, "2", 1, 500L, true);
        Floor secondFloor = new Floor(100L, 10L, "1", 0, null, true);
        Plan plan = new Plan(500L, 200L, 4L, 1L, "published", java.time.OffsetDateTime.now());
        FormatSlot png = readySlot(501L, 500L, CampusMapFormat.PNG, 700L, PNG_CONTENT.length,
                sha256(PNG_CONTENT), 640, 480);
        FormatSlot svg = new FormatSlot(502L, 500L, CampusMapFormat.SVG,
                CampusMapFormatState.ABSENT, "image/svg+xml", null, 0, null,
                null, null, List.of());
        AssetMetadata pngMetadata = new AssetMetadata(700L, 500L, CampusMapFormat.PNG,
                "image/png", PNG_CONTENT.length, sha256(PNG_CONTENT), 640, 480, PNG_CONTENT.length);

        when(repository.findCurrentCatalogs()).thenReturn(List.of(catalog));
        when(repository.findActiveBuildings()).thenReturn(List.of(first, second));
        when(repository.findActiveFloors()).thenReturn(List.of(firstFloor, secondFloor));
        when(repository.findCurrentPlans()).thenReturn(List.of(plan));
        when(repository.findFormatSlots(500L)).thenReturn(List.of(png, svg));
        when(repository.findAssetMetadata(700L, 500L, CampusMapFormat.PNG))
                .thenReturn(Optional.of(pngMetadata));

        ManifestResult result = service.readManifest(0L, activeClaims());

        assertThat(result.unchanged()).isFalse();
        Manifest manifest = result.manifest();
        assertThat(manifest.revision()).isEqualTo(9L);
        assertThat(manifest.buildings()).extracting(ManifestBuilding::id)
                .containsExactly(20L, 10L);
        assertThat(manifest.buildings().get(0).floors()).extracting(ManifestFloor::id)
                .containsExactly(200L);
        ManifestPlan aggregate = manifest.buildings().get(0).floors().get(0).plan();
        assertThat(aggregate.png().state()).isEqualTo(CampusMapFormatState.READY);
        assertThat(aggregate.svg().state()).isEqualTo(CampusMapFormatState.ABSENT);
        assertThat(manifest.buildings().get(1).floors().get(0).plan()).isNull();
    }

    @Test
    void manifestAllowsRetainedPublishedPlanFromPriorCatalogAlongsideChangedFloor() {
        Catalog current = new Catalog(2L, 10L, 2, 3, true);
        Building building = new Building(20L, "B", 0, true);
        Floor retainedFloor = new Floor(200L, 20L, "2", 0, 500L, true);
        Floor changedFloor = new Floor(201L, 20L, "3", 1, 600L, true);
        Plan retainedPlan = new Plan(500L, 200L, 4L, 1L, "retained-r1", java.time.OffsetDateTime.now());
        Plan changedPlan = new Plan(600L, 201L, 1L, 2L, "changed-r2", java.time.OffsetDateTime.now());

        when(repository.findCurrentCatalogs()).thenReturn(List.of(current));
        when(repository.findActiveBuildings()).thenReturn(List.of(building));
        when(repository.findActiveFloors()).thenReturn(List.of(retainedFloor, changedFloor));
        when(repository.findCurrentPlans()).thenReturn(List.of(retainedPlan, changedPlan));
        when(repository.findFormatSlots(500L)).thenReturn(absentSlots(500L, 501L, 502L));
        when(repository.findFormatSlots(600L)).thenReturn(absentSlots(600L, 601L, 602L));

        Manifest manifest = service.readManifest(0L, activeClaims()).manifest();

        assertThat(manifest.revision()).isEqualTo(10L);
        assertThat(manifest.buildings().get(0).floors()).extracting(ManifestFloor::id)
                .containsExactly(200L, 201L);
        assertThat(manifest.buildings().get(0).floors().get(0).plan().label())
                .isEqualTo("retained-r1");
        assertThat(manifest.buildings().get(0).floors().get(1).plan().label())
                .isEqualTo("changed-r2");
    }

    @Test
    void equalRevisionReturnsUnchangedWithoutReadingGraphRows() {
        when(repository.findCurrentCatalogs()).thenReturn(List.of(new Catalog(1L, 9L, 2, 3, true)));

        ManifestResult result = service.readManifest(9L, activeClaims());

        assertThat(result.unchanged()).isTrue();
        assertThat(result.manifest()).isNull();
        verify(repository, never()).findActiveBuildings();
        verify(repository, never()).findActiveFloors();
        verify(repository, never()).findCurrentPlans();
    }

    @Test
    void absentCurrentCatalogIsUnavailable() {
        when(repository.findCurrentCatalogs()).thenReturn(List.of());

        assertCode(UNAVAILABLE, () -> service.readManifest(0L, activeClaims()));
    }

    @Test
    void suspendedStudentIdentityIsDeniedBeforeMapLookup() {
        assertCode(PERMISSION_DENIED, () -> service.readManifest(0L, claims("SUSPENDED", false)));
        verify(repository, never()).findCurrentCatalogs();
    }

    @Test
    void activeTeacherIdentityCanReadTheSameManifest() {
        when(repository.findCurrentCatalogs()).thenReturn(List.of(new Catalog(1L, 9L, 2, 3, true)));
        when(repository.findActiveBuildings()).thenReturn(List.of());
        when(repository.findActiveFloors()).thenReturn(List.of());
        when(repository.findCurrentPlans()).thenReturn(List.of());

        InternalJwtClaims teacher = new InternalJwtClaims(
                100L, SESSION_ID, 1L, 1L, "TEACHER", "ACTIVE", null, false, false);

        assertThatCode(() -> service.readManifest(0L, teacher)).doesNotThrowAnyException();
        verify(users).findByIdIncludingArchived(100L);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidClaimCases")
    void invalidSignedClaimsAreDeniedBeforeUserAndMapLookups(String name, InternalJwtClaims claims) {
        assertCode(PERMISSION_DENIED, () -> service.readManifest(0L, claims));

        verifyNoInteractions(users, repository);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("statusCases")
    void signedStudentStatusMatrixAllowsOnlyActiveRegularAndTerminalReadOnly(
            String name,
            InternalJwtClaims claims,
            boolean allowed) {
        if (!allowed) {
            assertCode(PERMISSION_DENIED, () -> service.readManifest(0L, claims));
            verifyNoInteractions(users, repository);
            return;
        }

        when(repository.findCurrentCatalogs()).thenReturn(List.of(new Catalog(1L, 9L, 2, 3, true)));
        when(repository.findActiveBuildings()).thenReturn(List.of());
        when(repository.findActiveFloors()).thenReturn(List.of());
        when(repository.findCurrentPlans()).thenReturn(List.of());

        assertThatCode(() -> service.readManifest(0L, claims)).doesNotThrowAnyException();
        verify(users).findByIdIncludingArchived(100L);
        verify(repository).findCurrentCatalogs();
    }

    @Test
    void missingArchivedAwareUserIsDeniedBeforeMapLookup() {
        when(users.findByIdIncludingArchived(100L)).thenReturn(Optional.empty());

        assertCode(PERMISSION_DENIED, () -> service.readManifest(0L, activeClaims()));

        verify(users).findByIdIncludingArchived(100L);
        verifyNoInteractions(repository);
    }

    @Test
    void floorPlanDistinguishesNoPlanFromUnknownBuildingAndRejectsNonCanonicalIds() {
        when(repository.findCurrentCatalogs()).thenReturn(List.of(new Catalog(1L, 9L, 2, 3, true)));
        Building building = new Building(20L, "B", 0, true);
        Floor noPlan = new Floor(200L, 20L, "2", 0, null, true);
        when(repository.findActiveBuilding(20L)).thenReturn(Optional.of(building));
        when(repository.findActiveFloor(200L)).thenReturn(Optional.of(noPlan));

        PlanResult result = service.readFloorPlan("20", "200", activeClaims());

        assertThat(result.hasPlan()).isFalse();
        assertCode(NOT_FOUND, () -> service.readFloorPlan("21", "200", activeClaims()));
        verify(repository).findActiveBuilding(21L);
        clearInvocations(repository);
        assertCode(INVALID_ARGUMENT, () -> service.readFloorPlan("020", "200", activeClaims()));
        verifyNoInteractions(repository);
    }

    @Test
    void nonCanonicalFloorAndAssetIdsAreRejectedBeforeMapLookup() {
        assertCode(INVALID_ARGUMENT, () -> service.readFloorPlan("20", "00200", activeClaims()));
        assertCode(INVALID_ARGUMENT, () -> service.readAsset("20", "200", 3L,
                CampusMapFormat.PNG, "00700", activeClaims(), () -> false));
        assertCode(INVALID_ARGUMENT, () -> service.readAsset("20", "200", 3L,
                CampusMapFormat.PNG, "9223372036854775808", activeClaims(), () -> false));

        verifyNoInteractions(repository);
    }

    @Test
    void nonPositiveVersionAndUnspecifiedFormatAreRejectedBeforeMapLookup() {
        assertCode(INVALID_ARGUMENT, () -> service.readAsset("20", "200", 0L,
                CampusMapFormat.PNG, "700", activeClaims(), () -> false));
        assertCode(INVALID_ARGUMENT, () -> service.readAsset("20", "200", 3L,
                null, "700", activeClaims(), () -> false));

        verifyNoInteractions(repository);
    }

    @Test
    void unknownFloorIsNotFoundBeforePlanReads() {
        when(repository.findCurrentCatalogs()).thenReturn(List.of(new Catalog(1L, 9L, 2, 3, true)));
        when(repository.findActiveBuilding(20L)).thenReturn(Optional.of(new Building(20L, "B", 0, true)));
        when(repository.findActiveFloor(999L)).thenReturn(Optional.empty());

        assertCode(NOT_FOUND, () -> service.readFloorPlan("20", "999", activeClaims()));

        verify(repository).findActiveFloor(999L);
        verify(repository, never()).findPlanById(anyLong(), anyLong());
        verify(repository, never()).findFormatSlots(anyLong());
    }

    @Test
    void floorWithForeignBuildingParentIsNotFoundBeforePlanReads() {
        when(repository.findCurrentCatalogs()).thenReturn(List.of(new Catalog(1L, 9L, 2, 3, true)));
        when(repository.findActiveBuilding(20L)).thenReturn(Optional.of(new Building(20L, "B", 0, true)));
        when(repository.findActiveFloor(200L)).thenReturn(
                Optional.of(new Floor(200L, 21L, "2", 0, null, true)));

        assertCode(NOT_FOUND, () -> service.readFloorPlan("20", "200", activeClaims()));

        verify(repository, never()).findPlanById(anyLong(), anyLong());
        verify(repository, never()).findFormatSlots(anyLong());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCurrentPointerCases")
    void invalidCurrentPointerFailsClosedBeforeFormatReads(
            String name,
            long currentVersionId,
            boolean missingPlanRowLookup) {
        when(repository.findCurrentCatalogs()).thenReturn(List.of(new Catalog(1L, 9L, 2, 3, true)));
        when(repository.findActiveBuilding(20L)).thenReturn(Optional.of(new Building(20L, "B", 0, true)));
        when(repository.findActiveFloor(200L)).thenReturn(
                Optional.of(new Floor(200L, 20L, "2", 0, currentVersionId, true)));
        if (missingPlanRowLookup) {
            when(repository.findPlanById(500L, 200L)).thenReturn(Optional.empty());
        }

        assertCode(CampusMapReadException.Code.DATA_LOSS,
                () -> service.readFloorPlan("20", "200", activeClaims()));

        if (missingPlanRowLookup) {
            verify(repository).findPlanById(500L, 200L);
        } else {
            verify(repository, never()).findPlanById(anyLong(), anyLong());
        }
        verify(repository, never()).findFormatSlots(anyLong());
    }

    @Test
    void floorPlanResolvesCurrentPointerAsPlanId() {
        Plan plan = new Plan(500L, 200L, 4L, 1L, "published", java.time.OffsetDateTime.now());
        FormatSlot png = readySlot(501L, 500L, CampusMapFormat.PNG, 700L, PNG_CONTENT.length,
                sha256(PNG_CONTENT), 640, 480);
        FormatSlot svg = new FormatSlot(502L, 500L, CampusMapFormat.SVG,
                CampusMapFormatState.ABSENT, "image/svg+xml", null, 0, null,
                null, null, List.of());
        AssetMetadata metadata = new AssetMetadata(700L, 500L, CampusMapFormat.PNG,
                "image/png", PNG_CONTENT.length, sha256(PNG_CONTENT), 640, 480, PNG_CONTENT.length);
        when(repository.findCurrentCatalogs()).thenReturn(List.of(new Catalog(1L, 9L, 2, 3, true)));
        when(repository.findActiveBuilding(20L)).thenReturn(Optional.of(new Building(20L, "B", 0, true)));
        when(repository.findActiveFloor(200L)).thenReturn(Optional.of(new Floor(200L, 20L, "2", 0, 500L, true)));
        when(repository.findPlanById(500L, 200L)).thenReturn(Optional.of(plan));
        when(repository.findFormatSlots(500L)).thenReturn(List.of(png, svg));
        when(repository.findAssetMetadata(700L, 500L, CampusMapFormat.PNG)).thenReturn(Optional.of(metadata));

        assertThat(service.readFloorPlan("20", "200", activeClaims()).plan().version()).isEqualTo(4L);
        verify(repository).findPlanById(500L, 200L);
        verify(repository, never()).findPlan(200L, 500L);
    }

    @Test
    void assetChecksMetadataBeforeLoadingBytesAndSupportsHistoricalPublishedVersion() {
        Plan plan = new Plan(500L, 200L, 3L, 8L, "historical", java.time.OffsetDateTime.now());
        FormatSlot slot = readySlot(501L, 500L, CampusMapFormat.PNG, 700L, PNG_CONTENT.length,
                sha256(PNG_CONTENT), 640, 480);
        AssetMetadata metadata = new AssetMetadata(700L, 500L, CampusMapFormat.PNG,
                "image/png", PNG_CONTENT.length, sha256(PNG_CONTENT), 640, 480, PNG_CONTENT.length);
        when(repository.findActiveBuilding(20L)).thenReturn(Optional.of(new Building(20L, "B", 0, true)));
        when(repository.findActiveFloor(200L)).thenReturn(Optional.of(new Floor(200L, 20L, "2", 0, 900L, true)));
        when(repository.findPlan(200L, 3L)).thenReturn(Optional.of(plan));
        when(repository.findFormatSlots(500L)).thenReturn(List.of(slot));
        when(repository.findAssetMetadata(700L, 500L, CampusMapFormat.PNG)).thenReturn(Optional.of(metadata));
        when(repository.loadAsset(700L, 500L, CampusMapFormat.PNG)).thenReturn(Optional.of(new Asset(PNG_CONTENT)));

        Optional<Asset> result = service.readAsset("20", "200", 3L, CampusMapFormat.PNG,
                "700", activeClaims(), () -> false);

        assertThat(result).contains(new Asset(PNG_CONTENT));
        InOrder order = inOrder(repository);
        order.verify(repository).findAssetMetadata(700L, 500L, CampusMapFormat.PNG);
        order.verify(repository).loadAsset(700L, 500L, CampusMapFormat.PNG);
    }

    @Test
    void unknownHistoricalVersionIsNotFoundBeforeFormatReads() {
        when(repository.findActiveBuilding(20L)).thenReturn(Optional.of(new Building(20L, "B", 0, true)));
        when(repository.findActiveFloor(200L)).thenReturn(
                Optional.of(new Floor(200L, 20L, "2", 0, 900L, true)));
        when(repository.findPlan(200L, 99L)).thenReturn(Optional.empty());

        assertCode(NOT_FOUND, () -> service.readAsset("20", "200", 99L,
                CampusMapFormat.PNG, "700", activeClaims(), () -> false));

        verify(repository, never()).findFormatSlots(anyLong());
        verify(repository, never()).findAssetMetadata(anyLong(), anyLong(), any());
        verify(repository, never()).loadAsset(anyLong(), anyLong(), any());
    }

    @Test
    void wrongAssetIdIsNotFoundBeforeMetadataOrByteLoad() {
        Plan plan = historicalPlan();
        FormatSlot slot = readyPngSlot(plan.id(), 700L);
        stubAssetPlan(plan, slot);

        assertCode(NOT_FOUND, () -> service.readAsset("20", "200", plan.version(),
                CampusMapFormat.PNG, "701", activeClaims(), () -> false));

        verify(repository, never()).findAssetMetadata(anyLong(), anyLong(), any());
        verify(repository, never()).loadAsset(anyLong(), anyLong(), any());
    }

    @Test
    void missingPlanCatalogParentFailsClosedBeforeFormatReads() {
        Plan plan = historicalPlan();
        FormatSlot slot = readyPngSlot(plan.id(), 700L);
        stubAssetPlan(plan, slot);
        when(repository.findCatalogById(plan.catalogRevisionId())).thenReturn(Optional.empty());

        assertCode(CampusMapReadException.Code.DATA_LOSS, () -> service.readAsset("20", "200", plan.version(),
                CampusMapFormat.PNG, "700", activeClaims(), () -> false));

        verify(repository, never()).findFormatSlots(anyLong());
        verify(repository, never()).findAssetMetadata(anyLong(), anyLong(), any());
        verify(repository, never()).loadAsset(anyLong(), anyLong(), any());
    }

    @Test
    void oversizedSlotIsRejectedBeforeByteLoad() {
        Plan plan = new Plan(500L, 200L, 3L, 8L, "historical", java.time.OffsetDateTime.now());
        FormatSlot slot = readySlot(501L, 500L, CampusMapFormat.PNG, 700L,
                CampusMapReadRepository.MAX_ASSET_BYTES + 1, new byte[32], 640, 480);
        when(repository.findActiveBuilding(20L)).thenReturn(Optional.of(new Building(20L, "B", 0, true)));
        when(repository.findActiveFloor(200L)).thenReturn(Optional.of(new Floor(200L, 20L, "2", 0, 900L, true)));
        when(repository.findPlan(200L, 3L)).thenReturn(Optional.of(plan));
        when(repository.findFormatSlots(500L)).thenReturn(List.of(slot));

        assertCode(RESOURCE_EXHAUSTED, () -> service.readAsset("20", "200", 3L,
                CampusMapFormat.PNG, "700", activeClaims(), () -> false));
        verify(repository, never()).loadAsset(anyLong(), anyLong(), any());
    }

    @Test
    void metadataSizeMismatchIsRejectedBeforeByteLoad() {
        Plan plan = new Plan(500L, 200L, 3L, 8L, "historical", java.time.OffsetDateTime.now());
        FormatSlot slot = readySlot(501L, 500L, CampusMapFormat.PNG, 700L, PNG_CONTENT.length,
                sha256(PNG_CONTENT), 640, 480);
        AssetMetadata metadata = new AssetMetadata(700L, 500L, CampusMapFormat.PNG,
                "image/png", PNG_CONTENT.length + 1L, sha256(PNG_CONTENT), 640, 480,
                PNG_CONTENT.length + 1L);
        when(repository.findActiveBuilding(20L)).thenReturn(Optional.of(new Building(20L, "B", 0, true)));
        when(repository.findActiveFloor(200L)).thenReturn(Optional.of(new Floor(200L, 20L, "2", 0, 900L, true)));
        when(repository.findPlan(200L, 3L)).thenReturn(Optional.of(plan));
        when(repository.findFormatSlots(500L)).thenReturn(List.of(slot));
        when(repository.findAssetMetadata(700L, 500L, CampusMapFormat.PNG)).thenReturn(Optional.of(metadata));

        assertCode(CampusMapReadException.Code.DATA_LOSS, () -> service.readAsset("20", "200", 3L,
                CampusMapFormat.PNG, "700", activeClaims(), () -> false));
        verify(repository, never()).loadAsset(anyLong(), anyLong(), any());
    }

    @Test
    void slotMimeMismatchIsRejectedBeforeByteLoad() {
        Plan plan = new Plan(500L, 200L, 3L, 8L, "historical", java.time.OffsetDateTime.now());
        FormatSlot slot = new FormatSlot(501L, 500L, CampusMapFormat.PNG,
                CampusMapFormatState.READY, "text/plain", 700L, PNG_CONTENT.length,
                sha256(PNG_CONTENT), 640, 480, List.of());
        when(repository.findActiveBuilding(20L)).thenReturn(Optional.of(new Building(20L, "B", 0, true)));
        when(repository.findActiveFloor(200L)).thenReturn(Optional.of(new Floor(200L, 20L, "2", 0, 900L, true)));
        when(repository.findPlan(200L, 3L)).thenReturn(Optional.of(plan));
        when(repository.findFormatSlots(500L)).thenReturn(List.of(slot));

        assertCode(CampusMapReadException.Code.DATA_LOSS, () -> service.readAsset("20", "200", 3L,
                CampusMapFormat.PNG, "700", activeClaims(), () -> false));
        verify(repository, never()).findAssetMetadata(anyLong(), anyLong(), any());
        verify(repository, never()).loadAsset(anyLong(), anyLong(), any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonReadySlotStates")
    void manifestKeepsEachNonReadyFormatStateWithoutByteMetadataLookup(
            String name,
            CampusMapFormatState state) {
        Plan plan = historicalPlan();
        when(repository.findCurrentCatalogs()).thenReturn(List.of(new Catalog(1L, 9L, 2, 3, true)));
        when(repository.findActiveBuildings()).thenReturn(List.of(new Building(20L, "B", 0, true)));
        when(repository.findActiveFloors()).thenReturn(
                List.of(new Floor(200L, 20L, "2", 0, plan.id(), true)));
        when(repository.findCurrentPlans()).thenReturn(List.of(plan));
        when(repository.findFormatSlots(plan.id())).thenReturn(List.of(
                new FormatSlot(501L, plan.id(), CampusMapFormat.PNG, state,
                        "image/png", null, 0, null, null, null, List.of()),
                new FormatSlot(502L, plan.id(), CampusMapFormat.SVG,
                        CampusMapFormatState.ABSENT, "image/svg+xml", null, 0,
                        null, null, null, List.of())));

        ManifestPlan result = service.readManifest(0L, activeClaims())
                .manifest().buildings().get(0).floors().get(0).plan();

        assertThat(result.png().state()).isEqualTo(state);
        verify(repository, never()).findAssetMetadata(anyLong(), anyLong(), any());
        verify(repository, never()).loadAsset(anyLong(), anyLong(), any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonReadySlotStates")
    void nonReadyAssetWithNullAssetIdIsNotFoundBeforeMetadataOrByteLoad(
            String name,
            CampusMapFormatState state) {
        Plan plan = historicalPlan();
        FormatSlot slot = new FormatSlot(501L, plan.id(), CampusMapFormat.PNG, state,
                "image/png", null, 0L, null, null, null, List.of());
        stubAssetPlan(plan, slot);

        assertCode(NOT_FOUND, () -> service.readAsset("20", "200", plan.version(),
                CampusMapFormat.PNG, "700", activeClaims(), () -> false));
        verify(repository, never()).findAssetMetadata(anyLong(), anyLong(), any());
        verify(repository, never()).loadAsset(anyLong(), anyLong(), any());
    }

    @Test
    void readyAssetWithoutMetadataFailsBeforeByteLoad() {
        Plan plan = historicalPlan();
        FormatSlot slot = readyPngSlot(plan.id(), 700L);
        stubAssetPlan(plan, slot);
        when(repository.findAssetMetadata(700L, plan.id(), CampusMapFormat.PNG))
                .thenReturn(Optional.empty());

        assertCode(CampusMapReadException.Code.DATA_LOSS,
                () -> service.readAsset("20", "200", plan.version(), CampusMapFormat.PNG,
                        "700", activeClaims(), () -> false));
        verify(repository).findAssetMetadata(700L, plan.id(), CampusMapFormat.PNG);
        verify(repository, never()).loadAsset(anyLong(), anyLong(), any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCurrentSlotCases")
    void currentPlanRejectsMissingDuplicateOrMisparentedFormatSlots(
            String name,
            List<FormatSlot> slots) {
        Plan plan = historicalPlan();
        when(repository.findCurrentCatalogs()).thenReturn(List.of(new Catalog(1L, 9L, 2, 3, true)));
        when(repository.findActiveBuilding(20L)).thenReturn(
                Optional.of(new Building(20L, "B", 0, true)));
        when(repository.findActiveFloor(200L)).thenReturn(
                Optional.of(new Floor(200L, 20L, "2", 0, plan.id(), true)));
        when(repository.findPlanById(plan.id(), 200L)).thenReturn(Optional.of(plan));
        when(repository.findFormatSlots(plan.id())).thenReturn(slots);

        assertCode(CampusMapReadException.Code.DATA_LOSS,
                () -> service.readFloorPlan("20", "200", activeClaims()));
        verify(repository, never()).findAssetMetadata(anyLong(), anyLong(), any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("assetMetadataMismatchCases")
    void assetMetadataParentHashAndDimensionsMustMatchBeforeByteLoad(
            String name,
            AssetMetadata metadata) {
        Plan plan = historicalPlan();
        FormatSlot slot = readyPngSlot(plan.id(), 700L);
        stubAssetPlan(plan, slot);
        when(repository.findAssetMetadata(700L, plan.id(), CampusMapFormat.PNG))
                .thenReturn(Optional.of(metadata));

        assertCode(CampusMapReadException.Code.DATA_LOSS,
                () -> service.readAsset("20", "200", plan.version(), CampusMapFormat.PNG,
                        "700", activeClaims(), () -> false));
        verify(repository, never()).loadAsset(anyLong(), anyLong(), any());
    }

    @Test
    void cancellationStopsBeforeIdentityLookupAndDoesNotLoadBytes() {
        AtomicBoolean cancelled = new AtomicBoolean(true);

        Optional<Asset> result = service.readAsset("20", "200", 3L, CampusMapFormat.PNG,
                "700", activeClaims(), cancelled::get);

        assertThat(result).isEmpty();
        verifyNoInteractions(users, repository);
    }

    @Test
    void readOnlyTerminalClaimIsAllowedWhenArchivedAwareUserExists() {
        when(repository.findCurrentCatalogs()).thenReturn(List.of(new Catalog(1L, 9L, 2, 3, true)));
        when(repository.findActiveBuildings()).thenReturn(List.of());
        when(repository.findActiveFloors()).thenReturn(List.of());
        when(repository.findCurrentPlans()).thenReturn(List.of());

        assertThat(service.readManifest(0L, claims("ARCHIVED", true)).manifest().buildings()).isEmpty();
        verify(users).findByIdIncludingArchived(100L);
    }

    @Test
    void unpublishedPlanFailsClosed() {
        Plan unpublished = new Plan(500L, 200L, 3L, 8L, "draft", null);
        when(repository.findActiveBuilding(20L)).thenReturn(Optional.of(new Building(20L, "B", 0, true)));
        when(repository.findActiveFloor(200L)).thenReturn(Optional.of(new Floor(200L, 20L, "2", 0, 900L, true)));
        when(repository.findPlan(200L, 3L)).thenReturn(Optional.of(unpublished));

        assertCode(FAILED_PRECONDITION, () -> service.readAsset("20", "200", 3L,
                CampusMapFormat.PNG, "700", activeClaims(), () -> false));
    }

    @Test
    void digestMismatchFailsAfterMetadataButBeforeReturningAsset() {
        Plan plan = new Plan(500L, 200L, 3L, 8L, "historical", java.time.OffsetDateTime.now());
        byte[] wrongDigest = new byte[32];
        FormatSlot slot = readySlot(501L, 500L, CampusMapFormat.PNG, 700L, PNG_CONTENT.length,
                wrongDigest, 640, 480);
        AssetMetadata metadata = new AssetMetadata(700L, 500L, CampusMapFormat.PNG,
                "image/png", PNG_CONTENT.length, wrongDigest, 640, 480, PNG_CONTENT.length);
        when(repository.findActiveBuilding(20L)).thenReturn(Optional.of(new Building(20L, "B", 0, true)));
        when(repository.findActiveFloor(200L)).thenReturn(Optional.of(new Floor(200L, 20L, "2", 0, 900L, true)));
        when(repository.findPlan(200L, 3L)).thenReturn(Optional.of(plan));
        when(repository.findFormatSlots(500L)).thenReturn(List.of(slot));
        when(repository.findAssetMetadata(700L, 500L, CampusMapFormat.PNG)).thenReturn(Optional.of(metadata));
        when(repository.loadAsset(700L, 500L, CampusMapFormat.PNG)).thenReturn(Optional.of(new Asset(PNG_CONTENT)));

        assertCode(CampusMapReadException.Code.DATA_LOSS, () -> service.readAsset("20", "200", 3L,
                CampusMapFormat.PNG, "700", activeClaims(), () -> false));
        verify(repository).loadAsset(700L, 500L, CampusMapFormat.PNG);
    }

    @Test
    void manifestResultTruthTableKeepsFullAndUnchangedStatesDistinct() {
        Manifest manifest = new Manifest(2, 3, 9L, List.of());

        assertThatCode(() -> new ManifestResult(false, 9L, manifest)).doesNotThrowAnyException();
        assertThatCode(() -> new ManifestResult(true, 9L, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new ManifestResult(false, 9L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManifestResult(true, 9L, manifest))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManifestResult(false, 0L, manifest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Arguments> invalidClaimCases() {
        return Stream.of(
                arguments("missing claims", (InternalJwtClaims) null),
                arguments("zero user", claim(0L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", 10L, false)),
                arguments("negative user", claim(-1L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", 10L, false)),
                arguments("missing group", claim(100L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", null, false)),
                arguments("zero group", claim(100L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", 0L, false)),
                arguments("negative group", claim(100L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", -1L, false)),
                arguments("missing session", claim(100L, null, 1L, 1L, "STUDENT", "ACTIVE", 10L, false)),
                arguments("zero session version", claim(100L, SESSION_ID, 0L, 1L, "STUDENT", "ACTIVE", 10L, false)),
                arguments("zero roles version", claim(100L, SESSION_ID, 1L, 0L, "STUDENT", "ACTIVE", 10L, false)),
                arguments("foreign role", claim(100L, SESSION_ID, 1L, 1L, "ADMIN", "ACTIVE", 10L, false)),
                arguments("missing role", claim(100L, SESSION_ID, 1L, 1L, null, "ACTIVE", 10L, false)),
                arguments("missing status", claim(100L, SESSION_ID, 1L, 1L, "STUDENT", null, 10L, false)),
                arguments("unknown status", claim(100L, SESSION_ID, 1L, 1L, "STUDENT", "UNKNOWN", 10L, false)));
    }

    private static Stream<Arguments> statusCases() {
        return Stream.of(
                arguments("ACTIVE regular is allowed", claims("ACTIVE", false), true),
                arguments("EXPELLED read-only is allowed", claims("EXPELLED", true), true),
                arguments("GRADUATED read-only is allowed", claims("GRADUATED", true), true),
                arguments("ARCHIVED read-only is allowed", claims("ARCHIVED", true), true),
                arguments("ACTIVE read-only is denied", claims("ACTIVE", true), false),
                arguments("EXPELLED regular is denied", claims("EXPELLED", false), false),
                arguments("GRADUATED regular is denied", claims("GRADUATED", false), false),
                arguments("ARCHIVED regular is denied", claims("ARCHIVED", false), false),
                arguments("SUSPENDED regular is denied", claims("SUSPENDED", false), false),
                arguments("SUSPENDED read-only is denied", claims("SUSPENDED", true), false));
    }

    private static Stream<Arguments> invalidCurrentPointerCases() {
        return Stream.of(
                arguments("zero current pointer", 0L, false),
                arguments("negative current pointer", -1L, false),
                arguments("missing pointed plan row", 500L, true));
    }

    private static Stream<Arguments> nonReadySlotStates() {
        return Stream.of(
                arguments("ABSENT", CampusMapFormatState.ABSENT),
                arguments("PROCESSING", CampusMapFormatState.PROCESSING),
                arguments("FAILED", CampusMapFormatState.FAILED));
    }

    private static Stream<Arguments> invalidCurrentSlotCases() {
        FormatSlot png = absentSlot(501L, 500L, CampusMapFormat.PNG);
        FormatSlot svg = absentSlot(502L, 500L, CampusMapFormat.SVG);
        return Stream.of(
                arguments("missing SVG slot", List.of(png)),
                arguments("duplicate PNG format", List.of(png,
                        absentSlot(503L, 500L, CampusMapFormat.PNG))),
                arguments("duplicate slot id", List.of(png,
                        absentSlot(501L, 500L, CampusMapFormat.SVG))),
                arguments("format row points to another plan", List.of(
                        absentSlot(501L, 999L, CampusMapFormat.PNG), svg)));
    }

    private static Stream<Arguments> assetMetadataMismatchCases() {
        byte[] digest = sha256(PNG_CONTENT);
        return Stream.of(
                arguments("asset id parent", assetMetadata(701L, 500L, CampusMapFormat.PNG,
                        "image/png", PNG_CONTENT.length, digest, 640, 480, PNG_CONTENT.length)),
                arguments("plan version parent", assetMetadata(700L, 501L, CampusMapFormat.PNG,
                        "image/png", PNG_CONTENT.length, digest, 640, 480, PNG_CONTENT.length)),
                arguments("format parent", assetMetadata(700L, 500L, CampusMapFormat.SVG,
                        "image/svg+xml", PNG_CONTENT.length, digest, 640, 480, PNG_CONTENT.length)),
                arguments("content type", assetMetadata(700L, 500L, CampusMapFormat.PNG,
                        "text/plain", PNG_CONTENT.length, digest, 640, 480, PNG_CONTENT.length)),
                arguments("digest", assetMetadata(700L, 500L, CampusMapFormat.PNG,
                        "image/png", PNG_CONTENT.length, new byte[32], 640, 480, PNG_CONTENT.length)),
                arguments("width", assetMetadata(700L, 500L, CampusMapFormat.PNG,
                        "image/png", PNG_CONTENT.length, digest, 641, 480, PNG_CONTENT.length)),
                arguments("height", assetMetadata(700L, 500L, CampusMapFormat.PNG,
                        "image/png", PNG_CONTENT.length, digest, 640, 481, PNG_CONTENT.length)),
                arguments("content length", assetMetadata(700L, 500L, CampusMapFormat.PNG,
                        "image/png", PNG_CONTENT.length, digest, 640, 480, PNG_CONTENT.length + 1L)));
    }

    private static InternalJwtClaims claim(long userId,
                                           UUID sessionId,
                                           long sessionVersion,
                                           long rolesVersion,
                                           String role,
                                           String status,
                                           Long groupId,
                                           boolean readOnly) {
        return new InternalJwtClaims(userId, sessionId, sessionVersion, rolesVersion,
                role, status, groupId, false, readOnly);
    }

    private static Plan historicalPlan() {
        return new Plan(500L, 200L, 3L, 8L, "historical", java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    }

    private static FormatSlot readyPngSlot(long planId, long assetId) {
        return readySlot(501L, planId, CampusMapFormat.PNG, assetId, PNG_CONTENT.length,
                sha256(PNG_CONTENT), 640, 480);
    }

    private static FormatSlot absentSlot(long id, long planId, CampusMapFormat format) {
        return new FormatSlot(id, planId, format, CampusMapFormatState.ABSENT,
                format == CampusMapFormat.PNG ? "image/png" : "image/svg+xml",
                null, 0L, null, null, null, List.of());
    }

    private static AssetMetadata assetMetadata(long id,
                                               long planId,
                                               CampusMapFormat format,
                                               String contentType,
                                               long bytes,
                                               byte[] digest,
                                               Integer width,
                                               Integer height,
                                               long contentLength) {
        return new AssetMetadata(id, planId, format, contentType, bytes, digest,
                width, height, contentLength);
    }

    private void stubAssetPlan(Plan plan, FormatSlot slot) {
        when(repository.findActiveBuilding(20L)).thenReturn(
                Optional.of(new Building(20L, "B", 0, true)));
        when(repository.findActiveFloor(200L)).thenReturn(
                Optional.of(new Floor(200L, 20L, "2", 0, 900L, true)));
        when(repository.findPlan(200L, plan.version())).thenReturn(Optional.of(plan));
        when(repository.findFormatSlots(plan.id())).thenReturn(List.of(slot));
    }

    private static FormatSlot readySlot(long id,
                                         long planId,
                                         CampusMapFormat format,
                                         long assetId,
                                         long bytes,
                                         byte[] sha,
                                         int width,
                                         int height) {
        return new FormatSlot(id, planId, format, CampusMapFormatState.READY,
                format == CampusMapFormat.PNG ? "image/png" : "image/svg+xml",
                assetId, bytes, sha, width, height, List.of());
    }

    private static List<FormatSlot> absentSlots(long planId, long pngId, long svgId) {
        return List.of(
                new FormatSlot(pngId, planId, CampusMapFormat.PNG,
                        CampusMapFormatState.ABSENT, "image/png", null, 0, null,
                        null, null, List.of()),
                new FormatSlot(svgId, planId, CampusMapFormat.SVG,
                        CampusMapFormatState.ABSENT, "image/svg+xml", null, 0, null,
                        null, null, List.of()));
    }

    private InternalJwtClaims activeClaims() {
        return claims("ACTIVE", false);
    }

    private static InternalJwtClaims claims(String status, boolean readOnly) {
        return new InternalJwtClaims(100L, SESSION_ID, 1L, 1L,
                "STUDENT", status, 10L, false, readOnly);
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }

    private static void assertCode(CampusMapReadException.Code code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CampusMapReadException.class)
                .extracting(error -> ((CampusMapReadException) error).code())
                .isEqualTo(code);
    }
}
