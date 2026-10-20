package ru.rutcampustrack.academic.map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels;
import ru.rutcampustrack.academic.exception.BadRequestException;

import java.nio.charset.StandardCharsets;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class CampusMapAdminServiceTest {
    @Mock private CampusMapAdminRepository repository;

    private CampusMapAdminService service;

    @BeforeEach
    void setUp() {
        service = new CampusMapAdminService(repository);
    }

    @Test
    void targetLookupRejectsNoncanonicalNumbersBeforeRepositoryAccess() {
        for (String code : List.of("0", "01", " 1", "1 ", "١", "1".repeat(65))) {
            assertThatThrownBy(() -> service.getPlanTarget(code, "2"))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> service.getPlanTarget("1", code))
                    .isInstanceOf(BadRequestException.class);
        }
        verifyNoInteractions(repository);
    }

    @Test
    void invalidMimeAndUnsafeSvgAreRejectedBeforeTheFloorLock() {
        MockMultipartFile png =
                new MockMultipartFile("png", "map.png", "image/svg+xml", validPng());

        assertThatThrownBy(() -> service.uploadVersion("200", null, png, null))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository);

        MockMultipartFile svg =
                new MockMultipartFile(
                        "svg",
                        "map.svg",
                        "image/svg+xml",
                        "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                                .getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.uploadVersion("200", null, null, svg))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository);

        MockMultipartFile eventSvg =
                new MockMultipartFile(
                        "svg",
                        "event.svg",
                        "image/svg+xml",
                        "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"></svg>"
                                .getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.uploadVersion("200", null, null, eventSvg))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository);
    }

    private static byte[] validPng() {
        return CampusMapFileFixture.png(640, 480);
    }

    @Test
    void deletionRequiresAdminAndLivePasswordEvenForACompletedReplay() {
        var tx = org.mockito.Mockito.mock(CampusMapDeletionTransaction.class);
        var auth = org.mockito.Mockito.mock(AuthMapDeletionClient.class);
        var context = new ru.rutcampustrack.academic.security.RequestContext();
        var deletion = new CampusMapDeletionService(tx, auth, context);
        org.springframework.test.util.ReflectionTestUtils.setField(
                deletion,
                "adminAccess",
                org.mockito.Mockito.mock(
                        ru.rutcampustrack.academic.admin.AcademicAdminAccess.class));
        var request =
                new CampusMapAdminModels.DeleteRequest(
                        java.util.UUID.randomUUID(), "a".repeat(64), "password");
        context.setUserId(10L);
        context.setRole(ru.rutcampustrack.academic.contract.enums.UserRole.TEACHER);
        assertThatThrownBy(
                        () ->
                                deletion.delete(
                                        CampusMapAdminModels.DeletionTarget.FLOOR, "200", request))
                .isInstanceOf(ru.rutcampustrack.academic.exception.AccessDeniedException.class);
        verifyNoInteractions(auth, tx);
        context.setRole(ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN);
        org.mockito.Mockito.doThrow(
                        new ru.rutcampustrack.academic.exception.AccessDeniedException("password"))
                .when(auth)
                .confirm(
                        CampusMapAdminModels.DeletionTarget.FLOOR,
                        200L,
                        request.operationId(),
                        request.previewDigest(),
                        request.password());
        assertThatThrownBy(
                        () ->
                                deletion.delete(
                                        CampusMapAdminModels.DeletionTarget.FLOOR, "200", request))
                .isInstanceOf(ru.rutcampustrack.academic.exception.AccessDeniedException.class);
        verifyNoInteractions(tx);
        org.mockito.Mockito.doNothing()
                .when(auth)
                .confirm(
                        CampusMapAdminModels.DeletionTarget.FLOOR,
                        200L,
                        request.operationId(),
                        request.previewDigest(),
                        request.password());
        deletion.delete(CampusMapAdminModels.DeletionTarget.FLOOR, "200", request);
        deletion.delete(CampusMapAdminModels.DeletionTarget.FLOOR, "200", request);
        verify(auth, org.mockito.Mockito.times(3))
                .confirm(
                        CampusMapAdminModels.DeletionTarget.FLOOR,
                        200L,
                        request.operationId(),
                        request.previewDigest(),
                        request.password());
        verify(tx, org.mockito.Mockito.times(2))
                .delete(CampusMapAdminModels.DeletionTarget.FLOOR, 200L, 10L, request);
    }

    private static byte[] sha256(byte[] content) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(content);
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }
}
