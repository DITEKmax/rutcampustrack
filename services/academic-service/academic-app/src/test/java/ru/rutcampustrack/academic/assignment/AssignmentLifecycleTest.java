package ru.rutcampustrack.academic.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.academic.config.AssignmentLifecycleConverter;

class AssignmentLifecycleTest {
    private final AssignmentLifecycleConverter converter = new AssignmentLifecycleConverter();

    @Test
    void stableCodesRoundTripWithoutOrdinalOrCaseChanges() {
        for (AssignmentLifecycle state : AssignmentLifecycle.values()) {
            assertThat(converter.convertToDatabaseColumn(state)).isEqualTo(state.name());
            assertThat(converter.convertToEntityAttribute(state.getCode())).isSameAs(state);
        }
    }

    @Test
    void unknownMissingAndNullNeverBecomeActive() {
        for (String code : new String[] {"", "active", "UNKNOWN", "0"}) {
            assertThatThrownBy(() -> converter.convertToEntityAttribute(code))
                    .isInstanceOf(
                            ru.rutcampustrack.academic.exception.UnknownAssignmentCodeException
                                    .class);
        }
        assertThatThrownBy(() -> converter.convertToEntityAttribute(null))
                .isInstanceOf(
                        ru.rutcampustrack.academic.exception.UnknownAssignmentCodeException.class);
        assertThatThrownBy(() -> converter.convertToDatabaseColumn(null))
                .isInstanceOf(
                        ru.rutcampustrack.academic.exception.UnknownAssignmentCodeException.class);
    }

    @Test
    void operationStateAndKindKeepTextAndRejectUnknownNullAndMissingCodes() {
        var states = new ru.rutcampustrack.academic.config.AssignmentOperationStateConverter();
        var kinds = new ru.rutcampustrack.academic.config.AssignmentOperationKindConverter();
        for (var state : AssignmentOperationState.values()) {
            assertThat(states.convertToDatabaseColumn(state)).isEqualTo(state.name());
            assertThat(states.convertToEntityAttribute(state.getCode())).isSameAs(state);
        }
        for (var kind : AssignmentOperationKind.values()) {
            assertThat(kinds.convertToDatabaseColumn(kind)).isEqualTo(kind.name());
            assertThat(kinds.convertToEntityAttribute(kind.getCode())).isSameAs(kind);
        }
        for (String code : new String[] {null, "", "prepared", "UNKNOWN", "0"}) {
            assertThatThrownBy(() -> states.convertToEntityAttribute(code))
                    .isInstanceOf(
                            ru.rutcampustrack.academic.exception.UnknownAssignmentCodeException
                                    .class);
            assertThatThrownBy(() -> kinds.convertToEntityAttribute(code))
                    .isInstanceOf(
                            ru.rutcampustrack.academic.exception.UnknownAssignmentCodeException
                                    .class);
        }
        assertThatThrownBy(() -> states.convertToDatabaseColumn(null))
                .isInstanceOf(
                        ru.rutcampustrack.academic.exception.UnknownAssignmentCodeException.class);
        assertThatThrownBy(() -> kinds.convertToDatabaseColumn(null))
                .isInstanceOf(
                        ru.rutcampustrack.academic.exception.UnknownAssignmentCodeException.class);
    }
}
