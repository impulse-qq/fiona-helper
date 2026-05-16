package io.promptforge.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assemble_session")
public class AssembleSessionEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "pipeline_id", nullable = false)
    public UUID pipelineId;

    @Column(name = "character_id")
    public UUID characterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public SessionStatus status = SessionStatus.CREATED;

    @Column(name = "current_slot_index", nullable = false)
    public int currentSlotIndex = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public Instant createdAt;

    public AssembleSessionEntity() {
    }
}
