package io.promptforge.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "slot_draft")
public class SlotDraftEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "session_id", nullable = false)
    public UUID sessionId;

    @Column(name = "slot_id", nullable = false)
    public UUID slotId;

    @Column(name = "value", nullable = false, length = 4000)
    public String value;

    @Column(name = "filled_at")
    public Instant filledAt;

    public SlotDraftEntity() {
    }

    public SlotDraftEntity(UUID sessionId, UUID slotId, String value) {
        this.sessionId = sessionId;
        this.slotId = slotId;
        this.value = value;
        this.filledAt = Instant.now();
    }
}
