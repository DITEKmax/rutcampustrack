package ru.rutcampustrack.academic.group;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests для {@link GroupNameParser} и {@link ProgramType} (BUG-006-6 / план 58-06).
 */
class GroupNameParserTest {

    private final GroupNameParser parser = new GroupNameParser();

    // ---- ProgramType ----------------------------------------------------

    @Test
    void programType_fromDigit_1_returnsBachelor() {
        ProgramType t = ProgramType.fromDigit(1);
        assertThat(t).isEqualTo(ProgramType.BACHELOR);
        assertThat(t.getMaxCourse()).isEqualTo(4);
    }

    @Test
    void programType_fromDigit_7_returnsMaster() {
        ProgramType t = ProgramType.fromDigit(7);
        assertThat(t).isEqualTo(ProgramType.MASTER);
        assertThat(t.getMaxCourse()).isEqualTo(2);
    }

    @Test
    void programType_fromDigit_5_throwsUnknownProgramTypeException() {
        assertThatThrownBy(() -> ProgramType.fromDigit(5))
                .isInstanceOf(UnknownProgramTypeException.class)
                .extracting(e -> ((UnknownProgramTypeException) e).getDigit())
                .isEqualTo(5);
    }

    // ---- Parser — happy path --------------------------------------------

    @Test
    void parse_UIT311() {
        GroupNameParser.ParsedName p = parser.parse("УИТ-311");
        assertThat(p.prefix()).isEqualTo("УИТ");
        assertThat(p.course()).isEqualTo(3);
        assertThat(p.type()).isEqualTo(1);
        assertThat(p.number()).isEqualTo(1);
        assertThat(p.toActive()).isEqualTo("УИТ-311");
    }

    @Test
    void parse_UVPv511_withLowercaseTailLetter() {
        GroupNameParser.ParsedName p = parser.parse("УВПв-511");
        assertThat(p.prefix()).isEqualTo("УВПв");
        assertThat(p.course()).isEqualTo(5);
        assertThat(p.type()).isEqualTo(1);
        assertThat(p.number()).isEqualTo(1);
    }

    // ---- Parser — rejection ---------------------------------------------

    @Test
    void parse_archivedFormat_throws() {
        assertThatThrownBy(() -> parser.parse("УИТ-411 (выпуск 2026)"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_lowercaseFirstLetter_throws() {
        assertThatThrownBy(() -> parser.parse("уит-311"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_twoDigitsAfterDash_throws() {
        assertThatThrownBy(() -> parser.parse("УИТ-31"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_null_throws() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_empty_throws() {
        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- promote --------------------------------------------------------

    @Test
    void promote_bachelor_course1_to_course2() {
        GroupNameParser.PromoteResult r = parser.promote("УИТ-111", ProgramType.BACHELOR);
        assertThat(r).isInstanceOf(GroupNameParser.PromoteResult.Renamed.class);
        assertThat(((GroupNameParser.PromoteResult.Renamed) r).newName()).isEqualTo("УИТ-211");
    }

    @Test
    void promote_bachelor_course4_archived() {
        GroupNameParser.PromoteResult r = parser.promote("УИТ-411", ProgramType.BACHELOR);
        assertThat(r).isInstanceOf(GroupNameParser.PromoteResult.Archived.class);
    }

    @Test
    void promote_master_course2_archived() {
        GroupNameParser.PromoteResult r = parser.promote("УИТ-271", ProgramType.MASTER);
        assertThat(r).isInstanceOf(GroupNameParser.PromoteResult.Archived.class);
    }

    @Test
    void promote_master_course1_to_course2() {
        GroupNameParser.PromoteResult r = parser.promote("УИТ-171", ProgramType.MASTER);
        assertThat(r).isInstanceOf(GroupNameParser.PromoteResult.Renamed.class);
        assertThat(((GroupNameParser.PromoteResult.Renamed) r).newName()).isEqualTo("УИТ-271");
    }
}
