package ru.rutcampustrack.academic.map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.*;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.auth.session.model.AuthRole;

/**
 * Real HTTP admission and PostgreSQL BYTEA/transaction proof on this fixture's synthetic graphs.
 * The unchanged remote Auth password confirmation is stubbed; its protocol is covered separately.
 */
class CampusMapWholeDeletionHttpIT extends AbstractAcademicIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired CampusMapAdminService admin;
    @Autowired CampusMapAdminRepository catalog;
    @MockitoBean AuthMapDeletionClient auth;
    @MockitoSpyBean CampusMapDeletionRepository deletion;
    private SelectedActor actor;
    private byte[] png;
    private byte[] svg;

    @BeforeEach
    void fixture() {
        actor = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        png = CampusMapFileFixture.png(2, 2);
        svg =
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"2\" height=\"2\"/>"
                        .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void allVersionsAndBothFormatsArePurgedButEqualHashOtherFloorAndUsageSurvive()
            throws Exception {
        var first = upload("1");
        var second = upload("1");
        var other = upload("2");
        long floor = Long.parseLong(first.floorId());
        long building = Long.parseLong(first.buildingId());
        seedUsage(building, floor);
        for (var plan : List.of(first, second, other)) assertAssets(plan, HttpStatus.OK);

        var selectedFormat = get(previewUrl("1") + "&format=png");
        assertThat(selectedFormat.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(selectedFormat.getBody()).path("deletionScope").asText())
                .isEqualTo("CURRENT_FORMAT");
        var preview = preview("1");
        assertThat(preview.path("format").isNull()).isTrue();
        assertThat(preview.path("files").asLong()).isEqualTo(4);
        assertThat(preview.path("bytes").asLong()).isEqualTo(2L * (png.length + svg.length));
        var request = request("1", preview.path("previewDigest").asText());
        var result = delete(request);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(mapper.readTree(result.getBody()).path("status").asText())
                .isEqualTo("COMPLETED");
        assertGraphCounts(floor, 0, 0, 0);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_version_id FROM campus_map_floor WHERE id=?",
                                Long.class,
                                floor))
                .isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM campus_map_building WHERE id=?",
                                Long.class,
                                building))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT open_count FROM campus_map_floor_daily_demand WHERE floor_id=?",
                                Long.class,
                                floor))
                .isEqualTo(7);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM campus_map_floor_demand_dedupe WHERE floor_id=?",
                                Long.class,
                                floor))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM campus_map_open_intent WHERE floor_id=?",
                                Long.class,
                                floor))
                .isEqualTo(1);
        for (var plan : List.of(first, second)) {
            assertThat(get(planUrl(plan)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertAssets(plan, HttpStatus.NOT_FOUND);
        }
        assertAssets(other, HttpStatus.OK);
        assertGraphCounts(Long.parseLong(other.floorId()), 1, 2, 2);
        assertThat(get(previewUrl("1")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertGuarded(other);
    }

    @Test
    void oldPreviewAndOrphanHistoryRequireFreshPurgeAndReplayCannotRemoveNewMonotonicUpload()
            throws Exception {
        upload("1");
        upload("1");
        var third = upload("1");
        long floor = Long.parseLong(third.floorId());
        // Reproduce the old null-format preview exactly; it cannot authorise the wider new purge.
        var oldRequest = request("1", legacyCurrentDigest(third));
        assertThat(delete(oldRequest).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertGraphCounts(floor, 3, 6, 6);
        // An old successful pointer clear can leave retained history. It remains explicitly
        // purgeable.
        jdbc.update(
                "UPDATE campus_map_floor SET current_version_id=NULL, plan_version_high_water=0 WHERE id=?",
                floor);
        var preview = preview("1");
        assertThat(preview.path("files").asLong()).isEqualTo(6);
        assertThat(preview.path("label").asText()).isEqualTo(third.label());
        var request = request("1", preview.path("previewDigest").asText());
        assertThat(delete(request).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertGraphCounts(floor, 0, 0, 0);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT plan_version_high_water FROM campus_map_floor WHERE id=?",
                                Long.class,
                                floor))
                .isEqualTo(3);
        var fourth = upload("1");
        assertThat(fourth.version()).isEqualTo("4");
        assertThat(delete(request).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertGraphCounts(floor, 1, 2, 2);
        assertAssets(fourth, HttpStatus.OK);
        assertAssets(third, HttpStatus.NOT_FOUND);
        assertThat(delete(request("1", request.previewDigest())).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(auth, times(2))
                .confirm(
                        eq(DeletionTarget.FLOOR),
                        eq(floor),
                        eq(request.operationId()),
                        eq(request.previewDigest()),
                        eq(request.password()));
        assertGuarded(fourth);
    }

    @Test
    void databaseFailureAfterGraphDeletionRollsBackBytesPointerHighWaterRevisionAndReceipt()
            throws Exception {
        var first = upload("1");
        var second = upload("1");
        long floor = Long.parseLong(first.floorId());
        jdbc.update("UPDATE campus_map_floor SET plan_version_high_water=0 WHERE id=?", floor);
        long revision = catalog.latestCatalogRevision();
        var request = request("1", preview("1").path("previewDigest").asText());
        doAnswer(
                        invocation -> {
                            // Run inside the real mutation transaction, after FK-safe graph
                            // deletion.
                            assertGraphCounts(floor, 0, 0, 0);
                            jdbc.queryForObject(
                                    "SELECT forced_missing_column FROM campus_map_floor",
                                    Integer.class);
                            return null;
                        })
                .when(deletion)
                .insertPlanReceipt(
                        eq(request.operationId()),
                        anyLong(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        isNull());
        assertThat(delete(request).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertGraphCounts(floor, 2, 4, 4);
        assertThat(catalog.latestCatalogRevision()).isEqualTo(revision);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT plan_version_high_water FROM campus_map_floor WHERE id=?",
                                Long.class,
                                floor))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT version FROM campus_map_plan_version WHERE id=(SELECT current_version_id FROM campus_map_floor WHERE id=?)",
                                Long.class,
                                floor))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM campus_map_deletion_receipt WHERE operation_id=?",
                                Long.class,
                                request.operationId()))
                .isZero();
        assertAssets(first, HttpStatus.OK);
        assertAssets(second, HttpStatus.OK);
        assertGuarded(second);
    }

    private PlanResponse upload(String floor) {
        return admin.uploadPlan(
                        "971",
                        floor,
                        "Синтетическая схема",
                        new MockMultipartFile("png", "map.png", "image/png", png),
                        new MockMultipartFile("svg", "map.svg", "image/svg+xml", svg))
                .plan();
    }

    private void seedUsage(long building, long floor) {
        UUID intent = UUID.randomUUID();
        var at = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        jdbc.update(
                "INSERT INTO campus_map_floor_daily_demand(floor_id,utc_day,open_count) VALUES (?,CURRENT_DATE,7)",
                floor);
        jdbc.update(
                "INSERT INTO campus_map_open_intent(owner_hmac,intent_id,building_id,floor_id,payload_hash,accepted_utc_day,accepted_at,expires_at) VALUES (?,?,?,?,?,?,?,?)",
                new byte[32],
                intent,
                building,
                floor,
                new byte[32],
                at.toLocalDate(),
                at,
                at.plusHours(48));
        jdbc.update(
                "INSERT INTO campus_map_floor_demand_dedupe(owner_hmac,floor_id,utc_day,intent_id,accepted_at) VALUES (?,?,?,?,?)",
                new byte[32],
                floor,
                at.toLocalDate(),
                intent,
                at);
        jdbc.update(
                "UPDATE campus_map_floor_daily_demand SET open_count=7 WHERE floor_id=?", floor);
    }

    private String legacyCurrentDigest(PlanResponse response) throws Exception {
        long floorId = Long.parseLong(response.floorId());
        var floor = catalog.findActiveFloorByCodes("971", "1").orElseThrow();
        var plan = catalog.findCurrentPlan(floorId).orElseThrow();
        String value =
                "PLAN_CLEAR:3:971:1:1:"
                        + floorId
                        + ":"
                        + floor.buildingId()
                        + ":"
                        + plan.id()
                        + ":null:"
                        + plan.label().length()
                        + ":"
                        + plan.label()
                        + ":"
                        + catalog.latestCatalogRevision();
        for (var slot : catalog.findSlots(plan.id())) {
            value +=
                    ":"
                            + slot.format()
                            + ":"
                            + slot.state()
                            + ":"
                            + slot.assetId()
                            + ":"
                            + slot.bytes()
                            + ":"
                            + (slot.sha256() == null
                                    ? "null"
                                    : HexFormat.of().formatHex(slot.sha256()));
        }
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private JsonNode preview(String floor) throws Exception {
        var response = get(previewUrl(floor));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        var preview = mapper.readTree(response.getBody());
        assertThat(preview.path("deletionScope").asText()).isEqualTo("ALL_VERSIONS");
        return preview;
    }

    private String previewUrl(String floor) {
        return "/academic/map/plans/target/deletion-preview?buildingCode=971&floorCode=" + floor;
    }

    private PlanDeleteRequest request(String floor, String digest) {
        return new PlanDeleteRequest(
                "971", floor, null, UUID.randomUUID(), digest, "synthetic-confirmation");
    }

    private ResponseEntity<String> delete(PlanDeleteRequest request) {
        return http.exchange(
                "/academic/map/plans/target/deletion",
                HttpMethod.POST,
                new HttpEntity<>(request, headers()),
                String.class);
    }

    private ResponseEntity<String> get(String url) {
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers()), String.class);
    }

    private HttpHeaders headers() {
        var headers = new HttpHeaders();
        headers.set("X-Internal-Token", actor.internalToken());
        return headers;
    }

    private String planUrl(PlanResponse plan) {
        return "/academic/map/floors/" + plan.floorId() + "/versions/" + plan.version();
    }

    private void assertAssets(PlanResponse plan, HttpStatus expected) {
        for (var slot : List.of(plan.png(), plan.svg())) {
            var result =
                    http.exchange(
                            planUrl(plan) + "/assets/" + slot.format() + "/" + slot.id(),
                            HttpMethod.GET,
                            new HttpEntity<>(headers()),
                            byte[].class);
            assertThat(result.getStatusCode()).isEqualTo(expected);
            if (expected == HttpStatus.OK)
                assertThat(result.getBody())
                        .containsExactly(slot.format() == Format.png ? png : svg);
        }
    }

    private void assertGraphCounts(long floor, long versions, long slots, long assets) {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM campus_map_plan_version WHERE floor_id=?",
                                Long.class,
                                floor))
                .isEqualTo(versions);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM campus_map_plan_format s JOIN campus_map_plan_version p ON p.id=s.plan_version_id WHERE p.floor_id=?",
                                Long.class,
                                floor))
                .isEqualTo(slots);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM campus_map_asset a JOIN campus_map_plan_version p ON p.id=a.plan_version_id WHERE p.floor_id=?",
                                Long.class,
                                floor))
                .isEqualTo(assets);
    }

    private void assertGuarded(PlanResponse plan) {
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "DELETE FROM campus_map_asset WHERE id=?",
                                        Long.parseLong(plan.png().id())))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
