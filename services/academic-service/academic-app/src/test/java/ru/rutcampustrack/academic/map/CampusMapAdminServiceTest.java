package ru.rutcampustrack.academic.map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels;
import ru.rutcampustrack.academic.exception.BadRequestException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampusMapAdminServiceTest {
    @Mock private CampusMapAdminRepository repository;

    private CampusMapAdminService service;

    @BeforeEach
    void setUp() {
        service = new CampusMapAdminService(repository);
    }

    @Test
    void invalidMimeAndUnsafeSvgAreRejectedBeforeTheFloorLock() {
        MockMultipartFile png = new MockMultipartFile(
                "png", "map.png", "image/svg+xml", validPng());

        assertThatThrownBy(() -> service.uploadVersion("200", null, png, null))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository);

        MockMultipartFile svg = new MockMultipartFile(
                "svg", "map.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                        .getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.uploadVersion("200", null, null, svg))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository);

        MockMultipartFile eventSvg = new MockMultipartFile(
                "svg", "event.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"></svg>"
                        .getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.uploadVersion("200", null, null, eventSvg))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void firstPngUploadPublishesAnExplicitAbsentSvgSlotAtomically() {
        CampusMapAdminRepository.FloorRow floor = new CampusMapAdminRepository.FloorRow(
                200L, 10L, "2", "Этаж 2", 0, null, true);
        CampusMapAdminRepository.PlanRow plan = new CampusMapAdminRepository.PlanRow(
                500L, 200L, 1L, 1L, "Этаж 2", OffsetDateTime.parse("2026-09-21T10:00:00Z"));
        byte[] png = validPng();
        byte[] sha = sha256(png);

        when(repository.findFloorForUpdate(200L)).thenReturn(Optional.of(floor));
        when(repository.findCurrentPlan(200L)).thenReturn(Optional.empty());
        when(repository.advanceCatalogRevision())
                .thenReturn(new CampusMapAdminRepository.CatalogRevision(1L, 1L));
        when(repository.insertPlan(200L, 1L, 1L, "Этаж 2")).thenReturn(500L);
        when(repository.insertAsset(eq(500L), eq(CampusMapFormat.PNG), eq("image/png"), any(byte[].class),
                eq((long) png.length), any(byte[].class), eq(640), eq(480))).thenReturn(700L);
        when(repository.findPlan(200L, 1L)).thenReturn(Optional.of(plan));
        when(repository.findSlots(500L)).thenReturn(List.of(
                new CampusMapAdminRepository.SlotRow(701L, 500L, CampusMapFormat.PNG,
                        CampusMapFormatState.READY, "image/png", 700L, png.length, sha, 640, 480),
                new CampusMapAdminRepository.SlotRow(702L, 500L, CampusMapFormat.SVG,
                        CampusMapFormatState.ABSENT, "image/svg+xml", null, 0, null, null, null)));

        CampusMapAdminModels.PlanResponse result = service.uploadVersion(
                "200", null,
                new MockMultipartFile("png", "map.png", "image/png", png),
                null);

        assertThat(result.version()).isEqualTo("1");
        assertThat(result.png().state()).isEqualTo(CampusMapAdminModels.FormatState.ready);
        assertThat(result.svg().state()).isEqualTo(CampusMapAdminModels.FormatState.absent);
        verify(repository).insertSlot(500L, CampusMapFormat.SVG, CampusMapFormatState.ABSENT,
                "image/svg+xml", null, 0L, null, null, null);
        verify(repository).publishPlan(500L);
        verify(repository).pointFloorAt(200L, 500L);
    }

    private static byte[] validPng() {
        byte[] content = new byte[24];
        System.arraycopy(new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        }, 0, content, 0, 8);
        ByteBuffer.wrap(content).order(ByteOrder.BIG_ENDIAN)
                .putInt(16, 640)
                .putInt(20, 480);
        return content;
    }

    @Test
    void deletionRequiresAdminAndLivePasswordEvenForACompletedReplay() {
        var tx = org.mockito.Mockito.mock(CampusMapDeletionTransaction.class);
        var auth = org.mockito.Mockito.mock(AuthMapDeletionClient.class);
        var context = new ru.rutcampustrack.academic.security.RequestContext();
        var deletion = new CampusMapDeletionService(tx, auth, context);
        var request = new CampusMapAdminModels.DeleteRequest(java.util.UUID.randomUUID(), "a".repeat(64), "password");
        context.setUserId(10L);
        context.setRole(ru.rutcampustrack.academic.contract.enums.UserRole.TEACHER);
        assertThatThrownBy(() -> deletion.delete(CampusMapAdminModels.DeletionTarget.FLOOR, "200", request))
                .isInstanceOf(ru.rutcampustrack.academic.exception.AccessDeniedException.class);
        verifyNoInteractions(auth, tx);
        context.setRole(ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN);
        org.mockito.Mockito.doThrow(new ru.rutcampustrack.academic.exception.AccessDeniedException("password"))
                .when(auth).confirm(CampusMapAdminModels.DeletionTarget.FLOOR, 200L,
                        request.operationId(), request.previewDigest(), request.password());
        assertThatThrownBy(() -> deletion.delete(CampusMapAdminModels.DeletionTarget.FLOOR, "200", request))
                .isInstanceOf(ru.rutcampustrack.academic.exception.AccessDeniedException.class);
        verifyNoInteractions(tx);
        org.mockito.Mockito.doNothing().when(auth).confirm(CampusMapAdminModels.DeletionTarget.FLOOR, 200L,
                request.operationId(), request.previewDigest(), request.password());
        deletion.delete(CampusMapAdminModels.DeletionTarget.FLOOR, "200", request);
        deletion.delete(CampusMapAdminModels.DeletionTarget.FLOOR, "200", request);
        verify(auth, org.mockito.Mockito.times(3)).confirm(CampusMapAdminModels.DeletionTarget.FLOOR, 200L,
                request.operationId(), request.previewDigest(), request.password());
        verify(tx, org.mockito.Mockito.times(2)).delete(CampusMapAdminModels.DeletionTarget.FLOOR, 200L, 10L, request);
    }

    private static byte[] sha256(byte[] content) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(content);
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }
}
