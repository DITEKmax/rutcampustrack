package ru.rutcampustrack.auth.event;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import ru.rutcampustrack.shared.outbox.jpa.OutboxEntity;

@Entity
@Table(name = "auth_outbox")
public class AuthOutboxEntity extends OutboxEntity {
    public AuthOutboxEntity() {
    }
}
