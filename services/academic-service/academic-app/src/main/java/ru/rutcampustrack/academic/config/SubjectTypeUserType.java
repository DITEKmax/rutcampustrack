package ru.rutcampustrack.academic.config;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Locale;

/**
 * Explicit PostgreSQL enum binding for subject lesson types.
 *
 * <p>The shared auto-apply converter is sufficient for ordinary enum columns,
 * but Hibernate does not consistently carry it into enum query parameters and
 * composite identifiers. This type keeps the public enum while binding a
 * typed PostgreSQL {@code subject_type} value, so composite-id tuple
 * comparisons resolve the enum's native equality operator.</p>
 */
public final class SubjectTypeUserType implements UserType<SubjectType> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<SubjectType> returnedClass() {
        return SubjectType.class;
    }

    @Override
    public boolean equals(SubjectType left, SubjectType right) {
        return left == right;
    }

    @Override
    public int hashCode(SubjectType value) {
        return value == null ? 0 : value.hashCode();
    }

    @Override
    public SubjectType nullSafeGet(ResultSet resultSet,
                                   int position,
                                   SharedSessionContractImplementor session,
                                   Object owner) throws SQLException {
        String value = resultSet.getString(position);
        return value == null ? null : SubjectType.valueOf(value.toUpperCase(Locale.ROOT));
    }

    @Override
    public void nullSafeSet(PreparedStatement statement,
                            SubjectType value,
                            int index,
                            SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.OTHER, "subject_type");
            return;
        }
        PGobject pgObject = new PGobject();
        pgObject.setType("subject_type");
        pgObject.setValue(value.name().toLowerCase(Locale.ROOT));
        statement.setObject(index, pgObject);
    }

    @Override
    public SubjectType deepCopy(SubjectType value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(SubjectType value) {
        return value;
    }

    @Override
    public SubjectType assemble(Serializable cached, Object owner) {
        return (SubjectType) cached;
    }

    @Override
    public SubjectType replace(SubjectType detached, SubjectType managed, Object owner) {
        return detached;
    }
}
