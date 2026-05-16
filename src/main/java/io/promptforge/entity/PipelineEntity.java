package io.promptforge.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "pipeline")
public class PipelineEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "name", nullable = false, length = 128)
    public String name;

    @Column(name = "description")
    public String description;

    @Column(name = "is_public", nullable = false)
    public boolean isPublic = false;

    /** 项目级固定世界观，如 "赛博朋克" */
    @Column(name = "world_setting", length = 64)
    public String worldSetting;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public Instant updatedAt;

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("orderIndex ASC")
    public List<SlotEntity> slots = new ArrayList<>();

    public PipelineEntity() {
    }

    public PipelineEntity(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void addSlot(SlotEntity slot) {
        slots.add(slot);
        slot.pipeline = this;
    }

    public void removeSlot(SlotEntity slot) {
        slots.remove(slot);
        slot.pipeline = null;
    }
}
