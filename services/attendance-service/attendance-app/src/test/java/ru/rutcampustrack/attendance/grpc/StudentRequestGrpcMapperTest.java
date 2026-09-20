package ru.rutcampustrack.attendance.grpc;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestModels;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudentRequestGrpcMapperTest {

    @Test
    void mapsCommentRequiredExplicitlyForOtherAndNonOtherReasons() {
        StudentRequestModels.RequestOptions domain = new StudentRequestModels.RequestOptions(
                List.of(
                        new StudentRequestModels.ReasonOption(ExcuseType.OTHER, "Другое", true),
                        new StudentRequestModels.ReasonOption(ExcuseType.ILLNESS, "Болезнь", false)),
                new StudentRequestModels.FileLimits(2, 10L, 20L, List.of(), List.of()),
                new StudentRequestModels.Budget(30L, 5, 0, 5), List.of());

        StudentRequestOptions result = StudentRequestGrpcMapper.options(domain);

        assertThat(result.getReasonsList()).hasSize(2);
        assertThat(result.getReasons(0).getCommentRequired()).isTrue();
        assertThat(result.getReasons(1).getCommentRequired()).isFalse();
    }
}