package ru.rutcampustrack.academic.config;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for the explicit typed PostgreSQL enum binding. */
class SubjectTypeUserTypeTest {

    private final SubjectTypeUserType userType = new SubjectTypeUserType();

    @Test
    void bindsTypedLowercaseLabelsAndNullAsPostgresEnum() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);

        userType.nullSafeSet(statement, SubjectType.LAB, 2, null);
        userType.nullSafeSet(statement, null, 3, null);

        var bound = org.mockito.ArgumentCaptor.forClass(PGobject.class);
        verify(statement).setObject(eq(2), bound.capture());
        assertThat(bound.getValue().getType()).isEqualTo("subject_type");
        assertThat(bound.getValue().getValue()).isEqualTo("lab");
        verify(statement).setNull(3, Types.OTHER, "subject_type");
    }

    @Test
    void readsAllV25LabelsAndNullIntoThePublicEnum() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString(1)).thenReturn("lecture", "practice", "lab", null);

        assertThat(userType.nullSafeGet(resultSet, 1, null, null)).isEqualTo(SubjectType.LECTURE);
        assertThat(userType.nullSafeGet(resultSet, 1, null, null)).isEqualTo(SubjectType.PRACTICE);
        assertThat(userType.nullSafeGet(resultSet, 1, null, null)).isEqualTo(SubjectType.LAB);
        assertThat(userType.nullSafeGet(resultSet, 1, null, null)).isNull();
    }
}
